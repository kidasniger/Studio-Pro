package com.kidas.studiopro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiProjectAssistantTest {
    private static final class Recorder implements AiProjectAssistant.ProjectCommandCallback {
        String action = "";
        String value = "";
        int width;
        int height;
        int fps;

        @Override public void onVisualizerLengthChanged(float scale) { action = "visualizerLength"; value = String.valueOf(scale); }
        @Override public void onLyricsWidthChanged(float widthPercent) { action = "lyricsWidth"; value = String.valueOf(widthPercent); }
        @Override public void onLyricsAlignmentChanged(String alignment) { action = "lyricsAlignment"; value = alignment; }
        @Override public void onLyricsVerticalOffsetChanged(float offset) { action = "lyricsOffset"; value = String.valueOf(offset); }
        @Override public void onLyricsModeChanged(String mode) { action = "lyricsMode"; value = mode; }
        @Override public void onExportConfigChanged(int width, int height, int fps) { action = "export"; this.width = width; this.height = height; this.fps = fps; }
        @Override public void onStyleChanged(String style) { action = "style"; value = style; }
        @Override public void onColorChanged(String colorHex) { action = "color"; value = colorHex; }
        @Override public void onAudioEffectsToggled(boolean enabled) { action = "effects"; value = String.valueOf(enabled); }
        @Override public void onPlaybackCommand(boolean play) { action = "playback"; value = String.valueOf(play); }
        @Override public void onOpenAudioBrowser() { action = "browser"; }
        @Override public void onTranslateLyricsCommand(String targetLanguage) { action = "translate"; value = targetLanguage; }
    }

    @Test
    public void understandsVerticalExportCommand() {
        Recorder recorder = new Recorder();
        AiProjectAssistant.CommandExecutionResult result = AiProjectAssistant.processUserPrompt("export en vertical 60 fps", recorder);
        assertTrue(result.handled);
        assertEquals("export", recorder.action);
        assertEquals(1080, recorder.width);
        assertEquals(1920, recorder.height);
        assertEquals(60, recorder.fps);
    }

    @Test
    public void understandsZarmaTranslationCommand() {
        Recorder recorder = new Recorder();
        AiProjectAssistant.CommandExecutionResult result = AiProjectAssistant.processUserPrompt("traduis les paroles en zarma", recorder);
        assertTrue(result.handled);
        assertEquals("translate", recorder.action);
        assertEquals("Zarma (Djerma / Songhaï)", recorder.value);
    }

    @Test
    public void rejectsUnknownCommand() {
        Recorder recorder = new Recorder();
        AiProjectAssistant.CommandExecutionResult result = AiProjectAssistant.processUserPrompt("fais quelque chose d'inconnu", recorder);
        assertFalse(result.handled);
    }
}
