from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/humbleman/visualiseur/MainActivity.java')
GRADLE = Path('app/build.gradle')

main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

# Keep the currently published release line used by the repository workflows.
gradle = re.sub(r'(?m)^\s*versionCode\s+\d+\s*$', '        versionCode 23', gradle, count=1)
gradle = re.sub(r'(?m)^\s*versionName\s+"[^"]+"\s*$', '        versionName "2.8.1"', gradle, count=1)

# Use a different identifier from the old automatic fixer, which forced visualizerScale back to 1.0.
main = re.sub(r'private float visualizerScale\s*=\s*[^;]+;', 'private float visualizerLengthScale = 0.92f;', main, count=1)
if 'private float visualizerLengthScale' not in main:
    main = main.replace('    private Uri currentAudioUri;\n', '    private Uri currentAudioUri;\n    private float visualizerLengthScale = 0.92f;\n', 1)
main = main.replace('visualizerScale', 'visualizerLengthScale')
if 'private float lyricsMaxWidth' not in main:
    main = main.replace('    private float visualizerLengthScale = 0.92f;\n', '    private float visualizerLengthScale = 0.92f;\n    private float lyricsMaxWidth = 92f;\n    private float textVerticalOffset = 0f;\n', 1)

# Persist and restore the real on-screen dimensions.
if 'o.put("lyricsMaxWidth", lyricsMaxWidth);' not in main:
    main = main.replace('            o.put("visualizerLengthScale", visualizerLengthScale);\n', '            o.put("visualizerLengthScale", visualizerLengthScale);\n            o.put("lyricsMaxWidth", lyricsMaxWidth);\n            o.put("textVerticalOffset", textVerticalOffset);\n', 1)
if 'lyricsMaxWidth = (float) o.optDouble("lyricsMaxWidth"' not in main:
    main = main.replace('            visualizerLengthScale = (float) o.optDouble("visualizerLengthScale", visualizerLengthScale);\n', '            visualizerLengthScale = (float) o.optDouble("visualizerLengthScale", visualizerLengthScale);\n            lyricsMaxWidth = (float) o.optDouble("lyricsMaxWidth", lyricsMaxWidth);\n            textVerticalOffset = (float) o.optDouble("textVerticalOffset", textVerticalOffset);\n', 1)

# Replace the old intensity controls with true length/width/position controls.
start = main.find('        TextView visIntensityLabel = subtitle("Intensité du visualiseur')
end = main.find('        c.addView(sizeCard);', start)
if start >= 0 and end >= 0:
    controls = '''        TextView visSizeLabel = subtitle("Longueur du visualiseur : " + Math.round(visualizerLengthScale * 100) + "%");
        visSizeLabel.setTextSize(12);
        sizeCard.addView(visSizeLabel);
        SeekBar visSizeSeek = new SeekBar(this);
        visSizeSeek.setMax(75);
        visSizeSeek.setProgress(Math.max(0, Math.min(75, Math.round((visualizerLengthScale - 0.50f) * 100f))));
        visSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                visualizerLengthScale = 0.50f + progress / 100f;
                visSizeLabel.setText("Longueur du visualiseur : " + Math.round(visualizerLengthScale * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(visSizeSeek, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView lyrWidthLabel = subtitle("Largeur des paroles : " + Math.round(lyricsMaxWidth) + "%");
        lyrWidthLabel.setTextSize(12);
        lyrWidthLabel.setPadding(0, dp(6), 0, 0);
        sizeCard.addView(lyrWidthLabel);
        SeekBar lyrWidthSeek = new SeekBar(this);
        lyrWidthSeek.setMax(50);
        lyrWidthSeek.setProgress(Math.max(0, Math.min(50, Math.round(lyricsMaxWidth - 50f))));
        lyrWidthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lyricsMaxWidth = 50f + progress;
                lyrWidthLabel.setText("Largeur des paroles : " + Math.round(lyricsMaxWidth) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(lyrWidthSeek, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView offsetLabel = subtitle("Position verticale des paroles : " + Math.round(textVerticalOffset * 100) + "%");
        offsetLabel.setTextSize(12);
        offsetLabel.setPadding(0, dp(6), 0, 0);
        sizeCard.addView(offsetLabel);
        SeekBar offsetSeek = new SeekBar(this);
        offsetSeek.setMax(40);
        offsetSeek.setProgress(Math.max(0, Math.min(40, Math.round((textVerticalOffset + 0.20f) * 100f))));
        offsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textVerticalOffset = -0.20f + progress / 100f;
                offsetLabel.setText("Position verticale des paroles : " + Math.round(textVerticalOffset * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(offsetSeek, new LinearLayout.LayoutParams(-1, dp(40)));
'''
    main = main[:start] + controls + main[end:]

# Apply length scaling only to the visualizer layer; the background remains full-frame.
anchor = '            int visualizerLayerSave = c.saveLayerAlpha(0, 0, w, h, alphaFor(255, visualizerIntensity));\n\n            if ("bars".equals(style)) {'
if anchor in main:
    main = main.replace(anchor, '            int visualizerLayerSave = c.saveLayerAlpha(0, 0, w, h, alphaFor(255, visualizerIntensity));\n            int visualizerScaleSave = c.save();\n            c.scale(visualizerLengthScale, visualizerLengthScale, cx, cy);\n\n            if ("bars".equals(style)) {', 1)
restore = '            paint.clearShadowLayer();\n            c.restoreToCount(visualizerLayerSave);\n            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);'
if restore in main:
    main = main.replace(restore, '            paint.clearShadowLayer();\n            c.restoreToCount(visualizerScaleSave);\n            c.restoreToCount(visualizerLayerSave);\n            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);', 1)

# Restore the old lyric occupation/position behavior.
lyr_start = main.find('        private void drawLyricsAndText(Canvas c, int w, int h, long curMs) {')
lyr_end = main.find('        private void drawWatermark(Canvas c, int w, int h) {', lyr_start)
if lyr_start >= 0 and lyr_end > lyr_start:
    lyr = main[lyr_start:lyr_end]
    lyr = lyr.replace('float baseY = h * 0.50f;', 'float baseY = h * 0.50f + h * textVerticalOffset;', 1)
    lyr = lyr.replace('w * 0.92f', '(w * 0.92f * lyricsMaxWidth / 100f)')
    lyr = lyr.replace('w * 0.90f', '(w * 0.90f * lyricsMaxWidth / 100f)')
    main = main[:lyr_start] + lyr + main[lyr_end:]

# Never keep emoji characters in the Java UI source.
def strip_emoji(s):
    out = []
    for ch in s:
        cp = ord(ch)
        if (0x1F1E6 <= cp <= 0x1FAFF) or (0x1FC00 <= cp <= 0x1FFFF) or (0x2600 <= cp <= 0x27BF) or (0xFE00 <= cp <= 0xFE0F) or cp == 0x200D or (0x20E3 == cp):
            continue
        out.append(ch)
    return ''.join(out)
main = strip_emoji(main)

# Replace the AAC transcoder with codec-sized PCM feeding and a hard deadline.
tx_start = main.find('        private static void transcodeToAac(')
tx_end = main.find('        static int find(android.media.MediaExtractor e, String p) {', tx_start)
if tx_start >= 0 and tx_end > tx_start:
    transcoder = '''        private static void transcodeToAac(android.media.MediaExtractor extractor, int audioTrackIndex, File outFile, long startUs, long endUs, long targetUs) throws Exception {
            extractor.selectTrack(audioTrackIndex);
            extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            android.media.MediaFormat inputFormat = extractor.getTrackFormat(audioTrackIndex);
            String inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME);
            int sampleRate = inputFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channelCount = inputFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) : 2;
            int bytesPerFrame = Math.max(1, channelCount * 2);

            android.media.MediaCodec decoder = android.media.MediaCodec.createDecoderByType(inputMime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            android.media.MediaFormat outputFormat = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount);
            outputFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 192000);
            outputFormat.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);

            android.media.MediaCodec encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm");
            encoder.configure(outputFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            android.media.MediaMuxer muxer = new android.media.MediaMuxer(outFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerTrack = -1;
            boolean muxerStarted = false;
            android.media.MediaCodec.BufferInfo decInfo = new android.media.MediaCodec.BufferInfo();
            android.media.MediaCodec.BufferInfo encInfo = new android.media.MediaCodec.BufferInfo();
            boolean extractorDone = false;
            boolean decoderDone = false;
            boolean encoderEosQueued = false;
            boolean encoderDone = false;
            long queuedFrames = 0L;
            long maxFrames = (targetUs > 0 ? targetUs : (endUs > startUs ? endUs - startUs : Long.MAX_VALUE));
            maxFrames = maxFrames == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, (maxFrames * sampleRate) / 1000000L);
            java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream(65536);
            long deadline = System.currentTimeMillis() + 180000L;

            while (!encoderDone) {
                if (System.currentTimeMillis() > deadline) throw new Exception("Finalisation audio trop longue.");

                if (!extractorDone) {
                    int inIndex = decoder.dequeueInputBuffer(1000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIndex);
                        int sampleSize = extractor.readSampleData(inBuf, 0);
                        long sampleTime = extractor.getSampleTime();
                        if (sampleSize < 0 || sampleTime < 0 || sampleTime > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            extractorDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
                            extractor.advance();
                        }
                    }
                }

                if (!decoderDone) {
                    int outIndex = decoder.dequeueOutputBuffer(decInfo, 1000);
                    if (outIndex >= 0) {
                        ByteBuffer decBuf = decoder.getOutputBuffer(outIndex);
                        if (decInfo.size > 0 && decBuf != null && queuedFrames < maxFrames) {
                            int wanted = decInfo.size;
                            long pendingFrames = pending.size() / bytesPerFrame;
                            long remainingFrames = maxFrames == Long.MAX_VALUE ? Long.MAX_VALUE : maxFrames - queuedFrames - pendingFrames;
                            if (remainingFrames != Long.MAX_VALUE) wanted = (int) Math.min(wanted, Math.max(0L, remainingFrames * bytesPerFrame));
                            wanted -= wanted % bytesPerFrame;
                            if (wanted > 0) {
                                decBuf.position(decInfo.offset);
                                decBuf.limit(decInfo.offset + wanted);
                                byte[] pcm = new byte[wanted];
                                decBuf.get(pcm);
                                pending.write(pcm, 0, pcm.length);
                            }
                        }
                        boolean eos = (decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        decoder.releaseOutputBuffer(outIndex, false);
                        if (eos) decoderDone = true;
                    }
                }

                int encInIndex = encoder.dequeueInputBuffer(1000);
                if (encInIndex >= 0) {
                    ByteBuffer encIn = encoder.getInputBuffer(encInIndex);
                    encIn.clear();
                    int send = Math.min(encIn.remaining(), pending.size());
                    send -= send % bytesPerFrame;
                    if (send > 0) {
                        byte[] data = pending.toByteArray();
                        encIn.put(data, 0, send);
                        pending.reset();
                        if (data.length > send) pending.write(data, send, data.length - send);
                        long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                        encoder.queueInputBuffer(encInIndex, 0, send, ptsUs, 0);
                        queuedFrames += send / bytesPerFrame;
                    } else if (decoderDone && pending.size() == 0 && !encoderEosQueued) {
                        long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                        encoder.queueInputBuffer(encInIndex, 0, 0, ptsUs, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        encoderEosQueued = true;
                    }
                }

                int encOutIndex = encoder.dequeueOutputBuffer(encInfo, 1000);
                if (encOutIndex >= 0) {
                    ByteBuffer encOut = encoder.getOutputBuffer(encOutIndex);
                    if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0 && muxerStarted && encOut != null) {
                        encOut.position(encInfo.offset);
                        encOut.limit(encInfo.offset + encInfo.size);
                        muxer.writeSampleData(muxerTrack, encOut, encInfo);
                    }
                    if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encoderDone = true;
                    encoder.releaseOutputBuffer(encOutIndex, false);
                } else if (encOutIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    muxerTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
            }

            try { decoder.stop(); } catch (Exception ignored) {}
            try { decoder.release(); } catch (Exception ignored) {}
            try { encoder.stop(); } catch (Exception ignored) {}
            try { encoder.release(); } catch (Exception ignored) {}
            try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); } catch (Exception ignored) {}
        }

'''
    main = main[:tx_start] + transcoder + main[tx_end:]

MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')
