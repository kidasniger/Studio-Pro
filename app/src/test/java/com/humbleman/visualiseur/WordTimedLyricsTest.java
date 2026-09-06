package com.kidas.studiopro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class WordTimedLyricsTest {
    @Test public void parsesWhisperWordTimestamps() {
        String json = "{\"segments\":[{\"start\":0.5,\"end\":2.5,\"text\":\"hello world\",\"words\":[{\"word\":\"hello\",\"start\":0.5,\"end\":1.2},{\"word\":\"world\",\"start\":1.2,\"end\":2.5}]}]}";
        WordTimedLyrics t = WordTimedLyrics.fromWhisperJson(json);
        assertTrue(t.hasWords());
        assertEquals("hello", t.wordAt(700));
        assertEquals("world", t.wordAt(1700));
    }

    @Test public void translatedWordsStayInsideOriginalWordSpan() {
        String json = "{\"segments\":[{\"start\":1.0,\"end\":3.0,\"text\":\"hello world\",\"words\":[{\"word\":\"hello\",\"start\":1.0,\"end\":1.8},{\"word\":\"world\",\"start\":1.8,\"end\":3.0}]}]}";
        WordTimedLyrics t = WordTimedLyrics.fromWhisperJson(json);
        List<MainActivity.LyricLine> src = Arrays.asList(new MainActivity.LyricLine(1000, 3000, "hello world"));
        List<MainActivity.LyricLine> dst = Arrays.asList(new MainActivity.LyricLine(1000, 3000, "bonjour monde"));
        WordTimedLyrics mapped = t.mapTranslatedLines(src, dst);
        assertEquals(1000L, mapped.getLines().get(0).startMs);
        assertEquals(3000L, mapped.getLines().get(0).endMs);
        assertEquals("bonjour", mapped.wordAt(1300));
        assertEquals("monde", mapped.wordAt(2300));
    }
}
