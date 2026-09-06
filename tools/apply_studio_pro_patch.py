from pathlib import Path
import re

ROOT = Path('.')
MAIN = ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
TAGGER = ROOT / 'app/src/main/java/com/humbleman/visualiseur/AudioLyricsTagger.java'
GRADLE = ROOT / 'app/build.gradle'

EMOJI_RANGES = [
    (0x1F1E6, 0x1F1FF), (0x1F300, 0x1FAFF), (0x1FC00, 0x1FFFF),
    (0x2600, 0x27BF), (0x2300, 0x23FF), (0xFE00, 0xFE0F),
    (0x200D, 0x200D), (0x20E3, 0x20E3)
]

def strip_emoji(text):
    return ''.join(ch for ch in text if not any(a <= ord(ch) <= b for a, b in EMOJI_RANGES))

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Patch anchor missing: {label}')
    return text.replace(old, new, 1)

main = MAIN.read_text(encoding='utf-8')
gradle = GRADLE.read_text(encoding='utf-8')

# Version bump.
gradle = re.sub(r'(?m)^\s*versionCode\s+\d+\s*$', '        versionCode 19', gradle, count=1)
gradle = re.sub(r'(?m)^\s*versionName\s+"[^"]+"\s*$', '        versionName "2.7.0"', gradle, count=1)

# Fields/imports.
main = replace_once(main, 'import android.widget.ScrollView;\n', 'import android.widget.ScrollView;\nimport android.widget.SeekBar;\n', 'SeekBar import')
main = replace_once(main, '    private long exportCurrentMs = 0;\n', '''    private long exportCurrentMs = 0;\n    private Uri currentAudioUri;\n    private float visualizerScale = 1.08f;\n    private float lyricsScale = 1.45f;\n    private boolean exportIncludeVisualizer = true;\n    private boolean exportIncludeLyrics = true;\n''', 'render state fields')
main = replace_once(main, '    private TextView visualPageTitle, visualPageBgTag;\n', '    private TextView visualPageTitle, visualPageBgTag;\n    private Button exportVisualizerToggleButton, exportLyricsToggleButton;\n', 'export toggle fields')

# Real document URI with read/write permission.
main = main.replace('getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);',
                    'getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);', 1)
main = main.replace('Intent.ACTION_GET_CONTENT', 'Intent.ACTION_OPEN_DOCUMENT')
main = main.replace('intent.addCategory(Intent.CATEGORY_OPENABLE);',
                    'intent.addCategory(Intent.CATEGORY_OPENABLE);\n                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);')
main = replace_once(main, '    private void loadAudio(Uri uri) {\n        try {\n',
                    '    private void loadAudio(Uri uri) {\n        try {\n            currentAudioUri = uri;\n', 'current audio URI')

# Larger visual preview.
main = replace_once(main,
                    'previewCard.addView(visualPagePreview, new LinearLayout.LayoutParams(-1, dp(240)));',
                    'previewCard.addView(visualPagePreview, new LinearLayout.LayoutParams(-1, dp(360)));',
                    'visual preview height')

visual_controls = '''
        LinearLayout sizeCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        sizeCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        sizeCard.addView(section("TAILLE DU RENDU"));
        sizeCard.addView(gap(8));
        TextView visSizeLabel = subtitle("Visualiseur : " + Math.round(visualizerScale * 100) + "%");
        visSizeLabel.setTextSize(12);
        sizeCard.addView(visSizeLabel);
        SeekBar visSeek = new SeekBar(this);
        visSeek.setMax(50);
        visSeek.setProgress(Math.max(0, Math.min(50, Math.round((visualizerScale - 0.80f) * 100f))));
        visSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                visualizerScale = 0.80f + progress / 100f;
                visSizeLabel.setText("Visualiseur : " + Math.round(visualizerScale * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(visSeek, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView lyrSizeLabel = subtitle("Paroles : " + Math.round(lyricsScale * 100) + "%");
        lyrSizeLabel.setTextSize(12);
        lyrSizeLabel.setPadding(0, dp(6), 0, 0);
        sizeCard.addView(lyrSizeLabel);
        SeekBar lyrSeek = new SeekBar(this);
        lyrSeek.setMax(80);
        lyrSeek.setProgress(Math.max(0, Math.min(80, Math.round((lyricsScale - 1.00f) * 100f))));
        lyrSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lyricsScale = 1.00f + progress / 100f;
                lyrSizeLabel.setText("Paroles : " + Math.round(lyricsScale * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(lyrSeek, new LinearLayout.LayoutParams(-1, dp(40)));
        c.addView(sizeCard);
        c.addView(gap(16));
'''
main = replace_once(main, '        c.addView(previewCard);\n        c.addView(gap(16));\n',
                    '        c.addView(previewCard);\n        c.addView(gap(16));\n' + visual_controls,
                    'visual size controls')

export_controls = '''
        LinearLayout exportOptionsCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        exportOptionsCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        exportOptionsCard.addView(section("ÉLÉMENTS À EXPORTER"));
        exportOptionsCard.addView(gap(8));
        LinearLayout exportOptionsRow = row();
        exportVisualizerToggleButton = button(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        exportVisualizerToggleButton.setAllCaps(false);
        exportVisualizerToggleButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav_visual, 0, 0, 0);
        exportVisualizerToggleButton.setCompoundDrawablePadding(dp(8));
        exportVisualizerToggleButton.setOnClickListener(v -> {
            exportIncludeVisualizer = !exportIncludeVisualizer;
            exportVisualizerToggleButton.setText(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non");
            exportVisualizerToggleButton.setTextColor(exportIncludeVisualizer ? Color.WHITE : 0xFF9CA3AF);
            saveSession();
        });
        exportOptionsRow.addView(exportVisualizerToggleButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        exportLyricsToggleButton = button(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        exportLyricsToggleButton.setAllCaps(false);
        exportLyricsToggleButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav_lyrics, 0, 0, 0);
        exportLyricsToggleButton.setCompoundDrawablePadding(dp(8));
        exportLyricsToggleButton.setOnClickListener(v -> {
            exportIncludeLyrics = !exportIncludeLyrics;
            exportLyricsToggleButton.setText(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non");
            exportLyricsToggleButton.setTextColor(exportIncludeLyrics ? Color.WHITE : 0xFF9CA3AF);
            saveSession();
        });
        LinearLayout.LayoutParams lyrToggleLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        lyrToggleLp.leftMargin = dp(8);
        exportOptionsRow.addView(exportLyricsToggleButton, lyrToggleLp);
        exportOptionsCard.addView(exportOptionsRow);
        c.addView(exportOptionsCard);
        c.addView(gap(16));
'''
main = replace_once(main, '        c.addView(fpsTrack);\n        c.addView(gap(18));\n',
                    '        c.addView(fpsTrack);\n        c.addView(gap(18));\n' + export_controls,
                    'export options')

# Settings persistence.
main = replace_once(main, '            o.put("loopTargetMs", loopTargetMs);\n', '''            o.put("loopTargetMs", loopTargetMs);
            o.put("visualizerScale", visualizerScale);
            o.put("lyricsScale", lyricsScale);
            o.put("exportIncludeVisualizer", exportIncludeVisualizer);
            o.put("exportIncludeLyrics", exportIncludeLyrics);
''', 'save settings')
main = replace_once(main, '            loopTargetMs = o.optLong("loopTargetMs", loopTargetMs);\n', '''            loopTargetMs = o.optLong("loopTargetMs", loopTargetMs);
            visualizerScale = (float) o.optDouble("visualizerScale", visualizerScale);
            lyricsScale = (float) o.optDouble("lyricsScale", lyricsScale);
            exportIncludeVisualizer = o.optBoolean("exportIncludeVisualizer", exportIncludeVisualizer);
            exportIncludeLyrics = o.optBoolean("exportIncludeLyrics", exportIncludeLyrics);
''', 'load settings')
main = replace_once(main, '            updateFpsSelection(exportFps);\n', '''            updateFpsSelection(exportFps);
            if (exportVisualizerToggleButton != null) {
                exportVisualizerToggleButton.setText(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non");
                exportVisualizerToggleButton.setTextColor(exportIncludeVisualizer ? Color.WHITE : 0xFF9CA3AF);
            }
            if (exportLyricsToggleButton != null) {
                exportLyricsToggleButton.setText(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non");
                exportLyricsToggleButton.setTextColor(exportIncludeLyrics ? Color.WHITE : 0xFF9CA3AF);
            }
''', 'refresh export settings')

# Replace audio embed action with same-file URI overwrite.
pat = re.compile(r'    private void embedLyricsDirectlyToAudio\(\) \{.*?\n    \}\n\n    private void exportCurrentLrcFile', re.S)
new_embed = '''    private void embedLyricsDirectlyToAudio() {
        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(this, "Veuillez d'abord charger un fichier audio.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lyricsList.isEmpty() && (quote == null || quote.trim().isEmpty())) {
            Toast.makeText(this, "Aucune parole à intégrer. Générez-les ou importez un fichier LRC.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentAudioUri == null) {
            Toast.makeText(this, "Ouvrez le MP3 depuis le sélecteur de fichiers Android pour autoriser sa modification sur place.", Toast.LENGTH_LONG).show();
            return;
        }
        showStatus("Injection ID3 USLT + SYLT dans le MP3 original…");
        new Thread(() -> {
            File tagged = null;
            try {
                String title = (currentTrackTitle == null || currentTrackTitle.isEmpty()) ? currentFileName : currentTrackTitle;
                String artist = currentTrackArtist == null ? "" : currentTrackArtist;
                tagged = AudioLyricsTagger.embedLyricsIntoAudio(MainActivity.this, audioFile, audioMime, title, artist, lyricsList, quote);
                android.content.ContentResolver resolver = getContentResolver();
                try (InputStream in = new FileInputStream(tagged); OutputStream out = resolver.openOutputStream(currentAudioUri, "wt")) {
                    if (out == null) throw new Exception("Le fournisseur de stockage refuse l'écriture sur le fichier original.");
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();
                }
                runOnUiThread(() -> {
                    showStatus("Paroles intégrées dans le MP3 original sans supprimer la pochette.");
                    Toast.makeText(MainActivity.this, "USLT + SYLT enregistrés dans le même MP3.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showStatus("Échec intégration : " + friendlyError(e));
                    Toast.makeText(MainActivity.this, "Erreur : " + friendlyError(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (tagged != null) tagged.delete();
            }
        }).start();
    }

    private void exportCurrentLrcFile'''
main, count = pat.subn(new_embed, main, count=1)
if count != 1:
    raise SystemExit('embedLyricsDirectlyToAudio method not found')

# Export elements flags.
main = replace_once(main, 'visualizerView.drawInto(c, w, h, exportCurrentMs);',
                    'visualizerView.drawInto(c, w, h, exportCurrentMs, exportIncludeVisualizer, exportIncludeLyrics);',
                    'export flags call')

# Visualizer overload + scale + conditional lyrics.
old = '        void drawInto(Canvas c, int w, int h, long currentAudioMs) {\n'
new = '''        void drawInto(Canvas c, int w, int h, long currentAudioMs) {
            drawInto(c, w, h, currentAudioMs, true, true);
        }

        void drawInto(Canvas c, int w, int h, long currentAudioMs, boolean includeVisualizer, boolean includeLyrics) {
'''
main = replace_once(main, old, new, 'visualizer overload')
main = replace_once(main,
                    '            float cx = w / 2f, cy = h / 2f;\n\n            if ("bars".equals(style)) {',
                    '''            float cx = w / 2f, cy = h / 2f;

            if (includeVisualizer) {
                c.save();
                c.scale(visualizerScale, visualizerScale, cx, cy);
            }

            if ("bars".equals(style)) {''',
                    'visualizer scaling')
main = replace_once(main,
                    '            paint.clearShadowLayer();\n            drawLyricsAndText(c, w, h, currentAudioMs);',
                    '''            if (includeVisualizer) {
                c.restore();
            }
            paint.clearShadowLayer();
            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);''',
                    'visualizer restore')

# Bigger lyric viewport and independent scaling.
main = replace_once(main,
                    '            float baseY = "top".equals(textPosition) ? h * 0.16f : "center".equals(textPosition) ? h * 0.50f : h * 0.82f;',
                    '            float baseY = h * 0.50f;', 'lyrics base')
main = replace_once(main, '                    float lineSpacing = dp(textSize * 1.5f);',
                    '                    float lineSpacing = Math.max(dp((int) (textSize * 1.20f * lyricsScale)), h * 0.095f);',
                    'lyrics spacing')
main = replace_once(main, '                    for (int offset = -2; offset <= 2; offset++) {',
                    '                    for (int offset = -4; offset <= 4; offset++) {',
                    'lyrics rows')
main = replace_once(main, '                            textPaint.setTextSize(dp((int) (textSize * 1.18f)));',
                    '                            textPaint.setTextSize(dp((int) (textSize * 1.18f * lyricsScale)));',
                    'lyrics active scale')
main = replace_once(main, '                            textPaint.setTextSize(dp((int) (textSize * 0.90f)));',
                    '                            textPaint.setTextSize(dp((int) (textSize * 0.90f * lyricsScale)));',
                    'lyrics secondary scale')
main = replace_once(main, '                    textPaint.setTextSize(dp((int) (textSize * 1.15f)));',
                    '                    textPaint.setTextSize(dp((int) (textSize * 1.15f * lyricsScale)));',
                    'lyrics active line scale')
main = replace_once(main, '                        textPaint.setTextSize(dp((int) (textSize * 1.35f)));',
                    '                        textPaint.setTextSize(dp((int) (textSize * 1.35f * lyricsScale)));',
                    'lyrics word scale')

fit_helper = '''\n        private void drawFittedCentered(Canvas c, String text, float centerX, float baseline, float maxWidth) {\n            if (text == null || text.isEmpty()) return;\n            float original = textPaint.getTextSize();\n            float measured = textPaint.measureText(text);\n            if (measured > maxWidth && measured > 0) textPaint.setTextSize(original * maxWidth / measured);\n            c.drawText(text, centerX, baseline, textPaint);\n            textPaint.setTextSize(original);\n        }\n'''
main = replace_once(main, '        private void drawWatermark(Canvas c, int w, int h) {', fit_helper + '\n        private void drawWatermark(Canvas c, int w, int h) {', 'fit helper')
main = replace_once(main, '                            c.drawText(l.text, w / 2f, lineY, textPaint);',
                    '                            drawFittedCentered(c, l.text, w / 2f, lineY, w * 0.92f);', 'fit active lyrics')
main = replace_once(main, '                    c.drawText(curLine.text, w / 2f, baseY, textPaint);',
                    '                    drawFittedCentered(c, curLine.text, w / 2f, baseY, w * 0.90f);', 'fit active line')
main = replace_once(main, '                        c.drawText(words[wordIdx], w / 2f, baseY, textPaint);',
                    '                        drawFittedCentered(c, words[wordIdx], w / 2f, baseY, w * 0.90f);', 'fit word')

# Audio transcoding: never drop decoder PCM when encoder input is busy.
old_codec = '''                        int encInIndex = encoder.dequeueInputBuffer(5000);\n                        if (encInIndex >= 0) {\n                            ByteBuffer encInBuf = encoder.getInputBuffer(encInIndex);\n                            encInBuf.clear();\n                            if (decInfo.size > 0 && decBuf != null) {\n                                decBuf.position(decInfo.offset);\n                                decBuf.limit(decInfo.offset + decInfo.size);\n                                encInBuf.put(decBuf);\n                            }\n                            if ((decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {\n                                encoder.queueInputBuffer(encInIndex, 0, decInfo.size, Math.max(0, relPts), android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);\n                                decoderDone = true;\n                            } else {\n                                encoder.queueInputBuffer(encInIndex, 0, decInfo.size, Math.max(0, relPts), 0);\n                            }\n                        }\n                        decoder.releaseOutputBuffer(outIndex, false);\n'''
new_codec = '''                        int encInIndex;
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
                        decoder.releaseOutputBuffer(outIndex, false);
'''
main = replace_once(main, old_codec, new_codec, 'AAC buffer preservation')

# Never display Unicode emoji in the app.
main = strip_emoji(main)

# Write source files.
MAIN.write_text(main, encoding='utf-8')
GRADLE.write_text(gradle, encoding='utf-8')

TAGGER.write_text('''package com.humbleman.visualiseur;

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
                    boolean keep = !"USLT".equals(id) && !"SYLT".equals(id) && !isLyricsTxxx(input, p + 10, fs, oldVersion);
                    if (keep) kept.write(input, p, 10 + fs);
                    p += 10 + fs;
                }
            }
        }

        StringBuilder lrc = new StringBuilder();
        if (lines != null && !lines.isEmpty()) {
            for (MainActivity.LyricLine line : lines) {
                long m = line.startMs / 60000;
                long s = (line.startMs % 60000) / 1000;
                long cs = (line.startMs % 1000) / 10;
                lrc.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", m, s, cs, line.text));
            }
        } else {
            lrc.append(plain == null ? "" : plain.trim());
        }
        String lrcText = lrc.toString().trim();

        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(kept.toByteArray());
        if (title != null && !title.trim().isEmpty()) textFrame(frames, "TIT2", title.trim(), oldVersion);
        if (artist != null && !artist.trim().isEmpty()) textFrame(frames, "TPE1", artist.trim(), oldVersion);
        if (!lrcText.isEmpty()) usltFrame(frames, lrcText, oldVersion);
        if (lines != null && !lines.isEmpty()) syltFrame(frames, lines, oldVersion);
        if (!lrcText.isEmpty()) {
            txxxFrame(frames, "LYRICS", lrcText, oldVersion);
            txxxFrame(frames, "SYNCEDLYRICS", lrcText, oldVersion);
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
        if (len < 0) return false;
        String desc = decode(data, off + 1, len, enc).trim();
        return "LYRICS".equalsIgnoreCase(desc) || "SYNCEDLYRICS".equalsIgnoreCase(desc);
    }

    private static void textFrame(ByteArrayOutputStream out, String id, String text, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(3); p.write(text.getBytes(StandardCharsets.UTF_8));
        frame(out, id, p.toByteArray(), version);
    }

    private static void usltFrame(ByteArrayOutputStream out, String text, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(3); p.write("fra".getBytes(StandardCharsets.ISO_8859_1)); p.write(0); p.write(text.getBytes(StandardCharsets.UTF_8));
        frame(out, "USLT", p.toByteArray(), version);
    }

    private static void syltFrame(ByteArrayOutputStream out, List<MainActivity.LyricLine> lines, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(3); p.write("fra".getBytes(StandardCharsets.ISO_8859_1)); p.write(2); p.write(1); p.write(0);
        for (MainActivity.LyricLine line : lines) {
            p.write(line.text.getBytes(StandardCharsets.UTF_8)); p.write(0);
            long ms = Math.max(0, line.startMs);
            p.write((int)(ms >> 24) & 0xFF); p.write((int)(ms >> 16) & 0xFF); p.write((int)(ms >> 8) & 0xFF); p.write((int)ms & 0xFF);
        }
        frame(out, "SYLT", p.toByteArray(), version);
    }

    private static void txxxFrame(ByteArrayOutputStream out, String desc, String text, int version) throws Exception {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write(3); p.write(desc.getBytes(StandardCharsets.UTF_8)); p.write(0); p.write(text.getBytes(StandardCharsets.UTF_8));
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

    private static int synchsafe(byte[] d, int p) { return ((d[p]&0x7F)<<21)|((d[p+1]&0x7F)<<14)|((d[p+2]&0x7F)<<7)|(d[p+3]&0x7F); }
    private static int int32(byte[] d, int p) { return ((d[p]&0xFF)<<24)|((d[p+1]&0xFF)<<16)|((d[p+2]&0xFF)<<8)|(d[p+3]&0xFF); }
    private static void writeSynchsafe(ByteArrayOutputStream out, int v) { out.write((v>>21)&0x7F); out.write((v>>14)&0x7F); out.write((v>>7)&0x7F); out.write(v&0x7F); }
}
'''.replace('🙂',''), encoding='utf-8')

# Strip emoji from all Java UI sources, while keeping functional code unchanged.
for p in (ROOT / 'app/src/main/java').rglob('*.java'):
    try:
        txt = p.read_text(encoding='utf-8')
        cleaned = strip_emoji(txt)
        if cleaned != txt:
            p.write_text(cleaned, encoding='utf-8')
    except Exception:
        pass

print('Studio Pro patch applied successfully.')
