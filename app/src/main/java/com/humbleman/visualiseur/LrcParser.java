package com.kidas.studiopro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure JVM-testable LRC parser. */
public final class LrcParser {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    private LrcParser() {}

    public static final class Line {
        public final long startMs;
        public final long endMs;
        public final String text;
        public Line(long startMs, long endMs, String text) {
            this.startMs = Math.max(0L, startMs);
            this.endMs = Math.max(this.startMs + 250L, endMs);
            this.text = text == null ? "" : text.trim();
        }
    }

    public static List<Line> parse(String content) {
        List<Line> result = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) return result;
        for (String raw : content.split("\\r?\\n")) {
            Matcher m = TIME.matcher(raw);
            List<Long> timestamps = new ArrayList<>();
            int end = -1;
            while (m.find()) {
                long minutes = Long.parseLong(m.group(1));
                long seconds = Long.parseLong(m.group(2));
                String fraction = m.group(3);
                long ms = 0L;
                if (fraction != null && !fraction.isEmpty()) {
                    if (fraction.length() == 1) ms = Long.parseLong(fraction) * 100L;
                    else if (fraction.length() == 2) ms = Long.parseLong(fraction) * 10L;
                    else ms = Long.parseLong(fraction.substring(0, 3));
                }
                timestamps.add(minutes * 60000L + seconds * 1000L + ms);
                end = m.end();
            }
            if (timestamps.isEmpty() || end < 0) continue;
            String text = raw.substring(end).trim();
            if (text.isEmpty()) continue;
            for (Long timestamp : timestamps) result.add(new Line(timestamp, timestamp + 3500L, text));
        }
        Collections.sort(result, (a, b) -> Long.compare(a.startMs, b.startMs));
        for (int i = 0; i + 1 < result.size(); i++) {
            Line current = result.get(i);
            Line next = result.get(i + 1);
            result.set(i, new Line(current.startMs, Math.max(current.startMs + 250L, next.startMs), current.text));
        }
        return result;
    }
}
