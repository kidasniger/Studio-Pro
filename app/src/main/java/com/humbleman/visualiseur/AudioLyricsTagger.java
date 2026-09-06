package com.kidas.studiopro;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AudioLyricsTagger {
    private AudioLyricsTagger() {}

    public static final class ExtractionResult {
        public final List<MainActivity.LyricLine> lines;
        public final String plainText;
        public final String sourceFormat;
        public ExtractionResult(List<MainActivity.LyricLine> lines, String plainText, String sourceFormat) {
            this.lines = lines == null ? new ArrayList<>() : lines;
            this.plainText = plainText == null ? "" : plainText;
            this.sourceFormat = sourceFormat == null ? "Unknown" : sourceFormat;
        }
    }

    public static ExtractionResult readEmbeddedLyrics(File file) {
        if (file == null || !file.exists() || file.length() < 10) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] h = new byte[10];
            if (in.read(h) != 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return null;
            return readId3(file, h);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ExtractionResult readEmbeddedLyrics(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            byte[] h = new byte[10];
            int read = in.read(h);
            if (read != 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return null;
            int version = h[3] & 0xFF;
            int size = synchsafe(h, 6);
            if (size <= 0 || size > 16 * 1024 * 1024) return null;
            byte[] data = new byte[size];
            int got = 0;
            while (got < size) {
                int n = in.read(data, got, size - got);
                if (n < 0) break;
                got += n;
            }
            return parseId3Data(data, version, got);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ExtractionResult readId3(File file, byte[] h) {
        try (FileInputStream in = new FileInputStream(file)) {
            int version = h[3] & 0xFF;
            int size = synchsafe(h, 6);
            if (size <= 0 || size > 32 * 1024 * 1024) return null;
            byte[] data = new byte[size];
            if (in.skip(10) != 10) return null;
            int got = 0;
            while (got < size) {
                int n = in.read(data, got, size - got);
                if (n < 0) break;
                got += n;
            }
            return parseId3Data(data, version, got);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ExtractionResult parseId3Data(byte[] data, int version, int got) {
        try {
            String uslt = null;
            List<MainActivity.LyricLine> sylt = null;
            int p = 0;
            while (p + 10 <= got) {
                String id = new String(data, p, 4, StandardCharsets.ISO_8859_1);
                if (id.charAt(0) == 0) break;
                int fs = version == 4 ? synchsafe(data, p + 4) : int32(data, p + 4);
                if (fs <= 0 || p + 10 + fs > got) break;
                if ("USLT".equals(id)) uslt = parseUslt(data, p + 10, fs);
                if ("SYLT".equals(id)) sylt = parseSylt(data, p + 10, fs);
                p += 10 + fs;
            }
            if (sylt != null && !sylt.isEmpty()) {
                StringBuilder plain = new StringBuilder();
                for (MainActivity.LyricLine line : sylt) {
                    if (plain.length() > 0) plain.append('\n');
                    plain.append(line.text);
                }
                return new ExtractionResult(sylt, plain.toString(), "ID3v2 SYLT");
            }
            if (uslt != null && !uslt.trim().isEmpty()) {
                List<MainActivity.LyricLine> parsed = MainActivity.LyricLine.parseLrcString(uslt);
                if (!parsed.isEmpty()) return new ExtractionResult(parsed, uslt, "ID3v2 USLT");
                List<MainActivity.LyricLine> one = new ArrayList<>();
                one.add(new MainActivity.LyricLine(0, 10000, uslt));
                return new ExtractionResult(one, uslt, "ID3v2 USLT");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String parseUslt(byte[] data, int off, int size) {
        if (size < 5) return null;
        int enc = data[off] & 0xFF;
        int p = skipTerm(data, off + 4, off + size, enc);
        return p < off + size ? decode(data, p, off + size - p, enc) : null;
    }

    private static List<MainActivity.LyricLine> parseSylt(byte[] data, int off, int size) {
        if (size < 7) return null;
        int enc = data[off] & 0xFF;
        int end = off + size;
        int p = skipTerm(data, off + 6, end, enc);
        List<MainActivity.LyricLine> out = new ArrayList<>();
        while (p < end) {
            int start = p;
            p = skipTerm(data, p, end, enc);
            if (p <= start || p + 4 > end) break;
            int textLen = p - start - ((enc == 1 || enc == 2) ? 2 : 1);
            if (textLen < 0) break;
            String text = decode(data, start, textLen, enc).trim();
            long ts = ((data[p] & 0xFFL) << 24) | ((data[p + 1] & 0xFFL) << 16) | ((data[p + 2] & 0xFFL) << 8) | (data[p + 3] & 0xFFL);
            p += 4;
            if (!text.isEmpty()) out.add(new MainActivity.LyricLine(ts, ts + 3500, text));
        }
        for (int i = 0; i + 1 < out.size(); i++) {
            MainActivity.LyricLine a = out.get(i), b = out.get(i + 1);
            out.set(i, new MainActivity.LyricLine(a.startMs, Math.max(a.startMs + 250, b.startMs), a.text));
        }
        return out;
    }

    public static File embedLyricsIntoAudio(Context context, File source, String mime, String title, String artist,
                                             List<MainActivity.LyricLine> lines, String plain) throws Exception {
        if (source == null || !source.exists()) throw new Exception("Fichier source introuvable.");
        String name = source.getName().toLowerCase(Locale.ROOT);
        String mt = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".mp3") || mt.contains("mpeg") || mt.contains("mp3"))) {
            throw new Exception("L'intégration USLT/SYLT sur place est disponible pour les fichiers MP3.");
        }
        if ((lines == null || lines.isEmpty()) && (plain == null || plain.trim().isEmpty())) throw new Exception("Aucune parole à intégrer.");
        File out = new File(context.getCacheDir(), "tagged_" + System.nanoTime() + "_" + source.getName());
        rewriteMp3PreservingFrames(source, out, title, artist, lines, plain);
        return out;
    }

    private static void rewriteMp3PreservingFrames(File src, File dst, String title, String artist,
                                                    List<MainActivity.LyricLine> lines, String plain) throws Exception {
        byte[] input;
        try (FileInputStream in = new FileInputStream(src); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
            input = b.toByteArray();
        }
        int oldVersion = 3;
        int oldTagBytes = 0;
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        if (input.length >= 10 && input[0] == 'I' && input[1] == 'D' && input[2] == '3') {
            oldVersion = (input[3] & 0xFF) == 4 ? 4 : 3;
            int oldSize = synchsafe(input, 6);
            oldTagBytes = 10 + oldSize;
            if (oldTagBytes <= input.length) {
                int p = 10;
                int end = 10 + oldSize;
                while (p + 10 <= end) {
                    String id = new String(input, p, 4, StandardCharsets.ISO_8859_1);
                    if (id.charAt(0) == 0) break;
                    int fs = oldVersion == 4 ? synchsafe(input, p + 4) : int32(input, p + 4);
                    if (fs <= 0 || p + 10 + fs > end) break;
                    boolean isLyrics = "USLT".equals(id) || "SYLT".equals(id) || "ULT".equals(id) || "SLT".equals(id)
                            || isLyricsTxxx(input, p + 10, fs, oldVersion) || isLyricsComm(input, p + 10, fs, oldVersion);
                    if (!isLyrics) kept.write(input, p, 10 + fs);
                    p += 10 + fs;
                }
            }
        }

        StringBuilder lrc = new StringBuilder();
        StringBuilder plainTextSb = new StringBuilder();
        if (lines != null && !lines.isEmpty()) {
            for (MainActivity.LyricLine line : lines) {
                long m = line.startMs / 60000;
                long s = (line.startMs % 60000) / 1000;
                long cs = (line.startMs % 1000) / 10;
                lrc.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", m, s, cs, line.text));
                if (plainTextSb.length() > 0) plainTextSb.append("\n");
                plainTextSb.append(line.text);
            }
        } else if (plain != null && !plain.trim().isEmpty()) {
            lrc.append(plain.trim());
            plainTextSb.append(plain.trim());
        }
        String lrcText = lrc.toString().trim();
        String plainLyrics = plainTextSb.toString().trim();

        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(kept.toByteArray());
        if (title != null && !title.trim().isEmpty()) textFrame(frames, "TIT2", title.trim(), oldVersion);
        if (artist != null && !artist.trim().isEmpty()) textFrame(frames, "TPE1", artist.trim(), oldVersion);
        
        if (!lrcText.isEmpty()) {
            // USLT avec tag "xxx" (universel tous lecteurs/Musicolet) contenant le format LRC synchronisé
            usltFrame(frames, lrcText, "xxx", oldVersion);
            // USLT avec tag "eng" pour les lecteurs configurés en anglais
            usltFrame(frames, lrcText, "eng", oldVersion);
            // Si des lignes synchronisées sont fournies, générer aussi la trame binaire SYLT
            if (lines != null && !lines.isEmpty()) {
                syltFrame(frames, lines, oldVersion);
            }
            // Trames TXXX universelles pour les lecteurs alternatifs (foobar2000, Musicolet, TagLib)
            txxxFrame(frames, "LYRICS", lrcText, oldVersion);
            txxxFrame(frames, "SYNCEDLYRICS", lrcText, oldVersion);
            if (!plainLyrics.isEmpty() && !plainLyrics.equals(lrcText)) {
                txxxFrame(frames, "UNSYNCEDLYRICS", plainLyrics, oldVersion);
            }
        }

        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        tag.write('I'); tag.write('D'); tag.write('3'); tag.write(oldVersion); tag.write(0); tag.write(0);
        writeSynchsafe(tag, frames.size());
        tag.write(frames.toByteArray());

        int payload = oldTagBytes > 0 && oldTagBytes <= input.length ? oldTagBytes : 0;
        try (FileOutputStream out = new FileOutputStream(dst)) {
            out.write(tag.toByteArray());
            out.write(input, payload, input.length - payload);
        }
    }

    private static boolean isLyricsTxxx(byte[] data, int off, int size, int version) {
        if (size < 2) return false;
        int enc = data[off] & 0xFF;
        int end = off + size;
        int p = skipTerm(data, off + 1, end, enc);
        if (p <= off + 1) return false;
        int len = p - (off + 1) - ((enc == 1 || enc == 2) ? 2 : 1);
        if (len <= 0) return false;
        String desc = decode(data, off + 1, len, enc).trim();
        return "LYRICS".equalsIgnoreCase(desc) || "SYNCEDLYRICS".equalsIgnoreCase(desc) || "UNSYNCEDLYRICS".equalsIgnoreCase(desc);
    }

    private static boolean isLyricsComm(byte[] data, int off, int size, int version) {
        if (size < 5) return false;
        int enc = data[off] & 0xFF;
        int end = off + size;
        int p = skipTerm(data, off + 4, end, enc);
        if (p <= off + 4) return false;
        int len = p - (off + 4) - ((enc == 1 || enc == 2) ? 2 : 1);
        if (len <= 0) return false;
        String desc = decode(data, off + 4, len, enc).trim();
        return "LYRICS".equalsIgnoreCase(desc) || "PAROLES".equalsIgnoreCase(desc) || "SYNCEDLYRICS".equalsIgnoreCase(desc);
    }

    private static void textFrame(ByteArrayOutputStream out, String id, String text, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        if (version == 4) {
            p.write(3); // UTF-8
            p.write(text.getBytes(StandardCharsets.UTF_8));
        } else {
            p.write(1); // UTF-16 with BOM (Standard ID3v2.3)
            p.write(text.getBytes(StandardCharsets.UTF_16));
        }
        frame(out, id, p.toByteArray(), version);
    }

    private static void usltFrame(ByteArrayOutputStream out, String text, String lang, int version) throws Exception {
        if (text == null || text.trim().isEmpty()) return;
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        byte[] langBytes = (lang == null || lang.length() < 3) ? "xxx".getBytes(StandardCharsets.ISO_8859_1) : lang.substring(0, 3).toLowerCase(Locale.US).getBytes(StandardCharsets.ISO_8859_1);
        
        if (version == 4) {
            p.write(3); // UTF-8
            p.write(langBytes);
            p.write(0); // description vide terminée par 0
            p.write(text.getBytes(StandardCharsets.UTF_8));
        } else {
            p.write(1); // UTF-16 avec BOM pour compatibilité totale ID3v2.3 (Musicolet, Samsung Music, Poweramp)
            p.write(langBytes);
            // Description vide terminée en UTF-16 (0x00, 0x00)
            p.write(0); p.write(0);
            p.write(text.getBytes(StandardCharsets.UTF_16));
        }
        frame(out, "USLT", p.toByteArray(), version);
    }

    private static void syltFrame(ByteArrayOutputStream out, List<MainActivity.LyricLine> lines, int version) throws Exception {
        if (lines == null || lines.isEmpty()) return;
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        if (version == 4) {
            p.write(3); // UTF-8
            p.write("xxx".getBytes(StandardCharsets.ISO_8859_1));
            p.write(2); // format horodatage : millisecondes
            p.write(1); // type de contenu : paroles (lyrics)
            p.write(0); // description vide
            for (MainActivity.LyricLine line : lines) {
                p.write(line.text.getBytes(StandardCharsets.UTF_8));
                p.write(0);
                long ms = Math.max(0, line.startMs);
                p.write((int) (ms >> 24) & 0xFF);
                p.write((int) (ms >> 16) & 0xFF);
                p.write((int) (ms >> 8) & 0xFF);
                p.write((int) ms & 0xFF);
            }
        } else {
            p.write(1); // UTF-16 avec BOM
            p.write("xxx".getBytes(StandardCharsets.ISO_8859_1));
            p.write(2); // format horodatage : millisecondes
            p.write(1); // type de contenu : paroles
            p.write(0); p.write(0); // description vide en UTF-16
            for (MainActivity.LyricLine line : lines) {
                p.write(line.text.getBytes(StandardCharsets.UTF_16));
                p.write(0); p.write(0);
                long ms = Math.max(0, line.startMs);
                p.write((int) (ms >> 24) & 0xFF);
                p.write((int) (ms >> 16) & 0xFF);
                p.write((int) (ms >> 8) & 0xFF);
                p.write((int) ms & 0xFF);
            }
        }
        frame(out, "SYLT", p.toByteArray(), version);
    }

    private static void txxxFrame(ByteArrayOutputStream out, String desc, String text, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        if (version == 4) {
            p.write(3); // UTF-8
            p.write(desc.getBytes(StandardCharsets.UTF_8));
            p.write(0);
            p.write(text.getBytes(StandardCharsets.UTF_8));
        } else {
            p.write(1); // UTF-16 avec BOM
            p.write(desc.getBytes(StandardCharsets.UTF_16));
            p.write(0); p.write(0);
            p.write(text.getBytes(StandardCharsets.UTF_16));
        }
        frame(out, "TXXX", p.toByteArray(), version);
    }

    private static void frame(ByteArrayOutputStream out, String id, byte[] payload, int version) throws Exception {
        out.write(id.getBytes(StandardCharsets.ISO_8859_1));
        if (version == 4) writeSynchsafe(out, payload.length);
        else { out.write((payload.length >> 24) & 0xFF); out.write((payload.length >> 16) & 0xFF); out.write((payload.length >> 8) & 0xFF); out.write(payload.length & 0xFF); }
        out.write(0); out.write(0); out.write(payload);
    }

    private static int skipTerm(byte[] d, int p, int end, int enc) {
        if (enc == 1 || enc == 2) {
            while (p + 1 < end) { if (d[p] == 0 && d[p + 1] == 0) return p + 2; p += 2; }
            return end;
        }
        while (p < end) { if (d[p] == 0) return p + 1; p++; }
        return end;
    }

    private static String decode(byte[] d, int off, int len, int enc) {
        if (len <= 0) return "";
        try {
            if (enc == 1) return new String(d, off, len, StandardCharsets.UTF_16);
            if (enc == 2) return new String(d, off, len, StandardCharsets.UTF_16BE);
            if (enc == 3) return new String(d, off, len, StandardCharsets.UTF_8);
            return new String(d, off, len, StandardCharsets.ISO_8859_1);
        } catch (Exception e) { return new String(d, off, len, StandardCharsets.UTF_8); }
    }

    public static final class TagVerificationResult {
        public final boolean validId3;
        public final boolean hasUslt;
        public final boolean hasSylt;
        public final boolean hasApicCover;
        public final int totalFramesCount;

        public TagVerificationResult(boolean validId3, boolean hasUslt, boolean hasSylt, boolean hasApicCover, int totalFramesCount) {
            this.validId3 = validId3;
            this.hasUslt = hasUslt;
            this.hasSylt = hasSylt;
            this.hasApicCover = hasApicCover;
            this.totalFramesCount = totalFramesCount;
        }

        public String getSummary() {
            return String.format(Locale.FRENCH, "ID3v2 validé (%d frames, USLT: %s, SYLT: %s, Pochette APIC: %s)",
                    totalFramesCount,
                    hasUslt ? "Oui" : "Non",
                    hasSylt ? "Oui" : "Non",
                    hasApicCover ? "Conservée" : "Absente");
        }
    }

    public static TagVerificationResult verifyEmbeddedTags(File file) {
        if (file == null || !file.exists() || file.length() < 10) {
            return new TagVerificationResult(false, false, false, false, 0);
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] h = new byte[10];
            if (in.read(h) != 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') {
                return new TagVerificationResult(false, false, false, false, 0);
            }
            int version = h[3] & 0xFF;
            int size = synchsafe(h, 6);
            if (size <= 0 || size > 32 * 1024 * 1024) {
                return new TagVerificationResult(true, false, false, false, 0);
            }
            byte[] data = new byte[size];
            int got = 0;
            while (got < size) {
                int n = in.read(data, got, size - got);
                if (n < 0) break;
                got += n;
            }
            boolean hasUslt = false;
            boolean hasSylt = false;
            boolean hasApic = false;
            int count = 0;
            int p = 0;
            while (p + 10 <= got) {
                String id = new String(data, p, 4, StandardCharsets.ISO_8859_1);
                if (id.charAt(0) == 0) break;
                int fs = version == 4 ? synchsafe(data, p + 4) : int32(data, p + 4);
                if (fs <= 0 || p + 10 + fs > got) break;
                count++;
                if ("USLT".equals(id)) hasUslt = true;
                if ("SYLT".equals(id)) hasSylt = true;
                if ("APIC".equals(id) || "PIC".equals(id)) hasApic = true;
                p += 10 + fs;
            }
            return new TagVerificationResult(true, hasUslt, hasSylt, hasApic, count);
        } catch (Exception ignored) {
            return new TagVerificationResult(false, false, false, false, 0);
        }
    }

    private static int synchsafe(byte[] d, int p) { return ((d[p]&0x7F)<<21)|((d[p+1]&0x7F)<<14)|((d[p+2]&0x7F)<<7)|(d[p+3]&0x7F); }
    private static int int32(byte[] d, int p) { return ((d[p]&0xFF)<<24)|((d[p+1]&0xFF)<<16)|((d[p+2]&0xFF)<<8)|(d[p+3]&0xFF); }
    private static void writeSynchsafe(ByteArrayOutputStream out, int v) { out.write((v>>21)&0x7F); out.write((v>>14)&0x7F); out.write((v>>7)&0x7F); out.write(v&0x7F); }
}
