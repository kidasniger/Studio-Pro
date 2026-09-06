package com.kidas.studiopro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class LrcParserTest {
    @Test
    public void parsesMultipleTimestampTags() {
        List<LrcParser.Line> lines = LrcParser.parse("[00:01.00][00:03.50]hello");
        assertEquals(2, lines.size());
        assertEquals(1000L, lines.get(0).startMs);
        assertEquals(3500L, lines.get(1).startMs);
        assertEquals(3500L, lines.get(0).endMs);
    }

    @Test
    public void normalizesFractionalSecondsAndSorts() {
        List<LrcParser.Line> lines = LrcParser.parse("[00:05.1]late\n[00:02.25]early");
        assertEquals(2, lines.size());
        assertEquals(2250L, lines.get(0).startMs);
        assertEquals(5100L, lines.get(1).startMs);
        assertTrue(lines.get(0).endMs >= 2500L);
    }
}
