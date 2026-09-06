package com.kidas.studiopro;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Size;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lecteur de Musique Complet et Immersif Studio Pro :
 * - Disque vinyle animé avec rotation temps-réel et pochette d'album haute résolution
 * - Barre de progression tactile avec scrubbing fluide et affichage des durées
 * - Modes Aléatoire (Shuffle), Répétition (Off / Tout / 1) et saut rapide +/- 10s
 * - Sélecteur de vitesse de lecture dynamique (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
 * - Gestionnaire de file d'attente / playlist avec sélection, suppression et ajout direct
 * - Accès direct aux paroles synchronisées et aux effets audio / égaliseur
 */
public class MusicPlayerDialog extends Dialog implements MusicPlayerManager.PlaybackStateListener {

    private final Context context;
    private final MusicPlayerManager playerManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ObjectAnimator vinylAnimator;
    private FrameLayout vinylContainer;
    private ImageView vinylCoverImage;

    private TextView trackTitleText;
    private TextView trackArtistText;
    private TextView trackAlbumText;
    private TextView timeCurrentText;
    private TextView timeDurationText;
    private TextView formatBadgeText;
    private SeekBar playSeekBar;

    private ImageView btnShuffle;
    private ImageView btnRepeat;
    private ImageView btnPrev;
    private ImageView btnNext;
    private ImageView btnRewind10;
    private ImageView btnForward10;
    private ImageView btnPlayPause;
    private FrameLayout playPauseContainer;

    private TextView btnSpeed;
    private TextView queueCountBadge;

    private LinearLayout queueContainer;
    private ListView queueListView;
    private QueueAdapter queueAdapter;
    private boolean isQueueVisible = false;
    private boolean isUserSeeking = false;

    public interface OnOpenLyricsListener {
        void onOpenLyrics();
    }

    public interface OnLyricsUpdatedListener {
        void onLyricsUpdated(List<MainActivity.LyricLine> lyrics);
    }

    private OnOpenLyricsListener lyricsListener;
    private OnLyricsUpdatedListener lyricsUpdatedListener;

    // Gestion des Paroles Synchronisées (Karaoké dans le lecteur)
    private final List<MainActivity.LyricLine> lyricsList = new ArrayList<>();
    private final List<TextView> lyricLineViews = new ArrayList<>();
    private int activeLyricIndex = -1;
    private boolean isShowingLyrics = false;
    private int lyricsSyncOffsetMs = 250; // Anticipation intelligente (250ms) pour aligner exactement la voix et l'affichage

    private FrameLayout visualContainer;
    private View vinylArtworkView;
    private View lyricsView;
    private TextView tabVinyl;
    private TextView tabLyrics;
    private TextView miniLyricsSubtitle;
    private ScrollView lyricsScrollView;
    private LinearLayout lyricsLinesContainer;
    private TextView lyricsHeaderStatus;
    private View lyricsEmptyState;
    private TextView btnFetchLrc;
    private TextView btnOpenFullScreenLyrics;
    private TextView lyricsSyncStatusText;

    private View upNextCardView;
    private TextView upNextTitleText;
    private TextView upNextArtistText;

    private LinearLayout permissionBannerContainer;
    private TextView permBannerTitle;
    private TextView permBannerSubtitle;
    private Button btnGrantPermission;
    private Button btnPickFileManually;
    private Button btnLoadDemoSong;

    public MusicPlayerDialog(@NonNull Context context) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Exception ignored) {}
        this.context = context;
        this.playerManager = MusicPlayerManager.getInstance(context);
        try {
            SharedPreferences sp = context.getSharedPreferences("music_player_pref", Context.MODE_PRIVATE);
            this.lyricsSyncOffsetMs = sp.getInt("lyrics_sync_offset_ms", 450);
        } catch (Exception ignored) {
            this.lyricsSyncOffsetMs = 450;
        }
    }

    private void saveSyncOffset() {
        try {
            SharedPreferences sp = context.getSharedPreferences("music_player_pref", Context.MODE_PRIVATE);
            sp.edit().putInt("lyrics_sync_offset_ms", lyricsSyncOffsetMs).apply();
        } catch (Exception ignored) {}
    }

    public void setOnOpenLyricsListener(OnOpenLyricsListener listener) {
        this.lyricsListener = listener;
    }

    public void setOnLyricsUpdatedListener(OnLyricsUpdatedListener listener) {
        this.lyricsUpdatedListener = listener;
    }

    public void setLyricsList(List<MainActivity.LyricLine> initialLyrics) {
        lyricsList.clear();
        if (initialLyrics != null) {
            lyricsList.addAll(initialLyrics);
        }
        if (lyricsLinesContainer != null) {
            populateLyricsView();
        }
    }

    public boolean hasStoragePermission() {
        if (context instanceof MainActivity) {
            return ((MainActivity) context).hasStorageAudioPermission();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestStoragePermission() {
        if (context instanceof MainActivity) {
            ((MainActivity) context).requestStorageAudioPermission(() -> {
                playerManager.scanAndRefreshDeviceTracks(context, false, tracks -> {
                    onPermissionRefreshed();
                });
            });
        }
    }

    public void onPermissionRefreshed() {
        handler.post(() -> {
            updatePermissionBannerState();
            updateAllUi();
        });
    }

    private View createPermissionBanner() {
        permissionBannerContainer = new LinearLayout(context);
        permissionBannerContainer.setOrientation(LinearLayout.VERTICAL);
        permissionBannerContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        permissionBannerContainer.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(0x221E1B4B);
        bannerBg.setCornerRadius(dp(14));
        bannerBg.setStroke(dp(1), 0x558BE9FD);
        permissionBannerContainer.setBackground(bannerBg);

        LinearLayout.LayoutParams pbcLp = new LinearLayout.LayoutParams(-1, -2);
        pbcLp.bottomMargin = dp(12);
        permissionBannerContainer.setLayoutParams(pbcLp);

        // Header du banner (Icône + Titre)
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.ic_music_note);
        iconView.setColorFilter(0xFF8BE9FD);
        titleRow.addView(iconView, new LinearLayout.LayoutParams(dp(22), dp(22)));

        titleRow.addView(gap(8));

        permBannerTitle = new TextView(context);
        permBannerTitle.setText("Autorisation au stockage requise");
        permBannerTitle.setTextColor(0xFF8BE9FD);
        permBannerTitle.setTextSize(13);
        permBannerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(permBannerTitle, new LinearLayout.LayoutParams(0, -2, 1));

        permissionBannerContainer.addView(titleRow);

        // Sous-titre explicatif
        permBannerSubtitle = new TextView(context);
        permBannerSubtitle.setText("Autorisez l'accès pour détecter, afficher et écouter les musiques stockées sur votre appareil.");
        permBannerSubtitle.setTextColor(0xCCFFFFFF);
        permBannerSubtitle.setTextSize(11);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(10);
        permissionBannerContainer.addView(permBannerSubtitle, subLp);

        // Rangée de boutons d'action
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        btnGrantPermission = new Button(context);
        btnGrantPermission.setText("Autoriser l'accès");
        btnGrantPermission.setTextSize(11);
        btnGrantPermission.setTextColor(Color.WHITE);
        btnGrantPermission.setTypeface(Typeface.DEFAULT_BOLD);
        btnGrantPermission.setAllCaps(false);
        GradientDrawable gpbBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF8B5CF6, 0xFF06B6D4}
        );
        gpbBg.setCornerRadius(dp(10));
        btnGrantPermission.setBackground(gpbBg);
        btnGrantPermission.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnGrantPermission.setOnClickListener(v -> requestStoragePermission());
        LinearLayout.LayoutParams gpbLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        gpbLp.rightMargin = dp(6);
        btnRow.addView(btnGrantPermission, gpbLp);

        btnPickFileManually = new Button(context);
        btnPickFileManually.setText("Ouvrir fichier");
        btnPickFileManually.setTextSize(11);
        btnPickFileManually.setTextColor(0xFF8BE9FD);
        btnPickFileManually.setTypeface(Typeface.DEFAULT_BOLD);
        btnPickFileManually.setAllCaps(false);
        GradientDrawable pfmBg = new GradientDrawable();
        pfmBg.setColor(0x228BE9FD);
        pfmBg.setCornerRadius(dp(10));
        pfmBg.setStroke(dp(1), 0x448BE9FD);
        btnPickFileManually.setBackground(pfmBg);
        btnPickFileManually.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnPickFileManually.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).launchAudioSystemPicker();
                dismiss();
            }
        });
        LinearLayout.LayoutParams pfmLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        pfmLp.rightMargin = dp(6);
        btnRow.addView(btnPickFileManually, pfmLp);

        btnLoadDemoSong = new Button(context);
        btnLoadDemoSong.setText("Démo");
        btnLoadDemoSong.setTextSize(11);
        btnLoadDemoSong.setTextColor(0xFFF1FA8C);
        btnLoadDemoSong.setTypeface(Typeface.DEFAULT_BOLD);
        btnLoadDemoSong.setAllCaps(false);
        GradientDrawable ldsBg = new GradientDrawable();
        ldsBg.setColor(0x22F1FA8C);
        ldsBg.setCornerRadius(dp(10));
        ldsBg.setStroke(dp(1), 0x44F1FA8C);
        btnLoadDemoSong.setBackground(ldsBg);
        btnLoadDemoSong.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnLoadDemoSong.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).createSampleDemoAudio();
                dismiss();
            }
        });
        btnRow.addView(btnLoadDemoSong, new LinearLayout.LayoutParams(dp(75), dp(38)));

        permissionBannerContainer.addView(btnRow);

        updatePermissionBannerState();
        return permissionBannerContainer;
    }

    private void updatePermissionBannerState() {
        if (permissionBannerContainer == null) return;
        boolean hasPerm = hasStoragePermission();
        AudioBrowserDialog.AudioTrackItem cur = playerManager.getCurrentTrack();

        if (!hasPerm) {
            permissionBannerContainer.setVisibility(View.VISIBLE);
            permBannerTitle.setText("Autorisation au stockage requise");
            permBannerSubtitle.setText("Autorisez l'accès pour détecter, afficher et écouter les musiques de votre appareil.");
            btnGrantPermission.setVisibility(View.VISIBLE);
            btnGrantPermission.setText("Autoriser l'accès");
            btnGrantPermission.setOnClickListener(v -> requestStoragePermission());
            btnPickFileManually.setText("Ouvrir fichier");
            btnPickFileManually.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).launchAudioSystemPicker();
                    dismiss();
                }
            });
            btnLoadDemoSong.setVisibility(View.VISIBLE);
            btnLoadDemoSong.setText("Démo");
            btnLoadDemoSong.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).createSampleDemoAudio();
                    dismiss();
                }
            });
        } else if (cur == null || (playerManager.getQueue().isEmpty() && cur.id == 0)) {
            permissionBannerContainer.setVisibility(View.VISIBLE);
            permBannerTitle.setText("Aucune musique dans la file");
            permBannerSubtitle.setText("Scannez votre appareil pour ajouter toutes vos musiques ou explorez votre bibliothèque audio.");
            btnGrantPermission.setVisibility(View.VISIBLE);
            btnGrantPermission.setText("Scanner les musiques");
            btnGrantPermission.setOnClickListener(v -> {
                Toast.makeText(context, "Recherche de tous les fichiers audio...", Toast.LENGTH_SHORT).show();
                playerManager.scanAndRefreshDeviceTracks(context, false, tracks -> {
                    int count = (tracks != null) ? tracks.size() : 0;
                    Toast.makeText(context, count + " musiques trouvées", Toast.LENGTH_SHORT).show();
                    updatePermissionBannerState();
                    updateAllUi();
                });
            });
            btnPickFileManually.setText("Bibliothèque");
            btnPickFileManually.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).openAudioBrowser();
                    dismiss();
                }
            });
            btnLoadDemoSong.setVisibility(View.VISIBLE);
            btnLoadDemoSong.setText("Fichier");
            btnLoadDemoSong.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).launchAudioSystemPicker();
                    dismiss();
                }
            });
        } else {
            permissionBannerContainer.setVisibility(View.GONE);
        }
    }

    private int getScreenWidthDp() {
        Configuration config = context.getResources().getConfiguration();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return config.screenWidthDp > 0 ? config.screenWidthDp : (int) (dm.widthPixels / dm.density);
    }

    private int getScreenHeightDp() {
        Configuration config = context.getResources().getConfiguration();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return config.screenHeightDp > 0 ? config.screenHeightDp : (int) (dm.heightPixels / dm.density);
    }

    private boolean isCompactScreen() {
        return getScreenWidthDp() < 360 || getScreenHeightDp() < 700;
    }

    private int getResponsiveVinylSizeDp() {
        int w = getScreenWidthDp();
        int h = getScreenHeightDp();
        boolean isLandscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            return Math.min(220, (int) (h * 0.55f));
        }
        // Affichage ample, net et spacieux sur smartphone vertical
        int base = Math.min(w, h);
        int computed = (int) (base * 0.74f);
        return Math.max(250, Math.min(340, computed));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Exception ignored) {}
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFF090A10));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        setContentView(buildView());
        initVinylAnimation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(0xFF090A10));
            WindowCompat.setDecorFitsSystemWindows(window, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        playerManager.addListener(this);
        updatePermissionBannerState();
        updateAllUi();
        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else if (playerManager.getQueue().isEmpty()) {
            playerManager.scanAndRefreshDeviceTracks(context, false, tracks -> {
                updatePermissionBannerState();
                updateAllUi();
            });
        }
    }

    @Override
    protected void onStop() {
        playerManager.removeListener(this);
        if (vinylAnimator != null && vinylAnimator.isRunning()) {
            vinylAnimator.pause();
        }
        super.onStop();
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundResource(R.drawable.bg_gradient);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        final int initialHPad = dp(isCompactScreen() ? 10 : 14);
        shell.setPadding(initialHPad, dp(8), initialHPad, dp(12));
        scrollView.addView(shell, new ScrollView.LayoutParams(-1, -2));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            int topPad = Math.max(dp(8), insets.top);
            int bottomPad = Math.max(dp(12), insets.bottom);
            int leftPad = Math.max(dp(8), insets.left);
            int rightPad = Math.max(dp(8), insets.right);

            shell.setPadding(leftPad, topPad, rightPad, bottomPad);
            if (queueContainer != null) {
                queueContainer.setPadding(leftPad, topPad, rightPad, bottomPad);
            }
            return windowInsets;
        });

        // 1. Barre supérieure (Header)
        shell.addView(createHeader());

        // 2. Contenu principal (Vinyle + Paroles + Métadonnées + Contrôles)
        LinearLayout mainContent = new LinearLayout(context);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(-1, -2);
        shell.addView(mainContent, mcLp);

        // Bannière d'autorisation et d'accès au stockage
        mainContent.addView(createPermissionBanner());

        // Sélecteur Mode Disque Vinyle / Mode Paroles Synchronisées
        mainContent.addView(createVisualModeToggle());

        // Conteneur visuel central (Disque vinyle & Affichage Karaoké Paroles)
        visualContainer = new FrameLayout(context);
        visualContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        vinylArtworkView = createVinylArtworkView();
        lyricsView = createLyricsView();
        lyricsView.setVisibility(View.GONE);

        visualContainer.addView(vinylArtworkView);
        visualContainer.addView(lyricsView);
        mainContent.addView(visualContainer);

        // Informations du morceau
        mainContent.addView(createTrackInfoView());

        // Barre de progression
        mainContent.addView(createProgressView());

        // Contrôles de lecture
        mainContent.addView(createPlaybackControlsView());

        // Barre d'outils secondaire (Vitesse, Paroles, Effets, File)
        mainContent.addView(createSecondaryToolbarView());

        // Carte Prochain Morceau / File d'attente (remplit harmonieusement le bas de l'écran)
        upNextCardView = createUpNextCard();
        mainContent.addView(upNextCardView);

        // 3. Panneau de la File d'Attente (Overlay Queue)
        queueContainer = createQueueView();
        queueContainer.setVisibility(View.GONE);
        root.addView(queueContainer, new FrameLayout.LayoutParams(-1, -1));

        return root;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        boolean isNarrow = isCompactScreen();
        header.setPadding(0, dp(2), 0, dp(isNarrow ? 6 : 10));

        int iconBtnSize = dp(isNarrow ? 36 : 40);

        // Bouton Réduire / Fermer avec icône chevron bas
        FrameLayout btnClose = createCircleButton(R.drawable.ic_chevron_down, 0x22FFFFFF, v -> dismiss());
        header.addView(btnClose, new LinearLayout.LayoutParams(iconBtnSize, iconBtnSize));

        // Titre
        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, -2, 1);
        tcLp.leftMargin = dp(6);
        tcLp.rightMargin = dp(6);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER);

        // Logo Officiel Studio Pro affiché dans la barre en haut
        FrameLayout logoCard = new FrameLayout(context);
        GradientDrawable lcBg = new GradientDrawable();
        lcBg.setShape(GradientDrawable.RECTANGLE);
        lcBg.setCornerRadius(dp(7));
        lcBg.setColor(0xFF121216);
        lcBg.setStroke(dp(1), 0x3322D3EE);
        logoCard.setBackground(lcBg);
        ImageView logoIv = new ImageView(context);
        logoIv.setImageResource(R.drawable.ic_launcher_foreground);
        logoCard.addView(logoIv, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));

        LinearLayout.LayoutParams lcLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        lcLp.rightMargin = dp(6);
        titleRow.addView(logoCard, lcLp);

        TextView title = new TextView(context);
        title.setText("LECTEUR STUDIO PRO");
        title.setTextColor(0xFF8BE9FD);
        title.setTextSize(isNarrow ? 12 : 13);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.08f);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title);

        TextView subTitle = new TextView(context);
        subTitle.setText("Haute Fidélité · Audio HD");
        subTitle.setTextColor(0x99FFFFFF);
        subTitle.setTextSize(isNarrow ? 10 : 11);
        subTitle.setGravity(Gravity.CENTER);

        titleCol.addView(titleRow);
        titleCol.addView(subTitle);
        header.addView(titleCol, tcLp);

        // Bouton File d'attente avec badge
        FrameLayout btnQueueWrapper = new FrameLayout(context);
        FrameLayout btnQueue = createCircleButton(R.drawable.ic_queue_music, 0x22FFFFFF, v -> toggleQueueVisibility());
        btnQueueWrapper.addView(btnQueue, new FrameLayout.LayoutParams(iconBtnSize, iconBtnSize));

        queueCountBadge = new TextView(context);
        queueCountBadge.setTextColor(0xFFFFFFFF);
        queueCountBadge.setTextSize(9);
        queueCountBadge.setTypeface(Typeface.DEFAULT_BOLD);
        queueCountBadge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFFFF5555);
        queueCountBadge.setBackground(badgeBg);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END);
        queueCountBadge.setLayoutParams(bp);
        btnQueueWrapper.addView(queueCountBadge);

        header.addView(btnQueueWrapper, new LinearLayout.LayoutParams(iconBtnSize, iconBtnSize));

        return header;
    }

    private View createVinylArtworkView() {
        FrameLayout container = new FrameLayout(context);
        boolean isCompact = isCompactScreen();
        container.setPadding(0, dp(isCompact ? 4 : 8), 0, dp(isCompact ? 4 : 8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.gravity = Gravity.CENTER;
        container.setLayoutParams(clp);

        int size = dp(getResponsiveVinylSizeDp());

        vinylContainer = new FrameLayout(context);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        vinylContainer.setLayoutParams(vlp);

        // Disque Vinyle (Arrière-plan texturé)
        ImageView vinylBase = new ImageView(context);
        vinylBase.setImageBitmap(createVinylDiscBitmap(size));
        vinylContainer.addView(vinylBase, new FrameLayout.LayoutParams(-1, -1));

        // Pochette d'album centrale
        vinylCoverImage = new ImageView(context);
        vinylCoverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int coverSize = (int) (size * 0.58f);
        FrameLayout.LayoutParams covLp = new FrameLayout.LayoutParams(coverSize, coverSize, Gravity.CENTER);
        vinylCoverImage.setLayoutParams(covLp);

        // Masque circulaire pour la pochette
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFF1E1B4B);
        vinylCoverImage.setBackground(mask);
        vinylCoverImage.setClipToOutline(true);

        vinylContainer.addView(vinylCoverImage);

        // Centre du vinyle (trou métallique)
        View centerHole = new View(context);
        GradientDrawable holeBg = new GradientDrawable();
        holeBg.setShape(GradientDrawable.OVAL);
        holeBg.setColor(0xFF090A10);
        holeBg.setStroke(dp(2.5f), 0xFF8BE9FD);
        centerHole.setBackground(holeBg);
        int holeSize = Math.max(dp(16), (int) (size * 0.095f));
        vinylContainer.addView(centerHole, new FrameLayout.LayoutParams(holeSize, holeSize, Gravity.CENTER));

        // Clic sur le vinyle pour basculer vers les paroles
        vinylContainer.setOnClickListener(v -> setVisualMode(true));
        vinylContainer.setContentDescription("Disque Vinyle interactif. Touchez pour afficher les paroles synchronisées.");

        container.addView(vinylContainer);
        return container;
    }

    private View createVisualModeToggle() {
        LinearLayout toggleBar = new LinearLayout(context);
        toggleBar.setOrientation(LinearLayout.HORIZONTAL);
        toggleBar.setGravity(Gravity.CENTER);
        toggleBar.setPadding(0, dp(2), 0, dp(isCompactScreen() ? 4 : 6));

        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0x33121526);
        pillBg.setCornerRadius(dp(20));
        pillBg.setStroke(dp(1), 0x228BE9FD);
        pill.setBackground(pillBg);

        int padH = dp(isCompactScreen() ? 10 : 14);
        int padV = dp(isCompactScreen() ? 4 : 6);
        float tabSize = isCompactScreen() ? 11 : 12;

        tabVinyl = new TextView(context);
        tabVinyl.setText("Disque");
        tabVinyl.setTextSize(tabSize);
        tabVinyl.setTypeface(Typeface.DEFAULT_BOLD);
        tabVinyl.setPadding(padH, padV, padH, padV);
        tabVinyl.setOnClickListener(v -> setVisualMode(false));

        tabLyrics = new TextView(context);
        tabLyrics.setText("Paroles");
        tabLyrics.setTextSize(tabSize);
        tabLyrics.setTypeface(Typeface.DEFAULT_BOLD);
        tabLyrics.setPadding(padH, padV, padH, padV);
        tabLyrics.setOnClickListener(v -> setVisualMode(true));

        pill.addView(tabVinyl);
        pill.addView(tabLyrics);
        toggleBar.addView(pill);

        updateToggleTabStyles();
        return toggleBar;
    }

    private void updateToggleTabStyles() {
        if (tabVinyl == null || tabLyrics == null) return;
        int activeColor = 0xFF22D3EE;
        if (isShowingLyrics) {
            tabVinyl.setTextColor(0x88FFFFFF);
            tabVinyl.setBackground(null);

            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(activeColor);
            activeBg.setCornerRadius(dp(16));
            tabLyrics.setTextColor(0xFF090A10);
            tabLyrics.setBackground(activeBg);
        } else {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setColor(activeColor);
            activeBg.setCornerRadius(dp(16));
            tabVinyl.setTextColor(0xFF090A10);
            tabVinyl.setBackground(activeBg);

            tabLyrics.setTextColor(0x88FFFFFF);
            tabLyrics.setBackground(null);
        }
    }

    private void setVisualMode(boolean showLyrics) {
        this.isShowingLyrics = showLyrics;
        if (vinylArtworkView != null) {
            vinylArtworkView.setVisibility(showLyrics ? View.GONE : View.VISIBLE);
        }
        if (lyricsView != null) {
            lyricsView.setVisibility(showLyrics ? View.VISIBLE : View.GONE);
            if (showLyrics) {
                updateActiveLyric(playerManager.getCurrentPosition(), true);
            }
        }
        updateToggleTabStyles();
    }

    private View createLyricsView() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        boolean isCompact = isCompactScreen();
        int size = dp(Math.max(340, Math.min(480, (int) (getScreenHeightDp() * 0.48f))));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        clp.setMargins(dp(12), dp(2), dp(12), dp(4));
        card.setLayoutParams(clp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x280D1120);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x338BE9FD);
        card.setBackground(bg);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));

        // En-tête de la carte Paroles (Badge statut + Boutons d'action)
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), 0, dp(4), dp(4));

        lyricsHeaderStatus = new TextView(context);
        lyricsHeaderStatus.setText("PAROLES SYNCHRONISÉES");
        lyricsHeaderStatus.setTextColor(0xFF8BE9FD);
        lyricsHeaderStatus.setTextSize(isCompact ? 10 : 11);
        lyricsHeaderStatus.setTypeface(Typeface.DEFAULT_BOLD);
        lyricsHeaderStatus.setLetterSpacing(0.06f);
        topBar.addView(lyricsHeaderStatus, new LinearLayout.LayoutParams(0, -2, 1));

        // Bouton Recherche LRCLIB
        btnFetchLrc = new TextView(context);
        btnFetchLrc.setText("Chercher");
        btnFetchLrc.setTextColor(0xFF50FA7B);
        btnFetchLrc.setTextSize(isCompact ? 10 : 11);
        btnFetchLrc.setTypeface(Typeface.DEFAULT_BOLD);
        btnFetchLrc.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable fBg = new GradientDrawable();
        fBg.setColor(0x2250FA7B);
        fBg.setCornerRadius(dp(10));
        btnFetchLrc.setBackground(fBg);
        btnFetchLrc.setOnClickListener(v -> {
            AudioBrowserDialog.AudioTrackItem cur = playerManager.getCurrentTrack();
            if (cur != null) {
                fetchLyricsFromLrcLib(cur, true);
            } else {
                Toast.makeText(context, "Aucune piste sélectionnée", Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(btnFetchLrc);

        // Bouton Plein Écran
        btnOpenFullScreenLyrics = new TextView(context);
        btnOpenFullScreenLyrics.setText("Plein écran");
        btnOpenFullScreenLyrics.setTextColor(0xFFFF79C6);
        btnOpenFullScreenLyrics.setTextSize(isCompact ? 10 : 11);
        btnOpenFullScreenLyrics.setTypeface(Typeface.DEFAULT_BOLD);
        btnOpenFullScreenLyrics.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable fsBg = new GradientDrawable();
        fsBg.setColor(0x22FF79C6);
        fsBg.setCornerRadius(dp(10));
        btnOpenFullScreenLyrics.setBackground(fsBg);
        LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(-2, -2);
        fsLp.leftMargin = dp(6);
        btnOpenFullScreenLyrics.setLayoutParams(fsLp);
        btnOpenFullScreenLyrics.setOnClickListener(v -> {
            if (lyricsListener != null) {
                lyricsListener.onOpenLyrics();
            }
        });
        topBar.addView(btnOpenFullScreenLyrics);

        card.addView(topBar);

        // FrameLayout pour la liste déroulante ou l'état vide
        FrameLayout contentFrame = new FrameLayout(context);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));

        // 1. Liste de défilement des paroles
        lyricsScrollView = new ScrollView(context);
        lyricsScrollView.setVerticalScrollBarEnabled(false);
        lyricsScrollView.setFillViewport(true);

        lyricsLinesContainer = new LinearLayout(context);
        lyricsLinesContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsLinesContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricsScrollView.addView(lyricsLinesContainer, new ScrollView.LayoutParams(-1, -2));
        contentFrame.addView(lyricsScrollView, new FrameLayout.LayoutParams(-1, -1));

        // 2. État vide si aucune parole
        lyricsEmptyState = createLyricsEmptyState();
        contentFrame.addView(lyricsEmptyState, new FrameLayout.LayoutParams(-1, -1));

        card.addView(contentFrame);

        // Barre d'ajustement fin de la synchronisation (Permet d'avancer ou reculer les paroles)
        LinearLayout syncBar = new LinearLayout(context);
        syncBar.setOrientation(LinearLayout.HORIZONTAL);
        syncBar.setGravity(Gravity.CENTER_VERTICAL);
        syncBar.setPadding(dp(4), dp(4), dp(4), dp(2));

        TextView btnSyncMinus500 = new TextView(context);
        btnSyncMinus500.setText("-0.5s");
        btnSyncMinus500.setTextColor(0xFF8BE9FD);
        btnSyncMinus500.setTextSize(10);
        btnSyncMinus500.setTypeface(Typeface.DEFAULT_BOLD);
        btnSyncMinus500.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable sm500Bg = new GradientDrawable();
        sm500Bg.setColor(0x188BE9FD);
        sm500Bg.setCornerRadius(dp(8));
        btnSyncMinus500.setBackground(sm500Bg);
        btnSyncMinus500.setOnClickListener(v -> {
            lyricsSyncOffsetMs -= 500;
            saveSyncOffset();
            updateSyncStatusText();
            updateActiveLyric(playerManager.getCurrentPosition(), true);
        });
        syncBar.addView(btnSyncMinus500);

        TextView btnSyncMinus200 = new TextView(context);
        btnSyncMinus200.setText("-0.2s");
        btnSyncMinus200.setTextColor(0xFF8BE9FD);
        btnSyncMinus200.setTextSize(10);
        btnSyncMinus200.setTypeface(Typeface.DEFAULT_BOLD);
        btnSyncMinus200.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable sm200Bg = new GradientDrawable();
        sm200Bg.setColor(0x188BE9FD);
        sm200Bg.setCornerRadius(dp(8));
        btnSyncMinus200.setBackground(sm200Bg);
        LinearLayout.LayoutParams sm200Lp = new LinearLayout.LayoutParams(-2, -2);
        sm200Lp.leftMargin = dp(4);
        btnSyncMinus200.setLayoutParams(sm200Lp);
        btnSyncMinus200.setOnClickListener(v -> {
            lyricsSyncOffsetMs -= 200;
            saveSyncOffset();
            updateSyncStatusText();
            updateActiveLyric(playerManager.getCurrentPosition(), true);
        });
        syncBar.addView(btnSyncMinus200);

        lyricsSyncStatusText = new TextView(context);
        lyricsSyncStatusText.setTextColor(0xEEFFFFFF);
        lyricsSyncStatusText.setTextSize(10);
        lyricsSyncStatusText.setGravity(Gravity.CENTER);
        lyricsSyncStatusText.setOnClickListener(v -> {
            lyricsSyncOffsetMs = 450;
            saveSyncOffset();
            updateSyncStatusText();
            updateActiveLyric(playerManager.getCurrentPosition(), true);
            Toast.makeText(context, "Calage réinitialisé (+450ms)", Toast.LENGTH_SHORT).show();
        });
        updateSyncStatusText();
        syncBar.addView(lyricsSyncStatusText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView btnSyncPlus200 = new TextView(context);
        btnSyncPlus200.setText("+0.2s");
        btnSyncPlus200.setTextColor(0xFF8BE9FD);
        btnSyncPlus200.setTextSize(10);
        btnSyncPlus200.setTypeface(Typeface.DEFAULT_BOLD);
        btnSyncPlus200.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable sp200Bg = new GradientDrawable();
        sp200Bg.setColor(0x188BE9FD);
        sp200Bg.setCornerRadius(dp(8));
        btnSyncPlus200.setBackground(sp200Bg);
        btnSyncPlus200.setOnClickListener(v -> {
            lyricsSyncOffsetMs += 200;
            saveSyncOffset();
            updateSyncStatusText();
            updateActiveLyric(playerManager.getCurrentPosition(), true);
        });
        syncBar.addView(btnSyncPlus200);

        TextView btnSyncPlus500 = new TextView(context);
        btnSyncPlus500.setText("+0.5s");
        btnSyncPlus500.setTextColor(0xFF8BE9FD);
        btnSyncPlus500.setTextSize(10);
        btnSyncPlus500.setTypeface(Typeface.DEFAULT_BOLD);
        btnSyncPlus500.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable sp500Bg = new GradientDrawable();
        sp500Bg.setColor(0x188BE9FD);
        sp500Bg.setCornerRadius(dp(8));
        btnSyncPlus500.setBackground(sp500Bg);
        LinearLayout.LayoutParams sp500Lp = new LinearLayout.LayoutParams(-2, -2);
        sp500Lp.leftMargin = dp(4);
        btnSyncPlus500.setLayoutParams(sp500Lp);
        btnSyncPlus500.setOnClickListener(v -> {
            lyricsSyncOffsetMs += 500;
            saveSyncOffset();
            updateSyncStatusText();
            updateActiveLyric(playerManager.getCurrentPosition(), true);
        });
        syncBar.addView(btnSyncPlus500);

        card.addView(syncBar);
        return card;
    }

    private void updateSyncStatusText() {
        if (lyricsSyncStatusText != null) {
            String sign = lyricsSyncOffsetMs > 0 ? "+" : "";
            lyricsSyncStatusText.setText("Avance : " + sign + lyricsSyncOffsetMs + " ms");
        }
    }

    private View createLyricsEmptyState() {
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(10), dp(16), dp(10));

        ImageView ic = new ImageView(context);
        ic.setImageResource(R.drawable.ic_nav_studio);
        ic.setColorFilter(0x668BE9FD);
        empty.addView(ic, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView title = new TextView(context);
        title.setText("Aucune parole synchronisée");
        title.setTextColor(0xDDFFFFFF);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-2, -2);
        tLp.topMargin = dp(6);
        empty.addView(title, tLp);

        TextView sub = new TextView(context);
        sub.setText("Téléchargez les paroles officielles avec synchronisation temporelle");
        sub.setTextColor(0x88FFFFFF);
        sub.setTextSize(11);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.topMargin = dp(2);
        empty.addView(sub, sLp);

        Button btnSearch = new Button(context);
        btnSearch.setText("Rechercher sur LRCLIB");
        btnSearch.setTextColor(0xFF090A10);
        btnSearch.setTextSize(11);
        btnSearch.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(0xFF8BE9FD);
        bBg.setCornerRadius(dp(16));
        btnSearch.setBackground(bBg);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2, dp(34));
        bLp.topMargin = dp(8);
        btnSearch.setLayoutParams(bLp);
        btnSearch.setPadding(dp(16), 0, dp(16), 0);
        btnSearch.setOnClickListener(v -> {
            AudioBrowserDialog.AudioTrackItem cur = playerManager.getCurrentTrack();
            if (cur != null) {
                fetchLyricsFromLrcLib(cur, true);
            } else {
                Toast.makeText(context, "Veuillez d'abord sélectionner un morceau", Toast.LENGTH_SHORT).show();
            }
        });
        empty.addView(btnSearch);

        return empty;
    }

    private void populateLyricsView() {
        if (lyricsLinesContainer == null) return;
        lyricsLinesContainer.removeAllViews();
        lyricLineViews.clear();
        activeLyricIndex = -1;

        if (lyricsList.isEmpty()) {
            if (lyricsEmptyState != null) lyricsEmptyState.setVisibility(View.VISIBLE);
            if (lyricsScrollView != null) lyricsScrollView.setVisibility(View.GONE);
            if (tabLyrics != null) tabLyrics.setText("Paroles");
            if (lyricsHeaderStatus != null) lyricsHeaderStatus.setText("Aucune parole");
            if (miniLyricsSubtitle != null) miniLyricsSubtitle.setText("Appuyez pour rechercher les paroles");
            return;
        }

        if (lyricsEmptyState != null) lyricsEmptyState.setVisibility(View.GONE);
        if (lyricsScrollView != null) lyricsScrollView.setVisibility(View.VISIBLE);
        if (tabLyrics != null) tabLyrics.setText("Paroles (" + lyricsList.size() + ")");
        if (lyricsHeaderStatus != null) lyricsHeaderStatus.setText("LRC · " + lyricsList.size() + " lignes");

        View topPad = new View(context);
        topPad.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(40)));
        lyricsLinesContainer.addView(topPad);

        for (int i = 0; i < lyricsList.size(); i++) {
            MainActivity.LyricLine line = lyricsList.get(i);

            TextView lineTv = new TextView(context);
            lineTv.setText(line.text);
            lineTv.setTextSize(14);
            lineTv.setTextColor(0x77FFFFFF);
            lineTv.setGravity(Gravity.CENTER);
            lineTv.setPadding(dp(12), dp(6), dp(12), dp(6));
            lineTv.setTypeface(Typeface.DEFAULT);

            lineTv.setOnClickListener(v -> {
                playerManager.seekTo((int) line.startMs);
                updateActiveLyric((int) line.startMs, true);
            });

            lyricsLinesContainer.addView(lineTv);
            lyricLineViews.add(lineTv);
        }

        View bottomPad = new View(context);
        bottomPad.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(60)));
        lyricsLinesContainer.addView(bottomPad);

        updateActiveLyric(playerManager.getCurrentPosition(), false);
    }

    private void updateActiveLyric(int currentPosMs, boolean forceScroll) {
        if (lyricsList.isEmpty()) {
            if (miniLyricsSubtitle != null && (miniLyricsSubtitle.getText() == null || miniLyricsSubtitle.getText().toString().isEmpty())) {
                miniLyricsSubtitle.setText("Studio Pro Haute Fidélité");
            }
            return;
        }

        // Calage intelligent de synchronisation (compense la latence audio & aligne la voix)
        int syncPosMs = Math.max(0, currentPosMs + lyricsSyncOffsetMs);

        int targetIndex = -1;
        for (int i = 0; i < lyricsList.size(); i++) {
            MainActivity.LyricLine line = lyricsList.get(i);
            long nextStart = (i + 1 < lyricsList.size()) ? lyricsList.get(i + 1).startMs : (line.startMs + 6000L);
            if (syncPosMs >= line.startMs && syncPosMs < nextStart) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            for (int i = lyricsList.size() - 1; i >= 0; i--) {
                if (syncPosMs >= lyricsList.get(i).startMs) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetIndex >= 0 && (targetIndex != activeLyricIndex || forceScroll)) {
            if (activeLyricIndex >= 0 && activeLyricIndex < lyricLineViews.size()) {
                TextView oldTv = lyricLineViews.get(activeLyricIndex);
                oldTv.setTextColor(0x77FFFFFF);
                oldTv.setTextSize(14);
                oldTv.setTypeface(Typeface.DEFAULT);
                oldTv.setBackground(null);
            }

            activeLyricIndex = targetIndex;
            if (activeLyricIndex < lyricLineViews.size()) {
                TextView newTv = lyricLineViews.get(activeLyricIndex);
                newTv.setTextColor(0xFF8BE9FD);
                newTv.setTextSize(16);
                newTv.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable hlBg = new GradientDrawable();
                hlBg.setColor(0x228BE9FD);
                hlBg.setCornerRadius(dp(10));
                hlBg.setStroke(dp(1), 0x448BE9FD);
                newTv.setBackground(hlBg);

                if (miniLyricsSubtitle != null) {
                    miniLyricsSubtitle.setText(lyricsList.get(activeLyricIndex).text);
                }

                if (isShowingLyrics && lyricsScrollView != null) {
                    newTv.post(() -> {
                        int scrollY = newTv.getTop() - (lyricsScrollView.getHeight() / 2) + (newTv.getHeight() / 2);
                        lyricsScrollView.smoothScrollTo(0, Math.max(0, scrollY));
                    });
                }
            }
        }
    }

    private void loadLyricsForTrack(AudioBrowserDialog.AudioTrackItem track) {
        // Vider immédiatement la liste pour que les paroles du morceau précédent ne collent JAMAIS au morceau suivant
        lyricsList.clear();
        lyricLineViews.clear();
        activeLyricIndex = -1;
        if (miniLyricsSubtitle != null) {
            miniLyricsSubtitle.setText(track != null && track.title != null ? track.title : "Studio Pro");
        }
        if (lyricsHeaderStatus != null) {
            lyricsHeaderStatus.setText("PAROLES SYNCHRONISÉES");
        }
        populateLyricsView();
        notifyLyricsUpdated();

        if (track == null) return;

        // 1. Si MainActivity possède des paroles pour ce morceau (par URI ou par titre)
        if (context instanceof MainActivity) {
            MainActivity ma = (MainActivity) context;
            boolean matchUri = (ma.getCurrentAudioUri() != null && track.contentUri != null &&
                    ma.getCurrentAudioUri().equals(track.contentUri));
            boolean matchTitle = (track.title != null && !track.title.isEmpty() &&
                    ma.getCurrentTrackTitle() != null &&
                    ma.getCurrentTrackTitle().equalsIgnoreCase(track.title));
            if (matchUri || matchTitle) {
                List<MainActivity.LyricLine> maLyrics = ma.getLyricsList();
                if (maLyrics != null && !maLyrics.isEmpty()) {
                    lyricsList.clear();
                    lyricsList.addAll(maLyrics);
                    populateLyricsView();
                    notifyLyricsUpdated();
                    return;
                }
            }
        }

        // 2. Paroles intégrées dans le fichier audio (USLT / SYLT / ID3)
        // a. Via l'Uri ContentResolver (pistes importées via le sélecteur Android SAF)
        if (track.contentUri != null) {
            try {
                AudioLyricsTagger.ExtractionResult res = AudioLyricsTagger.readEmbeddedLyrics(context, track.contentUri);
                if (res != null && !res.lines.isEmpty()) {
                    lyricsList.clear();
                    lyricsList.addAll(res.lines);
                    populateLyricsView();
                    notifyLyricsUpdated();
                    return;
                }
            } catch (Exception ignored) {}
        }

        // b. Via le fichier physique direct
        if (track.dataPath != null && !track.dataPath.isEmpty()) {
            File f = new File(track.dataPath);
            if (f.exists()) {
                try {
                    AudioLyricsTagger.ExtractionResult res = AudioLyricsTagger.readEmbeddedLyrics(f);
                    if (res != null && !res.lines.isEmpty()) {
                        lyricsList.clear();
                        lyricsList.addAll(res.lines);
                        populateLyricsView();
                        notifyLyricsUpdated();
                        return;
                    }
                } catch (Exception ignored) {}

                // Fichier .lrc local associé dans le même dossier
                try {
                    String lrcPath = track.dataPath.replaceAll("\\.[a-zA-Z0-9]+$", ".lrc");
                    File lrcFile = new File(lrcPath);
                    if (lrcFile.exists()) {
                        String content = readFileToString(lrcFile);
                        List<MainActivity.LyricLine> parsed = MainActivity.LyricLine.parseLrcString(content);
                        if (!parsed.isEmpty()) {
                            lyricsList.clear();
                            lyricsList.addAll(parsed);
                            populateLyricsView();
                            notifyLyricsUpdated();
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // c. Via le cache audio de MainActivity si la piste correspond
        if (context instanceof MainActivity) {
            MainActivity ma = (MainActivity) context;
            File caf = ma.getAudioFile();
            if (caf != null && caf.exists()) {
                try {
                    AudioLyricsTagger.ExtractionResult res = AudioLyricsTagger.readEmbeddedLyrics(caf);
                    if (res != null && !res.lines.isEmpty()) {
                        lyricsList.clear();
                        lyricsList.addAll(res.lines);
                        populateLyricsView();
                        notifyLyricsUpdated();
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Recherche automatique en ligne sur LRCLIB
        fetchLyricsFromLrcLib(track, false);
    }

    private void fetchLyricsFromLrcLib(AudioBrowserDialog.AudioTrackItem track, boolean userInitiated) {
        if (track == null) return;
        final long requestedTrackId = track.id;
        if (lyricsHeaderStatus != null) {
            lyricsHeaderStatus.setText("Recherche LRCLIB…");
        }
        if (userInitiated) {
            Toast.makeText(context, "Recherche des paroles pour \"" + track.title + "\"…", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            try {
                int durationSec = (int) (track.durationMs / 1000);
                LrcLibClient.LyricsResult res = LrcLibClient.fetchLyrics(track.title, track.artist, durationSec);
                handler.post(() -> {
                    // Si l'utilisateur a changé de morceau entre temps, on ignore ce résultat obsolète !
                    AudioBrowserDialog.AudioTrackItem cur = playerManager.getCurrentTrack();
                    if (cur == null || cur.id != requestedTrackId) {
                        return;
                    }
                    if (res != null && res.hasSynced()) {
                        List<MainActivity.LyricLine> parsed = MainActivity.LyricLine.parseLrcString(res.syncedLyrics);
                        if (!parsed.isEmpty()) {
                            lyricsList.clear();
                            lyricsList.addAll(parsed);
                            populateLyricsView();
                            notifyLyricsUpdated();
                            if (lyricsHeaderStatus != null) {
                                lyricsHeaderStatus.setText("PAROLES SYNCHRONISÉES (LRCLIB)");
                            }
                            if (userInitiated) {
                                Toast.makeText(context, "Paroles synchronisées (" + parsed.size() + " lignes) prêtes !", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                    }
                    if (lyricsHeaderStatus != null) {
                        lyricsHeaderStatus.setText("Aucune parole trouvée sur LRCLIB");
                    }
                    if (userInitiated) {
                        Toast.makeText(context, "Aucune parole synchronisée trouvée sur LRCLIB.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    AudioBrowserDialog.AudioTrackItem cur = playerManager.getCurrentTrack();
                    if (cur != null && cur.id == requestedTrackId && lyricsHeaderStatus != null) {
                        lyricsHeaderStatus.setText("Erreur de recherche LRCLIB");
                    }
                });
            }
        }).start();
    }

    private void notifyLyricsUpdated() {
        if (lyricsUpdatedListener != null) {
            lyricsUpdatedListener.onLyricsUpdated(lyricsList);
        }
    }

    private String readFileToString(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private View createTrackInfoView() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean isCompact = isCompactScreen();
        col.setPadding(dp(8), dp(isCompact ? 4 : 6), dp(8), dp(isCompact ? 4 : 6));

        trackTitleText = new TextView(context);
        trackTitleText.setText("Titre de la musique");
        trackTitleText.setTextColor(0xFFFFFFFF);
        trackTitleText.setTextSize(isCompact ? 16 : 18);
        trackTitleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        trackTitleText.setGravity(Gravity.CENTER);
        trackTitleText.setSingleLine(true);
        trackTitleText.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        trackTitleText.setMarqueeRepeatLimit(-1);
        trackTitleText.setHorizontallyScrolling(true);
        trackTitleText.setFocusable(true);
        trackTitleText.setFocusableInTouchMode(true);
        trackTitleText.setSelected(true);
        trackTitleText.setPadding(dp(12), 0, dp(12), 0);

        trackArtistText = new TextView(context);
        trackArtistText.setText("Artiste");
        trackArtistText.setTextColor(0xBBBD93F9);
        trackArtistText.setTextSize(isCompact ? 13 : 14);
        trackArtistText.setGravity(Gravity.CENTER);
        trackArtistText.setPadding(0, dp(2), 0, 0);
        trackArtistText.setSingleLine(true);
        trackArtistText.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER);
        metaRow.setPadding(0, dp(4), 0, 0);

        formatBadgeText = new TextView(context);
        formatBadgeText.setText("AUDIO HD");
        formatBadgeText.setTextColor(0xFF50FA7B);
        formatBadgeText.setTextSize(10);
        formatBadgeText.setTypeface(Typeface.DEFAULT_BOLD);
        formatBadgeText.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable fbBg = new GradientDrawable();
        fbBg.setColor(0x2250FA7B);
        fbBg.setCornerRadius(dp(4));
        formatBadgeText.setBackground(fbBg);

        trackAlbumText = new TextView(context);
        trackAlbumText.setText("Album");
        trackAlbumText.setTextColor(0x88FFFFFF);
        trackAlbumText.setTextSize(11);
        trackAlbumText.setPadding(dp(8), 0, 0, 0);
        trackAlbumText.setSingleLine(true);
        trackAlbumText.setEllipsize(TextUtils.TruncateAt.END);

        metaRow.addView(formatBadgeText);
        metaRow.addView(trackAlbumText);

        col.addView(trackTitleText);
        col.addView(trackArtistText);
        col.addView(metaRow);

        // Mini sous-titre Karaoké en temps réel sous les métadonnées
        miniLyricsSubtitle = new TextView(context);
        miniLyricsSubtitle.setText("Paroles synchronisées · Appuyez pour afficher");
        miniLyricsSubtitle.setTextColor(0xFF8BE9FD);
        miniLyricsSubtitle.setTextSize(isCompact ? 11 : 12);
        miniLyricsSubtitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        miniLyricsSubtitle.setGravity(Gravity.CENTER);
        miniLyricsSubtitle.setSingleLine(true);
        miniLyricsSubtitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        miniLyricsSubtitle.setMarqueeRepeatLimit(-1);
        miniLyricsSubtitle.setHorizontallyScrolling(true);
        miniLyricsSubtitle.setSelected(true);
        miniLyricsSubtitle.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable subBg = new GradientDrawable();
        subBg.setColor(0x1A22D3EE);
        subBg.setCornerRadius(dp(12));
        subBg.setStroke(dp(1), 0x3322D3EE);
        miniLyricsSubtitle.setBackground(subBg);
        miniLyricsSubtitle.setOnClickListener(v -> setVisualMode(true));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(6);
        subLp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(miniLyricsSubtitle, subLp);

        return col;
    }

    private View createProgressView() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(6), dp(2), dp(6), dp(4));

        playSeekBar = new SeekBar(context);
        playSeekBar.setMax(1000);
        playSeekBar.setProgress(0);

        // Personnalisation SeekBar
        playSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int duration = playerManager.getDuration();
                    if (duration > 0) {
                        int pos = (int) ((progress / 1000f) * duration);
                        timeCurrentText.setText(MusicPlayerManager.formatDuration(pos));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    int targetPos = (int) ((seekBar.getProgress() / 1000f) * duration);
                    playerManager.seekTo(targetPos);
                }
            }
        });

        col.addView(playSeekBar);

        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setPadding(dp(10), dp(1), dp(10), 0);

        timeCurrentText = new TextView(context);
        timeCurrentText.setText("0:00");
        timeCurrentText.setTextColor(0xAAFFFFFF);
        timeCurrentText.setTextSize(11);

        timeDurationText = new TextView(context);
        timeDurationText.setText("0:00");
        timeDurationText.setTextColor(0xAAFFFFFF);
        timeDurationText.setTextSize(11);
        timeDurationText.setGravity(Gravity.END);

        timeRow.addView(timeCurrentText, new LinearLayout.LayoutParams(0, -2, 1));
        timeRow.addView(timeDurationText, new LinearLayout.LayoutParams(0, -2, 1));

        col.addView(timeRow);
        return col;
    }

    private View createPlaybackControlsView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int widthDp = getScreenWidthDp();
        boolean isNarrow = widthDp < 360;
        boolean isLarge = widthDp >= 412;

        int horizPad = dp(isNarrow ? 2 : (isLarge ? 10 : 4));
        row.setPadding(horizPad, dp(6), horizPad, dp(10));

        int playSize = dp(isNarrow ? 54 : (isLarge ? 64 : 58));
        int playIconSize = dp(isNarrow ? 24 : (isLarge ? 28 : 26));
        int navSize = dp(isNarrow ? 38 : (isLarge ? 44 : 40));
        int subSize = dp(isNarrow ? 30 : (isLarge ? 36 : 32));

        // 1. Bouton Aléatoire (Shuffle)
        btnShuffle = createControlIcon(R.drawable.ic_shuffle, v -> playerManager.toggleShuffle());
        row.addView(btnShuffle, new LinearLayout.LayoutParams(subSize, subSize));

        row.addView(createSpacer());

        // 2. Saut -10s
        btnRewind10 = createControlIcon(R.drawable.ic_skip_back, v -> playerManager.seekRelative(-10000));
        row.addView(btnRewind10, new LinearLayout.LayoutParams(subSize, subSize));

        row.addView(createSpacer());

        // 3. Morceau Précédent
        btnPrev = createControlIcon(R.drawable.ic_skip_back, v -> playerManager.previous());
        row.addView(btnPrev, new LinearLayout.LayoutParams(navSize, navSize));

        row.addView(createSpacer());

        // 4. Bouton Principal Play / Pause (Grand Cercle Dégradé)
        playPauseContainer = new FrameLayout(context);
        GradientDrawable ppBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899}
        );
        ppBg.setShape(GradientDrawable.OVAL);
        playPauseContainer.setBackground(ppBg);
        playPauseContainer.setElevation(dp(8));

        btnPlayPause = new ImageView(context);
        btnPlayPause.setImageResource(R.drawable.ic_play);
        btnPlayPause.setColorFilter(0xFFFFFFFF);
        FrameLayout.LayoutParams ppLp = new FrameLayout.LayoutParams(playIconSize, playIconSize, Gravity.CENTER);
        btnPlayPause.setLayoutParams(ppLp);
        playPauseContainer.addView(btnPlayPause);

        playPauseContainer.setOnClickListener(v -> playerManager.togglePlayPause());
        row.addView(playPauseContainer, new LinearLayout.LayoutParams(playSize, playSize));

        row.addView(createSpacer());

        // 5. Morceau Suivant
        btnNext = createControlIcon(R.drawable.ic_skip_forward, v -> playerManager.next());
        row.addView(btnNext, new LinearLayout.LayoutParams(navSize, navSize));

        row.addView(createSpacer());

        // 6. Saut +10s
        btnForward10 = createControlIcon(R.drawable.ic_skip_forward, v -> playerManager.seekRelative(10000));
        row.addView(btnForward10, new LinearLayout.LayoutParams(subSize, subSize));

        row.addView(createSpacer());

        // 7. Répétition (Repeat)
        btnRepeat = createControlIcon(R.drawable.ic_repeat, v -> playerManager.cycleRepeatMode());
        row.addView(btnRepeat, new LinearLayout.LayoutParams(subSize, subSize));

        return row;
    }

    private View createSpacer() {
        View spacer = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1.0f);
        spacer.setLayoutParams(lp);
        return spacer;
    }

    private View createSecondaryToolbarView() {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setFillViewport(true);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, dp(2), 0, dp(8));

        boolean isNarrow = isCompactScreen();
        int padH = dp(isNarrow ? 10 : 12);
        int padV = dp(isNarrow ? 5 : 6);
        int textSize = isNarrow ? 11 : 12;
        int chipGap = dp(isNarrow ? 6 : 10);

        // Bouton Vitesse (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
        btnSpeed = new TextView(context);
        btnSpeed.setText("1.0x");
        btnSpeed.setTextColor(0xFF8BE9FD);
        btnSpeed.setTextSize(textSize);
        btnSpeed.setTypeface(Typeface.DEFAULT_BOLD);
        btnSpeed.setPadding(padH, padV, padH, padV);
        GradientDrawable spBg = new GradientDrawable();
        spBg.setColor(0x228BE9FD);
        spBg.setCornerRadius(dp(16));
        spBg.setStroke(dp(1), 0x448BE9FD);
        btnSpeed.setBackground(spBg);
        btnSpeed.setOnClickListener(v -> cyclePlaybackSpeed());
        row.addView(btnSpeed);

        row.addView(gap(chipGap));

        // Bouton Paroles Synchronisées
        TextView btnLyrics = new TextView(context);
        btnLyrics.setText("Paroles");
        btnLyrics.setTextColor(0xFFFF79C6);
        btnLyrics.setTextSize(textSize);
        btnLyrics.setTypeface(Typeface.DEFAULT_BOLD);
        btnLyrics.setPadding(padH, padV, padH, padV);
        GradientDrawable lyBg = new GradientDrawable();
        lyBg.setColor(0x22FF79C6);
        lyBg.setCornerRadius(dp(16));
        lyBg.setStroke(dp(1), 0x44FF79C6);
        btnLyrics.setBackground(lyBg);
        btnLyrics.setOnClickListener(v -> {
            setVisualMode(!isShowingLyrics);
        });
        row.addView(btnLyrics);

        row.addView(gap(chipGap));

        // Bouton Effets Audio & Equalizer
        TextView btnFx = new TextView(context);
        btnFx.setText("Égaliseur & FX");
        btnFx.setTextColor(0xFFF1FA8C);
        btnFx.setTextSize(textSize);
        btnFx.setTypeface(Typeface.DEFAULT_BOLD);
        btnFx.setPadding(padH, padV, padH, padV);
        GradientDrawable fxBg = new GradientDrawable();
        fxBg.setColor(0x22F1FA8C);
        fxBg.setCornerRadius(dp(16));
        fxBg.setStroke(dp(1), 0x44F1FA8C);
        btnFx.setBackground(fxBg);
        btnFx.setOnClickListener(v -> {
            new AudioEffectsDialog(context, playerManager.getPlayer()).show();
        });
        row.addView(btnFx);

        row.addView(gap(chipGap));

        // Bouton Actualiser tous les fichiers du téléphone
        TextView btnRefresh = new TextView(context);
        btnRefresh.setText("Actualiser");
        btnRefresh.setTextColor(0xFF50FA7B);
        btnRefresh.setTextSize(textSize);
        btnRefresh.setTypeface(Typeface.DEFAULT_BOLD);
        btnRefresh.setPadding(padH, padV, padH, padV);
        GradientDrawable refBg = new GradientDrawable();
        refBg.setColor(0x2250FA7B);
        refBg.setCornerRadius(dp(16));
        refBg.setStroke(dp(1), 0x4450FA7B);
        btnRefresh.setBackground(refBg);
        btnRefresh.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                Toast.makeText(context, "Demande d'autorisation au stockage...", Toast.LENGTH_SHORT).show();
                requestStoragePermission();
            } else {
                Toast.makeText(context, "Recherche de tous les fichiers audio...", Toast.LENGTH_SHORT).show();
                playerManager.scanAndRefreshDeviceTracks(context, false, tracks -> {
                    int count = (tracks != null) ? tracks.size() : 0;
                    Toast.makeText(context, count + " musiques actualisées", Toast.LENGTH_SHORT).show();
                    updatePermissionBannerState();
                    updateAllUi();
                });
            }
        });
        row.addView(btnRefresh);

        hsv.addView(row);
        return hsv;
    }

    private View createUpNextCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        boolean isCompact = isCompactScreen();

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(isCompact ? 4 : 8), dp(4), dp(isCompact ? 4 : 8), dp(10));
        card.setLayoutParams(cardLp);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x20151928);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x2E8BE9FD);
        card.setBackground(bg);

        FrameLayout iconBox = new FrameLayout(context);
        int boxSize = dp(isCompact ? 34 : 38);
        GradientDrawable ibBg = new GradientDrawable();
        ibBg.setColor(0x228BE9FD);
        ibBg.setCornerRadius(dp(10));
        iconBox.setBackground(ibBg);

        ImageView iconIv = new ImageView(context);
        iconIv.setImageResource(R.drawable.ic_queue_music);
        iconIv.setColorFilter(0xFF8BE9FD);
        int iconSize = dp(isCompact ? 18 : 20);
        iconBox.addView(iconIv, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

        card.addView(iconBox, new LinearLayout.LayoutParams(boxSize, boxSize));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tcLp.leftMargin = dp(10);
        tcLp.rightMargin = dp(8);
        textCol.setLayoutParams(tcLp);

        TextView label = new TextView(context);
        label.setText("À SUIVRE");
        label.setTextColor(0xFF8BE9FD);
        label.setTextSize(isCompact ? 9 : 10);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.06f);
        textCol.addView(label);

        upNextTitleText = new TextView(context);
        upNextTitleText.setText("Aucun morceau suivant");
        upNextTitleText.setTextColor(0xFFFFFFFF);
        upNextTitleText.setTextSize(isCompact ? 12 : 13);
        upNextTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        upNextTitleText.setSingleLine(true);
        upNextTitleText.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(upNextTitleText);

        upNextArtistText = new TextView(context);
        upNextArtistText.setText("Appuyez pour ouvrir la file d'attente");
        upNextArtistText.setTextColor(0x99FFFFFF);
        upNextArtistText.setTextSize(isCompact ? 10 : 11);
        upNextArtistText.setSingleLine(true);
        upNextArtistText.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(upNextArtistText);

        card.addView(textCol);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron_down);
        chevron.setColorFilter(0x66FFFFFF);
        chevron.setRotation(270f);
        int chSize = dp(isCompact ? 18 : 20);
        card.addView(chevron, new LinearLayout.LayoutParams(chSize, chSize));

        card.setOnClickListener(v -> toggleQueueVisibility());

        updateUpNextInfo();
        return card;
    }

    private void updateUpNextInfo() {
        if (upNextTitleText == null || upNextArtistText == null) return;
        List<AudioBrowserDialog.AudioTrackItem> queue = playerManager.getQueue();
        int currentIndex = playerManager.getCurrentIndex();
        if (queue == null || queue.isEmpty()) {
            upNextTitleText.setText("File d'attente vide");
            upNextArtistText.setText("Appuyez pour ouvrir la file d'attente");
            return;
        }

        int nextIndex = -1;
        if (playerManager.isShuffleEnabled()) {
            if (queue.size() > 1) {
                nextIndex = (currentIndex + 1) % queue.size();
            }
        } else {
            if (currentIndex + 1 < queue.size()) {
                nextIndex = currentIndex + 1;
            } else if (playerManager.getRepeatMode() == MusicPlayerManager.REPEAT_ALL) {
                nextIndex = 0;
            }
        }

        if (nextIndex >= 0 && nextIndex < queue.size()) {
            AudioBrowserDialog.AudioTrackItem nextTrack = queue.get(nextIndex);
            upNextTitleText.setText(nextTrack.title != null && !nextTrack.title.isEmpty() ? nextTrack.title : "Titre inconnu");
            upNextArtistText.setText(nextTrack.artist != null && !nextTrack.artist.isEmpty() ? nextTrack.artist : "Artiste inconnu");
        } else {
            upNextTitleText.setText("Fin de la liste de lecture");
            upNextArtistText.setText("Appuyez pour voir la file d'attente");
        }
    }

    private LinearLayout createQueueView() {
        LinearLayout qShell = new LinearLayout(context);
        qShell.setOrientation(LinearLayout.VERTICAL);
        qShell.setBackgroundColor(0xF50D0E15);
        qShell.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Header de la file
        LinearLayout qHeader = new LinearLayout(context);
        qHeader.setOrientation(LinearLayout.HORIZONTAL);
        qHeader.setGravity(Gravity.CENTER_VERTICAL);
        qHeader.setPadding(0, 0, 0, dp(12));

        TextView qTitle = new TextView(context);
        qTitle.setText("FILE D'ATTENTE & PLAYLIST");
        qTitle.setTextColor(0xFFFFFFFF);
        qTitle.setTextSize(14);
        qTitle.setTypeface(Typeface.DEFAULT_BOLD);
        qHeader.addView(qTitle, new LinearLayout.LayoutParams(0, -2, 1));

        // Bouton Ajouter des musiques
        TextView btnAddMore = new TextView(context);
        btnAddMore.setText("+ Ajouter");
        btnAddMore.setTextColor(0xFF50FA7B);
        btnAddMore.setTextSize(12);
        btnAddMore.setTypeface(Typeface.DEFAULT_BOLD);
        btnAddMore.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(0x2250FA7B);
        addBg.setCornerRadius(dp(12));
        btnAddMore.setBackground(addBg);
        btnAddMore.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                requestStoragePermission();
            } else if (context instanceof MainActivity) {
                ((MainActivity) context).openAudioBrowser();
            } else {
                Activity act = context instanceof Activity ? (Activity) context : null;
                if (act != null) {
                    new AudioBrowserDialog(act, () -> {}, (uri, title, artist, durationMs) -> {
                        AudioBrowserDialog.AudioTrackItem item = new AudioBrowserDialog.AudioTrackItem(
                                System.currentTimeMillis(),
                                title,
                                artist,
                                "",
                                durationMs,
                                0,
                                System.currentTimeMillis(),
                                uri,
                                "",
                                "audio/mpeg"
                        );
                        playerManager.addToQueue(item);
                        Toast.makeText(context, "Ajouté à la file d'attente", Toast.LENGTH_SHORT).show();
                    }).show();
                }
            }
        });

        qHeader.addView(btnAddMore);

        qHeader.addView(gap(8));

        // Bouton Actualiser tous les fichiers du téléphone
        TextView btnRefreshQueue = new TextView(context);
        btnRefreshQueue.setText("Actualiser");
        btnRefreshQueue.setTextColor(0xFF8BE9FD);
        btnRefreshQueue.setTextSize(12);
        btnRefreshQueue.setTypeface(Typeface.DEFAULT_BOLD);
        btnRefreshQueue.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable rqBg = new GradientDrawable();
        rqBg.setColor(0x228BE9FD);
        rqBg.setCornerRadius(dp(12));
        btnRefreshQueue.setBackground(rqBg);
        btnRefreshQueue.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                Toast.makeText(context, "Demande d'autorisation au stockage...", Toast.LENGTH_SHORT).show();
                requestStoragePermission();
            } else {
                Toast.makeText(context, "Actualisation des musiques du téléphone...", Toast.LENGTH_SHORT).show();
                playerManager.scanAndRefreshDeviceTracks(context, false, tracks -> {
                    int count = (tracks != null) ? tracks.size() : 0;
                    Toast.makeText(context, count + " musiques actualisées", Toast.LENGTH_SHORT).show();
                    updatePermissionBannerState();
                    updateAllUi();
                });
            }
        });
        qHeader.addView(btnRefreshQueue);

        qHeader.addView(gap(8));

        // Bouton Vider la file
        TextView btnClear = new TextView(context);
        btnClear.setText("Vider");
        btnClear.setTextColor(0xFFFF5555);
        btnClear.setTextSize(12);
        btnClear.setPadding(dp(8), dp(4), dp(8), dp(4));
        btnClear.setOnClickListener(v -> playerManager.clearQueue());
        qHeader.addView(btnClear);

        qHeader.addView(gap(8));

        // Bouton Fermer le volet file
        FrameLayout btnCloseQueue = createCircleButton(R.drawable.ic_check, 0x33FFFFFF, v -> toggleQueueVisibility());
        qHeader.addView(btnCloseQueue, new LinearLayout.LayoutParams(dp(32), dp(32)));

        qShell.addView(qHeader);

        // Liste des morceaux
        queueListView = new ListView(context);
        queueListView.setDivider(new ColorDrawable(0x15FFFFFF));
        queueListView.setDividerHeight(dp(1));
        queueAdapter = new QueueAdapter();
        queueListView.setAdapter(queueAdapter);
        queueListView.setOnItemClickListener((parent, view, position, id) -> {
            playerManager.playTrackAtIndex(position, true);
            toggleQueueVisibility();
        });

        qShell.addView(queueListView, new LinearLayout.LayoutParams(-1, 0, 1));

        return qShell;
    }

    private void toggleQueueVisibility() {
        isQueueVisible = !isQueueVisible;
        queueContainer.setVisibility(isQueueVisible ? View.VISIBLE : View.GONE);
        if (isQueueVisible && queueAdapter != null) {
            queueAdapter.notifyDataSetChanged();
            if (playerManager.getCurrentIndex() >= 0) {
                queueListView.setSelection(playerManager.getCurrentIndex());
            }
        }
    }

    private void initVinylAnimation() {
        if (vinylContainer != null) {
            vinylAnimator = ObjectAnimator.ofFloat(vinylContainer, "rotation", 0f, 360f);
            vinylAnimator.setDuration(12000);
            vinylAnimator.setInterpolator(new LinearInterpolator());
            vinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
    }

    private void updateAllUi() {
        updatePermissionBannerState();
        AudioBrowserDialog.AudioTrackItem track = playerManager.getCurrentTrack();
        onTrackChanged(track);
        onPlaybackStateChanged(playerManager.isPlaying());
        onPlaybackModesChanged(playerManager.getRepeatMode(), playerManager.isShuffleEnabled());
        onSpeedChanged(playerManager.getPlaybackSpeed(), playerManager.getPlaybackPitch());
        onQueueChanged(playerManager.getQueue(), playerManager.getCurrentIndex());
        if (!isUserSeeking) {
            onProgressUpdated(playerManager.getCurrentPosition(), playerManager.getDuration());
        }
    }

    @Override
    public void onTrackChanged(AudioBrowserDialog.AudioTrackItem track) {
        handler.post(() -> {
            if (track != null) {
                trackTitleText.setText(track.title);
                trackArtistText.setText(track.artist.isEmpty() ? "Artiste Inconnu" : track.artist);
                trackAlbumText.setText(track.album.isEmpty() ? "Studio Pro Audio" : track.album);
                String mime = track.mimeType.toUpperCase(Locale.US).replace("AUDIO/", "");
                formatBadgeText.setText(mime.isEmpty() ? "MP3 AUDIO" : mime);
                timeDurationText.setText(track.getFormattedDuration());

                // Chargement de la pochette d'album
                Bitmap art = extractArtwork(track);
                if (art != null) {
                    vinylCoverImage.setImageBitmap(art);
                } else {
                    vinylCoverImage.setImageBitmap(createDefaultArtwork(track.title));
                }

                // Chargement automatique des paroles du morceau
                loadLyricsForTrack(track);
            } else {
                trackTitleText.setText("Aucune piste sélectionnée");
                trackArtistText.setText("Sélectionnez une musique pour commencer");
                trackAlbumText.setText("");
                formatBadgeText.setText("EN ATTENTE");
                timeDurationText.setText("0:00");
                vinylCoverImage.setImageBitmap(createDefaultArtwork("Studio Pro"));
                lyricsList.clear();
                populateLyricsView();
            }
            if (queueAdapter != null) queueAdapter.notifyDataSetChanged();
            updateUpNextInfo();
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        handler.post(() -> {
            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            if (vinylAnimator != null) {
                if (isPlaying) {
                    if (vinylAnimator.isPaused()) vinylAnimator.resume();
                    else if (!vinylAnimator.isRunning()) vinylAnimator.start();
                } else {
                    if (vinylAnimator.isRunning()) vinylAnimator.pause();
                }
            }
        });
    }

    @Override
    public void onProgressUpdated(int currentPositionMs, int durationMs) {
        if (isUserSeeking) return;
        handler.post(() -> {
            timeCurrentText.setText(MusicPlayerManager.formatDuration(currentPositionMs));
            timeDurationText.setText(MusicPlayerManager.formatDuration(durationMs));
            if (durationMs > 0 && playSeekBar != null) {
                int prog = (int) (((float) currentPositionMs / durationMs) * 1000);
                playSeekBar.setProgress(prog);
            }
            updateActiveLyric(currentPositionMs, false);
        });
    }

    @Override
    public void onQueueChanged(List<AudioBrowserDialog.AudioTrackItem> queue, int currentIndex) {
        handler.post(() -> {
            if (queueCountBadge != null) {
                queueCountBadge.setText(String.valueOf(queue.size()));
                queueCountBadge.setVisibility(queue.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (queueAdapter != null) {
                queueAdapter.notifyDataSetChanged();
            }
            updateUpNextInfo();
        });
    }

    @Override
    public void onPlaybackModesChanged(int repeatMode, boolean shuffleEnabled) {
        handler.post(() -> {
            if (btnShuffle != null) {
                btnShuffle.setColorFilter(shuffleEnabled ? 0xFF8BE9FD : 0x66FFFFFF);
            }
            if (btnRepeat != null) {
                if (repeatMode == MusicPlayerManager.REPEAT_OFF) {
                    btnRepeat.setImageResource(R.drawable.ic_repeat);
                    btnRepeat.setColorFilter(0x66FFFFFF);
                } else if (repeatMode == MusicPlayerManager.REPEAT_ALL) {
                    btnRepeat.setImageResource(R.drawable.ic_repeat);
                    btnRepeat.setColorFilter(0xFF50FA7B);
                } else {
                    btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                    btnRepeat.setColorFilter(0xFFFF79C6);
                }
            }
            updateUpNextInfo();
        });
    }

    @Override
    public void onSpeedChanged(float speed, float pitch) {
        handler.post(() -> {
            if (btnSpeed != null) {
                btnSpeed.setText(String.format(Locale.US, "%.2fx", speed));
            }
        });
    }

    private void cyclePlaybackSpeed() {
        float current = playerManager.getPlaybackSpeed();
        float next;
        if (current < 0.7f) next = 0.75f;
        else if (current < 0.95f) next = 1.0f;
        else if (current < 1.2f) next = 1.25f;
        else if (current < 1.45f) next = 1.5f;
        else if (current < 1.95f) next = 2.0f;
        else next = 0.5f;

        playerManager.setPlaybackSpeed(next, playerManager.getPlaybackPitch());
        Toast.makeText(context, "Vitesse : " + next + "x", Toast.LENGTH_SHORT).show();
    }

    private class QueueAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return playerManager.getQueue().size();
        }

        @Override
        public AudioBrowserDialog.AudioTrackItem getItem(int position) {
            List<AudioBrowserDialog.AudioTrackItem> q = playerManager.getQueue();
            return (position >= 0 && position < q.size()) ? q.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
            } else {
                row = (LinearLayout) convertView;
                row.removeAllViews();
            }

            AudioBrowserDialog.AudioTrackItem item = getItem(position);
            if (item == null) return row;

            boolean isCurrent = position == playerManager.getCurrentIndex();

            // Indicateur ou Numéro
            TextView num = new TextView(context);
            num.setText(isCurrent ? "" : String.valueOf(position + 1));
            num.setTextColor(isCurrent ? 0xFF8BE9FD : 0x88FFFFFF);
            num.setTextSize(12);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            row.addView(num, new LinearLayout.LayoutParams(dp(28), -2));

            // Titre & Artiste
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
            clp.leftMargin = dp(8);
            clp.rightMargin = dp(8);

            TextView title = new TextView(context);
            title.setText(item.title);
            title.setTextColor(isCurrent ? 0xFF8BE9FD : 0xFFFFFFFF);
            title.setTextSize(13);
            title.setTypeface(Typeface.create("sans-serif-medium", isCurrent ? Typeface.BOLD : Typeface.NORMAL));
            title.setSingleLine(true);

            TextView artist = new TextView(context);
            artist.setText(item.artist + (item.durationMs > 0 ? " · " + item.getFormattedDuration() : ""));
            artist.setTextColor(isCurrent ? 0xCC8BE9FD : 0x88FFFFFF);
            artist.setTextSize(11);

            col.addView(title);
            col.addView(artist);
            row.addView(col, clp);

            // Bouton supprimer de la file
            ImageView btnRemove = new ImageView(context);
            btnRemove.setImageResource(R.drawable.ic_stop);
            btnRemove.setColorFilter(0x66FF5555);
            int rmSize = dp(20);
            btnRemove.setPadding(dp(2), dp(2), dp(2), dp(2));
            btnRemove.setOnClickListener(v -> {
                playerManager.removeFromQueue(position);
            });
            row.addView(btnRemove, new LinearLayout.LayoutParams(rmSize, rmSize));

            if (isCurrent) {
                GradientDrawable activeRow = new GradientDrawable();
                activeRow.setColor(0x228BE9FD);
                activeRow.setCornerRadius(dp(8));
                row.setBackground(activeRow);
            } else {
                row.setBackground(null);
            }

            return row;
        }
    }

    private Bitmap extractArtwork(AudioBrowserDialog.AudioTrackItem track) {
        if (track == null) return null;

        // 1. Pochette en cache de MainActivity si c'est la piste en cours
        if (context instanceof MainActivity) {
            MainActivity ma = (MainActivity) context;
            Bitmap maCover = ma.getCurrentAudioCoverBitmap();
            if (maCover != null) {
                boolean matchUri = (ma.getCurrentAudioUri() != null && track.contentUri != null &&
                        ma.getCurrentAudioUri().equals(track.contentUri));
                boolean matchTitle = (track.title != null && !track.title.isEmpty() &&
                        ma.getCurrentTrackTitle() != null &&
                        ma.getCurrentTrackTitle().equalsIgnoreCase(track.title));
                if (matchUri || matchTitle) {
                    return maCover;
                }
            }
        }

        // 2. Extraction via MediaMetadataRetriever (embedded picture ID3/MP4)
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            if (track.dataPath != null && !track.dataPath.isEmpty() && new File(track.dataPath).exists()) {
                mmr.setDataSource(track.dataPath);
            } else if (track.contentUri != null) {
                mmr.setDataSource(context, track.contentUri);
            } else if (context instanceof MainActivity) {
                File caf = ((MainActivity) context).getAudioFile();
                if (caf != null && caf.exists()) {
                    mmr.setDataSource(caf.getAbsolutePath());
                } else {
                    return null;
                }
            } else {
                return null;
            }
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            if (art != null && art.length > 0) {
                Bitmap bmp = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (bmp != null) return bmp;
            }
        } catch (Exception ignored) {}

        // 3. Android 10+ (API 29+) : Chargement de la miniature système MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.contentUri != null) {
            try {
                Bitmap thumb = context.getContentResolver().loadThumbnail(track.contentUri, new Size(512, 512), null);
                if (thumb != null) return thumb;
            } catch (Exception ignored) {}
        }

        // 4. MediaStore Album Art classique
        if (track.contentUri != null) {
            try {
                String[] proj = {MediaStore.Audio.Media.ALBUM_ID};
                try (Cursor c = context.getContentResolver().query(track.contentUri, proj, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        long albumId = c.getLong(0);
                        Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/album_art"), albumId);
                        try (InputStream in = context.getContentResolver().openInputStream(albumArtUri)) {
                            Bitmap b = BitmapFactory.decodeStream(in);
                            if (b != null) return b;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        // 5. Recherche d'une image de pochette associée dans le même répertoire local
        if (track.dataPath != null && !track.dataPath.isEmpty()) {
            try {
                File audioFile = new File(track.dataPath);
                File dir = audioFile.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    String base = audioFile.getName().replaceAll("\\.[a-zA-Z0-9]+$", "");
                    String[] candidates = {
                            base + ".jpg", base + ".jpeg", base + ".png",
                            "cover.jpg", "cover.png", "folder.jpg", "front.jpg", "album.jpg"
                    };
                    for (String cand : candidates) {
                        File imgFile = new File(dir, cand);
                        if (imgFile.exists() && imgFile.length() > 500) {
                            Bitmap b = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                            if (b != null) return b;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Bitmap createDefaultArtwork(String title) {
        Bitmap logoBmp = MusicPlaybackService.createStudioProLogoBitmap(context, 256);
        if (logoBmp != null) return logoBmp;

        int s = 256;
        Bitmap bmp = Bitmap.createBitmap(sizeWithDensity(s), sizeWithDensity(s), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF4C1D95);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);

        p.setColor(0xFF8BE9FD);
        p.setTextSize(64);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        String initial = (title != null && !title.isEmpty()) ? title.substring(0, 1).toUpperCase(Locale.US) : "S";
        c.drawText(initial, s / 2f, s / 2f + 22, p);
        return bmp;
    }

    private int sizeWithDensity(int s) {
        return Math.max(128, s);
    }

    private Bitmap createVinylDiscBitmap(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float center = size / 2f;
        float radius = size / 2f - dp(4);

        // Fond Noir Vinyle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF11121A);
        canvas.drawCircle(center, center, radius, paint);

        // Sillons du Vinyle
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x22FFFFFF);
        paint.setStrokeWidth(dp(1.5f));

        for (float r = radius - dp(8); r > dp(60); r -= dp(6)) {
            canvas.drawCircle(center, center, r, paint);
        }

        // Reflet lumineux vinyle (arc de cercle)
        paint.setColor(0x18FFFFFF);
        paint.setStrokeWidth(dp(8));
        RectF arcRect = new RectF(center - radius + dp(12), center - radius + dp(12), center + radius - dp(12), center + radius - dp(12));
        canvas.drawArc(arcRect, 45, 60, false, paint);
        canvas.drawArc(arcRect, 225, 60, false, paint);

        return bmp;
    }

    private FrameLayout createCircleButton(int iconRes, int bgColor, View.OnClickListener onClick) {
        FrameLayout btn = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        btn.setBackground(bg);

        ImageView ic = new ImageView(context);
        ic.setImageResource(iconRes);
        ic.setColorFilter(0xFFFFFFFF);
        int icSize = dp(18);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(icSize, icSize, Gravity.CENTER);
        ic.setLayoutParams(lp);
        btn.addView(ic);

        btn.setOnClickListener(onClick);
        return btn;
    }

    private ImageView createControlIcon(int resId, View.OnClickListener onClick) {
        ImageView iv = new ImageView(context);
        iv.setImageResource(resId);
        iv.setColorFilter(0xFFFFFFFF);
        int pad = dp(isCompactScreen() ? 4 : 5);
        iv.setPadding(pad, pad, pad, pad);
        iv.setOnClickListener(onClick);
        return iv;
    }

    public List<MainActivity.LyricLine> getLyricsList() {
        return lyricsList;
    }

    private View gap(int dp) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), dp(dp)));
        return v;
    }

    private int dp(float v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
