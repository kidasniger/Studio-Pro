from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
GRADLE = ROOT / 'app/build.gradle'

text = MAIN.read_text(encoding='utf-8')

# Independent intensity controls. The visualizer remains full-frame; only its opacity/strength changes.
if 'private float visualizerIntensity' not in text:
    text = text.replace(
        '    private float visualizerScale = 1.08f;\n    private float lyricsScale = 1.45f;\n',
        '    private float visualizerScale = 1.0f;\n    private float lyricsScale = 1.45f;\n    private float visualizerIntensity = 0.60f;\n    private float lyricsIntensity = 1.0f;\n',
        1,
    )

# Replace the old physical-size settings card with intensity + lyric-size controls.
start_marker = '        LinearLayout sizeCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);\n'
end_marker = '        c.addView(sizeCard);\n'
a = text.find(start_marker)
b = text.find(end_marker, a) if a >= 0 else -1
if a < 0 or b < 0:
    raise SystemExit('settings card not found')
b += len(end_marker)
new_ui = '''        LinearLayout sizeCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);\n        sizeCard.setPadding(dp(14), dp(12), dp(14), dp(12));\n        sizeCard.addView(section("RÉGLAGES DU RENDU"));\n        sizeCard.addView(gap(8));\n\n        TextView visIntensityLabel = subtitle("Intensité du visualiseur : " + Math.round(visualizerIntensity * 100) + "%");\n        visIntensityLabel.setTextSize(12);\n        sizeCard.addView(visIntensityLabel);\n        SeekBar visIntensitySeek = new SeekBar(this);\n        visIntensitySeek.setMax(100);\n        visIntensitySeek.setProgress(Math.max(0, Math.min(100, Math.round(visualizerIntensity * 100f))));\n        visIntensitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {\n            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {\n                visualizerIntensity = progress / 100f;\n                visIntensityLabel.setText("Intensité du visualiseur : " + progress + "%");\n                if (visualizerView != null) visualizerView.invalidate();\n                if (visualPagePreview != null) visualPagePreview.invalidate();\n            }\n            public void onStartTrackingTouch(SeekBar seekBar) {}\n            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }\n        });\n        sizeCard.addView(visIntensitySeek, new LinearLayout.LayoutParams(-1, dp(40)));\n\n        TextView lyrSizeLabel = subtitle("Taille des paroles : " + Math.round(lyricsScale * 100) + "%");\n        lyrSizeLabel.setTextSize(12);\n        lyrSizeLabel.setPadding(0, dp(6), 0, 0);\n        sizeCard.addView(lyrSizeLabel);\n        SeekBar lyrSizeSeek = new SeekBar(this);\n        lyrSizeSeek.setMax(100);\n        lyrSizeSeek.setProgress(Math.max(0, Math.min(100, Math.round((lyricsScale - 0.80f) * 100f))));\n        lyrSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {\n            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {\n                lyricsScale = 0.80f + progress / 100f;\n                lyrSizeLabel.setText("Taille des paroles : " + Math.round(lyricsScale * 100) + "%");\n                if (visualizerView != null) visualizerView.invalidate();\n                if (visualPagePreview != null) visualPagePreview.invalidate();\n            }\n            public void onStartTrackingTouch(SeekBar seekBar) {}\n            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }\n        });\n        sizeCard.addView(lyrSizeSeek, new LinearLayout.LayoutParams(-1, dp(40)));\n\n        TextView lyrIntensityLabel = subtitle("Intensité des paroles : " + Math.round(lyricsIntensity * 100) + "%");\n        lyrIntensityLabel.setTextSize(12);\n        lyrIntensityLabel.setPadding(0, dp(6), 0, 0);\n        sizeCard.addView(lyrIntensityLabel);\n        SeekBar lyrIntensitySeek = new SeekBar(this);\n        lyrIntensitySeek.setMax(100);\n        lyrIntensitySeek.setProgress(Math.max(0, Math.min(100, Math.round(lyricsIntensity * 100f))));\n        lyrIntensitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {\n            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {\n                lyricsIntensity = progress / 100f;\n                lyrIntensityLabel.setText("Intensité des paroles : " + progress + "%");\n                if (visualizerView != null) visualizerView.invalidate();\n                if (visualPagePreview != null) visualPagePreview.invalidate();\n            }\n            public void onStartTrackingTouch(SeekBar seekBar) {}\n            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }\n        });\n        sizeCard.addView(lyrIntensitySeek, new LinearLayout.LayoutParams(-1, dp(40)));\n        c.addView(sizeCard);\n'''
text = text[:a] + new_ui + text[b:]

# Persist settings.
old_save = '            o.put("visualizerScale", visualizerScale);\n            o.put("lyricsScale", lyricsScale);\n'
new_save = '            o.put("visualizerScale", 1.0f);\n            o.put("lyricsScale", lyricsScale);\n            o.put("visualizerIntensity", visualizerIntensity);\n            o.put("lyricsIntensity", lyricsIntensity);\n'
text = text.replace(old_save, new_save, 1)
old_load = '            visualizerScale = (float) o.optDouble("visualizerScale", visualizerScale);\n            lyricsScale = (float) o.optDouble("lyricsScale", lyricsScale);\n'
new_load = '            visualizerScale = 1.0f;\n            lyricsScale = (float) o.optDouble("lyricsScale", lyricsScale);\n            visualizerIntensity = (float) o.optDouble("visualizerIntensity", visualizerIntensity);\n            lyricsIntensity = (float) o.optDouble("lyricsIntensity", lyricsIntensity);\n'
text = text.replace(old_load, new_load, 1)

# Remove physical scaling. The visualizer always uses the complete export canvas.
text = text.replace(
    '            if (includeVisualizer) {\n                c.save();\n                c.scale(visualizerScale, visualizerScale, cx, cy);\n            }\n\n',
    '',
    1,
)
text = text.replace(
    '            if (includeVisualizer) {\n                c.restore();\n            }\n            paint.clearShadowLayer();\n',
    '            paint.clearShadowLayer();\n',
    1,
)

# Add alpha helpers to make intensity independent from size.
anchor = '        private void drawLyricsAndText(Canvas c, int w, int h, long curMs) {'
helpers = '''        private int alphaFor(int baseAlpha, float intensity) {\n            float v = Math.max(0f, Math.min(1f, intensity));\n            return Math.max(0, Math.min(255, Math.round(baseAlpha * v)));\n        }\n\n        private int colorWithIntensity(int color, float intensity) {\n            return Color.argb(alphaFor(Color.alpha(color), intensity), Color.red(color), Color.green(color), Color.blue(color));\n        }\n\n'''
if 'private int alphaFor(int baseAlpha' not in text and anchor in text:
    text = text.replace(anchor, helpers + anchor, 1)

# Apply visualizer intensity to renderer alpha calls.
rs = text.find('            // Render visualizer styles')
if rs >= 0:
    re_end = text.find('            paint.clearShadowLayer();', rs)
    if re_end >= 0:
        section = text[rs:re_end]
        section = re.sub(r'paint\.setAlpha\(([^;]+)\);', r'paint.setAlpha(alphaFor((int)(\1), visualizerIntensity));', section)
        text = text[:rs] + section + text[re_end:]

# Apply lyric intensity to lyric foreground/shadows without touching font size.
ls = text.find('        private void drawLyricsAndText(')
le = text.find('        private void drawFittedCentered', ls) if ls >= 0 else -1
if ls >= 0 and le >= 0:
    lyr = text[ls:le]
    lyr = lyr.replace('textPaint.setColor(Color.WHITE);', 'textPaint.setColor(colorWithIntensity(Color.WHITE, lyricsIntensity));')
    lyr = lyr.replace('textPaint.setColor(Color.parseColor(activeColor));', 'textPaint.setColor(colorWithIntensity(Color.parseColor(activeColor), lyricsIntensity));')
    lyr = lyr.replace('textPaint.setShadowLayer(dp(16), 0, 0, Color.WHITE);', 'textPaint.setShadowLayer(dp(16), 0, 0, colorWithIntensity(Color.WHITE, lyricsIntensity));')
    lyr = lyr.replace('textPaint.setShadowLayer(dp(8), 0, 0, 0xCC000000);', 'textPaint.setShadowLayer(dp(8), 0, 0, colorWithIntensity(0xCC000000, lyricsIntensity));')
    text = text[:ls] + lyr + text[le:]

# Auto-detect language rather than forcing French.
text = text.replace(
    'GroqClient.Transcript t = GroqClient.transcribe(k, audioFile, audioMime, "fr");',
    'GroqClient.Transcript t = GroqClient.transcribe(k, audioFile, audioMime, "");',
    1,
)

# Replace the nested Groq transcription implementation with a more complete multilingual pass.
trans_start = text.find('        static Transcript transcribe(String rawKey, File file, String mime, String language) throws Exception {')
trans_end = text.find('        static List<LyricLine> translateLyrics', trans_start) if trans_start >= 0 else -1
if trans_start < 0 or trans_end < 0:
    raise SystemExit('Groq transcribe method not found')
new_transcribe = r'''        static Transcript transcribe(String rawKey, File file, String mime, String language) throws Exception {
            final String key = rawKey == null ? "" : rawKey.trim();
            if (key.isEmpty()) throw new Exception("Clé API Groq manquante.");
            if (file.length() > 25L * 1024L * 1024L) throw new Exception("Audio > 25 MB. Utilisez une version compressée ou découpée.");

            String[] models = {"whisper-large-v3", "whisper-large-v3-turbo"};
            Transcript best = null;
            Exception last = null;
            double bestScore = -1.0;

            for (String model : models) {
                try {
                    String boundary = "----VA" + System.nanoTime();
                    HttpURLConnection c = (HttpURLConnection) new URL(TRANS).openConnection();
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(180000);
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    try (OutputStream out = c.getOutputStream()) {
                        field(out, boundary, "model", model);
                        if (language != null && !language.isEmpty()) field(out, boundary, "language", language);
                        field(out, boundary, "temperature", "0.0");
                        field(out, boundary, "response_format", "verbose_json");
                        field(out, boundary, "timestamp_granularities[]", "segment");
                        field(out, boundary, "timestamp_granularities[]", "word");
                        part(out, boundary, "file", file.getName(), mime, new FileInputStream(file));
                        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    }

                    String raw = read(c);
                    ensure(c, raw);
                    JSONObject json = new JSONObject(raw);
                    String fullText = json.optString("text", "").trim();
                    ArrayList<LyricLine> lines = new ArrayList<>();

                    JSONArray segments = json.optJSONArray("segments");
                    if (segments != null) {
                        for (int i = 0; i < segments.length(); i++) {
                            JSONObject seg = segments.getJSONObject(i);
                            long start = Math.max(0L, Math.round(seg.optDouble("start", 0.0) * 1000.0));
                            long end = Math.max(start + 350L, Math.round(seg.optDouble("end", 0.0) * 1000.0));
                            String value = seg.optString("text", "").replaceAll("\\s+", " ").trim();
                            if (!value.isEmpty()) lines.add(new LyricLine(start, end, value));
                        }
                    }

                    if (lines.isEmpty()) {
                        JSONArray words = json.optJSONArray("words");
                        if (words != null) {
                            StringBuilder buffer = new StringBuilder();
                            long start = 0L, end = 0L;
                            for (int i = 0; i < words.length(); i++) {
                                JSONObject w = words.getJSONObject(i);
                                long ws = Math.max(0L, Math.round(w.optDouble("start", 0.0) * 1000.0));
                                long we = Math.max(ws + 200L, Math.round(w.optDouble("end", 0.0) * 1000.0));
                                String value = w.optString("word", "").trim();
                                if (value.isEmpty()) continue;
                                if (buffer.length() == 0) start = ws;
                                if (buffer.length() > 0) buffer.append(' ');
                                buffer.append(value);
                                end = we;
                                if (buffer.length() >= 80 || end - start >= 5000L) {
                                    lines.add(new LyricLine(start, Math.max(start + 350L, end), buffer.toString()));
                                    buffer.setLength(0);
                                }
                            }
                            if (buffer.length() > 0) lines.add(new LyricLine(start, Math.max(start + 350L, end), buffer.toString()));
                        }
                    }

                    if (lines.isEmpty() && !fullText.isEmpty()) lines.add(new LyricLine(0L, 10000L, fullText));
                    Collections.sort(lines, (x, y) -> Long.compare(x.startMs, y.startMs));

                    long duration = Math.max(Math.round(json.optDouble("duration", 0.0) * 1000.0), lines.isEmpty() ? 0L : lines.get(lines.size() - 1).endMs);
                    long covered = 0L;
                    long cursor = -1L;
                    for (LyricLine line : lines) {
                        long start = Math.max(line.startMs, cursor < 0 ? line.startMs : cursor);
                        long end = Math.max(line.endMs, start + 350L);
                        covered += Math.max(0L, end - start);
                        cursor = Math.max(cursor, end);
                    }
                    double coverage = duration > 0 ? Math.min(1.0, covered / (double) duration) : 0.0;
                    double score = fullText.length() + lines.size() * 28.0 + coverage * 700.0;
                    Transcript candidate = new Transcript(fullText, lines);
                    if (score > bestScore) { bestScore = score; best = candidate; }
                    if (model.equals("whisper-large-v3") && coverage >= 0.80 && lines.size() >= 4) break;
                } catch (Exception e) {
                    last = e;
                }
            }

            if (best != null && !best.lines.isEmpty()) return best;
            if (last != null) throw last;
            throw new Exception("Échec de la transcription Whisper.");
        }

'''
text = text[:trans_start] + new_transcribe + text[trans_end:]

# Never leave emoji code points in the Java UI source.
ranges = [(0x1F1E6,0x1F1FF),(0x1F300,0x1FAFF),(0x1FC00,0x1FFFF),(0xFE00,0xFE0F),(0x200D,0x200D),(0x20E3,0x20E3)]
for p in (ROOT / 'app/src/main/java').rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    clean = ''.join(ch for ch in s if not any(lo <= ord(ch) <= hi for lo, hi in ranges))
    if clean != s: p.write_text(clean, encoding='utf-8')

MAIN.write_text(text, encoding='utf-8')

g = GRADLE.read_text(encoding='utf-8')
g = re.sub(r'(?m)^\s*versionCode\s+\d+\s*$', '        versionCode 22', g, count=1)
g = re.sub(r'(?m)^\s*versionName\s+"[^"]+"\s*$', '        versionName "2.8.0"', g, count=1)
GRADLE.write_text(g, encoding='utf-8')

print('Applied: visualizer intensity, lyric size/intensity, full-frame visualizer, auto-language transcription, segment+word timestamps, Whisper quality fallback, emoji cleanup, version 2.8.0/22')
