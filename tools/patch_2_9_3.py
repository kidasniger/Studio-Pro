from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
EDITOR = ROOT / 'app/src/main/java/com/humbleman/visualiseur/AudioEditorDialog.java'
text = MAIN.read_text(encoding='utf-8')
editor = EDITOR.read_text(encoding='utf-8')

# State and badge.
if 'private WordTimedLyrics wordTimedLyrics' not in text:
    anchor = 'private final List<LyricLine> lyricsList = new ArrayList<>();'
    assert anchor in text
    text = text.replace(anchor, anchor + '\n    private WordTimedLyrics wordTimedLyrics = WordTimedLyrics.empty();', 1)
text = text.replace('LRC • 98% SYNC', 'LRC • SYNC ACTIVE')

# Compact preview: use real word timestamps, never proportional fake timing.
old_preview = '''            if ("word".equals(textMode)) {
                LyricLine curLine = lyricsList.get(activeIndex);
                String[] words = curLine.text.split("\\\\s+");
                if (words.length > 0) {
                    float frac = (curLine.endMs > curLine.startMs) ? (curMs - curLine.startMs) / (float) (curLine.endMs - curLine.startMs) : 0f;
                    int wordIdx = Math.max(0, Math.min(words.length - 1, (int) (frac * words.length)));
                    karaokeActiveLine.setText(words[wordIdx]);
                } else {
                    karaokeActiveLine.setText(curLine.text);
                }
            } else {
                karaokeActiveLine.setText(lyricsList.get(activeIndex).text);
            }'''
new_preview = '''            if ("word".equals(textMode) && wordTimedLyrics != null && wordTimedLyrics.hasWords()) {
                LyricLine curLine = lyricsList.get(activeIndex);
                String timedWord = wordTimedLyrics.wordAt(curMs);
                karaokeActiveLine.setText(timedWord.isEmpty() ? curLine.text : timedWord);
            } else {
                karaokeActiveLine.setText(lyricsList.get(activeIndex).text);
            }'''
if old_preview in text:
    text = text.replace(old_preview, new_preview, 1)
elif 'int wordIdx =' in text and 'wordTimedLyrics.wordAt(curMs)' not in text:
    raise SystemExit('Ancien rendu mot-à-mot détecté et non reconnu; arrêt volontaire.')

# Export/visualizer renderer: exact block replacement. This returns from the word branch correctly.
old_render = '''                } else if ("word".equals(textMode)) {
                    LyricLine curLine = lyricsList.get(activeIndex);
                    String[] words = curLine.text.split("\\\\s+");
                    if (words.length > 0) {
                        float frac = (curLine.endMs > curLine.startMs) ? (curMs - curLine.startMs) / (float) (curLine.endMs - curLine.startMs) : 0f;
                        int wordIdx = Math.max(0, Math.min(words.length - 1, (int) (frac * words.length)));
                        textPaint.setTextSize(dp((int) (textSize * 1.35f * lyricsScale)));
                        textPaint.setColor(colorWithIntensity(Color.parseColor(activeColor), lyricsIntensity));
                        textPaint.setShadowLayer(dp(16), 0, 0, colorWithIntensity(Color.WHITE, lyricsIntensity));
                        drawFittedCentered(c, words[wordIdx], w / 2f, baseY, (w * 0.90f * lyricsMaxWidth / 100f));
                        textPaint.clearShadowLayer();
                    }
                    return;
                }'''
new_render = '''                } else if ("word".equals(textMode)) {
                    String timedWord = wordTimedLyrics == null ? "" : wordTimedLyrics.wordAt(curMs);
                    String wordText = timedWord.isEmpty() ? lyricsList.get(activeIndex).text : timedWord;
                    textPaint.setTextSize(dp((int) (textSize * 1.35f * lyricsScale)));
                    textPaint.setColor(colorWithIntensity(Color.parseColor(activeColor), lyricsIntensity));
                    textPaint.setShadowLayer(dp(16), 0, 0, colorWithIntensity(Color.WHITE, lyricsIntensity));
                    drawFittedCentered(c, wordText, w / 2f, baseY, (w * 0.90f * lyricsMaxWidth / 100f));
                    textPaint.clearShadowLayer();
                    return;
                }'''
if old_render in text:
    text = text.replace(old_render, new_render, 1)
elif 'int wordIdx =' in text and 'wordTimedLyrics.wordAt(curMs)' not in text:
    raise SystemExit('Ancien rendu export mot-à-mot détecté et non reconnu; arrêt volontaire.')

# Transcript carries real Whisper word timeline.
if 'final WordTimedLyrics wordTimeline;' not in text:
    marker = '''        static final class Transcript {
            final String text;
            final List<LyricLine> lines;
            Transcript(String t, List<LyricLine> lines) {
                this.text = t;
                this.lines = lines != null ? lines : new ArrayList<>();
            }
        }'''
    replacement = '''        static final class Transcript {
            final String text;
            final List<LyricLine> lines;
            final WordTimedLyrics wordTimeline;
            Transcript(String t, List<LyricLine> lines) {
                this(t, lines, WordTimedLyrics.empty());
            }
            Transcript(String t, List<LyricLine> lines, WordTimedLyrics wordTimeline) {
                this.text = t;
                this.lines = lines != null ? lines : new ArrayList<>();
                this.wordTimeline = wordTimeline == null ? WordTimedLyrics.empty() : wordTimeline;
            }
        }'''
    assert marker in text
    text = text.replace(marker, replacement, 1)

if 'return new Transcript(fullText, segmentLines, WordTimedLyrics.fromWhisperJson(raw));' not in text:
    assert 'return new Transcript(fullText, segmentLines);' in text
    text = text.replace('return new Transcript(fullText, segmentLines);', 'return new Transcript(fullText, segmentLines, WordTimedLyrics.fromWhisperJson(raw));', 1)

if 'final WordTimedLyrics nextWordTimeline = t.wordTimeline;' not in text:
    needle = 'GroqClient.Transcript t = GroqClient.transcribe(k, audioFile, audioMime, "");\n                runOnUiThread(() -> {'
    assert needle in text
    text = text.replace(needle, 'GroqClient.Transcript t = GroqClient.transcribe(k, audioFile, audioMime, "");\n                final WordTimedLyrics nextWordTimeline = t.wordTimeline;\n                runOnUiThread(() -> {', 1)
if 'wordTimedLyrics = nextWordTimeline;' not in text:
    needle = 'lyricsList.clear();\n                    lyricsList.addAll(t.lines);\n                    quote = t.text;'
    assert needle in text
    text = text.replace(needle, 'wordTimedLyrics = nextWordTimeline == null ? WordTimedLyrics.empty() : nextWordTimeline;\n                    lyricsList.clear();\n                    lyricsList.addAll(t.lines);\n                    quote = t.text;', 1)

# Ask Whisper for segment and word timestamps in the existing transcription request.
response = 'field(out, boundary, "response_format", "verbose_json");'
assert response in text
if 'field(out, boundary, "timestamp_granularities[]", "word");' not in text:
    text = text.replace(response, response + '\n                        field(out, boundary, "timestamp_granularities[]", "segment");\n                        field(out, boundary, "timestamp_granularities[]", "word");', 1)

# Clear stale word timings whenever an external LRC replaces the current lyrics.
load = 'private void loadLrcFile(Uri uri) {'
assert load in text
pos = text.index(load)
brace = text.index('{', pos) + 1
if 'wordTimedLyrics = WordTimedLyrics.empty();' not in text[brace:brace + 220]:
    text = text[:brace] + '\n        wordTimedLyrics = WordTimedLyrics.empty();' + text[brace:]

# Translation: preserve source line timing and map translated words inside the source word span.
anchor = 'boolean hasLrc = !lyricsList.isEmpty();\n        String currentText ='
if anchor in text and 'sourceLyricsForTranslation' not in text:
    text = text.replace(anchor, 'boolean hasLrc = !lyricsList.isEmpty();\n        final List<LyricLine> sourceLyricsForTranslation = new ArrayList<>(lyricsList);\n        String currentText =', 1)
if 'final WordTimedLyrics translatedWordTimeline' not in text:
    tr = 'List<LyricLine> translated = GroqClient.translateLyrics(key, lyricsList, targetLanguage);\n                    runOnUiThread(() -> {'
    if tr in text:
        text = text.replace(tr, 'List<LyricLine> translated = GroqClient.translateLyrics(key, lyricsList, targetLanguage);\n                    final WordTimedLyrics translatedWordTimeline = wordTimedLyrics == null ? WordTimedLyrics.empty() : wordTimedLyrics.mapTranslatedLines(sourceLyricsForTranslation, translated);\n                    runOnUiThread(() -> {', 1)
if 'wordTimedLyrics = translatedWordTimeline;' not in text:
    tr2 = 'lyricsList.clear();\n                        lyricsList.addAll(translated);\n                        StringBuilder sb = new StringBuilder();'
    if tr2 in text:
        text = text.replace(tr2, 'wordTimedLyrics = translatedWordTimeline;\n                        lyricsList.clear();\n                        lyricsList.addAll(translated);\n                        StringBuilder sb = new StringBuilder();', 1)

# Existing editor processing is retained; replace only if the dialog still calls the raw engine.
if 'AudioEditSafety.process(' not in editor:
    assert 'AudioEditEngine.process(source, processed' in editor
    editor = editor.replace('AudioEditEngine.process(source, processed', 'AudioEditSafety.process(source, processed', 1)

MAIN.write_text(text, encoding='utf-8')
EDITOR.write_text(editor, encoding='utf-8')
print('2.9.3 patch applied safely')
