package com.kidas.studiopro;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Real PCM-based audio editor used by the Studio Pro editor dialog. */
public final class AudioEditEngine {
    private AudioEditEngine() {}

    public static final class SilenceRange {
        public final long startMs;
        public final long endMs;
        SilenceRange(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static final class PcmData {
        final byte[] pcm;
        final int sampleRate;
        final int channels;
        PcmData(byte[] pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    public static File process(File source, File destination, long startMs, long endMs,
                               float gain, float speed, float fadeInSec, float fadeOutSec,
                               boolean normalize) throws Exception {
        validateArguments(source, destination, startMs, endMs, gain, speed, fadeInSec, fadeOutSec);

        PcmData data = decodePcm16(source);
        int frameBytes = data.channels * 2;
        long totalFrames = data.pcm.length / frameBytes;
        long startFrame = clampFrame(startMs, data.sampleRate, totalFrames);
        long endFrame = endMs > startMs ? clampFrame(endMs, data.sampleRate, totalFrames) : totalFrames;
        if (endFrame <= startFrame) throw new Exception("La zone audio sélectionnée est vide.");

        long selectedFrames = endFrame - startFrame;
        if (selectedFrames > Integer.MAX_VALUE / Math.max(1, frameBytes)) {
            throw new Exception("La sélection audio est trop grande pour être traitée en mémoire.");
        }

        byte[] trimmed = Arrays.copyOfRange(data.pcm, (int) (startFrame * frameBytes), (int) (endFrame * frameBytes));
        int outSampleRate = data.sampleRate;
        float safeSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        if (Math.abs(safeSpeed - 1.0f) > 0.001f) {
            trimmed = resampleSpeed(trimmed, data.channels, safeSpeed);
        }

        float safeGain = Math.max(0.0f, Math.min(2.0f, gain));
        if (normalize) safeGain *= computeNormalizeGain(trimmed);
        applyGainAndFades(trimmed, data.channels, data.sampleRate, safeGain, fadeInSec, fadeOutSec);

        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("Impossible de créer le dossier de sortie.");
        }

        File temp = new File(parent == null ? destination.getAbsoluteFile().getParentFile() : parent,
                "." + destination.getName() + ".studiopro.tmp");
        if (temp.equals(source.getAbsoluteFile())) {
            throw new Exception("La destination temporaire ne peut pas remplacer la source.");
        }

        try {
            if (temp.exists() && !temp.delete()) throw new Exception("Impossible de préparer le fichier temporaire audio.");
            writeWav(temp, trimmed, outSampleRate, data.channels, 16);
            if (!temp.exists() || temp.length() < 44) throw new Exception("Résultat audio invalide.");
            if (destination.exists() && !destination.delete()) throw new Exception("Impossible de remplacer le résultat audio.");
            if (!temp.renameTo(destination)) throw new Exception("Impossible de finaliser le résultat audio.");
            return destination;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private static void validateArguments(File source, File destination, long startMs, long endMs,
                                          float gain, float speed, float fadeInSec, float fadeOutSec) throws Exception {
        if (source == null || !source.exists() || !source.isFile() || source.length() == 0) {
            throw new Exception("Fichier audio source introuvable.");
        }
        if (destination == null || destination.isDirectory()) {
            throw new Exception("Destination audio invalide.");
        }
        File sourceAbsolute = source.getAbsoluteFile();
        File destinationAbsolute = destination.getAbsoluteFile();
        if (sourceAbsolute.equals(destinationAbsolute)) {
            throw new Exception("La source et la destination audio doivent être différentes.");
        }
        if (startMs < 0 || (endMs > 0 && endMs <= startMs)) {
            throw new Exception("Découpe audio invalide.");
        }
        if (!Float.isFinite(gain) || gain < 0f || gain > 2f) {
            throw new Exception("Gain audio invalide.");
        }
        if (!Float.isFinite(speed) || speed < 0.5f || speed > 2f) {
            throw new Exception("Vitesse audio invalide.");
        }
        if (!Float.isFinite(fadeInSec) || !Float.isFinite(fadeOutSec) || fadeInSec < 0f || fadeOutSec < 0f) {
            throw new Exception("Fondu audio invalide.");
        }
    }

    public static SilenceRange detectSilence(File source) throws Exception {
        PcmData data = decodePcm16(source);
        int frameBytes = data.channels * 2;
        long totalFrames = data.pcm.length / frameBytes;
        if (totalFrames == 0) return new SilenceRange(0, 0);

        int windowFrames = Math.max(1, data.sampleRate / 50); // 20 ms
        final double threshold = 0.018;
        int firstActive = -1;
        int lastActive = -1;
        int quietWindows = 0;
        int activeWindows = 0;

        for (int frame = 0; frame < totalFrames; frame += windowFrames) {
            int end = (int) Math.min(totalFrames, frame + windowFrames);
            double sum = 0.0;
            long samples = 0;
            for (int f = frame; f < end; f++) {
                int base = f * frameBytes;
                for (int ch = 0; ch < data.channels; ch++) {
                    int lo = data.pcm[base + ch * 2] & 0xFF;
                    int hi = data.pcm[base + ch * 2 + 1];
                    short sample = (short) ((hi << 8) | lo);
                    double n = sample / 32768.0;
                    sum += n * n;
                    samples++;
                }
            }
            double rms = samples == 0 ? 0 : Math.sqrt(sum / samples);
            if (rms >= threshold) {
                if (firstActive < 0) firstActive = frame;
                lastActive = end;
                activeWindows++;
                quietWindows = 0;
            } else {
                quietWindows++;
                if (firstActive >= 0 && quietWindows >= 3) break;
            }
        }

        if (firstActive < 0 || activeWindows == 0) {
            return new SilenceRange(0, Math.max(0, totalFrames * 1000L / data.sampleRate));
        }

        long startMs = Math.max(0, firstActive * 1000L / data.sampleRate);
        long endMs = Math.min(totalFrames * 1000L / data.sampleRate, lastActive * 1000L / data.sampleRate);
        if (endMs <= startMs) return new SilenceRange(0, totalFrames * 1000L / data.sampleRate);
        return new SilenceRange(startMs, endMs);
    }

    private static long clampFrame(long ms, int sampleRate, long totalFrames) {
        long f = Math.max(0, (ms * sampleRate) / 1000L);
        return Math.min(totalFrames, f);
    }

    private static float computeNormalizeGain(byte[] pcm) {
        int peak = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int lo = pcm[i] & 0xFF;
            int hi = pcm[i + 1];
            int s = Math.abs((short) ((hi << 8) | lo));
            if (s > peak) peak = s;
        }
        if (peak <= 0) return 1.0f;
        return Math.min(2.0f, 0.98f * 32767.0f / peak);
    }

    private static void applyGainAndFades(byte[] pcm, int channels, int sampleRate,
                                          float gain, float fadeInSec, float fadeOutSec) {
        long frames = pcm.length / (channels * 2L);
        long fadeInFrames = Math.min(frames, Math.max(0L, Math.round(fadeInSec * sampleRate)));
        long fadeOutFrames = Math.min(frames, Math.max(0L, Math.round(fadeOutSec * sampleRate)));
        for (long f = 0; f < frames; f++) {
            float factor = gain;
            if (fadeInFrames > 0 && f < fadeInFrames) factor *= f / (float) fadeInFrames;
            if (fadeOutFrames > 0 && f >= frames - fadeOutFrames) factor *= (frames - 1 - f) / (float) fadeOutFrames;
            int base = (int) (f * channels * 2L);
            for (int ch = 0; ch < channels; ch++) {
                int idx = base + ch * 2;
                int lo = pcm[idx] & 0xFF;
                int hi = pcm[idx + 1];
                int sample = (short) ((hi << 8) | lo);
                int out = Math.max(-32768, Math.min(32767, Math.round(sample * factor)));
                pcm[idx] = (byte) (out & 0xFF);
                pcm[idx + 1] = (byte) ((out >> 8) & 0xFF);
            }
        }
    }

    private static byte[] resampleSpeed(byte[] pcm, int channels, float speed) {
        int frameBytes = channels * 2;
        int inFrames = pcm.length / frameBytes;
        int outFrames = Math.max(1, Math.round(inFrames / speed));
        if (outFrames > Integer.MAX_VALUE / frameBytes) throw new IllegalArgumentException("Audio trop volumineux.");
        byte[] out = new byte[outFrames * frameBytes];
        for (int i = 0; i < outFrames; i++) {
            float src = i * speed;
            int a = Math.min(inFrames - 1, (int) Math.floor(src));
            int b = Math.min(inFrames - 1, a + 1);
            float frac = src - a;
            for (int ch = 0; ch < channels; ch++) {
                int ia = a * frameBytes + ch * 2;
                int ib = b * frameBytes + ch * 2;
                short sa = (short) (((pcm[ia + 1] & 0xFF) << 8) | (pcm[ia] & 0xFF));
                short sb = (short) (((pcm[ib + 1] & 0xFF) << 8) | (pcm[ib] & 0xFF));
                int sample = Math.round(sa + (sb - sa) * frac);
                int io = i * frameBytes + ch * 2;
                out[io] = (byte) (sample & 0xFF);
                out[io + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return out;
    }

    private static PcmData decodePcm16(File source) throws Exception {
        if (source == null || !source.exists() || !source.isFile() || source.length() == 0) throw new Exception("Fichier audio introuvable ou vide.");
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; break; }
            }
            if (track < 0) throw new Exception("Aucune piste audio décodable.");
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long deadline = System.currentTimeMillis() + 120000L;
            while (!outputDone) {
                if (System.currentTimeMillis() > deadline) throw new Exception("Décodage audio trop long.");
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(5000);
                    if (inIndex >= 0) {
                        ByteBuffer in = decoder.getInputBuffer(inIndex);
                        if (in == null) throw new Exception("Buffer audio indisponible.");
                        int size = extractor.readSampleData(in, 0);
                        long pts = extractor.getSampleTime();
                        if (size < 0 || pts < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, pts, extractor.getSampleFlags());
                            extractor.advance();
                        }
                    }
                }
                int outIndex = decoder.dequeueOutputBuffer(info, 5000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = decoder.getOutputFormat();
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) && outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) != android.media.AudioFormat.ENCODING_PCM_16BIT) {
                        throw new Exception("Le codec audio ne fournit pas de PCM 16 bits supporté par l'éditeur.");
                    }
                    continue;
                }
                if (outIndex >= 0) {
                    ByteBuffer out = decoder.getOutputBuffer(outIndex);
                    if (out != null && info.size > 0) {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        byte[] chunk = new byte[info.size];
                        out.get(chunk);
                        pcm.write(chunk);
                    }
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outIndex, false);
                    if (eos) outputDone = true;
                }
            }
            if (pcm.size() == 0) throw new Exception("Le décodage audio n'a produit aucune donnée PCM.");
            if (sampleRate <= 0 || channels <= 0) throw new Exception("Format PCM audio invalide.");
            if ((pcm.size() % (channels * 2)) != 0) throw new Exception("Données PCM audio incomplètes.");
            return new PcmData(pcm.toByteArray(), sampleRate, channels);
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                try { decoder.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static void writeWav(File file, byte[] pcm, int sampleRate, int channels, int bitsPerSample) throws Exception {
        if (file == null) throw new Exception("Fichier WAV invalide.");
        if (pcm == null || pcm.length == 0) throw new Exception("Données PCM vides.");
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeAscii(out, "RIFF");
            writeLE32(out, 36 + pcm.length);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeLE32(out, 16);
            writeLE16(out, 1);
            writeLE16(out, channels);
            writeLE32(out, sampleRate);
            writeLE32(out, byteRate);
            writeLE16(out, blockAlign);
            writeLE16(out, bitsPerSample);
            writeAscii(out, "data");
            writeLE32(out, pcm.length);
            out.write(pcm);
        }
    }

    private static void writeAscii(FileOutputStream out, String value) throws Exception {
        out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static void writeLE16(FileOutputStream out, int v) throws Exception {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF);
    }
    private static void writeLE32(FileOutputStream out, int v) throws Exception {
        out.write(v & 0xFF); out.write((v >> 8) & 0xFF); out.write((v >> 16) & 0xFF); out.write((v >> 24) & 0xFF);
    }
}
