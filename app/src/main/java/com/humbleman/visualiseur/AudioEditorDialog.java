package com.kidas.studiopro;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Éditeur Audio complet pour Studio Pro :
 * - Découpe précise début / fin
 * - Fondus entrant / sortant (Fade In / Fade Out)
 * - Gain & Normalisation
 * - Vitesse de lecture
 * - Détection et rognage des silences
 * - Boucle & Prévisualisation instantanée
 */
public class AudioEditorDialog extends Dialog {

    public interface OnAudioEditedListener {
        void onAudioEdited(long startMs, long endMs, float volumeGain, float speed, float fadeInSec, float fadeOutSec, boolean normalize, boolean loop);
    }

    private final File audioFile;
    private final long totalDurationMs;
    private long currentStartMs;
    private long currentEndMs;
    private float volumeGain = 1.0f;
    private float playbackSpeed = 1.0f;
    private float fadeInSec = 0.0f;
    private float fadeOutSec = 0.0f;
    private boolean normalize = false;
    private boolean loopMode = false;

    private final OnAudioEditedListener listener;
    private MediaPlayer previewPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;

    private WaveformEditorView waveformView;
    private TextView timeStartText;
    private TextView timeEndText;
    private TextView durationText;
    private TextView currentTimeText;
    private ImageView playIcon;
    private TextView fadeInLabel;
    private TextView fadeOutLabel;
    private TextView gainLabel;

    public AudioEditorDialog(Context context, File audioFile, long initialDurationMs, long initialStartMs, long initialEndMs, OnAudioEditedListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.audioFile = audioFile;
        this.totalDurationMs = initialDurationMs > 0 ? initialDurationMs : 60000;
        this.currentStartMs = Math.max(0, initialStartMs);
        this.currentEndMs = (initialEndMs > initialStartMs && initialEndMs <= totalDurationMs) ? initialEndMs : totalDurationMs;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(0xF00A0B10));
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        initPlayer();
        setContentView(buildView());
        startProgressUpdater();
    }

    private void initPlayer() {
        if (audioFile == null || !audioFile.exists()) return;
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(audioFile.getAbsolutePath());
            previewPlayer.prepare();
            previewPlayer.setVolume(volumeGain, volumeGain);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    android.media.PlaybackParams p = previewPlayer.getPlaybackParams();
                    if (p == null) p = new android.media.PlaybackParams();
                    p.setSpeed(playbackSpeed);
                    previewPlayer.setPlaybackParams(p);
                } catch (Exception ignored) {}
            }
            previewPlayer.setOnCompletionListener(mp -> {
                if (loopMode) {
                    previewPlayer.seekTo((int) currentStartMs);
                    previewPlayer.start();
                    isPlaying = true;
                } else {
                    isPlaying = false;
                }
                updatePlayButton();
            });
        } catch (Exception ignored) {}
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0B10);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        // 1. En-tête
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(getContext());
        titleView.setText("Éditeur Audio Studio Pro");
        titleView.setTextSize(17);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout closeBtn = new FrameLayout(getContext());
        shape(closeBtn, 0x14FFFFFF, dp(16), 0x22FFFFFF, true);
        ImageView closeIc = new ImageView(getContext());
        closeIc.setImageResource(R.drawable.ic_stop);
        closeIc.setColorFilter(0xB3FFFFFF);
        closeBtn.addView(closeIc, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        root.addView(header);
        root.addView(gap(14));

        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        // 2. Waveform Timeline & Trimming View
        LinearLayout waveCard = card(0x80000000, 0x1FFFFFFF, 18);
        waveCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        waveformView = new WaveformEditorView(getContext());
        waveCard.addView(waveformView, new LinearLayout.LayoutParams(-1, dp(100)));
        waveCard.addView(gap(8));

        LinearLayout timeLabelsRow = new LinearLayout(getContext());
        timeLabelsRow.setOrientation(LinearLayout.HORIZONTAL);

        timeStartText = new TextView(getContext());
        timeStartText.setText("Début: " + formatTime(currentStartMs));
        timeStartText.setTextSize(11);
        timeStartText.setTextColor(0xFF22D3EE);
        timeLabelsRow.addView(timeStartText, new LinearLayout.LayoutParams(0, -2, 1));

        currentTimeText = new TextView(getContext());
        currentTimeText.setText("Pos: " + formatTime(currentStartMs));
        currentTimeText.setTextSize(11);
        currentTimeText.setTextColor(0xFFFFFFFF);
        currentTimeText.setGravity(Gravity.CENTER);
        timeLabelsRow.addView(currentTimeText, new LinearLayout.LayoutParams(-2, -2));

        timeEndText = new TextView(getContext());
        timeEndText.setText("Fin: " + formatTime(currentEndMs));
        timeEndText.setTextSize(11);
        timeEndText.setTextColor(0xFFA855F7);
        timeEndText.setGravity(Gravity.END);
        timeLabelsRow.addView(timeEndText, new LinearLayout.LayoutParams(0, -2, 1));

        waveCard.addView(timeLabelsRow);
        content.addView(waveCard);
        content.addView(gap(12));

        // 3. Contrôles de lecture & navigation
        LinearLayout transportCard = card(0x0AFFFFFF, 0x14FFFFFF, 16);
        transportCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout transport = new LinearLayout(getContext());
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER);

        // Seek start
        FrameLayout btnSeekStart = new FrameLayout(getContext());
        shape(btnSeekStart, 0x14FFFFFF, dp(20), 0x22FFFFFF, true);
        ImageView icSeekStart = new ImageView(getContext());
        icSeekStart.setImageResource(R.drawable.ic_skip_back);
        icSeekStart.setColorFilter(0xB3FFFFFF);
        btnSeekStart.addView(icSeekStart, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btnSeekStart.setOnClickListener(v -> {
            if (previewPlayer != null) {
                previewPlayer.seekTo((int) currentStartMs);
                if (waveformView != null) waveformView.invalidate();
            }
        });
        transport.addView(btnSeekStart, new LinearLayout.LayoutParams(dp(42), dp(42)));
        transport.addView(gapW(20));

        // Play/Pause
        FrameLayout btnPlay = new FrameLayout(getContext());
        btnPlay.setBackgroundResource(R.drawable.bg_chip_active);
        playIcon = new ImageView(getContext());
        playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setColorFilter(Color.WHITE);
        btnPlay.addView(playIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btnPlay.setOnClickListener(v -> togglePlay());
        transport.addView(btnPlay, new LinearLayout.LayoutParams(dp(54), dp(54)));
        transport.addView(gapW(20));

        // Seek end
        FrameLayout btnSeekEnd = new FrameLayout(getContext());
        shape(btnSeekEnd, 0x14FFFFFF, dp(20), 0x22FFFFFF, true);
        ImageView icSeekEnd = new ImageView(getContext());
        icSeekEnd.setImageResource(R.drawable.ic_skip_forward);
        icSeekEnd.setColorFilter(0xB3FFFFFF);
        btnSeekEnd.addView(icSeekEnd, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btnSeekEnd.setOnClickListener(v -> {
            if (previewPlayer != null) {
                previewPlayer.seekTo((int) Math.max(currentStartMs, currentEndMs - 2000));
                if (waveformView != null) waveformView.invalidate();
            }
        });
        transport.addView(btnSeekEnd, new LinearLayout.LayoutParams(dp(42), dp(42)));

        transportCard.addView(transport);
        content.addView(transportCard);
        content.addView(gap(12));

        // 4. Cartes de réglages (Fondus, Gain, Vitesse, Normalisation, Silences)
        LinearLayout toolsCard = card(0x0AFFFFFF, 0x14FFFFFF, 16);
        toolsCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView secTitle = new TextView(getContext());
        secTitle.setText("TRAITEMENTS & EFFETS AUDIO");
        secTitle.setTextSize(11);
        secTitle.setTextColor(0xFF9CA3AF);
        secTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        secTitle.setLetterSpacing(0.06f);
        toolsCard.addView(secTitle);
        toolsCard.addView(gap(10));

        // Fade in
        fadeInLabel = new TextView(getContext());
        fadeInLabel.setText("Fondu entrant (Fade In) : " + String.format(Locale.US, "%.1fs", fadeInSec));
        fadeInLabel.setTextSize(12);
        fadeInLabel.setTextColor(0xFFE2E3EA);
        toolsCard.addView(fadeInLabel);

        SeekBar fadeInSeek = new SeekBar(getContext());
        fadeInSeek.setMax(50); // 0.0 à 5.0s
        fadeInSeek.setProgress((int) (fadeInSec * 10));
        fadeInSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fadeInSec = progress / 10.0f;
                fadeInLabel.setText("Fondu entrant (Fade In) : " + String.format(Locale.US, "%.1fs", fadeInSec));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        toolsCard.addView(fadeInSeek, new LinearLayout.LayoutParams(-1, dp(36)));
        toolsCard.addView(gap(6));

        // Fade out
        fadeOutLabel = new TextView(getContext());
        fadeOutLabel.setText("Fondu sortant (Fade Out) : " + String.format(Locale.US, "%.1fs", fadeOutSec));
        fadeOutLabel.setTextSize(12);
        fadeOutLabel.setTextColor(0xFFE2E3EA);
        toolsCard.addView(fadeOutLabel);

        SeekBar fadeOutSeek = new SeekBar(getContext());
        fadeOutSeek.setMax(50); // 0.0 à 5.0s
        fadeOutSeek.setProgress((int) (fadeOutSec * 10));
        fadeOutSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fadeOutSec = progress / 10.0f;
                fadeOutLabel.setText("Fondu sortant (Fade Out) : " + String.format(Locale.US, "%.1fs", fadeOutSec));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        toolsCard.addView(fadeOutSeek, new LinearLayout.LayoutParams(-1, dp(36)));
        toolsCard.addView(gap(6));

        // Volume / Gain boost
        gainLabel = new TextView(getContext());
        gainLabel.setText("Volume / Gain : " + Math.round(volumeGain * 100) + "%");
        gainLabel.setTextSize(12);
        gainLabel.setTextColor(0xFFE2E3EA);
        toolsCard.addView(gainLabel);

        SeekBar gainSeek = new SeekBar(getContext());
        gainSeek.setMax(150); // 50% à 200%
        gainSeek.setProgress(Math.round((volumeGain - 0.5f) * 100));
        gainSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeGain = 0.5f + (progress / 100.0f);
                gainLabel.setText("Volume / Gain : " + Math.round(volumeGain * 100) + "%");
                if (previewPlayer != null) previewPlayer.setVolume(volumeGain, volumeGain);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        toolsCard.addView(gainSeek, new LinearLayout.LayoutParams(-1, dp(36)));
        toolsCard.addView(gap(10));

        // Vitesse de lecture
        TextView speedLabel = new TextView(getContext());
        speedLabel.setText("Vitesse de lecture :");
        speedLabel.setTextSize(12);
        speedLabel.setTextColor(0xFFE2E3EA);
        toolsCard.addView(speedLabel);
        toolsCard.addView(gap(6));

        LinearLayout speedRow = new LinearLayout(getContext());
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] speedNames = {"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};

        for (int i = 0; i < speeds.length; i++) {
            final float sp = speeds[i];
            Button sb = new Button(getContext());
            sb.setText(speedNames[i]);
            sb.setTextSize(11);
            sb.setAllCaps(false);
            sb.setPadding(dp(8), dp(4), dp(8), dp(4));
            if (Math.abs(playbackSpeed - sp) < 0.05f) {
                sb.setBackgroundResource(R.drawable.bg_chip_active);
                sb.setTextColor(Color.WHITE);
            } else {
                shape(sb, 0x14FFFFFF, dp(12), 0x1FFFFFFF, true);
                sb.setTextColor(0xB3FFFFFF);
            }
            sb.setOnClickListener(v -> {
                playbackSpeed = sp;
                if (previewPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        android.media.PlaybackParams p = previewPlayer.getPlaybackParams();
                        if (p == null) p = new android.media.PlaybackParams();
                        p.setSpeed(playbackSpeed);
                        previewPlayer.setPlaybackParams(p);
                    } catch (Exception ignored) {}
                }
                Toast.makeText(getContext(), "Vitesse : " + sp + "x", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
            if (i > 0) lp.leftMargin = dp(6);
            speedRow.addView(sb, lp);
        }
        toolsCard.addView(speedRow);
        toolsCard.addView(gap(12));

        // Actions rapides : Détection silences & Normalisation & Boucle
        LinearLayout toggleRow = new LinearLayout(getContext());
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);

        // Bouton Détecter silences
        Button btnSilence = new Button(getContext());
        btnSilence.setText("Détecter silences");
        btnSilence.setTextSize(11);
        btnSilence.setAllCaps(false);
        shape(btnSilence, 0x1A22D3EE, dp(12), 0x3322D3EE, true);
        btnSilence.setTextColor(0xFF22D3EE);
        btnSilence.setOnClickListener(v -> autoTrimSilence());
        toggleRow.addView(btnSilence, new LinearLayout.LayoutParams(0, dp(38), 1));
        toggleRow.addView(gapW(8));

        // Bouton Normaliser
        Button btnNorm = new Button(getContext());
        btnNorm.setText(normalize ? "Normalisé" : "Normaliser");
        btnNorm.setTextSize(11);
        btnNorm.setAllCaps(false);
        shape(btnNorm, normalize ? 0x33A855F7 : 0x14FFFFFF, dp(12), 0x22FFFFFF, true);
        btnNorm.setTextColor(normalize ? 0xFFA855F7 : 0xFFE2E3EA);
        btnNorm.setOnClickListener(v -> {
            normalize = !normalize;
            btnNorm.setText(normalize ? "Normalisé" : "Normaliser");
            shape(btnNorm, normalize ? 0x33A855F7 : 0x14FFFFFF, dp(12), 0x22FFFFFF, true);
            btnNorm.setTextColor(normalize ? 0xFFA855F7 : 0xFFE2E3EA);
            Toast.makeText(getContext(), normalize ? "Normalisation activée" : "Normalisation désactivée", Toast.LENGTH_SHORT).show();
        });
        toggleRow.addView(btnNorm, new LinearLayout.LayoutParams(0, dp(38), 1));
        toggleRow.addView(gapW(8));

        // Bouton Boucle
        Button btnLoop = new Button(getContext());
        btnLoop.setText(loopMode ? "Boucle: Oui" : "Boucle: Non");
        btnLoop.setTextSize(11);
        btnLoop.setAllCaps(false);
        shape(btnLoop, loopMode ? 0x3310B981 : 0x14FFFFFF, dp(12), 0x22FFFFFF, true);
        btnLoop.setTextColor(loopMode ? 0xFF10B981 : 0xFFE2E3EA);
        btnLoop.setOnClickListener(v -> {
            loopMode = !loopMode;
            btnLoop.setText(loopMode ? "Boucle: Oui" : "Boucle: Non");
            shape(btnLoop, loopMode ? 0x3310B981 : 0x14FFFFFF, dp(12), 0x22FFFFFF, true);
            btnLoop.setTextColor(loopMode ? 0xFF10B981 : 0xFFE2E3EA);
        });
        toggleRow.addView(btnLoop, new LinearLayout.LayoutParams(0, dp(38), 1));

        toolsCard.addView(toggleRow);
        content.addView(toolsCard);
        content.addView(gap(16));

        // 5. Boutons du bas (Réinitialiser & Appliquer)
        LinearLayout bottomActions = new LinearLayout(getContext());
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);

        Button btnReset = new Button(getContext());
        btnReset.setText("Réinitialiser");
        btnReset.setTextSize(13);
        btnReset.setAllCaps(false);
        shape(btnReset, 0x14FFFFFF, dp(20), 0x22FFFFFF, true);
        btnReset.setTextColor(0xFFE2E3EA);
        btnReset.setOnClickListener(v -> resetAll());
        bottomActions.addView(btnReset, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomActions.addView(gapW(10));

        Button btnApply = new Button(getContext());
        btnApply.setText("Appliquer au projet");
        btnApply.setTextSize(13);
        btnApply.setAllCaps(false);
        btnApply.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnApply.setBackgroundResource(R.drawable.btn_gradient);
        btnApply.setTextColor(Color.WHITE);
        btnApply.setOnClickListener(v -> applyChanges());
        bottomActions.addView(btnApply, new LinearLayout.LayoutParams(0, dp(48), 1.5f));

        content.addView(bottomActions);
        content.addView(gap(20));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void togglePlay() {
        if (previewPlayer == null) return;
        if (isPlaying) {
            previewPlayer.pause();
            isPlaying = false;
        } else {
            if (previewPlayer.getCurrentPosition() < currentStartMs || previewPlayer.getCurrentPosition() >= currentEndMs) {
                previewPlayer.seekTo((int) currentStartMs);
            }
            previewPlayer.start();
            isPlaying = true;
        }
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (playIcon != null) {
            playIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void autoTrimSilence() {
        if (audioFile == null || !audioFile.exists()) return;
        Toast.makeText(getContext(), "Analyse réelle des silences en cours…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final AudioEditEngine.SilenceRange range = AudioEditEngine.detectSilence(audioFile);
                handler.post(() -> {
                    currentStartMs = Math.max(0L, Math.min(range.startMs, totalDurationMs));
                    currentEndMs = Math.max(currentStartMs + 500L, Math.min(range.endMs, totalDurationMs));
                    updateTimeLabels();
                    if (waveformView != null) waveformView.invalidate();
                    if (previewPlayer != null) previewPlayer.seekTo((int) currentStartMs);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(getContext(), "Détection des silences impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }


    private void resetAll() {
        currentStartMs = 0;
        currentEndMs = totalDurationMs;
        fadeInSec = 0.0f;
        fadeOutSec = 0.0f;
        volumeGain = 1.0f;
        playbackSpeed = 1.0f;
        normalize = false;
        loopMode = false;
        updateTimeLabels();
        if (fadeInLabel != null) fadeInLabel.setText("Fondu entrant (Fade In) : 0.0s");
        if (fadeOutLabel != null) fadeOutLabel.setText("Fondu sortant (Fade Out) : 0.0s");
        if (gainLabel != null) gainLabel.setText("Volume / Gain : 100%");
        if (waveformView != null) waveformView.invalidate();
        if (previewPlayer != null) {
            previewPlayer.seekTo(0);
            previewPlayer.setVolume(1.0f, 1.0f);
        }
    }

    private void applyChanges() {
        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(getContext(), "Fichier audio indisponible.", Toast.LENGTH_LONG).show();
            return;
        }
        Button applyButton = null;
        final File source = audioFile;
        new Thread(() -> {
            File processed = new File(getContext().getCacheDir(), "edited_" + System.nanoTime() + ".wav");
            try {
                AudioEditEngine.process(source, processed, currentStartMs, currentEndMs, volumeGain, playbackSpeed, fadeInSec, fadeOutSec, normalize);
                if (source.exists()) {
                    try (InputStream in = new java.io.FileInputStream(processed); OutputStream out = new java.io.FileOutputStream(source)) {
                        byte[] buffer = new byte[65536];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        out.flush();
                    }
                }
                handler.post(() -> {
                    if (listener != null) listener.onAudioEdited(currentStartMs, currentEndMs, volumeGain, playbackSpeed, fadeInSec, fadeOutSec, normalize, loopMode);
                    Toast.makeText(getContext(), "Traitement audio appliqué au fichier de travail.", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(getContext(), "Édition audio impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (processed.exists()) processed.delete();
            }
        }).start();
    }



    private void updateTimeLabels() {
        if (timeStartText != null) timeStartText.setText("Début: " + formatTime(currentStartMs));
        if (timeEndText != null) timeEndText.setText("Fin: " + formatTime(currentEndMs));
    }

    private void startProgressUpdater() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (previewPlayer != null && isPlaying) {
                    int pos = previewPlayer.getCurrentPosition();
                    if (pos >= currentEndMs) {
                        if (loopMode) {
                            previewPlayer.seekTo((int) currentStartMs);
                        } else {
                            previewPlayer.pause();
                            isPlaying = false;
                            updatePlayButton();
                        }
                    }
                    if (currentTimeText != null) {
                        currentTimeText.setText("Pos: " + formatTime(pos));
                    }
                    if (waveformView != null) waveformView.invalidate();
                }
                handler.postDelayed(this, 50);
            }
        });
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
        if (previewPlayer != null) {
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    private String formatTime(long ms) {
        long m = ms / 60000;
        long s = (ms % 60000) / 1000;
        long cs = (ms % 1000) / 10;
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs);
    }

    // ==========================================
    // Vue Waveform Interactive avec Trimming
    // ==========================================
    private class WaveformEditorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int activeDragMarker = 0; // 0 = none, 1 = start, 2 = end, 3 = playhead

        WaveformEditorView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Background track
            paint.setColor(0x33FFFFFF);
            c.drawRect(0, 0, w, h, paint);

            // Pseudo-waveform bars
            int bars = 60;
            float bw = w / (float) bars;
            for (int i = 0; i < bars; i++) {
                float progress = i / (float) bars;
                long barMs = (long) (progress * totalDurationMs);
                boolean inRange = (barMs >= currentStartMs && barMs <= currentEndMs);

                // Simulated amplitude pattern
                float amp = (float) (0.25 + 0.65 * Math.abs(Math.sin(i * 0.45) * Math.cos(i * 0.22)));
                float barH = amp * (h * 0.70f);
                float cy = h / 2f;

                paint.setColor(inRange ? 0xFF22D3EE : 0x40FFFFFF);
                c.drawRoundRect(new RectF(i * bw + dp(1), cy - barH / 2, (i + 1) * bw - dp(1), cy + barH / 2), dp(2), dp(2), paint);
            }

            // Darken inactive regions (outside trim)
            paint.setColor(0x88000000);
            float startX = (currentStartMs / (float) totalDurationMs) * w;
            float endX = (currentEndMs / (float) totalDurationMs) * w;
            c.drawRect(0, 0, startX, h, paint);
            c.drawRect(endX, 0, w, h, paint);

            // Selection boundary lines
            paint.setColor(0xFF22D3EE);
            paint.setStrokeWidth(dp(3));
            c.drawLine(startX, 0, startX, h, paint);

            paint.setColor(0xFFA855F7);
            c.drawLine(endX, 0, endX, h, paint);

            // Draw Marker Handles
            paint.setColor(0xFF22D3EE);
            c.drawRoundRect(new RectF(startX - dp(6), 0, startX + dp(6), dp(24)), dp(4), dp(4), paint);

            paint.setColor(0xFFA855F7);
            c.drawRoundRect(new RectF(endX - dp(6), h - dp(24), endX + dp(6), h), dp(4), dp(4), paint);

            // Playhead indicator
            if (previewPlayer != null) {
                float playX = (previewPlayer.getCurrentPosition() / (float) totalDurationMs) * w;
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(dp(2));
                c.drawLine(playX, 0, playX, h, paint);
                c.drawCircle(playX, h / 2f, dp(4), paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            int w = getWidth();
            if (w <= 0) return false;

            float startX = (currentStartMs / (float) totalDurationMs) * w;
            float endX = (currentEndMs / (float) totalDurationMs) * w;
            float touchRadius = dp(24);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (Math.abs(x - startX) <= touchRadius) {
                        activeDragMarker = 1;
                    } else if (Math.abs(x - endX) <= touchRadius) {
                        activeDragMarker = 2;
                    } else {
                        activeDragMarker = 3;
                        long seekMs = (long) (Math.max(0, Math.min(1, x / w)) * totalDurationMs);
                        if (previewPlayer != null) previewPlayer.seekTo((int) seekMs);
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float ratio = Math.max(0f, Math.min(1f, x / w));
                    long targetMs = (long) (ratio * totalDurationMs);

                    if (activeDragMarker == 1) {
                        currentStartMs = Math.max(0, Math.min(targetMs, currentEndMs - 500));
                        updateTimeLabels();
                    } else if (activeDragMarker == 2) {
                        currentEndMs = Math.min(totalDurationMs, Math.max(targetMs, currentStartMs + 500));
                        updateTimeLabels();
                    } else if (activeDragMarker == 3) {
                        if (previewPlayer != null) previewPlayer.seekTo((int) targetMs);
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activeDragMarker = 0;
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ==========================================
    // Helpers UI
    // ==========================================
    private LinearLayout card(int bg, int stroke, int radiusDp) {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        l.setBackground(d);
        return l;
    }

    private void shape(View v, int bg, int radiusPx, int stroke, boolean ripple) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(radiusPx);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        v.setBackground(d);
    }

    private View gap(int dpVal) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(dpVal)));
        return v;
    }

    private View gapW(int dpVal) {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dpVal), -1));
        return v;
    }

    private int dp(float v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
