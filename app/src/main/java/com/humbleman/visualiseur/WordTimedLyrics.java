package com.kidas.studiopro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WordTimedLyrics {
    public static final class Word {
        public final long startMs;
        public final long endMs;
        public final String text;
        public Word(long startMs, long endMs, String text) {
            this.startMs = Math.max(0L, startMs);
            this.endMs = Math.max(this.startMs + 1L, endMs);
            this.text = text == null ? "" : text.trim();
        }
    }

    public static final class Line {
        public final long startMs;
        public final long endMs;
        public final String text;
        public final List<Word> words;
        Line(long startMs, long endMs, String text, List<Word> words) {
            this.startMs = Math.max(0L, startMs);
            this.endMs = Math.max(this.startMs + 1L, endMs);
            this.text = text == null ? "" : text.trim();
            this.words = Collections.unmodifiableList(new ArrayList<>(words));
        }
    }

    private static final Pattern SEGMENT_PATTERN = Pattern.compile(
            "\\{\\s*\\\"start\\\"\\s*:\\s*([0-9.]+).*?\\\"end\\\"\\s*:\\s*([0-9.]+).*?\\\"words\\\"\\s*:\\s*\\[(.*?)\\]\\s*\\}",
            Pattern.DOTALL);
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "\\{\\s*\\\"word\\\"\\s*:\\s*\\\"((?:\\\\\\\"|[^\"])*)\\\"\\s*,\\s*\\\"start\\\"\\s*:\\s*([0-9.]+)\\s*,\\s*\\\"end\\\"\\s*:\\s*([0-9.]+)\\s*\\}",
            Pattern.DOTALL);

    private final List<Line> lines;
    private WordTimedLyrics(List<Line> lines) { this.lines = Collections.unmodifiableList(new ArrayList<>(lines)); }
    public static WordTimedLyrics empty() { return new WordTimedLyrics(Collections.emptyList()); }
    public boolean hasWords() {
        for (Line line : lines) if (!line.words.isEmpty()) return true;
        return false;
    }
    public List<Line> getLines() { return lines; }

    public static WordTimedLyrics fromWhisperJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return empty();
        List<Line> parsed = new ArrayList<>();
        Matcher segments = SEGMENT_PATTERN.matcher(rawJson);
        while (segments.find()) {
            try {
                long segmentStart = Math.max(0L, Math.round(Double.parseDouble(segments.group(1)) * 1000.0));
                long segmentEnd = Math.max(segmentStart + 1L, Math.round(Double.parseDouble(segments.group(2)) * 1000.0));
                Matcher wordsMatcher = WORD_PATTERN.matcher(segments.group(3));
                List<Word> words = new ArrayList<>();
                StringBuilder lineText = new StringBuilder();
                while (wordsMatcher.find()) {
                    String wordText = wordsMatcher.group(1).replace("\\\"", "\"").replaceAll("\\s+", " ").trim();
                    if (wordText.isEmpty()) continue;
                    long start = Math.max(segmentStart, Math.round(Double.parseDouble(wordsMatcher.group(2)) * 1000.0));
                    long end = Math.max(start + 1L, Math.round(Double.parseDouble(wordsMatcher.group(3)) * 1000.0));
                    if (end > segmentEnd) end = segmentEnd;
                    if (end <= start) continue;
                    words.add(new Word(start, end, wordText));
                    if (lineText.length() > 0) lineText.append(' ');
                    lineText.append(wordText);
                }
                if (!words.isEmpty()) parsed.add(new Line(segmentStart, segmentEnd, lineText.toString(), words));
            } catch (RuntimeException ignored) {}
        }
        return parsed.isEmpty() ? empty() : new WordTimedLyrics(parsed);
    }

    public String wordAt(long currentMs) {
        if (!hasWords()) return "";
        for (Line line : lines) {
            for (Word word : line.words) {
                if (currentMs >= word.startMs && currentMs < word.endMs) return word.text;
            }
            if (currentMs >= line.startMs && currentMs <= line.endMs && !line.words.isEmpty()) {
                if (currentMs < line.words.get(0).startMs) return line.words.get(0).text;
                return line.words.get(line.words.size() - 1).text;
            }
        }
        return "";
    }

    public WordTimedLyrics mapTranslatedLines(List<MainActivity.LyricLine> source, List<MainActivity.LyricLine> translated) {
        if (!hasWords() || source == null || translated == null || source.size() != translated.size()) return this;
        List<Line> mapped = new ArrayList<>();
        int count = Math.min(lines.size(), translated.size());
        for (int i = 0; i < count; i++) {
            Line sourceLine = lines.get(i);
            MainActivity.LyricLine target = translated.get(i);
            String[] tokens = target.text == null ? new String[0] : target.text.trim().split("\\s+");
            List<Word> mappedWords = new ArrayList<>();
            if (tokens.length > 0 && !sourceLine.words.isEmpty()) {
                long start = sourceLine.words.get(0).startMs;
                long end = sourceLine.words.get(sourceLine.words.size() - 1).endMs;
                long span = Math.max(1L, end - start);
                for (int token = 0; token < tokens.length; token++) {
                    long ws = start + Math.round(span * token / (double) tokens.length);
                    long we = start + Math.round(span * (token + 1) / (double) tokens.length);
                    if (token == tokens.length - 1) we = end;
                    mappedWords.add(new Word(ws, Math.max(ws + 1L, we), tokens[token]));
                }
            }
            mapped.add(new Line(target.startMs, target.endMs, target.text, mappedWords));
        }
        return new WordTimedLyrics(mapped);
    }

    public String diagnosticSummary() {
        int count = 0;
        for (Line line : lines) count += line.words.size();
        return String.format(Locale.US, "%d lignes, %d mots chronométrés", lines.size(), count);
    }
}
