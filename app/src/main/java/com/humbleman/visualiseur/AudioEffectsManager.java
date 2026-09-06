package com.kidas.studiopro;

import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.util.Log;

public class AudioEffectsManager {

    private static final String TAG = "AudioEffectsManager";
    private static AudioEffectsManager instance;

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private PresetReverb presetReverb;

    private boolean effectsEnabled = true;
    private int bassStrength = 0;
    private int virtualizerStrength = 0;
    private short currentReverbPreset = PresetReverb.PRESET_NONE;
    private short currentEqPreset = -1;
    private float playbackSpeed = 1.0f;
    private float playbackPitch = 1.0f;

    private AudioEffectsManager() {}

    public static synchronized AudioEffectsManager getInstance() {
        if (instance == null) instance = new AudioEffectsManager();
        return instance;
    }

    public void attachToPlayer(MediaPlayer player) {
        if (player == null) return;
        release();
        try {
            int sessionId = player.getAudioSessionId();
            if (sessionId != 0) {
                equalizer = new Equalizer(0, sessionId);
                equalizer.setEnabled(effectsEnabled);

                bassBoost = new BassBoost(0, sessionId);
                bassBoost.setEnabled(effectsEnabled);
                if (bassBoost.getStrengthSupported()) bassBoost.setStrength((short) bassStrength);

                virtualizer = new Virtualizer(0, sessionId);
                virtualizer.setEnabled(effectsEnabled);
                if (virtualizer.getStrengthSupported()) virtualizer.setStrength((short) virtualizerStrength);

                presetReverb = new PresetReverb(0, sessionId);
                presetReverb.setEnabled(effectsEnabled);
                presetReverb.setPreset(currentReverbPreset);

                if (currentEqPreset >= 0 && currentEqPreset < equalizer.getNumberOfPresets()) {
                    equalizer.usePreset(currentEqPreset);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to attach audio effects to playback session", e);
        }
        applySpeedAndPitch(player);
    }

    public void applySpeedAndPitch(MediaPlayer player) {
        if (player == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PlaybackParams params = player.getPlaybackParams();
            if (params == null) params = new PlaybackParams();
            params.setSpeed(playbackSpeed);
            params.setPitch(playbackPitch);
            player.setPlaybackParams(params);
        } catch (Exception e) {
            Log.w(TAG, "Unable to apply playback speed/pitch", e);
        }
    }

    public void setEffectsEnabled(boolean enabled) {
        effectsEnabled = enabled;
        try {
            if (equalizer != null) equalizer.setEnabled(enabled);
            if (bassBoost != null) bassBoost.setEnabled(enabled);
            if (virtualizer != null) virtualizer.setEnabled(enabled);
            if (presetReverb != null) presetReverb.setEnabled(enabled);
        } catch (Exception e) {
            Log.w(TAG, "Unable to toggle audio effects", e);
        }
    }

    public boolean isEffectsEnabled() { return effectsEnabled; }
    public Equalizer getEqualizer() { return equalizer; }

    public void setBassStrength(int strength) {
        bassStrength = Math.max(0, Math.min(1000, strength));
        if (bassBoost != null && bassBoost.getStrengthSupported()) {
            try { bassBoost.setStrength((short) bassStrength); }
            catch (Exception e) { Log.w(TAG, "Unable to set bass strength", e); }
        }
    }
    public int getBassStrength() { return bassStrength; }

    public void setVirtualizerStrength(int strength) {
        virtualizerStrength = Math.max(0, Math.min(1000, strength));
        if (virtualizer != null && virtualizer.getStrengthSupported()) {
            try { virtualizer.setStrength((short) virtualizerStrength); }
            catch (Exception e) { Log.w(TAG, "Unable to set virtualizer strength", e); }
        }
    }
    public int getVirtualizerStrength() { return virtualizerStrength; }

    public void setReverbPreset(short preset) {
        currentReverbPreset = preset;
        if (presetReverb != null) {
            try { presetReverb.setPreset(preset); }
            catch (Exception e) { Log.w(TAG, "Unable to set reverb preset", e); }
        }
    }
    public short getReverbPreset() { return currentReverbPreset; }

    public void setEqPreset(short preset) {
        currentEqPreset = preset;
        if (equalizer != null) {
            try {
                if (preset >= 0 && preset < equalizer.getNumberOfPresets()) equalizer.usePreset(preset);
            } catch (Exception e) { Log.w(TAG, "Unable to set equalizer preset", e); }
        }
    }
    public short getEqPreset() { return currentEqPreset; }

    public void setPlaybackSpeed(float speed, MediaPlayer player) {
        playbackSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        applySpeedAndPitch(player);
    }
    public float getPlaybackSpeed() { return playbackSpeed; }

    public void setPlaybackPitch(float pitch, MediaPlayer player) {
        playbackPitch = Math.max(0.5f, Math.min(2.0f, pitch));
        applySpeedAndPitch(player);
    }
    public float getPlaybackPitch() { return playbackPitch; }

    public void release() {
        try { if (equalizer != null) equalizer.release(); } catch (Exception e) { Log.w(TAG, "Unable to release equalizer", e); } finally { equalizer = null; }
        try { if (bassBoost != null) bassBoost.release(); } catch (Exception e) { Log.w(TAG, "Unable to release bass boost", e); } finally { bassBoost = null; }
        try { if (virtualizer != null) virtualizer.release(); } catch (Exception e) { Log.w(TAG, "Unable to release virtualizer", e); } finally { virtualizer = null; }
        try { if (presetReverb != null) presetReverb.release(); } catch (Exception e) { Log.w(TAG, "Unable to release reverb", e); } finally { presetReverb = null; }
    }
}
