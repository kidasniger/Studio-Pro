package com.kidas.studiopro;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FullScreenLyricsDialog extends Dialog {

    private final Context context;
    private final MediaPlayer player;
    private final List<MainActivity.LyricLine> lyrics;
    private final String trackTitle;
    private final String trackArtist;
    private final float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateProgressRunnable;

    private ScrollView lyricsScrollView;
    private LinearLayout lyricsContainer;
    private final List<TextView> lineViews = new ArrayList<>();
    private int currentActiveIndex = -1;

    private TextView currentTrackTimeText;
    private TextView totalTrackTimeText;
    private SeekBar progressBar;
    private ImageView playPauseIcon;
    private boolean isUserSeeking = false;

    public FullScreenLyricsDialog(@NonNull Context context, MediaPlayer player, List<MainActivity.LyricLine> lyrics, String trackTitle, String trackArtist) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.player = player;
        this.lyrics = lyrics != null ? lyrics : new ArrayList<>();
        this.trackTitle = trackTitle != null && !trackTitle.isEmpty() ? trackTitle : "Piste Audio";
        this.trackArtist = trackArtist != null && !trackArtist.isEmpty() ? trackArtist : "Artiste";
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(0xFF07090E));
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        setContentView(buildView());
        populateLyrics();
        startProgressLoop();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(context);
        
        // Gradient immersif de fond
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF181028, 0xFF0D0B18, 0xFF07090E, 0xFF05101A}
        );
        root.setBackground(bg);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(32), dp(24), dp(24));

        // En-tête : Titre & Bouton Fermer
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout trackInfo = new LinearLayout(context);
        trackInfo.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(trackTitle);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(18);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setSingleLine(true);
        trackInfo.addView(titleTv);

        TextView artistTv = new TextView(context);
        artistTv.setText(trackArtist + " · Mode Karaoké Immersif");
        artistTv.setTextColor(0xFF22D3EE);
        artistTv.setTextSize(12);
        artistTv.setPadding(0, dp(2), 0, 0);
        trackInfo.addView(artistTv);

        header.addView(trackInfo, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout closeBtn = new FrameLayout(context);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x22FFFFFF);
        closeBg.setCornerRadius(dp(20));
        closeBtn.setBackground(closeBg);
        TextView closeIcon = new TextView(context);
        closeIcon.setText("✕");
        closeIcon.setTextColor(Color.WHITE);
        closeIcon.setTextSize(16);
        closeIcon.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.addView(closeIcon, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        layout.addView(header);
        layout.addView(gap(16));

        // Conteneur de paroles avec défilement fluide
        lyricsScrollView = new ScrollView(context);
        lyricsScrollView.setVerticalScrollBarEnabled(false);
        lyricsContainer = new LinearLayout(context);
        lyricsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsContainer.setPadding(0, dp(120), 0, dp(140)); // padding haut/bas pour centrage

        lyricsScrollView.addView(lyricsContainer);
        layout.addView(lyricsScrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        layout.addView(gap(12));

        // Barre de progression & Contrôles
        LinearLayout controlsCard = new LinearLayout(context);
        controlsCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ctrlBg = new GradientDrawable();
        ctrlBg.setColor(0x1A000000);
        ctrlBg.setCornerRadius(dp(20));
        ctrlBg.setStroke(dp(1), 0x1FFFFFFF);
        controlsCard.setBackground(ctrlBg);
        controlsCard.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Slider & Timers
        progressBar = new SeekBar(context);
        if (player != null) {
            try {
                progressBar.setMax(player.getDuration());
                progressBar.setProgress(player.getCurrentPosition());
            } catch (Exception ignored) {}
        }
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    currentTrackTimeText.setText(formatDuration(p));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) {
                    try {
                        player.seekTo(sb.getProgress());
                    } catch (Exception ignored) {}
                }
                isUserSeeking = false;
            }
        });
        controlsCard.addView(progressBar);

        LinearLayout timerRow = new LinearLayout(context);
        timerRow.setOrientation(LinearLayout.HORIZONTAL);
        currentTrackTimeText = new TextView(context);
        currentTrackTimeText.setText("0:00");
        currentTrackTimeText.setTextColor(0x99FFFFFF);
        currentTrackTimeText.setTextSize(11);
        timerRow.addView(currentTrackTimeText, new LinearLayout.LayoutParams(0, -2, 1));

        totalTrackTimeText = new TextView(context);
        totalTrackTimeText.setText(player != null ? formatDuration(player.getDuration()) : "0:00");
        totalTrackTimeText.setTextColor(0x99FFFFFF);
        totalTrackTimeText.setTextSize(11);
        timerRow.addView(totalTrackTimeText);
        controlsCard.addView(timerRow);
        controlsCard.addView(gap(8));

        // Boutons de Lecture
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        // Skip -5s
        FrameLayout skipBackBtn = createCircleButton(R.drawable.ic_skip_back);
        skipBackBtn.setOnClickListener(v -> {
            if (player != null) {
                try {
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 5000));
                } catch (Exception ignored) {}
            }
        });
        btnRow.addView(skipBackBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        btnRow.addView(gapW(24));

        // Play / Pause
        FrameLayout playPauseBtn = new FrameLayout(context);
        GradientDrawable ppBg = new GradientDrawable();
        ppBg.setColor(0xFF22D3EE);
        ppBg.setCornerRadius(dp(30));
        playPauseBtn.setBackground(ppBg);

        playPauseIcon = new ImageView(context);
        boolean isPlaying = player != null && player.isPlaying();
        playPauseIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        playPauseIcon.setColorFilter(0xFF0F172A);
        playPauseBtn.addView(playPauseIcon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));

        playPauseBtn.setOnClickListener(v -> {
            if (player != null) {
                try {
                    if (player.isPlaying()) {
                        player.pause();
                        playPauseIcon.setImageResource(R.drawable.ic_play);
                    } else {
                        player.start();
                        playPauseIcon.setImageResource(R.drawable.ic_pause);
                    }
                } catch (Exception ignored) {}
            }
        });
        btnRow.addView(playPauseBtn, new LinearLayout.LayoutParams(dp(60), dp(60)));
        btnRow.addView(gapW(24));

        // Skip +5s
        FrameLayout skipFwdBtn = createCircleButton(R.drawable.ic_skip_forward);
        skipFwdBtn.setOnClickListener(v -> {
            if (player != null) {
                try {
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 5000));
                } catch (Exception ignored) {}
            }
        });
        btnRow.addView(skipFwdBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        controlsCard.addView(btnRow);
        layout.addView(controlsCard);

        root.addView(layout);
        return root;
    }

    private void populateLyrics() {
        lyricsContainer.removeAllViews();
        lineViews.clear();

        if (lyrics.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("Aucune parole synchronisée active.\nUtilisez la transcription Groq ou importez un fichier .LRC");
            emptyTv.setTextColor(0x66FFFFFF);
            emptyTv.setTextSize(16);
            emptyTv.setGravity(Gravity.CENTER);
            emptyTv.setPadding(0, dp(40), 0, 0);
            lyricsContainer.addView(emptyTv);
            return;
        }

        for (int i = 0; i < lyrics.size(); i++) {
            final int idx = i;
            final MainActivity.LyricLine line = lyrics.get(i);

            TextView lineTv = new TextView(context);
            lineTv.setText(line.text);
            lineTv.setTextSize(22);
            lineTv.setTextColor(0x40FFFFFF);
            lineTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            lineTv.setPadding(0, dp(14), 0, dp(14));
            lineTv.setLineSpacing(dp(4), 1.2f);

            // Tap pour caler la lecture directement à cette ligne
            lineTv.setOnClickListener(v -> {
                if (player != null) {
                    try {
                        player.seekTo((int) line.startMs);
                        if (!player.isPlaying()) {
                            player.start();
                            if (playPauseIcon != null) playPauseIcon.setImageResource(R.drawable.ic_pause);
                        }
                    } catch (Exception ignored) {}
                }
            });

            lineViews.add(lineTv);
            lyricsContainer.addView(lineTv);
        }
    }

    private void startProgressLoop() {
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    try {
                        int pos = player.getCurrentPosition();
                        if (!isUserSeeking && progressBar != null) {
                            progressBar.setProgress(pos);
                            currentTrackTimeText.setText(formatDuration(pos));
                        }

                        // Mise à jour de la ligne active
                        updateActiveLyric(pos);

                        if (playPauseIcon != null) {
                            playPauseIcon.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                        }
                    } catch (Exception ignored) {}
                }
                handler.postDelayed(this, 100);
            }
        };
        handler.post(updateProgressRunnable);
    }

    private void updateActiveLyric(int currentPosMs) {
        if (lyrics.isEmpty() || lineViews.isEmpty()) return;

        int activeIdx = -1;
        for (int i = 0; i < lyrics.size(); i++) {
            MainActivity.LyricLine line = lyrics.get(i);
            if (currentPosMs >= line.startMs && currentPosMs < line.endMs) {
                activeIdx = i;
                break;
            } else if (currentPosMs >= line.startMs) {
                activeIdx = i;
            }
        }

        if (activeIdx != currentActiveIndex) {
            currentActiveIndex = activeIdx;
            for (int i = 0; i < lineViews.size(); i++) {
                TextView tv = lineViews.get(i);
                if (i == currentActiveIndex) {
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(26);
                    tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    tv.setAlpha(1.0f);

                    // Auto-scroll vers la ligne active centrée
                    final int targetY = tv.getTop() - dp(160);
                    lyricsScrollView.post(() -> lyricsScrollView.smoothScrollTo(0, Math.max(0, targetY)));
                } else {
                    int dist = Math.abs(i - currentActiveIndex);
                    tv.setTextSize(22);
                    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    if (dist == 1) {
                        tv.setTextColor(0x80FFFFFF);
                        tv.setAlpha(0.65f);
                    } else {
                        tv.setTextColor(0x40FFFFFF);
                        tv.setAlpha(0.35f);
                    }
                }
            }
        }
    }

    @Override
    public void dismiss() {
        handler.removeCallbacks(updateProgressRunnable);
        super.dismiss();
    }

    private FrameLayout createCircleButton(int iconRes) {
        FrameLayout f = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(22));
        f.setBackground(bg);

        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE);
        f.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        return f;
    }

    private String formatDuration(int ms) {
        int sec = ms / 1000;
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    private View gap(int h) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(h)));
        return v;
    }

    private View gapW(int w) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), -1));
        return v;
    }
}
