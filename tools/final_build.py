from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/humbleman/visualiseur/MainActivity.java')
GRADLE = Path('app/build.gradle')
s = MAIN.read_text(encoding='utf-8')
g = GRADLE.read_text(encoding='utf-8')

# Version
for pattern, repl in [
    (r'(?m)^\s*versionCode\s+\d+\s*$', '        versionCode 24'),
    (r'(?m)^\s*versionName\s+"[^"]+"\s*$', '        versionName "2.8.2"'),
]:
    g = re.sub(pattern, repl, g, count=1)

# Restore the historical on-screen geometry controls.
s = s.replace('private float visualizerScale = 1.0f;', 'private float visualizerLengthScale = 0.92f;', 1)
s = s.replace('visualizerScale', 'visualizerLengthScale')
if 'private float lyricsMaxWidth' not in s:
    s = s.replace('    private float visualizerLengthScale = 0.92f;\n', '    private float visualizerLengthScale = 0.92f;\n    private float lyricsMaxWidth = 92f;\n    private float textVerticalOffset = 0f;\n', 1)

save_anchor = '            o.put("visualizerLengthScale", visualizerLengthScale);\n'
if save_anchor in s and 'o.put("lyricsMaxWidth", lyricsMaxWidth);' not in s:
    s = s.replace(save_anchor, save_anchor + '            o.put("lyricsMaxWidth", lyricsMaxWidth);\n            o.put("textVerticalOffset", textVerticalOffset);\n', 1)
if 'lyricsMaxWidth = (float) o.optDouble("lyricsMaxWidth"' not in s:
    load_anchor = '            visualizerLengthScale = (float) o.optDouble("visualizerLengthScale", visualizerLengthScale);\n'
    if load_anchor in s:
        s = s.replace(load_anchor, load_anchor + '            lyricsMaxWidth = (float) o.optDouble("lyricsMaxWidth", lyricsMaxWidth);\n            textVerticalOffset = (float) o.optDouble("textVerticalOffset", textVerticalOffset);\n', 1)

# Replace the current visualizer/lyrics size block.
ui_start = s.find('        TextView visIntensityLabel = subtitle("Intensité du visualiseur')
ui_end = s.find('        c.addView(sizeCard);', ui_start)
if ui_start >= 0 and ui_end > ui_start and 'Longueur du visualiseur :' not in s[ui_start:ui_end]:
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
    s = s[:ui_start] + controls + s[ui_end:]

# Visualizer length affects geometry, not canvas/background size.
anchor = '            int visualizerLayerSave = c.saveLayerAlpha(0, 0, w, h, alphaFor(255, visualizerIntensity));\n\n            if ("bars".equals(style)) {'
if anchor in s and 'int visualizerLengthScaleSave = c.save();' not in s:
    s = s.replace(anchor, '            int visualizerLayerSave = c.saveLayerAlpha(0, 0, w, h, alphaFor(255, visualizerIntensity));\n            int visualizerLengthScaleSave = c.save();\n            c.scale(visualizerLengthScale, visualizerLengthScale, cx, cy);\n\n            if ("bars".equals(style)) {', 1)
restore = '            paint.clearShadowLayer();\n            c.restoreToCount(visualizerLayerSave);\n            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);'
if restore in s and 'c.restoreToCount(visualizerLengthScaleSave);' not in s:
    s = s.replace(restore, '            paint.clearShadowLayer();\n            c.restoreToCount(visualizerLengthScaleSave);\n            c.restoreToCount(visualizerLayerSave);\n            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);', 1)

# Historical lyrics geometry.
lyr_start = s.find('        private void drawLyricsAndText(Canvas c, int w, int h, long curMs) {')
lyr_end = s.find('        private void drawWatermark(Canvas c, int w, int h) {', lyr_start)
if lyr_start >= 0 and lyr_end > lyr_start:
    block = s[lyr_start:lyr_end]
    if 'textVerticalOffset' not in block:
        block = block.replace('float baseY = h * 0.50f;', 'float baseY = h * 0.50f + h * textVerticalOffset;', 1)
    if 'lyricsMaxWidth' not in block:
        block = block.replace('w * 0.92f', '(w * 0.92f * lyricsMaxWidth / 100f)')
        block = block.replace('w * 0.90f', '(w * 0.90f * lyricsMaxWidth / 100f)')
    s = s[:lyr_start] + block + s[lyr_end:]

# Prevent finalization from waiting forever on an encoder input buffer and avoid PCM buffer overflow.
unsafe = '''                        int encInIndex;
                        do {
                            encInIndex = encoder.dequeueInputBuffer(5000);
                        } while (encInIndex < 0);
                        ByteBuffer encInBuf = encoder.getInputBuffer(encInIndex);
                        encInBuf.clear();
                        if (decInfo.size > 0 && decBuf != null) {
                            decBuf.position(decInfo.offset);
                            decBuf.limit(decInfo.offset + decInfo.size);
                            encInBuf.put(decBuf);
                        }
                        if ((decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoder.queueInputBuffer(encInIndex, 0, decInfo.size, Math.max(0, relPts), android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decoderDone = true;
                        } else {
                            encoder.queueInputBuffer(encInIndex, 0, decInfo.size, Math.max(0, relPts), 0);
                        }
                        decoder.releaseOutputBuffer(outIndex, false);'''
safe = '''                        long inputDeadline = System.currentTimeMillis() + 5000L;
                        int encInIndex;
                        do {
                            encInIndex = encoder.dequeueInputBuffer(250);
                            if (encInIndex < 0 && System.currentTimeMillis() >= inputDeadline) {
                                throw new Exception("Le moteur AAC ne libère pas de buffer d'entrée.");
                            }
                        } while (encInIndex < 0);
                        ByteBuffer encInBuf = encoder.getInputBuffer(encInIndex);
                        if (encInBuf == null) throw new Exception("Buffer AAC indisponible.");
                        encInBuf.clear();
                        int copySize = Math.min(decInfo.size, encInBuf.remaining());
                        if (copySize > 0 && decBuf != null) {
                            decBuf.position(decInfo.offset);
                            decBuf.limit(decInfo.offset + copySize);
                            encInBuf.put(decBuf);
                        }
                        if ((decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoder.queueInputBuffer(encInIndex, 0, copySize, Math.max(0, relPts), android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decoderDone = true;
                        } else {
                            encoder.queueInputBuffer(encInIndex, 0, copySize, Math.max(0, relPts), 0);
                        }
                        decoder.releaseOutputBuffer(outIndex, false);'''
if unsafe in s:
    s = s.replace(unsafe, safe, 1)

loop_anchor = '            long firstDecPts = -1;\n\n            while (!encoderDone) {'
if loop_anchor in s and 'long transcodeDeadline = System.currentTimeMillis() + 120000L;' not in s:
    s = s.replace(loop_anchor, '            long firstDecPts = -1;\n            long transcodeDeadline = System.currentTimeMillis() + 120000L;\n\n            while (!encoderDone) {\n                if (System.currentTimeMillis() > transcodeDeadline) throw new Exception("Finalisation audio trop longue.");', 1)

fallback = '''                } catch (Exception transcodeErr) {
                    // Fallback sur l'extraction directe si le transcodage échoue
                    processedAudio = a;
                }'''
strict = '''                } catch (Exception transcodeErr) {
                    if (tempAac.exists()) tempAac.delete();
                    throw new Exception("Échec du transcodage AAC : " + transcodeErr.getMessage(), transcodeErr);
                }'''
s = s.replace(fallback, strict, 1)

# No emoji glyphs in Java UI strings.
out=[]
for ch in s:
    cp=ord(ch)
    if (0x1F000 <= cp <= 0x1FAFF) or (0x2600 <= cp <= 0x27BF) or cp in (0x200D,0xFE0F,0x20E3):
        continue
    out.append(ch)
s=''.join(out)

MAIN.write_text(s, encoding='utf-8')
GRADLE.write_text(g, encoding='utf-8')
print('final Studio Pro repair applied')
