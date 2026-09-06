package com.kidas.studiopro;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackStateListener {
    private static final String PREFS = "visualiseur_prefs", KEY_DONE = "onboarding_completed", KEY_GROQ = "groq_key";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBackPressTime = 0;
    private VisualizerView visualizerView;
    private MediaPlayer player;
    private Visualizer visualizer;
    private byte[] fft = new byte[0], waveform = new byte[0];
    private File audioFile;
    private String audioMime = "audio/mpeg", currentFileName = "audio";
    private String currentTrackTitle = "", currentTrackArtist = "";
    private Bitmap backgroundBitmap, watermarkBitmap;
    private Bitmap currentAudioCoverBitmap = null;
    private ImageView audioPageCoverImage;
    private ImageView audioPageDiskIcon;
    private boolean playing;
    private long trimStartMs = 0, trimEndMs = -1, loopTargetMs = 0;
    private long exportCurrentMs = 0;
    private Uri currentAudioUri;
    private String currentAudioPath = "";
    private float visualizerLengthScale = 0.92f;
    private float lyricsMaxWidth = 92f;
    private float textVerticalOffset = 0f;
    private float lyricsScale = 1.45f;
    private float visualizerIntensity = 0.60f;
    private float lyricsIntensity = 1.0f;
    private boolean exportIncludeVisualizer = true;
    private boolean exportIncludeLyrics = true;

    // Visual options
    private String activeColor = "#8BE9FD";
    private String style = "bars"; // bars, mirror, wave, circle, particles, glow, cube, halo, cyber
    private String backgroundMode = "gradient"; // gradient, dark, image
    private String textMode = "scroll"; // scroll (karaoke défilant), active_line, word, fixed
    private String textPosition = "center"; // top, center, bottom
    private String quote = "", watermark = "";
    private int glow = 16, textSize = 24, formatW = 1080, formatH = 1920, exportFps = 30;
    private float sensitivity = 1.4f, shakeIntensity = 1.0f;
    private boolean beatShakeEnabled = true;

    // Synchronized lyrics
    private final List<LyricLine> lyricsList = new ArrayList<>();

    // UI elements
    private TextView status, audioMeta, aiStatus, homeTrackTitle, homeTrackTime, lyricsPreviewText, exportStatusInfo;
    private EditText quoteInput, watermarkInput, groqKeyInput, quoteThemeInput;
    private Button playButton, exportButton, transcribeButton, generateQuoteButton, snapshotButton, detectButton, loopButton;
    private boolean groqKeyVisible = false;
    private ImageView groqKeyToggleIcon;
    private TextView groqKeyBadge;
    private int transcriptionState = 0; // 0: Inactif, 1: En cours, 2: Terminé
    private int quoteState = 0; // 0: Inactif, 1: En cours, 2: Terminé
    private String lastAiResultType = "";
    private String lastAiResultContent = "";
    private TextView aiTranscriptionBadge, aiTranscriptionDesc;
    private Button aiTranscribeActionBtn;
    private TextView aiQuoteBadge, aiQuoteDesc;
    private Button aiQuoteActionBtn;
    private LinearLayout aiResultCard;
    private TextView aiResultTypeBadge, aiResultText;
    private FrameLayout aiResultCopyBtn, aiResultApplyBtn;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;
    private EditText chatInputField;
    private Button chatSendButton;
    private TextView lastAiMessageView;
    private final View[] navTabs = new View[6];
    private final ImageView[] navIcons = new ImageView[6];
    private final TextView[] navLabels = new TextView[6];
    private final List<Button> homeStyleButtons = new ArrayList<>();
    private final List<Button> visualStyleButtons = new ArrayList<>();
    private final List<View> visualStyleCards = new ArrayList<>();
    private final List<Button> colorButtons = new ArrayList<>();
    private final List<View> colorChipContainers = new ArrayList<>();
    private final List<TextView> colorChipTexts = new ArrayList<>();
    private final List<Button> bgChipButtons = new ArrayList<>();
    private final List<Button> lyricsModeButtons = new ArrayList<>();
    private final List<Button> formatButtons = new ArrayList<>();
    private final List<LinearLayout> formatCards = new ArrayList<>();
    private final List<TextView> formatTitles = new ArrayList<>();
    private final List<TextView> formatSubtitles = new ArrayList<>();
    private final List<TextView> formatRatioBadges = new ArrayList<>();
    private final List<ImageView> formatCheckIcons = new ArrayList<>();
    private final List<ImageView> formatAspectIcons = new ArrayList<>();
    private FrameLayout fps30Container, fps60Container;
    private TextView fps30Title, fps30Subtitle, fps60Title, fps60Subtitle;
    private ProgressBar exportProgressBar;
    private TextView exportPercentText, exportEtaText, exportSummaryTag;
    private TextView exportStateBadge, exportDetailInfoText;
    private Button bgImgBtn, bgGradBtn, bgDarkBtn;
    private Button fps30Btn, fps60Btn;
    private TextView karaokePastLine1, karaokePastLine2, karaokeActiveLine, karaokeNextLine1, karaokeNextLine2;
    private TextView lyricsHeaderBadge;
    private MiniLyricsScrubberView miniLyricsScrubberView;
    private Button audioPagePlayBtn;
    private ImageView audioPagePlayIcon;
    private View audioCoverProgressBar;
    private LinearLayout silenceTimelineContainer;
    private TextView audioPageTrackTitle, audioPageTrackArtist, audioPageCurrentTime, audioPageDurationTime;
    private TextView audioBpmKeyBadge;
    private BpmKeyDetector.AudioAnalysisResult audioAnalysisResult;
    private InteractiveWaveformView audioWaveformView;
    private View silenceIntroBar, silenceActiveBar, silenceOutroBar;
    private TextView silenceInfoText, silenceActiveLabel;
    private VisualizerView visualPagePreview;
    private TextView visualPageTitle, visualPageBgTag;
    private Button exportVisualizerToggleButton, exportLyricsToggleButton;
    private FrameLayout pageHost;
    private FrameLayout headerMusicPlayerBtn;
    private final List<LinearLayout> pages = new ArrayList<>();
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private ActivityResultLauncher<IntentSenderRequest> writeRequestLauncher;
    private AudioBrowserDialog currentAudioBrowserDialog;
    private MusicPlayerDialog currentMusicPlayerDialog;
    private Runnable pendingStoragePermissionCallback;
    private String pickPurpose = "audio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        splashScreen.setOnExitAnimationListener(splashScreenViewProvider -> {
            View splashView = splashScreenViewProvider.getView();
            splashView.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(splashScreenViewProvider::remove)
                    .start();
        });

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}
                if ("audio".equals(pickPurpose)) loadAudio(uri);
                else if ("lrc".equals(pickPurpose)) loadLrcFile(uri);
                else loadImage(uri, "background".equals(pickPurpose));
            }
        });

        audioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                try {
                    Toast.makeText(this, "Accès au stockage accordé !", Toast.LENGTH_SHORT).show();
                    if (pendingStoragePermissionCallback != null) {
                        Runnable cb = pendingStoragePermissionCallback;
                        pendingStoragePermissionCallback = null;
                        try { cb.run(); } catch (Exception ignored) {}
                    } else {
                        MusicPlayerManager.getInstance(this).scanAndRefreshDeviceTracks(this, false, tracks -> {
                            if (currentMusicPlayerDialog != null && currentMusicPlayerDialog.isShowing()) {
                                try {
                                    currentMusicPlayerDialog.onPermissionRefreshed();
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                    if (currentAudioBrowserDialog != null && currentAudioBrowserDialog.isShowing()) {
                        try {
                            currentAudioBrowserDialog.checkPermissionAndScan();
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Erreur audioPermissionLauncher", e);
                }
            } else {
                Toast.makeText(this, "Accès restreint. Vous pouvez aussi choisir vos fichiers manuellement.", Toast.LENGTH_LONG).show();
                if (currentMusicPlayerDialog != null && currentMusicPlayerDialog.isShowing()) {
                    try {
                        currentMusicPlayerDialog.onPermissionRefreshed();
                    } catch (Exception ignored) {}
                }
            }
        });

        writeRequestLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Toast.makeText(this, "Autorisation accordée. Modification sur place en cours…", Toast.LENGTH_SHORT).show();
                embedLyricsDirectlyToAudio();
            } else {
                Toast.makeText(this, "Autorisation refusée pour la modification du fichier original.", Toast.LENGTH_LONG).show();
            }
        });

        final boolean needsOnboarding = !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DONE, false);
        if (needsOnboarding) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        buildUi();
        loadPreferences();
        cleanupDocumentLyricsAndLegacyFiles();

        List<String> neededPerms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                neededPerms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!neededPerms.isEmpty()) {
            requestPermissions(neededPerms.toArray(new String[0]), 1001);
        }
        MusicPlayerManager.getInstance(this).addListener(this);
        MusicPlayerManager.getInstance(this).setPlaybackConflictListener(() -> runOnUiThread(this::pauseStudioPlayer));
        handleIncomingNotificationIntent(getIntent());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < 2000) {
                    finish();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this, "Appuyez une seconde fois pour quitter", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_gradient);
        LinearLayout shell = vertical();
        final boolean compact = getResources().getConfiguration().screenWidthDp < 360;
        final boolean largeScreen = getResources().getConfiguration().screenWidthDp >= 600;
        final int shellHorizontalPad = dp(compact ? 10 : (largeScreen ? 24 : 14));
        shell.setPadding(shellHorizontalPad, dp(8), shellHorizontalPad, dp(8));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(createHeader());

        pageHost = new FrameLayout(this);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, 0, 1);
        hp.topMargin = dp(6);
        hp.bottomMargin = dp(8);
        shell.addView(pageHost, hp);

        // Barre de navigation flottante (Bottom Nav Pill - radius 28dp, bg_bottom_nav_pill)
        LinearLayout nav = row();
        nav.setBackgroundResource(R.drawable.bg_bottom_nav_pill);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));

        int[] iconDrawables = {
            R.drawable.ic_nav_studio,
            R.drawable.ic_nav_visual,
            R.drawable.ic_nav_lyrics,
            R.drawable.ic_nav_audio,
            R.drawable.ic_nav_ai,
            R.drawable.ic_nav_export
        };
        String[] labels = {"Studio", "Visuel", "Paroles", "Audio", "IA", "Export"};

        for (int i = 0; i < 6; i++) {
            final int x = i;
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(4), dp(2), dp(4));

            FrameLayout iconPill = new FrameLayout(this);
            LinearLayout.LayoutParams iconPillLp = new LinearLayout.LayoutParams(dp(compact ? 34 : 40), dp(26));
            iconPill.setLayoutParams(iconPillLp);

            ImageView ic = new ImageView(this);
            ic.setImageResource(iconDrawables[i]);
            ic.setColorFilter(0x66FFFFFF); // 40% blanc
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(compact ? 16 : 18), dp(compact ? 16 : 18), Gravity.CENTER);
            ic.setLayoutParams(ip);
            iconPill.addView(ic);

            TextView lb = new TextView(this);
            lb.setText(labels[i]);
            lb.setTextSize(compact ? 9 : 10);
            lb.setGravity(Gravity.CENTER);
            lb.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            lb.setTextColor(0x66FFFFFF); // 40% blanc

            item.addView(iconPill);
            item.addView(gap(2));
            item.addView(lb);

            navTabs[i] = iconPill;
            navIcons[i] = ic;
            navLabels[i] = lb;

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) p.leftMargin = dp(2);
            nav.addView(item, p);

            item.setOnClickListener(v -> showPage(x));
        }
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(compact ? 56 : (largeScreen ? 66 : 62))));

        pages.add(createStudioPage());
        pages.add(createVisualPage());
        pages.add(createLyricsPage());
        pages.add(createAudioPage());
        pages.add(createAiPage());
        pages.add(createExportPage());

        for (LinearLayout p : pages) pageHost.addView(p, new FrameLayout.LayoutParams(-1, -1));
        showPage(0);

        // Overlay Splash Screen Studio Pro
        final StudioProSplashView splashOverlay = new StudioProSplashView(this);
        root.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        splashOverlay.startSplashAnimation();

        // Après l'animation complète barWave (1300ms), transition fluide vers l'écran de destination
        handler.postDelayed(() -> {
            splashOverlay.dismissSplash(null);
        }, 1300);
    }

    private View createHeader() {
        LinearLayout box = vertical();
        box.setPadding(dp(4), dp(8), dp(4), dp(6));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);

        // Badge Studio Pro avec logo officiel de l'application
        LinearLayout proPill = row();
        proPill.setGravity(Gravity.CENTER_VERTICAL);
        proPill.setPadding(dp(10), dp(4), dp(12), dp(4));
        proPill.setBackgroundResource(R.drawable.bg_chip_inactive);

        // Logo discret STUDIO PRO dans la barre en haut
        FrameLayout logoIconFrame = new FrameLayout(this);
        GradientDrawable lBg = new GradientDrawable();
        lBg.setShape(GradientDrawable.OVAL);
        lBg.setColor(0x2222D3EE);
        logoIconFrame.setBackground(lBg);
        ImageView logoIv = new ImageView(this);
        logoIv.setImageResource(R.drawable.ic_nav_audio);
        logoIv.setColorFilter(0xFF22D3EE);
        logoIconFrame.addView(logoIv, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        dpLp.rightMargin = dp(8);
        proPill.addView(logoIconFrame, dpLp);

        TextView proTxt = title("STUDIO PRO", 11, 0xFFF5F5F7);
        proTxt.setLetterSpacing(0.1f);
        proPill.addView(proTxt);
        top.addView(proPill);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        // Badges d'état et profil
        LinearLayout rightIcons = row();

        // Bouton Lecteur de Musique Studio Pro (demandé par l'utilisateur - Icône note de musique pure, non-sticker)
        FrameLayout musicPlayerBtn = new FrameLayout(this);
        musicPlayerBtn.setClickable(true);
        musicPlayerBtn.setFocusable(true);
        GradientDrawable mpBg = new GradientDrawable();
        mpBg.setShape(GradientDrawable.OVAL);
        mpBg.setColor(0x18FFFFFF);
        musicPlayerBtn.setBackground(mpBg);
        int btnSize = dp(38);
        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        musicPlayerBtn.setLayoutParams(mpLp);

        ImageView musicPlayerIc = new ImageView(this);
        musicPlayerIc.setImageResource(R.drawable.ic_music_note);
        musicPlayerIc.setColorFilter(0xFF8BE9FD); // Cyan studio vibrant
        musicPlayerIc.setClickable(false);
        musicPlayerBtn.addView(musicPlayerIc, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

        View.OnClickListener openPlayerListener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openMusicPlayer();
        };
        musicPlayerBtn.setOnClickListener(openPlayerListener);
        musicPlayerBtn.setContentDescription("Ouvrir le Lecteur Musique Studio Pro");
        this.headerMusicPlayerBtn = musicPlayerBtn;
        rightIcons.addView(musicPlayerBtn);
        rightIcons.addView(gapW(8));

        // Avatar profil épuré
        FrameLayout userBtn = new FrameLayout(this);
        GradientDrawable userBg = new GradientDrawable();
        userBg.setShape(GradientDrawable.OVAL);
        userBg.setColor(0x14FFFFFF);
        userBtn.setBackground(userBg);
        userBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        ImageView userIc = new ImageView(this);
        userIc.setImageResource(R.drawable.ic_nav_ai);
        userIc.setColorFilter(0xCCFFFFFF);
        userBtn.addView(userIc, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        rightIcons.addView(userBtn);

        top.addView(rightIcons);
        box.addView(top);
        return box;
    }

    private LinearLayout createStudioPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();

        // 1. Live Preview Card (_n : Glass Card - radius 24dp, bg_glass_card)
        LinearLayout hero = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout heroTop = row();
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heroTitles = vertical();
        heroTitles.addView(section("NOW PLAYING — PREVIEW LIVE"));

        homeTrackTitle = title(audioFile != null ? currentTrackTitle : "Midnight Signals • LØST", 18, 0xFFF5F5F7);
        homeTrackTitle.setPadding(0, dp(3), 0, dp(1));
        heroTitles.addView(homeTrackTitle);

        TextView trackMeta = subtitle(audioFile != null ? (currentTrackArtist.isEmpty() ? "Audio importé" : currentTrackArtist) + " • 48kHz • Stereo" : "03:42 • 48kHz • Stereo");
        heroTitles.addView(trackMeta);
        heroTop.addView(heroTitles, new LinearLayout.LayoutParams(0, -2, 1));

        // Menu More (...)
        FrameLayout moreBtn = new FrameLayout(this);
        moreBtn.setBackgroundResource(R.drawable.bg_chip_inactive);
        moreBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView moreTxt = title("•••", 10, 0x99FFFFFF);
        moreBtn.addView(moreTxt, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        heroTop.addView(moreBtn);

        hero.addView(heroTop);
        hero.addView(gap(14));

        // Visualizer View Container (bg: rgba(0,0,0,0.40), border: rgba(255,255,255,0.06), radius: 16dp)
        FrameLayout vpWrap = new FrameLayout(this);
        shape(vpWrap, 0x66000000, dp(16), 0x0FFFFFFF);
        vpWrap.setPadding(dp(6), dp(6), dp(6), dp(6));

        visualizerView = new VisualizerView(this);
        vpWrap.addView(visualizerView, new FrameLayout.LayoutParams(-1, dp(140)));

        // Live overlay timestamps (00:00 - LIVE PREVIEW • 60FPS - 03:42)
        LinearLayout timeOverlay = row();
        timeOverlay.setPadding(dp(10), 0, dp(10), dp(8));
        TextView tStart = subtitle("00:00");
        tStart.setTextSize(10);
        tStart.setTextColor(0x66FFFFFF);
        timeOverlay.addView(tStart);

        TextView tMid = subtitle("LIVE PREVIEW • 60FPS");
        tMid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        tMid.setGravity(Gravity.CENTER);
        tMid.setTextSize(10);
        tMid.setTextColor(0x66FFFFFF);
        timeOverlay.addView(tMid);

        homeTrackTime = subtitle("03:42");
        homeTrackTime.setTextSize(10);
        homeTrackTime.setTextColor(0x66FFFFFF);
        timeOverlay.addView(homeTrackTime);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        vpWrap.addView(timeOverlay, topLp);

        hero.addView(vpWrap);
        hero.addView(gap(12));

        // Playback & Pulse Controls
        LinearLayout ctl = row();
        ctl.setGravity(Gravity.CENTER);
        playButton = accent(" Lecture");
        playButton.setOnClickListener(v -> togglePlayback());
        ctl.addView(playButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button beatBtn = secondary(beatShakeEnabled ? "Beat Pulse : Actif" : "Beat Pulse : Inactif");
        beatBtn.setOnClickListener(v -> {
            beatShakeEnabled = !beatShakeEnabled;
            beatBtn.setText(beatShakeEnabled ? "Beat Pulse : Actif" : "Beat Pulse : Inactif");
            visualizerView.invalidate();
            saveSession();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
        bp.leftMargin = dp(8);
        ctl.addView(beatBtn, bp);
        hero.addView(ctl);
        c.addView(hero);
        c.addView(gap(14));

        // 2. Quick Access Grid (2x2)
        c.addView(section("ACCÈS RAPIDE STUDIO"));
        c.addView(gap(10));

        LinearLayout gridRow1 = row();
        LinearLayout cardNew = createQuickCard("Nouveau projet", "Importer audio", R.drawable.ic_nav_studio, true, v -> openAudioBrowser());
        gridRow1.addView(cardNew, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout cardLib = createQuickCard("Gestionnaire Audio", "Écouter & Importer", R.drawable.ic_nav_audio, false, v -> openAudioBrowser());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1);
        clp.leftMargin = dp(10);
        gridRow1.addView(cardLib, clp);
        c.addView(gridRow1);
        c.addView(gap(10));

        LinearLayout gridRow2 = row();
        LinearLayout cardPresets = createQuickCard("Presets", "9 styles", R.drawable.ic_nav_visual, false, v -> showPage(1));
        gridRow2.addView(cardPresets, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout cardHist = createQuickCard("Historique", "Derniers exports", R.drawable.ic_nav_export, false, v -> showPage(5));
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(0, -2, 1);
        chlp.leftMargin = dp(10);
        gridRow2.addView(cardHist, chlp);
        c.addView(gridRow2);
        c.addView(gap(20));

        // 3. AI Features Section ("Fonctions IA")
        LinearLayout aiHead = row();
        aiHead.addView(section("FONCTIONS IA"), new LinearLayout.LayoutParams(0, -2, 1));
        ImageView aiChevron = new ImageView(this);
        aiChevron.setImageResource(R.drawable.ic_nav_ai);
        aiChevron.setColorFilter(0x4DFFFFFF); // 30% blanc
        aiHead.addView(aiChevron, new LinearLayout.LayoutParams(dp(16), dp(16)));
        c.addView(aiHead);
        c.addView(gap(10));

        HorizontalScrollView aiScroll = new HorizontalScrollView(this);
        aiScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout aiCards = row();

        // Carte Transcription (Whisper)
        LinearLayout cardWhisper = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        cardWhisper.setPadding(dp(16), dp(16), dp(16), dp(16));
        cardWhisper.setOnClickListener(v -> {
            showPage(4);
            transcribeFromChat();
        });

        LinearLayout cwRow = row();
        FrameLayout cwIconWrap = new FrameLayout(this);
        cwIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView cwIcon = new ImageView(this);
        cwIcon.setImageResource(R.drawable.ic_nav_ai);
        cwIcon.setColorFilter(Color.WHITE);
        cwIconWrap.addView(cwIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        cwRow.addView(cwIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout cwTexts = vertical();
        cwTexts.setPadding(dp(12), 0, 0, 0);
        cwTexts.addView(title("Transcription", 14, Color.WHITE));
        TextView cwSub = subtitle("Paroles auto via Whisper");
        cwSub.setPadding(0, dp(2), 0, dp(6));
        cwTexts.addView(cwSub);

        // Badge discret "IA active"
        LinearLayout cwBadge = row();
        cwBadge.setBackgroundResource(R.drawable.bg_badge_ia);
        cwBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        View cwDot = new View(this);
        shape(cwDot, 0xFF22D3EE, dp(3), 0);
        cwBadge.addView(cwDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView cwBadgeTxt = title("IA ACTIVE", 10, 0xFF22D3EE);
        cwBadgeTxt.setLetterSpacing(0.1f);
        cwBadgeTxt.setPadding(dp(6), 0, 0, 0);
        cwBadge.addView(cwBadgeTxt);
        cwTexts.addView(cwBadge, new LinearLayout.LayoutParams(-2, -2));

        cwRow.addView(cwTexts, new LinearLayout.LayoutParams(0, -2, 1));
        cardWhisper.addView(cwRow);
        aiCards.addView(cardWhisper, new LinearLayout.LayoutParams(dp(240), -2));
        aiCards.addView(gapW(12));

        // Carte Micro Live IA
        LinearLayout cardRecLive = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        cardRecLive.setPadding(dp(16), dp(16), dp(16), dp(16));
        cardRecLive.setOnClickListener(v -> openVoiceRecorderModal());

        LinearLayout crRow = row();
        FrameLayout crIconWrap = new FrameLayout(this);
        crIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView crIcon = new ImageView(this);
        crIcon.setImageResource(R.drawable.ic_mic);
        crIcon.setColorFilter(0xFF22D3EE);
        crIconWrap.addView(crIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        crRow.addView(crIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout crTexts = vertical();
        crTexts.setPadding(dp(12), 0, 0, 0);
        crTexts.addView(title("Micro & Live IA", 14, Color.WHITE));
        TextView crSub = subtitle("Enregistre & transcris en direct");
        crSub.setPadding(0, dp(2), 0, dp(6));
        crTexts.addView(crSub);

        LinearLayout crBadge = row();
        crBadge.setBackgroundResource(R.drawable.bg_badge_ia);
        crBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        View crDot = new View(this);
        shape(crDot, 0xFFA855F7, dp(3), 0);
        crBadge.addView(crDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView crBadgeTxt = title("WHISPER IA", 10, 0xFFA855F7);
        crBadgeTxt.setLetterSpacing(0.1f);
        crBadgeTxt.setPadding(dp(6), 0, 0, 0);
        crBadge.addView(crBadgeTxt);
        crTexts.addView(crBadge, new LinearLayout.LayoutParams(-2, -2));

        crRow.addView(crTexts, new LinearLayout.LayoutParams(0, -2, 1));
        cardRecLive.addView(crRow);
        aiCards.addView(cardRecLive, new LinearLayout.LayoutParams(dp(240), -2));
        aiCards.addView(gapW(12));

        // Carte Citation IA
        LinearLayout cardQuote = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        cardQuote.setPadding(dp(16), dp(16), dp(16), dp(16));
        cardQuote.setOnClickListener(v -> {
            showPage(4);
            generateQuoteDialog();
        });

        LinearLayout cqRow = row();
        FrameLayout cqIconWrap = new FrameLayout(this);
        cqIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView cqIcon = new ImageView(this);
        cqIcon.setImageResource(R.drawable.ic_nav_lyrics);
        cqIcon.setColorFilter(Color.WHITE);
        cqIconWrap.addView(cqIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        cqRow.addView(cqIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout cqTexts = vertical();
        cqTexts.setPadding(dp(12), 0, 0, 0);
        cqTexts.addView(title("Citation IA", 14, Color.WHITE));
        TextView cqSub = subtitle("Génère hook viral depuis lyrics");
        cqSub.setPadding(0, dp(2), 0, dp(6));
        cqTexts.addView(cqSub);

        LinearLayout cqBadge = row();
        cqBadge.setBackgroundResource(R.drawable.bg_badge_ia);
        cqBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        View cqDot = new View(this);
        shape(cqDot, 0xFF22D3EE, dp(3), 0);
        cqBadge.addView(cqDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView cqBadgeTxt = title("IA ACTIVE", 10, 0xFF22D3EE);
        cqBadgeTxt.setLetterSpacing(0.1f);
        cqBadgeTxt.setPadding(dp(6), 0, 0, 0);
        cqBadge.addView(cqBadgeTxt);
        cqTexts.addView(cqBadge, new LinearLayout.LayoutParams(-2, -2));

        cqRow.addView(cqTexts, new LinearLayout.LayoutParams(0, -2, 1));
        cardQuote.addView(cqRow);
        aiCards.addView(cardQuote, new LinearLayout.LayoutParams(dp(240), -2));

        aiScroll.addView(aiCards);
        c.addView(aiScroll);
        c.addView(gap(16));

        s.addView(c);
        page.addView(s);
        return page;
    }

    private LinearLayout createQuickCard(String t, String sub, int iconRes, boolean primaryIcon, View.OnClickListener onClick) {
        LinearLayout l = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setOnClickListener(onClick);

        FrameLayout iconWrap = new FrameLayout(this);
        if (primaryIcon) {
            iconWrap.setBackgroundResource(R.drawable.btn_gradient);
        } else {
            shape(iconWrap, 0x0FFFFFFF, dp(12), 0);
        }
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setColorFilter(primaryIcon ? Color.WHITE : 0xB3FFFFFF);
        iconWrap.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        l.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));
        l.addView(gap(10));

        l.addView(title(t, 14, 0xFFF5F5F7));
        TextView st = subtitle(sub);
        st.setTextSize(12);
        st.setTextColor(0xFF9CA3AF);
        l.addView(st);
        return l;
    }

    private LinearLayout createVisualPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();

        // 1. En-tête de la page
        LinearLayout vHead = row();
        vHead.addView(title("Visuel", 16, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        vHead.addView(badge("9 PRESETS • GPU", 0x14FFFFFF, 0xFF9CA3AF, false));
        c.addView(vHead);
        c.addView(gap(12));

        // 2. Grand aperçu animé (h-320px, rounded-24px, border-white/0.08, bg-black)
        LinearLayout previewCard = card(0xFF000000, 0x14FFFFFF, 24);
        previewCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        visualPagePreview = new VisualizerView(this);
        previewCard.addView(visualPagePreview, new LinearLayout.LayoutParams(-1, dp(360)));

        LinearLayout previewMeta = row();
        previewMeta.setPadding(dp(4), dp(8), dp(4), dp(4));
        visualPageTitle = title(getStyleDisplayName(style) + " • " + getColorDisplayName(activeColor), 14, 0xFFF5F5F7);
        previewMeta.addView(visualPageTitle, new LinearLayout.LayoutParams(0, -2, 1));
        visualPageBgTag = badge(getBgDisplayName(backgroundMode).toUpperCase(), 0x1AFFFFFF, 0xB3FFFFFF, false);
        previewMeta.addView(visualPageBgTag);
        previewCard.addView(previewMeta);

        c.addView(previewCard);
        c.addView(gap(16));

        LinearLayout sizeCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        sizeCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        sizeCard.addView(section("RÉGLAGES DU RENDU"));
        sizeCard.addView(gap(8));

        TextView visSizeLabel = subtitle("Longueur du visualiseur : " + Math.round(visualizerLengthScale * 100) + "%");
        visSizeLabel.setTextSize(12);
        sizeCard.addView(visSizeLabel);
        SeekBar visSizeSeek = new SeekBar(this);
        visSizeSeek.setMax(75);
        visSizeSeek.setProgress(Math.max(0, Math.min(75, Math.round((visualizerLengthScale - 0.50f) * 100f))));
        visSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                visualizerLengthScale = 0.50f + progress / 100f;
                visSizeLabel.setText("Longueur du visualiseur : " + Math.round(visualizerLengthScale * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(visSizeSeek, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView lyrWidthLabel = subtitle("Largeur des paroles : " + Math.round(lyricsMaxWidth) + "%");
        lyrWidthLabel.setTextSize(12);
        lyrWidthLabel.setPadding(0, dp(6), 0, 0);
        sizeCard.addView(lyrWidthLabel);
        SeekBar lyrWidthSeek = new SeekBar(this);
        lyrWidthSeek.setMax(50);
        lyrWidthSeek.setProgress(Math.max(0, Math.min(50, Math.round(lyricsMaxWidth - 50f))));
        lyrWidthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lyricsMaxWidth = 50f + progress;
                lyrWidthLabel.setText("Largeur des paroles : " + Math.round(lyricsMaxWidth) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(lyrWidthSeek, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView offsetLabel = subtitle("Position verticale des paroles : " + Math.round(textVerticalOffset * 100) + "%");
        offsetLabel.setTextSize(12);
        offsetLabel.setPadding(0, dp(6), 0, 0);
        sizeCard.addView(offsetLabel);
        SeekBar offsetSeek = new SeekBar(this);
        offsetSeek.setMax(40);
        offsetSeek.setProgress(Math.max(0, Math.min(40, Math.round((textVerticalOffset + 0.20f) * 100f))));
        offsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textVerticalOffset = -0.20f + progress / 100f;
                offsetLabel.setText("Position verticale des paroles : " + Math.round(textVerticalOffset * 100) + "%");
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { saveSession(); }
        });
        sizeCard.addView(offsetSeek, new LinearLayout.LayoutParams(-1, dp(40)));
                c.addView(sizeCard);
        c.addView(gap(16));

        // 3. Grille des 9 vignettes de style (aspect-square, rounded-16px, 3 colonnes)
        c.addView(section("SÉLECTION STYLE"));
        c.addView(gap(10));

        String[] names = {"Barres", "Miroir", "Vague", "Cercle", "Particules", "Glow Néon", "3D Cubes", "Halo", "Cyber"};
        String[] vals = {"bars", "mirror", "wave", "circle", "particles", "glow", "cube", "halo", "cyber"};
        int[] icons = {
            R.drawable.ic_nav_visual,
            R.drawable.ic_nav_studio,
            R.drawable.ic_nav_visual,
            R.drawable.ic_nav_audio,
            R.drawable.ic_nav_ai,
            R.drawable.ic_nav_studio,
            R.drawable.ic_nav_visual,
            R.drawable.ic_nav_studio,
            R.drawable.ic_nav_export
        };

        visualStyleCards.clear();
        visualStyleButtons.clear();

        for (int r = 0; r < 3; r++) {
            LinearLayout rowStyles = row();
            for (int col = 0; col < 3; col++) {
                int idx = r * 3 + col;
                final int x = idx;
                boolean active = style.equals(vals[x]);

                // Vignette style card (aspect ratio square approx 96dp)
                LinearLayout styleCard = vertical();
                styleCard.setGravity(Gravity.CENTER);
                styleCard.setPadding(dp(6), dp(10), dp(6), dp(8));
                if (active) {
                    styleCard.setBackgroundResource(R.drawable.bg_style_card_selected);
                } else {
                    styleCard.setBackgroundResource(R.drawable.bg_style_card_unselected);
                }

                // Mini preview bars container
                LinearLayout miniBars = row();
                miniBars.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                int[] barH = {14, 22, 32, 26, 18};
                for (int bIdx = 0; bIdx < 5; bIdx++) {
                    View bar = new View(this);
                    shape(bar, active ? 0xFFA855F7 : 0x4DFFFFFF, dp(2), 0);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(3), dp(barH[bIdx]));
                    if (bIdx > 0) blp.leftMargin = dp(3);
                    miniBars.addView(bar, blp);
                }
                styleCard.addView(miniBars, new LinearLayout.LayoutParams(-1, dp(34)));
                styleCard.addView(gap(6));

                TextView lbl = new TextView(this);
                lbl.setText(names[x]);
                lbl.setTextSize(11);
                lbl.setGravity(Gravity.CENTER);
                lbl.setTextColor(active ? Color.WHITE : 0xCCFFFFFF);
                lbl.setTypeface(Typeface.create("sans-serif", active ? Typeface.BOLD : Typeface.NORMAL));
                styleCard.addView(lbl);

                visualStyleCards.add(styleCard);

                styleCard.setOnClickListener(v -> updateStyleSelection(vals[x]));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), 1);
                if (col > 0) lp.leftMargin = dp(8);
                rowStyles.addView(styleCard, lp);
            }
            c.addView(rowStyles);
            if (r < 2) c.addView(gap(8));
        }
        c.addView(gap(16));

        // 4. Couleur Accent (Cyan, Violet, Rose, Vert)
        c.addView(section("COULEUR ACCENT"));
        c.addView(gap(8));

        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout colorsRow = row();
        colorChipContainers.clear();
        colorChipTexts.clear();
        colorButtons.clear();

        String[] colorCodes = {"#22D3EE", "#A855F7", "#EC4899", "#22C55E"};
        String[] colorNames = {"cyan", "violet", "rose", "vert"};

        for (int i = 0; i < colorCodes.length; i++) {
            final String colCode = colorCodes[i];
            final String colName = colorNames[i];
            boolean sel = colCode.equalsIgnoreCase(activeColor) || colName.equalsIgnoreCase(activeColor);

            LinearLayout chip = row();
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            if (sel) {
                chip.setBackgroundResource(R.drawable.bg_chip_active);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_inactive);
            }

            View dot = new View(this);
            shape(dot, Color.parseColor(colCode), dp(6), 0);
            chip.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

            TextView txt = new TextView(this);
            txt.setText(" " + colName.substring(0, 1).toUpperCase() + colName.substring(1));
            txt.setTextSize(12);
            txt.setTextColor(sel ? Color.WHITE : 0x80FFFFFF);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            chip.addView(txt);

            colorChipContainers.add(chip);
            colorChipTexts.add(txt);

            chip.setOnClickListener(v -> updateColorSelection(colCode));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
            if (i > 0) p.leftMargin = dp(8);
            colorsRow.addView(chip, p);
        }
        cs.addView(colorsRow);
        c.addView(cs);
        c.addView(gap(16));

        // 5. Fond (Noir, Dégradé animé, Radial pulsé, Image)
        c.addView(section("FOND"));
        c.addView(gap(8));

        HorizontalScrollView bgScroll = new HorizontalScrollView(this);
        bgScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bgRow = row();
        bgChipButtons.clear();

        String[] bgLabels = {"Noir", "Dégradé animé", "Radial pulsé", "Image"};
        String[] bgModes = {"dark", "gradient", "radial", "image"};

        for (int i = 0; i < bgLabels.length; i++) {
            final String modeVal = bgModes[i];
            final String modeLabel = bgLabels[i];
            boolean sel = backgroundMode.equals(modeVal);

            Button chipBtn = new Button(this);
            chipBtn.setText(modeLabel);
            chipBtn.setTextSize(13);
            chipBtn.setAllCaps(false);
            chipBtn.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            chipBtn.setTextColor(sel ? Color.WHITE : 0xFF9CA3AF);
            chipBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
            chipBtn.setMinHeight(0);
            chipBtn.setMinimumHeight(0);
            if (sel) {
                chipBtn.setBackgroundResource(R.drawable.bg_chip_active);
            } else {
                chipBtn.setBackgroundResource(R.drawable.bg_chip_inactive);
            }

            chipBtn.setOnClickListener(v -> {
                if ("image".equals(modeVal)) {
                    pickPurpose = "background";
                    try {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.setType("image/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        filePickerLauncher.launch(Intent.createChooser(intent, "Choisir une image"));
                    } catch (Exception e1) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.setType("image/*");
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                            filePickerLauncher.launch(intent);
                        } catch (Exception ignored) {}
                    }
                } else {
                    updateBgSelection(modeVal);
                }
            });

            bgChipButtons.add(chipBtn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            if (i > 0) lp.leftMargin = dp(8);
            bgRow.addView(chipBtn, lp);
        }
        bgScroll.addView(bgRow);
        c.addView(bgScroll);
        c.addView(gap(20));

        // 6. Bouton Appliquer (Primary gradient button fixed)
        Button btnApply = accent("Appliquer");
        btnApply.setOnClickListener(v -> {
            showPage(0);
            Toast.makeText(this, "Style " + getStyleDisplayName(style) + " appliqué", Toast.LENGTH_SHORT).show();
        });
        c.addView(btnApply, new LinearLayout.LayoutParams(-1, dp(50)));
        c.addView(gap(12));

        s.addView(c);
        page.addView(s);
        return page;
    }

    private LinearLayout createLyricsPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();
        c.setPadding(0, dp(4), 0, dp(12));

        // 1. Header ("Paroles" + "LRC • 98% SYNC" ou "LRC • INACTIF")
        LinearLayout lHead = row();
        lHead.addView(title("Paroles", 16, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        lyricsHeaderBadge = badge(lyricsList.isEmpty() ? "LRC • INACTIF" : "LRC • 98% SYNC", 0x14FFFFFF, 0xFF9CA3AF, false);
        lHead.addView(lyricsHeaderBadge);
        c.addView(lHead);
        c.addView(gap(12));

        // 2. Mini Scrubber Waveform Bar (h-[52px], rounded-[14px], bg-black/50, border-white/0.08, p-2)
        FrameLayout scrubberWrap = new FrameLayout(this);
        shape(scrubberWrap, 0x80000000, dp(14), 0x14FFFFFF, false);
        scrubberWrap.setPadding(dp(8), dp(6), dp(8), dp(6));

        miniLyricsScrubberView = new MiniLyricsScrubberView(this);
        scrubberWrap.addView(miniLyricsScrubberView, new FrameLayout.LayoutParams(-1, dp(40), Gravity.CENTER));
        c.addView(scrubberWrap, new LinearLayout.LayoutParams(-1, dp(52)));
        c.addView(gap(14));

        // 3. Toggle Mode Paroles (Karaoké défilant / Mot à mot / Bloc fixe) dans une pilule w-fit
        LinearLayout toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.HORIZONTAL);
        shape(toggleContainer, 0x10FFFFFF, dp(999), 0x14FFFFFF, false);
        toggleContainer.setPadding(dp(4), dp(4), dp(4), dp(4));

        lyricsModeButtons.clear();
        String[] modes = {"Karaoké défilant", "Mot à mot", "Bloc fixe"};
        String[] modeVals = {"scroll", "word", "fixed"};
        for (int i = 0; i < modes.length; i++) {
            final String mv = modeVals[i];
            boolean sel = textMode.equals(mv);

            Button mb = new Button(this);
            mb.setText(modes[i]);
            mb.setTextSize(11);
            mb.setAllCaps(false);
            mb.setGravity(Gravity.CENTER);
            mb.setPadding(dp(12), dp(6), dp(12), dp(6));
            mb.setMinHeight(0);
            mb.setMinimumHeight(0);
            mb.setMinWidth(0);
            mb.setMinimumWidth(0);

            if (sel) {
                mb.setBackgroundResource(R.drawable.bg_chip_active);
                mb.setTextColor(Color.WHITE);
                mb.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            } else {
                mb.setBackground(null);
                mb.setTextColor(0x66FFFFFF); // 40% blanc
                mb.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }

            mb.setOnClickListener(v -> updateLyricsModeSelection(mv));
            lyricsModeButtons.add(mb);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(30));
            if (i > 0) lp.leftMargin = dp(2);
            toggleContainer.addView(mb, lp);
        }
        c.addView(toggleContainer, new LinearLayout.LayoutParams(-2, -2));
        c.addView(gap(16));

        // 4. Vue Karaoké Centrée (Ligne active 28sp dégradé au centre, dégradé d'opacité passé/futur 18sp)
        LinearLayout karaokeCard = vertical();
        karaokeCard.setGravity(Gravity.CENTER);
        karaokeCard.setPadding(dp(16), dp(24), dp(16), dp(24));

        karaokePastLine1 = new TextView(this);
        karaokePastLine1.setTextSize(18);
        karaokePastLine1.setTextColor(0xFFF5F5F7);
        karaokePastLine1.setAlpha(0.50f);
        karaokePastLine1.setGravity(Gravity.CENTER);
        karaokePastLine1.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        karaokePastLine1.setPadding(0, dp(4), 0, dp(4));
        karaokeCard.addView(karaokePastLine1);

        karaokePastLine2 = new TextView(this);
        karaokePastLine2.setTextSize(18);
        karaokePastLine2.setTextColor(0xFFF5F5F7);
        karaokePastLine2.setAlpha(0.75f);
        karaokePastLine2.setGravity(Gravity.CENTER);
        karaokePastLine2.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        karaokePastLine2.setPadding(0, dp(4), 0, dp(6));
        karaokeCard.addView(karaokePastLine2);

        karaokeActiveLine = new TextView(this);
        karaokeActiveLine.setTextSize(28);
        karaokeActiveLine.setTextColor(0xFF22D3EE);
        karaokeActiveLine.setGravity(Gravity.CENTER);
        karaokeActiveLine.setPadding(0, dp(8), 0, dp(8));
        karaokeActiveLine.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        karaokeActiveLine.setLetterSpacing(-0.02f);
        karaokeCard.addView(karaokeActiveLine);

        karaokeNextLine1 = new TextView(this);
        karaokeNextLine1.setTextSize(18);
        karaokeNextLine1.setTextColor(0xFFF5F5F7);
        karaokeNextLine1.setAlpha(0.75f);
        karaokeNextLine1.setGravity(Gravity.CENTER);
        karaokeNextLine1.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        karaokeNextLine1.setPadding(0, dp(6), 0, dp(4));
        karaokeCard.addView(karaokeNextLine1);

        karaokeNextLine2 = new TextView(this);
        karaokeNextLine2.setTextSize(18);
        karaokeNextLine2.setTextColor(0xFFF5F5F7);
        karaokeNextLine2.setAlpha(0.50f);
        karaokeNextLine2.setGravity(Gravity.CENTER);
        karaokeNextLine2.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        karaokeNextLine2.setPadding(0, dp(4), 0, dp(4));
        karaokeCard.addView(karaokeNextLine2);

        c.addView(karaokeCard, new LinearLayout.LayoutParams(-1, -2));
        c.addView(gap(16));

        // 5. Boutons Flottants en Bas ("Enregistrer Micro & Live IA", "Générer paroles (IA)" & "Importer .LRC")
        LinearLayout floatingActions = vertical();
        floatingActions.setPadding(0, dp(8), 0, dp(4));

        // Bouton Principal Pleine Largeur : Enregistrer & Transcrire en direct (Micro IA)
        LinearLayout btnLiveMic = row();
        shape(btnLiveMic, 0x1A22D3EE, dp(16), 0x3322D3EE, true);
        btnLiveMic.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnLiveMic.setGravity(Gravity.CENTER);
        btnLiveMic.setOnClickListener(v -> openVoiceRecorderModal());

        ImageView micIc = new ImageView(this);
        micIc.setImageResource(R.drawable.ic_mic);
        micIc.setColorFilter(0xFF22D3EE); // Cyan #22D3EE
        btnLiveMic.addView(micIc, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView micTxt = new TextView(this);
        micTxt.setText(" Enregistrer & Transcrire en direct (Micro IA)");
        micTxt.setTextSize(13);
        micTxt.setTextColor(Color.WHITE);
        micTxt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        micTxt.setPadding(dp(8), 0, 0, 0);
        btnLiveMic.addView(micTxt);

        floatingActions.addView(btnLiveMic, new LinearLayout.LayoutParams(-1, dp(48)));
        floatingActions.addView(gap(10));

        LinearLayout actionsRow = row();

        // Bouton Gauche : Générer paroles (IA)
        LinearLayout btnGenAi = row();
        shape(btnGenAi, 0x10FFFFFF, dp(16), 0x14FFFFFF, true);
        btnGenAi.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnGenAi.setGravity(Gravity.CENTER);
        btnGenAi.setOnClickListener(v -> transcribeFromChat());

        ImageView aiWandIc = new ImageView(this);
        aiWandIc.setImageResource(R.drawable.ic_nav_ai);
        aiWandIc.setColorFilter(0xFFA855F7); // Violet #A855F7
        btnGenAi.addView(aiWandIc, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView aiWandTxt = new TextView(this);
        aiWandTxt.setText("Générer (Fichier)");
        aiWandTxt.setTextSize(13);
        aiWandTxt.setTextColor(Color.WHITE);
        aiWandTxt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        aiWandTxt.setPadding(dp(8), 0, 0, 0);
        btnGenAi.addView(aiWandTxt);

        actionsRow.addView(btnGenAi, new LinearLayout.LayoutParams(0, dp(48), 1));
        actionsRow.addView(gapW(10));

        // Bouton Droit : Importer .LRC
        LinearLayout btnImpLrc = row();
        shape(btnImpLrc, 0x08FFFFFF, dp(16), 0x1FFFFFFF, true);
        btnImpLrc.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnImpLrc.setGravity(Gravity.CENTER);
        btnImpLrc.setOnClickListener(v -> {
            pickPurpose = "lrc";
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                filePickerLauncher.launch(Intent.createChooser(intent, "Choisir un fichier .LRC"));
            } catch (Exception e1) {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    filePickerLauncher.launch(intent);
                } catch (Exception ignored) {}
            }
        });

        TextView impLrcTxt = new TextView(this);
        impLrcTxt.setText("Importer .LRC");
        impLrcTxt.setTextSize(13);
        impLrcTxt.setTextColor(0xB3FFFFFF); // 70% blanc
        impLrcTxt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        btnImpLrc.addView(impLrcTxt);

        actionsRow.addView(btnImpLrc, new LinearLayout.LayoutParams(-2, dp(48)));
        floatingActions.addView(actionsRow);
        floatingActions.addView(gap(8));

        TextView capTxt = new TextView(this);
        capTxt.setText("COMPONENT: GLASS FLOATING BUTTONS");
        capTxt.setTextSize(11);
        capTxt.setLetterSpacing(0.12f);
        capTxt.setTextColor(0x4DFFFFFF);
        capTxt.setGravity(Gravity.CENTER);
        floatingActions.addView(capTxt);

        c.addView(floatingActions);
        c.addView(gap(14));

        // 6. Section Outils Complémentaires (Rechercher LRCLIB, Exporter .LRC, Citation manuelle)
        LinearLayout extraCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        extraCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        extraCard.addView(section("OUTILS & SYNCHRONISATION"));
        extraCard.addView(gap(8));

        LinearLayout extraBtns0 = row();
        Button fsKaraokeBtn = button(" Plein Écran Karaoké", 0x2622D3EE, 0xFF22D3EE, 0x4D22D3EE);
        fsKaraokeBtn.setTextSize(12);
        fsKaraokeBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        fsKaraokeBtn.setOnClickListener(v -> openFullScreenLyrics());
        extraBtns0.addView(fsKaraokeBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button timelineBtn = button(" Synchro fine", 0x26F59E0B, 0xFFF59E0B, 0x4DF59E0B);
        timelineBtn.setTextSize(12);
        timelineBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        timelineBtn.setOnClickListener(v -> openLyricsTimelineEditor());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(40), 1);
        tlp.leftMargin = dp(8);
        extraBtns0.addView(timelineBtn, tlp);
        extraCard.addView(extraBtns0);
        extraCard.addView(gap(8));

        LinearLayout extraBtnsTranslate = row();
        Button translateLyricsBtn = button(" Traduire (Groq IA)", 0x26A855F7, 0xFFA855F7, 0x4DA855F7);
        translateLyricsBtn.setTextSize(12);
        translateLyricsBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        translateLyricsBtn.setOnClickListener(v -> openLyricsTranslationDialog());
        extraBtnsTranslate.addView(translateLyricsBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button exportLrcBtn = button("Exporter .LRC", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        exportLrcBtn.setTextSize(12);
        exportLrcBtn.setOnClickListener(v -> exportCurrentLrcFile());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(40), 1);
        elp.leftMargin = dp(8);
        extraBtnsTranslate.addView(exportLrcBtn, elp);
        extraCard.addView(extraBtnsTranslate);
        extraCard.addView(gap(8));

        LinearLayout extraBtns = row();
        Button searchLrcLibBtn = button("Rechercher LRCLIB", 0x1A22D3EE, 0xFF22D3EE, 0x3322D3EE);
        searchLrcLibBtn.setTextSize(12);
        searchLrcLibBtn.setOnClickListener(v -> searchLrcLib(true));
        extraBtns.addView(searchLrcLibBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button embedLyricsBtn = button("Intégrer à l'audio", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        embedLyricsBtn.setTextSize(12);
        embedLyricsBtn.setOnClickListener(v -> embedLyricsDirectlyToAudio());
        LinearLayout.LayoutParams emp = new LinearLayout.LayoutParams(0, dp(40), 1);
        emp.leftMargin = dp(8);
        extraBtns.addView(embedLyricsBtn, emp);
        extraCard.addView(extraBtns);
        extraCard.addView(gap(10));

        lyricsPreviewText = subtitle(lyricsList.isEmpty() ? "Aucune parole synchronisée chargée." : lyricsList.size() + " lignes synchronisées actives.");
        lyricsPreviewText.setPadding(dp(2), dp(2), dp(2), dp(2));
        extraCard.addView(lyricsPreviewText);
        extraCard.addView(gap(8));

        quoteInput = edit("Texte ou citation manuelle");
        quoteInput.setText(quote);
        extraCard.addView(quoteInput);
        watermarkInput = edit("Watermark (@pseudo)");
        watermarkInput.setText(watermark);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, dp(44));
        wp.topMargin = dp(8);
        extraCard.addView(watermarkInput, wp);

        quoteInput.setOnFocusChangeListener((z, f) -> {
            if (!f) {
                quote = quoteInput.getText().toString();
                saveSession();
                visualizerView.invalidate();
                updateKaraokeLinesView();
            }
        });
        watermarkInput.setOnFocusChangeListener((z, f) -> {
            if (!f) {
                watermark = watermarkInput.getText().toString();
                saveSession();
                visualizerView.invalidate();
            }
        });

        c.addView(extraCard);

        updateKaraokeLinesView();

        s.addView(c);
        page.addView(s);
        return page;
    }

    private LinearLayout createAudioPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();
        c.setPadding(dp(16), dp(12), dp(16), dp(28));

        // En-tête Audio
        LinearLayout aHead = row();
        aHead.setGravity(Gravity.CENTER_VERTICAL);
        aHead.addView(title("Audio", 16, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        
        FrameLayout headBtn1 = new FrameLayout(this);
        shape(headBtn1, 0x10FFFFFF, dp(16), 0x14FFFFFF, true);
        ImageView icHead1 = new ImageView(this);
        icHead1.setImageResource(R.drawable.ic_nav_audio);
        icHead1.setColorFilter(0xB3FFFFFF);
        headBtn1.addView(icHead1, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        headBtn1.setOnClickListener(v -> openAudioBrowser());
        aHead.addView(headBtn1, new LinearLayout.LayoutParams(dp(32), dp(32)));

        aHead.addView(gapW(8));

        FrameLayout headBtnMic = new FrameLayout(this);
        shape(headBtnMic, 0x1A22D3EE, dp(16), 0x3322D3EE, true);
        ImageView icHeadMic = new ImageView(this);
        icHeadMic.setImageResource(R.drawable.ic_mic);
        icHeadMic.setColorFilter(0xFF22D3EE);
        headBtnMic.addView(icHeadMic, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        headBtnMic.setOnClickListener(v -> openVoiceRecorderModal());
        aHead.addView(headBtnMic, new LinearLayout.LayoutParams(dp(32), dp(32)));

        aHead.addView(gapW(8));

        FrameLayout headBtnEditor = new FrameLayout(this);
        shape(headBtnEditor, 0x1AA855F7, dp(16), 0x33A855F7, true);
        ImageView icHeadEditor = new ImageView(this);
        icHeadEditor.setImageResource(R.drawable.ic_cut);
        icHeadEditor.setColorFilter(0xFFA855F7);
        headBtnEditor.addView(icHeadEditor, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        headBtnEditor.setOnClickListener(v -> openAudioEditor());
        aHead.addView(headBtnEditor, new LinearLayout.LayoutParams(dp(32), dp(32)));
        
        c.addView(aHead);
        c.addView(gap(16));

        // 1. Lecteur Central : Pochette (220x220), Métadonnées, Waveform Scrubbable & Contrôles
        LinearLayout playerSection = vertical();
        playerSection.setGravity(Gravity.CENTER_HORIZONTAL);

        // Pochette vinyle centrée (220dp x 220dp, rayon 28dp, lueur violet/cyan en arrière-plan)
        FrameLayout coverContainer = new FrameLayout(this);
        
        // Lueur arrière-plan
        View coverGlow = new View(this);
        shape(coverGlow, 0x33A855F7, dp(36), 0, false);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER);
        coverContainer.addView(coverGlow, glowLp);

        // Carte principale
        FrameLayout coverCard = new FrameLayout(this);
        shape(coverCard, 0xFF161618, dp(28), 0x14FFFFFF, false);
        coverCard.setClipToOutline(true);

        // Pochette réelle de l'album importé
        audioPageCoverImage = new ImageView(this);
        audioPageCoverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (currentAudioCoverBitmap != null) {
            audioPageCoverImage.setImageBitmap(currentAudioCoverBitmap);
            audioPageCoverImage.setVisibility(View.VISIBLE);
        } else {
            audioPageCoverImage.setVisibility(View.GONE);
        }
        coverCard.addView(audioPageCoverImage, new FrameLayout.LayoutParams(-1, -1));

        // Icône audio centrale (64dp, 20% blanc) fallback si pas de pochette
        ImageView diskIcon = new ImageView(this);
        diskIcon.setImageResource(R.drawable.ic_nav_audio);
        diskIcon.setColorFilter(0x33FFFFFF);
        this.audioPageDiskIcon = diskIcon;
        if (currentAudioCoverBitmap != null) {
            diskIcon.setVisibility(View.GONE);
        }
        coverCard.addView(diskIcon, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));

        // Mini barre de progression en bas de la pochette (h-[3px] bg-white/10 rounded-full)
        FrameLayout progressTrack = new FrameLayout(this);
        shape(progressTrack, 0x1AFFFFFF, dp(2), 0, false);
        audioCoverProgressBar = new View(this);
        audioCoverProgressBar.setBackgroundResource(R.drawable.bg_chip_active);
        progressTrack.addView(audioCoverProgressBar, new FrameLayout.LayoutParams(dp(4), -1));

        FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
        ptLp.leftMargin = dp(12);
        ptLp.rightMargin = dp(12);
        ptLp.bottomMargin = dp(12);
        coverCard.addView(progressTrack, ptLp);

        coverContainer.addView(coverCard, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
        playerSection.addView(coverContainer, new LinearLayout.LayoutParams(dp(236), dp(236)));
        playerSection.addView(gap(16));

        // Titre et métadonnées
        audioPageTrackTitle = title(audioFile != null ? (currentTrackTitle.isEmpty() ? currentFileName : currentTrackTitle) : "Midnight Signals", 16, Color.WHITE);
        audioPageTrackTitle.setGravity(Gravity.CENTER);
        playerSection.addView(audioPageTrackTitle);

        audioPageTrackArtist = subtitle(audioFile != null ? (currentTrackArtist.isEmpty() ? "Audio chargé" : currentTrackArtist) : "LØST · 3:42 · 320 kbps");
        audioPageTrackArtist.setGravity(Gravity.CENTER);
        audioPageTrackArtist.setTextSize(12);
        audioPageTrackArtist.setTextColor(0xFF9CA3AF);
        playerSection.addView(audioPageTrackArtist);
        playerSection.addView(gap(20));

        // Waveform Interactive Scrubber (h-[64px] rounded-[16px] bg-black/50 border border-white/[0.08] p-3)
        FrameLayout waveformCard = new FrameLayout(this);
        shape(waveformCard, 0x80000000, dp(16), 0x14FFFFFF, false);
        waveformCard.setPadding(dp(12), dp(10), dp(12), dp(10));

        audioWaveformView = new InteractiveWaveformView(this);
        waveformCard.addView(audioWaveformView, new FrameLayout.LayoutParams(-1, dp(44)));
        playerSection.addView(waveformCard, new LinearLayout.LayoutParams(-1, dp(64)));
        playerSection.addView(gap(6));

        // Ligne de temps sous la waveform : [01:24] [COMPONENT: WAVEFORM SCRUBBABLE] [03:42]
        LinearLayout timeRow = row();
        audioPageCurrentTime = new TextView(this);
        audioPageCurrentTime.setText(player != null ? formatDuration(player.getCurrentPosition()) : "01:24");
        audioPageCurrentTime.setTextSize(11);
        audioPageCurrentTime.setTextColor(0x66FFFFFF);
        timeRow.addView(audioPageCurrentTime, new LinearLayout.LayoutParams(0, -2, 1));

        TextView waveformTag = new TextView(this);
        waveformTag.setText("COMPONENT: WAVEFORM SCRUBBABLE");
        waveformTag.setTextSize(11);
        waveformTag.setTextColor(0x66FFFFFF);
        waveformTag.setLetterSpacing(0.08f);
        waveformTag.setGravity(Gravity.CENTER);
        timeRow.addView(waveformTag, new LinearLayout.LayoutParams(-2, -2));

        audioPageDurationTime = new TextView(this);
        audioPageDurationTime.setText(player != null && player.getDuration() > 0 ? formatDuration(player.getDuration()) : "03:42");
        audioPageDurationTime.setTextSize(11);
        audioPageDurationTime.setTextColor(0x66FFFFFF);
        audioPageDurationTime.setGravity(Gravity.END);
        timeRow.addView(audioPageDurationTime, new LinearLayout.LayoutParams(0, -2, 1));
        playerSection.addView(timeRow, new LinearLayout.LayoutParams(-1, -2));
        playerSection.addView(gap(22));

        // Contrôles de lecture (Playback Controls) : Retour 10s (48dp), Lecture (72dp gradient), Avance 10s (48dp)
        LinearLayout transport = row();
        transport.setGravity(Gravity.CENTER);

        // Bouton Retour 10s
        FrameLayout btnBack = new FrameLayout(this);
        shape(btnBack, 0x10FFFFFF, dp(24), 0x14FFFFFF, true);
        ImageView backIc = new ImageView(this);
        backIc.setImageResource(R.drawable.ic_skip_back);
        backIc.setColorFilter(0xB3FFFFFF);
        btnBack.addView(backIc, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btnBack.setOnClickListener(v -> {
            if (player != null) {
                int pos = Math.max(0, player.getCurrentPosition() - 10000);
                player.seekTo(pos);
                updateAudioPageUi();
                if (homeTrackTime != null) homeTrackTime.setText(formatDuration(pos) + " / " + formatDuration(player.getDuration()));
                updateKaraokeLinesView();
            }
        });
        transport.addView(btnBack, new LinearLayout.LayoutParams(dp(48), dp(48)));

        // Espace entre Retour et Play (32dp)
        transport.addView(gapW(32));

        // Bouton Lecture / Pause central (72dp x 72dp, Dégradé Cyan/Violet)
        FrameLayout btnPlayWrap = new FrameLayout(this);
        btnPlayWrap.setBackgroundResource(R.drawable.bg_chip_active);
        btnPlayWrap.setElevation(dp(8));
        audioPagePlayIcon = new ImageView(this);
        audioPagePlayIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        audioPagePlayIcon.setColorFilter(Color.WHITE);
        btnPlayWrap.addView(audioPagePlayIcon, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        btnPlayWrap.setOnClickListener(v -> {
            togglePlayback();
            updateAudioPageUi();
        });
        transport.addView(btnPlayWrap, new LinearLayout.LayoutParams(dp(72), dp(72)));

        // Espace entre Play et Avance (32dp)
        transport.addView(gapW(32));

        // Bouton Avance 10s
        FrameLayout btnFwd = new FrameLayout(this);
        shape(btnFwd, 0x10FFFFFF, dp(24), 0x14FFFFFF, true);
        ImageView fwdIc = new ImageView(this);
        fwdIc.setImageResource(R.drawable.ic_skip_forward);
        fwdIc.setColorFilter(0xB3FFFFFF);
        btnFwd.addView(fwdIc, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btnFwd.setOnClickListener(v -> {
            if (player != null) {
                int pos = Math.min(player.getDuration(), player.getCurrentPosition() + 10000);
                player.seekTo(pos);
                updateAudioPageUi();
                if (homeTrackTime != null) homeTrackTime.setText(formatDuration(pos) + " / " + formatDuration(player.getDuration()));
                updateKaraokeLinesView();
            }
        });
        transport.addView(btnFwd, new LinearLayout.LayoutParams(dp(48), dp(48)));

        playerSection.addView(transport);
        playerSection.addView(gap(16));

        // Badge BPM & Tonalité
        audioBpmKeyBadge = badge(" BPM & Tonalité en cours...", 0x1A22D3EE, 0xFF22D3EE, false);
        audioBpmKeyBadge.setPadding(dp(14), dp(6), dp(14), dp(6));
        playerSection.addView(audioBpmKeyBadge);
        playerSection.addView(gap(16));

        // Boutons Rapides : Éditeur Audio, Égaliseur FX & Mode Plein Écran
        LinearLayout quickAudioBtns = row();
        Button btnAudioEd = button("Éditeur & Découpe", 0x2210B981, 0xFF10B981, 0x4410B981);
        btnAudioEd.setTextSize(11);
        btnAudioEd.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnAudioEd.setOnClickListener(v -> openAudioEditor());
        quickAudioBtns.addView(btnAudioEd, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button btnAudioFx = button("Égaliseur FX", 0x22A855F7, 0xFFA855F7, 0x44A855F7);
        btnAudioFx.setTextSize(11);
        btnAudioFx.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnAudioFx.setOnClickListener(v -> openAudioEffectsDialog());
        LinearLayout.LayoutParams fxLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        fxLp.leftMargin = dp(6);
        quickAudioBtns.addView(btnAudioFx, fxLp);

        Button btnFsKaraoke = button("Plein Écran", 0x2222D3EE, 0xFF22D3EE, 0x4422D3EE);
        btnFsKaraoke.setTextSize(11);
        btnFsKaraoke.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnFsKaraoke.setOnClickListener(v -> openFullScreenLyrics());
        LinearLayout.LayoutParams fskLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        fskLp.leftMargin = dp(6);
        quickAudioBtns.addView(btnFsKaraoke, fskLp);

        playerSection.addView(quickAudioBtns, new LinearLayout.LayoutParams(-1, -2));

        c.addView(playerSection);
        c.addView(gap(28));

        // 2. Détection intelligente des silences & Zone Utile
        TextView detSectionHeader = new TextView(this);
        detSectionHeader.setText("DÉTECTION INTELLIGENTE");
        detSectionHeader.setTextSize(11);
        detSectionHeader.setLetterSpacing(0.12f);
        detSectionHeader.setTextColor(0xFF9CA3AF);
        detSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(detSectionHeader);
        c.addView(gap(10));

        LinearLayout ops = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        ops.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Badges "Utile" et "Silence"
        LinearLayout badgeRow = row();
        TextView badgeUtile = badge("Utile", 0x2622D3EE, 0xFF22D3EE, false);
        badgeRow.addView(badgeUtile);
        badgeRow.addView(gapW(8));
        TextView badgeSilence = badge("Silence", 0x0DFFFFFF, 0x66FFFFFF, false);
        badgeRow.addView(badgeSilence);
        ops.addView(badgeRow);
        ops.addView(gap(12));

        // Barre multi-segments de la timeline (h-8 rounded-full bg-black/40 p-1 gap-1)
        silenceTimelineContainer = new LinearLayout(this);
        silenceTimelineContainer.setOrientation(LinearLayout.HORIZONTAL);
        shape(silenceTimelineContainer, 0x66000000, dp(16), 0, false);
        silenceTimelineContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        ops.addView(silenceTimelineContainer, new LinearLayout.LayoutParams(-1, dp(32)));
        ops.addView(gap(8));

        silenceInfoText = new TextView(this);
        silenceInfoText.setText("3 silences détectés • auto-trim proposé");
        silenceInfoText.setTextSize(11);
        silenceInfoText.setTextColor(0x4DFFFFFF);
        ops.addView(silenceInfoText);
        ops.addView(gap(12));

        // Boutons d'action pour la détection
        LinearLayout r = row();
        detectButton = secondary("Détecter les silences");
        loopButton = secondary("Créer une boucle");
        r.addView(detectButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.leftMargin = dp(8);
        r.addView(loopButton, lp);
        ops.addView(r);
        detectButton.setOnClickListener(v -> detectSilence());
        loopButton.setOnClickListener(v -> askLoopDuration());

        c.addView(ops);
        c.addView(gap(16));

        // 3. Zone d'import de fichier (Dropzone dashed)
        LinearLayout pickCard = new LinearLayout(this);
        pickCard.setOrientation(LinearLayout.VERTICAL);
        pickCard.setBackgroundResource(R.drawable.bg_dropzone_dashed);
        pickCard.setGravity(Gravity.CENTER);
        pickCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        pickCard.setOnClickListener(v -> openAudioBrowser());

        FrameLayout iconCircle = new FrameLayout(this);
        shape(iconCircle, 0x10FFFFFF, dp(20), 0, false);
        ImageView pickIcon = new ImageView(this);
        pickIcon.setImageResource(R.drawable.ic_upload);
        pickIcon.setColorFilter(0x99FFFFFF);
        iconCircle.addView(pickIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        pickCard.addView(iconCircle, new LinearLayout.LayoutParams(dp(40), dp(40)));
        pickCard.addView(gap(8));

        TextView pickTitle = new TextView(this);
        pickTitle.setText("Gestionnaire & Explorateur Audio");
        pickTitle.setTextSize(13);
        pickTitle.setTextColor(0xB3FFFFFF);
        pickTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pickCard.addView(pickTitle);

        TextView pickSub = new TextView(this);
        pickSub.setText("Écouter et importer n'importe quel audio du téléphone");
        pickSub.setTextSize(11);
        pickSub.setTextColor(0x4DFFFFFF);
        pickCard.addView(pickSub);

        c.addView(pickCard);

        updateSilenceTimelineUi();
        updateAudioPageUi();

        s.addView(c);
        page.addView(s);
        return page;
    }

    private LinearLayout createAiPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();
        c.setPadding(dp(16), dp(12), dp(16), dp(28));

        // En-tête IA
        LinearLayout iaHead = row();
        iaHead.setGravity(Gravity.CENTER_VERTICAL);
        iaHead.addView(title("IA", 16, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout headBtn1 = new FrameLayout(this);
        shape(headBtn1, 0x10FFFFFF, dp(16), 0x14FFFFFF, true);
        ImageView icHead1 = new ImageView(this);
        icHead1.setImageResource(R.drawable.ic_nav_ai);
        icHead1.setColorFilter(0xB3FFFFFF);
        headBtn1.addView(icHead1, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        headBtn1.setOnClickListener(v -> testDirectGroqKey());
        iaHead.addView(headBtn1, new LinearLayout.LayoutParams(dp(32), dp(32)));
        c.addView(iaHead);
        c.addView(gap(16));

        // 1. Section Clé API Groq (Champ masqué avec Toggle Visibilité)
        TextView keySectionHeader = new TextView(this);
        keySectionHeader.setText("CONFIGURATION CLÉ API");
        keySectionHeader.setTextSize(11);
        keySectionHeader.setLetterSpacing(0.12f);
        keySectionHeader.setTextColor(0xFF9CA3AF);
        keySectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(keySectionHeader);
        c.addView(gap(10));

        LinearLayout keyCard = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        keyCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout keyHeaderRow = row();
        keyHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        keyHeaderRow.addView(title("Clé API Groq", 14, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        groqKeyBadge = badge("NON CONFIGURÉE", 0x0DFFFFFF, 0x66FFFFFF, false);
        keyHeaderRow.addView(groqKeyBadge);
        keyCard.addView(keyHeaderRow);
        keyCard.addView(gap(12));

        // Champ de saisie avec icône oeil de bascule à l'intérieur
        FrameLayout keyInputContainer = new FrameLayout(this);
        shape(keyInputContainer, 0x4D000000, dp(14), 0x1AFFFFFF, false);

        groqKeyInput = new EditText(this);
        groqKeyInput.setHint("Collez votre clé Groq (gsk_...)");
        groqKeyInput.setHintTextColor(0x4DFFFFFF);
        groqKeyInput.setTextColor(Color.WHITE);
        groqKeyInput.setTextSize(13);
        groqKeyInput.setBackground(null);
        groqKeyInput.setPadding(dp(14), dp(10), dp(44), dp(10));
        groqKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        groqKeyVisible = false;

        String savedKey = "";
        try {
            savedKey = KeyStoreUtil.decrypt(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GROQ, ""));
        } catch (Exception ignored) {}
        if (!savedKey.isEmpty()) {
            groqKeyInput.setText(savedKey);
        }
        keyInputContainer.addView(groqKeyInput, new FrameLayout.LayoutParams(-1, dp(44), Gravity.CENTER_VERTICAL));

        // Bouton Toggle Visibilité (icône oeil / oeil barré)
        FrameLayout toggleEyeBtn = new FrameLayout(this);
        shape(toggleEyeBtn, 0, dp(16), 0, true);
        groqKeyToggleIcon = new ImageView(this);
        groqKeyToggleIcon.setImageResource(R.drawable.ic_eye);
        groqKeyToggleIcon.setColorFilter(0x80FFFFFF);
        toggleEyeBtn.addView(groqKeyToggleIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        toggleEyeBtn.setOnClickListener(v -> {
            groqKeyVisible = !groqKeyVisible;
            int sel = groqKeyInput.getSelectionEnd();
            if (groqKeyVisible) {
                groqKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                groqKeyToggleIcon.setImageResource(R.drawable.ic_eye_off);
                groqKeyToggleIcon.setColorFilter(0xFF22D3EE);
            } else {
                groqKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                groqKeyToggleIcon.setImageResource(R.drawable.ic_eye);
                groqKeyToggleIcon.setColorFilter(0x80FFFFFF);
            }
            if (sel >= 0) groqKeyInput.setSelection(Math.min(sel, groqKeyInput.getText().length()));
        });
        keyInputContainer.addView(toggleEyeBtn, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        keyCard.addView(keyInputContainer, new LinearLayout.LayoutParams(-1, dp(44)));
        keyCard.addView(gap(12));

        // Rangée Boutons Sauvegarder + Test
        LinearLayout keyActionsRow = row();
        Button saveKeyBtn = accent("Sauvegarder");
        saveKeyBtn.setOnClickListener(v -> saveDirectGroqKey());
        keyActionsRow.addView(saveKeyBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button testKeyBtn = secondary("Tester la clé");
        testKeyBtn.setOnClickListener(v -> testDirectGroqKey());
        LinearLayout.LayoutParams tkLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        tkLp.leftMargin = dp(8);
        keyActionsRow.addView(testKeyBtn, tkLp);
        keyCard.addView(keyActionsRow);
        keyCard.addView(gap(8));

        aiStatus = subtitle(savedKey.isEmpty() ? "Clé non configurée • Requise pour Whisper & Citations" : "Clé Groq active • Whisper & Citations opérationnels");
        aiStatus.setTextSize(11);
        aiStatus.setTextColor(0x66FFFFFF);
        keyCard.addView(aiStatus);

        c.addView(keyCard);
        c.addView(gap(20));

        // 2. Section Fonctions IA
        TextView fnSectionHeader = new TextView(this);
        fnSectionHeader.setText("FONCTIONS IA");
        fnSectionHeader.setTextSize(11);
        fnSectionHeader.setLetterSpacing(0.12f);
        fnSectionHeader.setTextColor(0xFF9CA3AF);
        fnSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(fnSectionHeader);
        c.addView(gap(10));

        // Carte 1 : Transcription Whisper
        LinearLayout cardWhisper = card(0x0AFFFFFF, 0x14FFFFFF, 22);
        cardWhisper.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout cwHead = row();
        cwHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout cwIconWrap = new FrameLayout(this);
        cwIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView cwIcon = new ImageView(this);
        cwIcon.setImageResource(R.drawable.ic_nav_ai);
        cwIcon.setColorFilter(Color.WHITE);
        cwIconWrap.addView(cwIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        cwHead.addView(cwIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout cwTexts = vertical();
        cwTexts.setPadding(dp(12), 0, dp(8), 0);
        cwTexts.addView(title("Transcription", 14, Color.WHITE));
        aiTranscriptionDesc = subtitle("Paroles auto via Whisper");
        aiTranscriptionDesc.setTextSize(12);
        cwTexts.addView(aiTranscriptionDesc);
        cwHead.addView(cwTexts, new LinearLayout.LayoutParams(0, -2, 1));

        aiTranscriptionBadge = badge("Inactif", 0x0DFFFFFF, 0x80FFFFFF, false);
        cwHead.addView(aiTranscriptionBadge);
        cardWhisper.addView(cwHead);
        cardWhisper.addView(gap(14));

        aiTranscribeActionBtn = accent("Lancer la transcription");
        transcribeButton = aiTranscribeActionBtn;
        aiTranscribeActionBtn.setOnClickListener(v -> transcribeFromChat());
        cardWhisper.addView(aiTranscribeActionBtn, new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(cardWhisper);
        c.addView(gap(12));

        // Carte 1bis : Enregistreur Vocal & Live IA (Whisper)
        LinearLayout cardRec = card(0x0AFFFFFF, 0x14FFFFFF, 22);
        cardRec.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout crHead = row();
        crHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crIconWrap = new FrameLayout(this);
        crIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView crIcon = new ImageView(this);
        crIcon.setImageResource(R.drawable.ic_mic);
        crIcon.setColorFilter(0xFF22D3EE);
        crIconWrap.addView(crIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        crHead.addView(crIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout crTexts = vertical();
        crTexts.setPadding(dp(12), 0, dp(8), 0);
        crTexts.addView(title("Enregistreur Micro & Live IA", 14, Color.WHITE));
        TextView crDesc = subtitle("Enregistre ta voix & transcris avec Whisper");
        crDesc.setTextSize(12);
        crTexts.addView(crDesc);
        crHead.addView(crTexts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView crBadge = badge("Live Micro", 0x1A22D3EE, 0xFF22D3EE, false);
        crHead.addView(crBadge);
        cardRec.addView(crHead);
        cardRec.addView(gap(14));

        Button crActionBtn = button(" Enregistrer & Transcrire en direct", 0x2222D3EE, 0xFF22D3EE, 0x4422D3EE);
        crActionBtn.setTextSize(13);
        crActionBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        crActionBtn.setOnClickListener(v -> openVoiceRecorderModal());
        cardRec.addView(crActionBtn, new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(cardRec);
        c.addView(gap(12));

        // Carte 2 : Génération de Citation
        LinearLayout cardQuote = card(0x0AFFFFFF, 0x14FFFFFF, 22);
        cardQuote.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout cqHead = row();
        cqHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout cqIconWrap = new FrameLayout(this);
        cqIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView cqIcon = new ImageView(this);
        cqIcon.setImageResource(R.drawable.ic_nav_lyrics);
        cqIcon.setColorFilter(Color.WHITE);
        cqIconWrap.addView(cqIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        cqHead.addView(cqIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout cqTexts = vertical();
        cqTexts.setPadding(dp(12), 0, dp(8), 0);
        cqTexts.addView(title("Génération de citation", 14, Color.WHITE));
        aiQuoteDesc = subtitle("Génère hook viral depuis lyrics");
        aiQuoteDesc.setTextSize(12);
        cqTexts.addView(aiQuoteDesc);
        cqHead.addView(cqTexts, new LinearLayout.LayoutParams(0, -2, 1));

        aiQuoteBadge = badge("Inactif", 0x0DFFFFFF, 0x80FFFFFF, false);
        cqHead.addView(aiQuoteBadge);
        cardQuote.addView(cqHead);
        cardQuote.addView(gap(14));

        aiQuoteActionBtn = accent("Générer une citation");
        generateQuoteButton = aiQuoteActionBtn;
        aiQuoteActionBtn.setOnClickListener(v -> generateQuoteDialog());
        cardQuote.addView(aiQuoteActionBtn, new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(cardQuote);
        c.addView(gap(12));

        // Carte 3 : Traduction Multilingue IA
        LinearLayout cardTrans = card(0x0AFFFFFF, 0x14FFFFFF, 22);
        cardTrans.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout ctHead = row();
        ctHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ctIconWrap = new FrameLayout(this);
        ctIconWrap.setBackgroundResource(R.drawable.bg_chip_active);
        ImageView ctIcon = new ImageView(this);
        ctIcon.setImageResource(R.drawable.ic_nav_lyrics);
        ctIcon.setColorFilter(0xFFA855F7);
        ctIconWrap.addView(ctIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        ctHead.addView(ctIconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout ctTexts = vertical();
        ctTexts.setPadding(dp(12), 0, dp(8), 0);
        ctTexts.addView(title("Traduction Multilingue IA", 14, Color.WHITE));
        TextView ctDesc = subtitle("Traduis les paroles en gardant le tempo LRC");
        ctDesc.setTextSize(12);
        ctTexts.addView(ctDesc);
        ctHead.addView(ctTexts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView ctBadge = badge("Groq LLaMA-3", 0x22A855F7, 0xFFA855F7, false);
        ctHead.addView(ctBadge);
        cardTrans.addView(ctHead);
        cardTrans.addView(gap(14));

        Button ctActionBtn = button(" Choisir la langue & Traduire", 0x22A855F7, 0xFFA855F7, 0x44A855F7);
        ctActionBtn.setTextSize(13);
        ctActionBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        ctActionBtn.setOnClickListener(v -> openLyricsTranslationDialog());
        cardTrans.addView(ctActionBtn, new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(cardTrans);
        c.addView(gap(20));

        // 3. Zone de Résultat IA
        TextView resSectionHeader = new TextView(this);
        resSectionHeader.setText("RÉSULTAT IA");
        resSectionHeader.setTextSize(11);
        resSectionHeader.setLetterSpacing(0.12f);
        resSectionHeader.setTextColor(0xFF9CA3AF);
        resSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(resSectionHeader);
        c.addView(gap(10));

        aiResultCard = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        aiResultCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout resHead = row();
        resHead.setGravity(Gravity.CENTER_VERTICAL);
        aiResultTypeBadge = badge("EN ATTENTE", 0x0DFFFFFF, 0x80FFFFFF, false);
        resHead.addView(aiResultTypeBadge, new LinearLayout.LayoutParams(0, -2, 1));

        // Bouton Copier
        aiResultCopyBtn = new FrameLayout(this);
        shape(aiResultCopyBtn, 0x10FFFFFF, dp(16), 0x14FFFFFF, true);
        ImageView copyIc = new ImageView(this);
        copyIc.setImageResource(R.drawable.ic_copy);
        copyIc.setColorFilter(0xB3FFFFFF);
        aiResultCopyBtn.addView(copyIc, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        aiResultCopyBtn.setOnClickListener(v -> copyAiResult());
        aiResultCopyBtn.setVisibility(View.GONE);
        resHead.addView(aiResultCopyBtn, new LinearLayout.LayoutParams(dp(32), dp(32)));

        // Bouton Appliquer au studio
        aiResultApplyBtn = new FrameLayout(this);
        shape(aiResultApplyBtn, 0x2622D3EE, dp(16), 0x4D22D3EE, true);
        ImageView applyIc = new ImageView(this);
        applyIc.setImageResource(R.drawable.ic_check);
        applyIc.setColorFilter(0xFF22D3EE);
        aiResultApplyBtn.addView(applyIc, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        aiResultApplyBtn.setOnClickListener(v -> applyAiResultToStudio());
        aiResultApplyBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        apLp.leftMargin = dp(8);
        resHead.addView(aiResultApplyBtn, apLp);

        aiResultCard.addView(resHead);
        aiResultCard.addView(gap(12));

        FrameLayout resBox = new FrameLayout(this);
        shape(resBox, 0x4D000000, dp(14), 0x14FFFFFF, false);
        resBox.setPadding(dp(14), dp(14), dp(14), dp(14));

        aiResultText = new TextView(this);
        aiResultText.setText("Aucun résultat généré pour l'instant. Choisissez une fonction IA ci-dessus pour démarrer.");
        aiResultText.setTextSize(13);
        aiResultText.setTextColor(0x66FFFFFF);
        aiResultText.setLineSpacing(dp(2), 1.2f);
        aiResultText.setTextIsSelectable(true);
        resBox.addView(aiResultText, new FrameLayout.LayoutParams(-1, -2));
        aiResultCard.addView(resBox);

        c.addView(aiResultCard);
        c.addView(gap(20));

        // 4. Section Assistant & Chat
        LinearLayout chatSectionHeaderRow = row();
        chatSectionHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView chatSectionHeader = new TextView(this);
        chatSectionHeader.setText("ASSISTANT & CONVERSATION");
        chatSectionHeader.setTextSize(11);
        chatSectionHeader.setLetterSpacing(0.12f);
        chatSectionHeader.setTextColor(0xFF9CA3AF);
        chatSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        chatSectionHeaderRow.addView(chatSectionHeader, new LinearLayout.LayoutParams(0, -2, 1));

        Button btnClearChat = button("Effacer", 0x14FFFFFF, 0xFF9CA3AF, 0x22FFFFFF);
        btnClearChat.setTextSize(11);
        btnClearChat.setPadding(dp(8), dp(2), dp(8), dp(2));
        btnClearChat.setOnClickListener(v -> {
            if (chatMessagesContainer != null) {
                chatMessagesContainer.removeAllViews();
                lastAiMessageView = null;
                setupInitialChat();
                Toast.makeText(MainActivity.this, "Historique de discussion réinitialisé.", Toast.LENGTH_SHORT).show();
            }
        });
        chatSectionHeaderRow.addView(btnClearChat, new LinearLayout.LayoutParams(-2, dp(28)));

        c.addView(chatSectionHeaderRow);
        c.addView(gap(10));

        LinearLayout chatCard = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        chatCard.setPadding(dp(12), dp(12), dp(12), dp(12));

        chatScrollView = new ScrollView(this);
        chatScrollView.setFillViewport(true);
        chatScrollView.setVerticalScrollBarEnabled(true);
        chatScrollView.setScrollbarFadingEnabled(false);
        chatScrollView.setNestedScrollingEnabled(true);

        // Déverrouillage du défilement tactile dans le ScrollView parent
        chatScrollView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP ||
                (event.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        chatMessagesContainer = vertical();
        chatMessagesContainer.setPadding(dp(4), dp(4), dp(4), dp(6));
        chatScrollView.addView(chatMessagesContainer, new FrameLayout.LayoutParams(-1, -2));
        chatCard.addView(chatScrollView, new LinearLayout.LayoutParams(-1, dp(260)));
        chatCard.addView(gap(8));

        LinearLayout inputDock = row();
        inputDock.setGravity(Gravity.CENTER_VERTICAL);
        inputDock.setPadding(dp(8), dp(4), dp(8), dp(4));
        shape(inputDock, 0x66000000, dp(18), 0x1AFFFFFF, false);

        chatInputField = edit("Demandez des paroles, un style, une citation…");
        chatInputField.setBackground(null);
        chatInputField.setPadding(dp(10), dp(8), dp(10), dp(8));
        chatInputField.setTextSize(13);
        inputDock.addView(chatInputField, new LinearLayout.LayoutParams(0, dp(40), 1));

        chatSendButton = accent("");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(40), dp(40));
        sp.leftMargin = dp(6);
        chatSendButton.setLayoutParams(sp);
        chatSendButton.setTextSize(14);
        chatSendButton.setOnClickListener(v -> {
            String text = chatInputField.getText().toString().trim();
            if (!text.isEmpty()) {
                onUserSendChatMessage(text);
            }
        });
        inputDock.addView(chatSendButton);
        chatCard.addView(inputDock);

        c.addView(chatCard);

        s.addView(c);
        page.addView(s);

        setupInitialChat();
        updateAiUiStates();
        return page;
    }

    private LinearLayout createExportPage() {
        LinearLayout page = page();
        ScrollView s = scroll();
        LinearLayout c = vertical();
        c.setPadding(dp(16), dp(12), dp(16), dp(28));

        // En-tête Export
        LinearLayout exHead = row();
        exHead.setGravity(Gravity.CENTER_VERTICAL);
        exHead.addView(title("Export", 16, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        exHead.addView(badge("PRO • GPU H.264", 0x1F22D3EE, 0xFF22D3EE, false));
        c.addView(exHead);
        c.addView(gap(16));

        // 1. Section Formats & Ratios (9:16, 1:1, 16:9)
        TextView formatSectionHeader = new TextView(this);
        formatSectionHeader.setText("FORMAT & RATIO");
        formatSectionHeader.setTextSize(11);
        formatSectionHeader.setLetterSpacing(0.12f);
        formatSectionHeader.setTextColor(0xFF9CA3AF);
        formatSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(formatSectionHeader);
        c.addView(gap(10));

        String[] formatRatios = {"9:16", "1:1", "16:9"};
        String[] formatTitlesArr = {
                "Vertical (1080×1920)",
                "Carré (1080×1080)",
                "Horizontal (1920×1080)"
        };
        String[] formatSubsArr = {
                "Reels • TikTok • Shorts • Stories",
                "Feed Instagram • Post carré",
                "YouTube • Grand Écran • TV"
        };
        int[] formatIcons = {
                R.drawable.ic_format_vertical,
                R.drawable.ic_format_square,
                R.drawable.ic_format_horizontal
        };
        int[][] dims = {
                {1080, 1920},
                {1080, 1080},
                {1920, 1080}
        };

        formatCards.clear();
        formatTitles.clear();
        formatSubtitles.clear();
        formatRatioBadges.clear();
        formatCheckIcons.clear();
        formatAspectIcons.clear();
        formatButtons.clear();

        for (int i = 0; i < formatRatios.length; i++) {
            final int x = i;
            boolean sel = (dims[x][0] == formatW && dims[x][1] == formatH);

            LinearLayout fCard = row();
            fCard.setGravity(Gravity.CENTER_VERTICAL);
            fCard.setPadding(dp(14), dp(12), dp(14), dp(12));
            if (sel) {
                fCard.setBackgroundResource(R.drawable.bg_style_card_selected);
            } else {
                fCard.setBackgroundResource(R.drawable.bg_style_card_unselected);
            }

            // Icône aspect format
            FrameLayout iconWrap = new FrameLayout(this);
            shape(iconWrap, 0x14FFFFFF, dp(12), 0x1AFFFFFF, false);
            ImageView ic = new ImageView(this);
            ic.setImageResource(formatIcons[i]);
            ic.setColorFilter(sel ? Color.WHITE : 0x80FFFFFF);
            iconWrap.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            fCard.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));
            formatAspectIcons.add(ic);

            // Textes du format
            LinearLayout fTexts = vertical();
            fTexts.setPadding(dp(12), 0, dp(8), 0);

            LinearLayout fTopRow = row();
            fTopRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView fRatio = badge(formatRatios[i], sel ? 0x3322D3EE : 0x0DFFFFFF, sel ? 0xFF22D3EE : 0x80FFFFFF, false);
            fTopRow.addView(fRatio);

            TextView fTitle = new TextView(this);
            fTitle.setText("  " + formatTitlesArr[i]);
            fTitle.setTextSize(13);
            fTitle.setTextColor(sel ? Color.WHITE : 0xCCFFFFFF);
            fTitle.setTypeface(Typeface.create("sans-serif", sel ? Typeface.BOLD : Typeface.NORMAL));
            fTopRow.addView(fTitle);
            fTexts.addView(fTopRow);

            TextView fSub = new TextView(this);
            fSub.setText(formatSubsArr[i]);
            fSub.setTextSize(11);
            fSub.setTextColor(0x80FFFFFF);
            fSub.setPadding(0, dp(3), 0, 0);
            fTexts.addView(fSub);

            formatTitles.add(fTitle);
            formatSubtitles.add(fSub);
            formatRatioBadges.add(fRatio);

            fCard.addView(fTexts, new LinearLayout.LayoutParams(0, -2, 1));

            // Indicateur de sélection Checkmark
            FrameLayout checkWrap = new FrameLayout(this);
            shape(checkWrap, 0x0DFFFFFF, dp(14), 0x1AFFFFFF, false);
            ImageView checkIc = new ImageView(this);
            if (sel) {
                checkIc.setImageResource(R.drawable.ic_check);
                checkIc.setColorFilter(0xFF22D3EE);
            }
            checkWrap.addView(checkIc, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
            fCard.addView(checkWrap, new LinearLayout.LayoutParams(dp(28), dp(28)));
            formatCheckIcons.add(checkIc);

            formatCards.add(fCard);
            fCard.setOnClickListener(v -> updateFormatSelection(dims[x][0], dims[x][1]));

            c.addView(fCard);
            if (i < formatRatios.length - 1) c.addView(gap(8));
        }
        c.addView(gap(18));

        // 2. Section Fréquence d'images (FPS)
        TextView fpsSectionHeader = new TextView(this);
        fpsSectionHeader.setText("FRÉQUENCE D'IMAGES (FPS)");
        fpsSectionHeader.setTextSize(11);
        fpsSectionHeader.setLetterSpacing(0.12f);
        fpsSectionHeader.setTextColor(0xFF9CA3AF);
        fpsSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(fpsSectionHeader);
        c.addView(gap(10));

        LinearLayout fpsTrack = row();
        fpsTrack.setPadding(dp(4), dp(4), dp(4), dp(4));
        shape(fpsTrack, 0x4D000000, dp(16), 0x1AFFFFFF, false);

        // 30 FPS Container
        fps30Container = new FrameLayout(this);
        if (exportFps == 30) {
            fps30Container.setBackgroundResource(R.drawable.btn_gradient);
        } else {
            fps30Container.setBackground(null);
        }
        shape(fps30Container, exportFps == 30 ? 0 : 0, dp(12), 0, true);

        LinearLayout fps30Content = vertical();
        fps30Content.setGravity(Gravity.CENTER);
        fps30Content.setPadding(dp(8), dp(8), dp(8), dp(8));
        fps30Title = new TextView(this);
        fps30Title.setText("30 FPS");
        fps30Title.setTextSize(13);
        fps30Title.setTextColor(exportFps == 30 ? Color.WHITE : 0x80FFFFFF);
        fps30Title.setTypeface(Typeface.create("sans-serif", exportFps == 30 ? Typeface.BOLD : Typeface.NORMAL));
        fps30Content.addView(fps30Title);

        fps30Subtitle = new TextView(this);
        fps30Subtitle.setText("Standard • Rendu rapide");
        fps30Subtitle.setTextSize(10);
        fps30Subtitle.setTextColor(exportFps == 30 ? 0xE6FFFFFF : 0x4DFFFFFF);
        fps30Content.addView(fps30Subtitle);
        fps30Container.addView(fps30Content, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        fps30Container.setOnClickListener(v -> updateFpsSelection(30));
        fpsTrack.addView(fps30Container, new LinearLayout.LayoutParams(0, dp(48), 1));

        // 60 FPS Container
        fps60Container = new FrameLayout(this);
        if (exportFps == 60) {
            fps60Container.setBackgroundResource(R.drawable.btn_gradient);
        } else {
            fps60Container.setBackground(null);
        }
        shape(fps60Container, exportFps == 60 ? 0 : 0, dp(12), 0, true);

        LinearLayout fps60Content = vertical();
        fps60Content.setGravity(Gravity.CENTER);
        fps60Content.setPadding(dp(8), dp(8), dp(8), dp(8));
        fps60Title = new TextView(this);
        fps60Title.setText("60 FPS");
        fps60Title.setTextSize(13);
        fps60Title.setTextColor(exportFps == 60 ? Color.WHITE : 0x80FFFFFF);
        fps60Title.setTypeface(Typeface.create("sans-serif", exportFps == 60 ? Typeface.BOLD : Typeface.NORMAL));
        fps60Content.addView(fps60Title);

        fps60Subtitle = new TextView(this);
        fps60Subtitle.setText("Ultra Fluide • Qualité Pro");
        fps60Subtitle.setTextSize(10);
        fps60Subtitle.setTextColor(exportFps == 60 ? 0xE6FFFFFF : 0x4DFFFFFF);
        fps60Content.addView(fps60Subtitle);
        fps60Container.addView(fps60Content, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        fps60Container.setOnClickListener(v -> updateFpsSelection(60));
        fpsTrack.addView(fps60Container, new LinearLayout.LayoutParams(0, dp(48), 1));

        c.addView(fpsTrack);
        c.addView(gap(18));

        LinearLayout exportOptionsCard = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        exportOptionsCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        exportOptionsCard.addView(section("ÉLÉMENTS À EXPORTER"));
        exportOptionsCard.addView(gap(8));
        LinearLayout exportOptionsRow = row();
        exportVisualizerToggleButton = button(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        exportVisualizerToggleButton.setAllCaps(false);
        exportVisualizerToggleButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav_visual, 0, 0, 0);
        exportVisualizerToggleButton.setCompoundDrawablePadding(dp(8));
        exportVisualizerToggleButton.setOnClickListener(v -> {
            exportIncludeVisualizer = !exportIncludeVisualizer;
            exportVisualizerToggleButton.setText(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non");
            exportVisualizerToggleButton.setTextColor(exportIncludeVisualizer ? Color.WHITE : 0xFF9CA3AF);
            saveSession();
        });
        exportOptionsRow.addView(exportVisualizerToggleButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        exportLyricsToggleButton = button(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non", 0x14FFFFFF, 0xFFE2E3EA, 0x26FFFFFF);
        exportLyricsToggleButton.setAllCaps(false);
        exportLyricsToggleButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nav_lyrics, 0, 0, 0);
        exportLyricsToggleButton.setCompoundDrawablePadding(dp(8));
        exportLyricsToggleButton.setOnClickListener(v -> {
            exportIncludeLyrics = !exportIncludeLyrics;
            exportLyricsToggleButton.setText(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non");
            exportLyricsToggleButton.setTextColor(exportIncludeLyrics ? Color.WHITE : 0xFF9CA3AF);
            saveSession();
        });
        LinearLayout.LayoutParams lyrToggleLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        lyrToggleLp.leftMargin = dp(8);
        exportOptionsRow.addView(exportLyricsToggleButton, lyrToggleLp);
        exportOptionsCard.addView(exportOptionsRow);
        c.addView(exportOptionsCard);
        c.addView(gap(16));

        // 3. Section Rendu & Progression
        TextView renderSectionHeader = new TextView(this);
        renderSectionHeader.setText("RENDU & EXPORTATION");
        renderSectionHeader.setTextSize(11);
        renderSectionHeader.setLetterSpacing(0.12f);
        renderSectionHeader.setTextColor(0xFF9CA3AF);
        renderSectionHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        c.addView(renderSectionHeader);
        c.addView(gap(10));

        LinearLayout renderCard = card(0x0AFFFFFF, 0x14FFFFFF, 24);
        renderCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        // En-tête Statut
        LinearLayout rhRow = row();
        rhRow.setGravity(Gravity.CENTER_VERTICAL);
        rhRow.addView(title("Statut du rendu", 14, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        exportStateBadge = badge("PRÊT", 0x0DFFFFFF, 0x80FFFFFF, false);
        rhRow.addView(exportStateBadge);
        renderCard.addView(rhRow);
        renderCard.addView(gap(12));

        // Barre de progression d'exportation
        exportProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        exportProgressBar.setMax(100);
        exportProgressBar.setProgress(0);
        exportProgressBar.setProgressTintList(ColorStateList.valueOf(0xFF22D3EE));
        exportProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x26FFFFFF));
        renderCard.addView(exportProgressBar, new LinearLayout.LayoutParams(-1, dp(8)));
        renderCard.addView(gap(8));

        // Ligne de statistiques & ETA
        LinearLayout statsRow = row();
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        exportPercentText = new TextView(this);
        exportPercentText.setText("0%");
        exportPercentText.setTextSize(12);
        exportPercentText.setTextColor(Color.WHITE);
        exportPercentText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        statsRow.addView(exportPercentText);

        exportSummaryTag = new TextView(this);
        exportSummaryTag.setText(" • 9:16 (1080×1920) • 30 FPS");
        exportSummaryTag.setTextSize(11);
        exportSummaryTag.setTextColor(0x80FFFFFF);
        statsRow.addView(exportSummaryTag, new LinearLayout.LayoutParams(0, -2, 1));

        exportEtaText = new TextView(this);
        exportEtaText.setText("Prêt");
        exportEtaText.setTextSize(11);
        exportEtaText.setTextColor(0xFF22D3EE);
        statsRow.addView(exportEtaText);
        renderCard.addView(statsRow);
        renderCard.addView(gap(12));

        // Message de détail d'état
        exportDetailInfoText = subtitle("Prêt pour l'export. Appuyez sur le bouton ci-dessous pour démarrer l'encodage matériel.");
        exportDetailInfoText.setTextSize(12);
        exportDetailInfoText.setTextColor(0x80FFFFFF);
        exportStatusInfo = exportDetailInfoText;
        renderCard.addView(exportDetailInfoText);
        renderCard.addView(gap(16));

        // Bouton Final Principal
        exportButton = accent("⇈ Exporter la vidéo MP4");
        exportButton.setOnClickListener(v -> startOrStopExport());
        renderCard.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(50)));
        renderCard.addView(gap(10));

        // Bouton secondaire : Capture Image PNG Cover
        snapshotButton = secondary("Capturer image PNG (Cover)");
        snapshotButton.setOnClickListener(v -> snapshot());
        renderCard.addView(snapshotButton, new LinearLayout.LayoutParams(-1, dp(42)));

        c.addView(renderCard);
        c.addView(gap(18));

        // 4. Dossier de Sauvegarde
        LinearLayout note = card(0x0AFFFFFF, 0x14FFFFFF, 20);
        note.setPadding(dp(16), dp(14), dp(16), dp(14));
        note.addView(section("DESTINATION DES FICHIERS"));
        note.addView(gap(6));
        note.addView(subtitle("• Vidéos MP4 → Galerie / Dossier Films/StudioPro\n• Images Cover → Galerie / Dossier Images/StudioPro\n• Paroles LRC → Documents/StudioPro"));
        c.addView(note);

        s.addView(c);
        page.addView(s);

        updateExportSummaryText();
        return page;
    }

    private void showPage(int i) {
        for (int x = 0; x < pages.size(); x++) pages.get(x).setVisibility(x == i ? View.VISIBLE : View.GONE);
        for (int x = 0; x < navTabs.length; x++) {
            boolean active = (x == i);
            if (active) {
                navTabs[x].setBackgroundResource(R.drawable.btn_gradient);
                navIcons[x].setColorFilter(Color.WHITE);
                navLabels[x].setTextColor(Color.WHITE);
                navLabels[x].setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            } else {
                navTabs[x].setBackground(null);
                navIcons[x].setColorFilter(0x66FFFFFF); // 40% blanc
                navLabels[x].setTextColor(0x66FFFFFF);
                navLabels[x].setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
        }
        if (i == 4) {
            updateAiUiStates();
        } else if (i == 3) {
            updateKaraokeLinesView();
        } else if (i == 5) {
            updateFormatSelection(formatW, formatH);
            updateFpsSelection(exportFps);
            if (exportVisualizerToggleButton != null) {
                exportVisualizerToggleButton.setText(exportIncludeVisualizer ? "Visualiseur : Oui" : "Visualiseur : Non");
                exportVisualizerToggleButton.setTextColor(exportIncludeVisualizer ? Color.WHITE : 0xFF9CA3AF);
            }
            if (exportLyricsToggleButton != null) {
                exportLyricsToggleButton.setText(exportIncludeLyrics ? "Paroles : Oui" : "Paroles : Non");
                exportLyricsToggleButton.setTextColor(exportIncludeLyrics ? Color.WHITE : 0xFF9CA3AF);
            }
        }
    }

    private LinearLayout page() { LinearLayout l = vertical(); l.setPadding(0, 0, 0, dp(8)); return l; }
    private ScrollView scroll() { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setVerticalScrollBarEnabled(false); return s; }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout card(int color, int stroke, int radius) { LinearLayout l = vertical(); l.setPadding(dp(14), dp(14), dp(14), dp(14)); shape(l, color, dp(radius), stroke); return l; }
    private TextView section(String s) { return title(s, 11, 0xFF00E5FF); }
    private TextView sectionIcon(String icon, String s) { TextView t = title(icon + "  " + s, 15, 0xFFD9D6FF); t.setPadding(0, 0, 0, dp(8)); return t; }
    private TextView fieldLabel(String s) { TextView t = subtitle(s); t.setTextColor(0xFFBDC1CC); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(0, dp(8), 0, dp(4)); return t; }
    private TextView title(String s, int size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t; }
    private TextView subtitle(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(12); t.setTextColor(0xFF858A98); return t; }
    private TextView badge(String s, int bg, int tc, boolean gold) { TextView t = title(s, 11, tc); t.setGravity(Gravity.CENTER); t.setPadding(dp(10), dp(7), dp(10), dp(7)); shape(t, bg, dp(18), gold ? 0xFFFFD700 : 0xFF00E5FF); return t; }
    private Button navButton(String s) { Button b = button(s, 0xFF171A20, 0xFFC9CBD3, 0xFF343844); b.setTextSize(10); return b; }
    private Button styleButton(String s, boolean active) { return button(s, active ? 0xFF3D237A : 0xFF1A1E29, active ? 0xFFFFFFFF : 0xFFD8DBE5, active ? 0xFF9E7BFF : 0xFF383E50); }
    private Button roundAction(String s, boolean active) { Button b = button(s, active ? 0xFF00E5FF : 0xFF4A5168, active ? 0xFF060B12 : Color.WHITE, active ? 0xFF00E5FF : 0xFF5D657E); b.setTextSize(14); return b; }
    private Button primary(String s) { return button(s, 0xFFE9ECFF, 0xFF15171C, 0xFFEEF0FF); }
    private Button accent(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setMinHeight(dp(44));
        b.setStateListAnimator(null);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundResource(R.drawable.btn_gradient);
        b.setTextColor(Color.WHITE);
        b.setHapticFeedbackEnabled(true);
        b.setSoundEffectsEnabled(true);
        return b;
    }
    private Button secondary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setMinHeight(dp(44));
        b.setStateListAnimator(null);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundResource(R.drawable.bg_glass_button);
        b.setTextColor(0xFFE2E4EC);
        b.setHapticFeedbackEnabled(true);
        b.setSoundEffectsEnabled(true);
        return b;
    }
    private Button button(String s, int bg, int tc, int stroke) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setMinHeight(dp(44));
        b.setStateListAnimator(null);
        b.setPadding(dp(10), 0, dp(10), 0);
        shape(b, bg, dp(14), stroke, true);
        b.setTextColor(tc);
        b.setHapticFeedbackEnabled(true);
        b.setSoundEffectsEnabled(true);
        return b;
    }
    private EditText edit(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(0xFF686D7A); e.setTextColor(0xFFF5F5F8); e.setTextSize(14); e.setSingleLine(true); e.setPadding(dp(12), 0, dp(12), 0); shape(e, 0xFF191D24, dp(14), 0xFF363B46, false); return e; }
    private View gap(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View gapW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
    private int dp(float x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
    private void shape(View v, int color, int radius, int stroke) { shape(v, color, radius, stroke, true); }
    private void shape(View v, int color, int radius, int stroke, boolean withRipple) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color);
        normal.setCornerRadius(radius);
        if (stroke != 0) normal.setStroke(dp(1), stroke);
        if (withRipple && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(radius);
            int rippleColor = (Color.alpha(color) > 100 && (Color.red(color) + Color.green(color) + Color.blue(color)) / 3 > 180)
                    ? 0x33000000 : 0x38FFFFFF;
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    normal,
                    mask
            );
            v.setBackground(ripple);
        } else {
            v.setBackground(normal);
        }
    }

    private String getStyleDisplayName(String s) {
        if ("bars".equalsIgnoreCase(s)) return "Barres";
        if ("mirror".equalsIgnoreCase(s)) return "Miroir";
        if ("wave".equalsIgnoreCase(s)) return "Vague";
        if ("circle".equalsIgnoreCase(s)) return "Cercle";
        if ("particles".equalsIgnoreCase(s)) return "Particules";
        if ("glow".equalsIgnoreCase(s)) return "Glow Néon";
        if ("cube".equalsIgnoreCase(s)) return "3D Cubes";
        if ("halo".equalsIgnoreCase(s)) return "Halo";
        if ("cyber".equalsIgnoreCase(s)) return "Cyber";
        return s;
    }

    private String getColorDisplayName(String c) {
        if ("#22D3EE".equalsIgnoreCase(c) || "cyan".equalsIgnoreCase(c)) return "Cyan";
        if ("#A855F7".equalsIgnoreCase(c) || "violet".equalsIgnoreCase(c)) return "Violet";
        if ("#EC4899".equalsIgnoreCase(c) || "rose".equalsIgnoreCase(c)) return "Rose";
        if ("#22C55E".equalsIgnoreCase(c) || "vert".equalsIgnoreCase(c)) return "Vert";
        return c;
    }

    private String getBgDisplayName(String b) {
        if ("dark".equalsIgnoreCase(b)) return "Noir";
        if ("gradient".equalsIgnoreCase(b)) return "Dégradé";
        if ("radial".equalsIgnoreCase(b)) return "Radial";
        if ("image".equalsIgnoreCase(b)) return "Image";
        return b;
    }

    private void updateStyleSelection(String newStyle) {
        this.style = newStyle;
        String[] vals = {"bars", "mirror", "wave", "circle", "particles", "glow", "cube", "halo", "cyber"};
        for (int i = 0; i < vals.length; i++) {
            boolean active = vals[i].equals(newStyle);
            if (i < visualStyleCards.size()) {
                View card = visualStyleCards.get(i);
                if (active) {
                    card.setBackgroundResource(R.drawable.bg_style_card_selected);
                } else {
                    card.setBackgroundResource(R.drawable.bg_style_card_unselected);
                }
            }
            if (i < homeStyleButtons.size()) {
                Button b = homeStyleButtons.get(i);
                shape(b, active ? 0xFF44306D : 0xFF20242C, dp(14), active ? 0xFF8B69FF : 0xFF424753, true);
                b.setTextColor(active ? Color.WHITE : 0xFFE2E3EA);
            }
        }
        if (visualizerView != null) visualizerView.invalidate();
        if (visualPagePreview != null) visualPagePreview.invalidate();
        if (visualPageTitle != null) {
            visualPageTitle.setText(getStyleDisplayName(style) + " • " + getColorDisplayName(activeColor));
        }
        saveSession();
    }

    private void updateColorSelection(String col) {
        this.activeColor = col;
        String[] codes = {"#22D3EE", "#A855F7", "#EC4899", "#22C55E"};
        for (int i = 0; i < codes.length; i++) {
            boolean sel = codes[i].equalsIgnoreCase(col);
            if (i < colorChipContainers.size()) {
                View chip = colorChipContainers.get(i);
                if (sel) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active);
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive);
                }
            }
            if (i < colorChipTexts.size()) {
                TextView txt = colorChipTexts.get(i);
                txt.setTextColor(sel ? Color.WHITE : 0x80FFFFFF);
            }
            if (i < colorButtons.size()) {
                Button b = colorButtons.get(i);
                int c = Color.parseColor(codes[i]);
                shape(b, c, dp(18), sel ? 0xFFFFFFFF : 0x44FFFFFF, true);
                b.setText(sel ? "" : "");
                b.setTextColor(0xFF10121A);
                b.setTextSize(14);
            }
        }
        if (visualizerView != null) visualizerView.invalidate();
        if (visualPagePreview != null) visualPagePreview.invalidate();
        if (visualPageTitle != null) {
            visualPageTitle.setText(getStyleDisplayName(style) + " • " + getColorDisplayName(activeColor));
        }
        saveSession();
    }

    private void updateBgSelection(String mode) {
        this.backgroundMode = mode;
        if ("gradient".equals(mode) || "dark".equals(mode) || "radial".equals(mode)) {
            backgroundBitmap = null;
        }
        String[] bgModes = {"dark", "gradient", "radial", "image"};
        for (int i = 0; i < bgModes.length; i++) {
            if (i < bgChipButtons.size()) {
                Button chip = bgChipButtons.get(i);
                boolean sel = bgModes[i].equals(mode);
                if (sel) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active);
                    chip.setTextColor(Color.WHITE);
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive);
                    chip.setTextColor(0xFF9CA3AF);
                }
            }
        }
        if (bgImgBtn != null) {
            boolean sel = "image".equals(mode) && backgroundBitmap != null;
            shape(bgImgBtn, sel ? 0xFF352569 : 0xFF1C2027, dp(14), sel ? 0xFF8B69FF : 0xFF3D424D, true);
            bgImgBtn.setTextColor(sel ? Color.WHITE : 0xFFE5E6EB);
        }
        if (bgGradBtn != null) {
            boolean sel = "gradient".equals(mode);
            shape(bgGradBtn, sel ? 0xFF352569 : 0xFF1C2027, dp(14), sel ? 0xFF8B69FF : 0xFF3D424D, true);
            bgGradBtn.setTextColor(sel ? Color.WHITE : 0xFFE5E6EB);
        }
        if (bgDarkBtn != null) {
            boolean sel = "dark".equals(mode);
            shape(bgDarkBtn, sel ? 0xFF352569 : 0xFF1C2027, dp(14), sel ? 0xFF8B69FF : 0xFF3D424D, true);
            bgDarkBtn.setTextColor(sel ? Color.WHITE : 0xFFE5E6EB);
        }
        if (visualizerView != null) visualizerView.invalidate();
        if (visualPagePreview != null) visualPagePreview.invalidate();
        if (visualPageBgTag != null) {
            visualPageBgTag.setText(getBgDisplayName(backgroundMode).toUpperCase());
        }
        saveSession();
    }

    private void updateLyricsModeSelection(String mode) {
        this.textMode = mode;
        String[] modeVals = {"scroll", "word", "fixed"};
        for (int i = 0; i < modeVals.length; i++) {
            if (i < lyricsModeButtons.size()) {
                Button mb = lyricsModeButtons.get(i);
                boolean sel = modeVals[i].equals(mode);
                if (sel) {
                    mb.setBackgroundResource(R.drawable.bg_chip_active);
                    mb.setTextColor(Color.WHITE);
                    mb.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                } else {
                    mb.setBackground(null);
                    mb.setTextColor(0x66FFFFFF); // 40% blanc
                    mb.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                }
            }
        }
        if (visualizerView != null) visualizerView.invalidate();
        if (visualPagePreview != null) visualPagePreview.invalidate();
        updateKaraokeLinesView();
        saveSession();
    }

    private void updateKaraokeLinesView() {
        if (karaokeActiveLine == null) return;
        if (lyricsHeaderBadge != null) {
            lyricsHeaderBadge.setText(lyricsList.isEmpty() ? "LRC • INACTIF" : "LRC • 98% SYNC");
        }
        if (miniLyricsScrubberView != null) {
            miniLyricsScrubberView.postInvalidate();
        }

        if (!lyricsList.isEmpty()) {
            long curMs = player != null ? player.getCurrentPosition() : 0;
            int activeIndex = 0;
            for (int i = 0; i < lyricsList.size(); i++) {
                LyricLine line = lyricsList.get(i);
                if (curMs >= line.startMs && curMs <= line.endMs) {
                    activeIndex = i;
                    break;
                } else if (curMs > line.endMs) {
                    activeIndex = i;
                }
            }

            if (karaokePastLine1 != null) {
                if (activeIndex >= 2) {
                    karaokePastLine1.setText(lyricsList.get(activeIndex - 2).text);
                    clearGradientShader(karaokePastLine1, 0xFFF5F5F7, 0.50f);
                    karaokePastLine1.setVisibility(View.VISIBLE);
                } else {
                    karaokePastLine1.setVisibility(View.GONE);
                }
            }
            if (karaokePastLine2 != null) {
                if (activeIndex >= 1) {
                    karaokePastLine2.setText(lyricsList.get(activeIndex - 1).text);
                    clearGradientShader(karaokePastLine2, 0xFFF5F5F7, 0.75f);
                    karaokePastLine2.setVisibility(View.VISIBLE);
                } else {
                    karaokePastLine2.setVisibility(View.GONE);
                }
            }

            if ("word".equals(textMode)) {
                LyricLine curLine = lyricsList.get(activeIndex);
                String[] words = curLine.text.split("\\s+");
                if (words.length > 0) {
                    float frac = (curLine.endMs > curLine.startMs) ? (curMs - curLine.startMs) / (float) (curLine.endMs - curLine.startMs) : 0f;
                    int wordIdx = Math.max(0, Math.min(words.length - 1, (int) (frac * words.length)));
                    karaokeActiveLine.setText(words[wordIdx]);
                } else {
                    karaokeActiveLine.setText(curLine.text);
                }
            } else {
                karaokeActiveLine.setText(lyricsList.get(activeIndex).text);
            }
            applyGradientShader(karaokeActiveLine, 0xFF22D3EE, 0xFFA855F7, dp(12), 0x80A855F7);

            if (karaokeNextLine1 != null) {
                if (activeIndex + 1 < lyricsList.size()) {
                    karaokeNextLine1.setText(lyricsList.get(activeIndex + 1).text);
                    clearGradientShader(karaokeNextLine1, 0xFFF5F5F7, 0.75f);
                    karaokeNextLine1.setVisibility(View.VISIBLE);
                } else {
                    karaokeNextLine1.setVisibility(View.GONE);
                }
            }
            if (karaokeNextLine2 != null) {
                if (activeIndex + 2 < lyricsList.size()) {
                    karaokeNextLine2.setText(lyricsList.get(activeIndex + 2).text);
                    clearGradientShader(karaokeNextLine2, 0xFFF5F5F7, 0.50f);
                    karaokeNextLine2.setVisibility(View.VISIBLE);
                } else {
                    karaokeNextLine2.setVisibility(View.GONE);
                }
            }
        } else {
            String q = quoteInput == null ? quote : quoteInput.getText().toString().trim();
            if (q.isEmpty()) {
                if (karaokePastLine1 != null) {
                    karaokePastLine1.setText("Quand la nuit tombe sur la ville");
                    clearGradientShader(karaokePastLine1, 0xFFF5F5F7, 0.50f);
                    karaokePastLine1.setVisibility(View.VISIBLE);
                }
                if (karaokePastLine2 != null) {
                    karaokePastLine2.setText("Je cherche encore ton écho");
                    clearGradientShader(karaokePastLine2, 0xFFF5F5F7, 0.75f);
                    karaokePastLine2.setVisibility(View.VISIBLE);
                }
                karaokeActiveLine.setText("Midnight signals dans le vide");
                applyGradientShader(karaokeActiveLine, 0xFF22D3EE, 0xFFA855F7, dp(12), 0x80A855F7);
                if (karaokeNextLine1 != null) {
                    karaokeNextLine1.setText("Ton nom résonne si fort");
                    clearGradientShader(karaokeNextLine1, 0xFFF5F5F7, 0.75f);
                    karaokeNextLine1.setVisibility(View.VISIBLE);
                }
                if (karaokeNextLine2 != null) {
                    karaokeNextLine2.setText("Je ne dors plus, je t'attends");
                    clearGradientShader(karaokeNextLine2, 0xFFF5F5F7, 0.50f);
                    karaokeNextLine2.setVisibility(View.VISIBLE);
                }
            } else {
                if (karaokePastLine1 != null) karaokePastLine1.setVisibility(View.GONE);
                if (karaokePastLine2 != null) karaokePastLine2.setVisibility(View.GONE);
                karaokeActiveLine.setText(q);
                applyGradientShader(karaokeActiveLine, 0xFF22D3EE, 0xFFA855F7, dp(12), 0x80A855F7);
                if (karaokeNextLine1 != null) karaokeNextLine1.setVisibility(View.GONE);
                if (karaokeNextLine2 != null) karaokeNextLine2.setVisibility(View.GONE);
            }
        }
    }

    private void applyGradientShader(TextView tv, int startColor, int endColor, float shadowRadius, int shadowColor) {
        if (tv == null) return;
        tv.setTextColor(startColor);
        tv.setAlpha(1.0f);
        tv.post(() -> {
            int w = tv.getWidth();
            if (w <= 0) {
                String text = tv.getText().toString();
                w = (int) tv.getPaint().measureText(text);
                if (w <= 0) w = dp(260);
            }
            Shader shader = new LinearGradient(
                    0, 0, w, tv.getTextSize(),
                    new int[]{startColor, endColor},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            tv.getPaint().setShader(shader);
            if (shadowRadius > 0) {
                tv.setShadowLayer(shadowRadius, 0, 0, shadowColor);
            } else {
                tv.getPaint().clearShadowLayer();
                tv.setShadowLayer(0, 0, 0, 0);
            }
            tv.invalidate();
        });
    }

    private void clearGradientShader(TextView tv, int textColor, float alpha) {
        if (tv == null) return;
        tv.getPaint().setShader(null);
        tv.getPaint().clearShadowLayer();
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setTextColor(textColor);
        tv.setAlpha(alpha);
        tv.invalidate();
    }

    private void updateSilenceTimelineUi() {
        if (silenceInfoText == null) return;
        long total = (player != null && player.getDuration() > 0) ? player.getDuration() : 180000;
        long usefulStart = trimStartMs;
        long usefulEnd = (trimEndMs > 0 && trimEndMs <= total) ? trimEndMs : total;
        long usefulDuration = Math.max(0, usefulEnd - usefulStart);
        int usefulPercent = (int) Math.min(100, Math.max(1, (usefulDuration * 100) / total));

        if (trimStartMs > 0 || (trimEndMs > 0 && trimEndMs < total)) {
            silenceInfoText.setText(String.format(Locale.US, "Zone utile : %s → %s (%d%% du fichier)",
                    formatDuration(usefulStart), formatDuration(usefulEnd), usefulPercent));
        } else {
            silenceInfoText.setText("3 silences détectés • auto-trim proposé");
        }

        if (silenceTimelineContainer != null) {
            silenceTimelineContainer.removeAllViews();
            if (trimStartMs > 0 || (trimEndMs > 0 && trimEndMs < total)) {
                // Barre dynamique avec zone utile
                float startWeight = Math.max(0.001f, (float) usefulStart / (float) total);
                float activeWeight = Math.max(0.05f, (float) usefulDuration / (float) total);
                float endWeight = Math.max(0.001f, (float) Math.max(0, total - usefulEnd) / (float) total);

                if (usefulStart > 300) {
                    View silenceStart = new View(this);
                    shape(silenceStart, 0x1FFFFFFF, dp(12), 0, false);
                    silenceTimelineContainer.addView(silenceStart, new LinearLayout.LayoutParams(0, -1, startWeight));
                }

                View usefulBar = new View(this);
                shape(usefulBar, 0xFF22D3EE, dp(12), 0, false);
                silenceTimelineContainer.addView(usefulBar, new LinearLayout.LayoutParams(0, -1, activeWeight));

                if ((total - usefulEnd) > 300) {
                    View silenceEnd = new View(this);
                    shape(silenceEnd, 0x1FFFFFFF, dp(12), 0, false);
                    silenceTimelineContainer.addView(silenceEnd, new LinearLayout.LayoutParams(0, -1, endWeight));
                }
            } else {
                // Répartition représentative de la maquette : [40%, 10%, 25%, 8%, 30%, 12%]
                int[] segments = {40, 10, 25, 8, 30, 12};
                for (int i = 0; i < segments.length; i++) {
                    View seg = new View(this);
                    int segColor = (i % 2 == 0) ? 0xFF22D3EE : 0x1FFFFFFF;
                    shape(seg, segColor, dp(12), 0, false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, (float) segments[i]);
                    if (i > 0) lp.leftMargin = dp(4);
                    silenceTimelineContainer.addView(seg, lp);
                }
            }
        }
    }

    private void updateAudioPageUi() {
        if (audioPageTrackTitle != null) {
            audioPageTrackTitle.setText(audioFile != null ? (currentTrackTitle.isEmpty() ? currentFileName : currentTrackTitle) : "Midnight Signals");
        }
        if (audioPageTrackArtist != null) {
            if (audioFile != null) {
                String durStr = (player != null && player.getDuration() > 0) ? " · " + formatDuration(player.getDuration()) : "";
                audioPageTrackArtist.setText(currentTrackArtist.isEmpty() ? "Audio chargé" + durStr : currentTrackArtist + durStr);
            } else {
                audioPageTrackArtist.setText("LØST · 3:42 · 320 kbps");
            }
        }
        if (audioPageDurationTime != null) {
            audioPageDurationTime.setText(player != null && player.getDuration() > 0 ? formatDuration(player.getDuration()) : "03:42");
        }
        if (audioPageCurrentTime != null) {
            audioPageCurrentTime.setText(player != null && player.getDuration() > 0 ? formatDuration(player.getCurrentPosition()) : "01:24");
        }
        if (audioPagePlayIcon != null) {
            audioPagePlayIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (audioPagePlayBtn != null) {
            audioPagePlayBtn.setText(playing ? "" : "");
        }
        if (audioCoverProgressBar != null) {
            float frac = (player != null && player.getDuration() > 0) ? Math.max(0f, Math.min(1f, (float) player.getCurrentPosition() / (float) player.getDuration())) : 0.38f;
            View parent = (View) audioCoverProgressBar.getParent();
            if (parent != null && parent.getWidth() > 0) {
                int w = (int) (parent.getWidth() * frac);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) audioCoverProgressBar.getLayoutParams();
                lp.width = Math.max(dp(4), w);
                audioCoverProgressBar.setLayoutParams(lp);
            }
        }
        if (audioWaveformView != null) {
            audioWaveformView.postInvalidate();
        }
    }

    private void updateFormatSelection(int w, int h) {
        this.formatW = w;
        this.formatH = h;
        int[][] dims = {{1080, 1920}, {1080, 1080}, {1920, 1080}};
        for (int i = 0; i < dims.length; i++) {
            boolean sel = (dims[i][0] == w && dims[i][1] == h);
            if (i < formatCards.size()) {
                LinearLayout card = formatCards.get(i);
                if (sel) {
                    card.setBackgroundResource(R.drawable.bg_style_card_selected);
                } else {
                    card.setBackgroundResource(R.drawable.bg_style_card_unselected);
                }
            }
            if (i < formatTitles.size()) {
                TextView tv = formatTitles.get(i);
                tv.setTextColor(sel ? Color.WHITE : 0xCCFFFFFF);
                tv.setTypeface(Typeface.create("sans-serif", sel ? Typeface.BOLD : Typeface.NORMAL));
            }
            if (i < formatRatioBadges.size()) {
                TextView b = formatRatioBadges.get(i);
                if (sel) {
                    shape(b, 0x3322D3EE, dp(8), 0xFF22D3EE, false);
                    b.setTextColor(0xFF22D3EE);
                } else {
                    shape(b, 0x0DFFFFFF, dp(8), 0x26FFFFFF, false);
                    b.setTextColor(0x80FFFFFF);
                }
            }
            if (i < formatCheckIcons.size()) {
                ImageView ck = formatCheckIcons.get(i);
                ck.setImageResource(sel ? R.drawable.ic_check : 0);
                ck.setColorFilter(sel ? 0xFF22D3EE : 0x4DFFFFFF);
            }
            if (i < formatAspectIcons.size()) {
                formatAspectIcons.get(i).setColorFilter(sel ? Color.WHITE : 0x80FFFFFF);
            }
        }
        updateExportSummaryText();
        showStatus("Format vidéo : " + formatW + "×" + formatH);
        saveSession();
    }

    private void updateFpsSelection(int fps) {
        this.exportFps = fps;
        boolean is30 = (fps == 30);
        if (fps30Container != null) {
            if (is30) {
                fps30Container.setBackgroundResource(R.drawable.btn_gradient);
            } else {
                fps30Container.setBackground(null);
            }
        }
        if (fps60Container != null) {
            if (!is30) {
                fps60Container.setBackgroundResource(R.drawable.btn_gradient);
            } else {
                fps60Container.setBackground(null);
            }
        }
        if (fps30Title != null) {
            fps30Title.setTextColor(is30 ? Color.WHITE : 0x80FFFFFF);
            fps30Title.setTypeface(Typeface.create("sans-serif", is30 ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (fps30Subtitle != null) {
            fps30Subtitle.setTextColor(is30 ? 0xE6FFFFFF : 0x4DFFFFFF);
        }
        if (fps60Title != null) {
            fps60Title.setTextColor(!is30 ? Color.WHITE : 0x80FFFFFF);
            fps60Title.setTypeface(Typeface.create("sans-serif", !is30 ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (fps60Subtitle != null) {
            fps60Subtitle.setTextColor(!is30 ? 0xE6FFFFFF : 0x4DFFFFFF);
        }
        updateExportSummaryText();
        saveSession();
    }

    private void updateExportSummaryText() {
        String ratioStr = (formatW == 1080 && formatH == 1920) ? "9:16" : (formatW == 1080 && formatH == 1080 ? "1:1" : "16:9");
        if (exportSummaryTag != null) {
            exportSummaryTag.setText(" • " + ratioStr + " (" + formatW + "×" + formatH + ") • " + exportFps + " FPS");
        }
    }
    private android.widget.SeekBar.OnSeekBarChangeListener seek(java.util.function.IntConsumer c) { return new android.widget.SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) { c.accept(p); } public void onStartTrackingTouch(android.widget.SeekBar s) {} public void onStopTrackingTouch(android.widget.SeekBar s) {} }; }

    public void openAudioBrowser() {
        currentAudioBrowserDialog = new AudioBrowserDialog(this, () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
            } else {
                audioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }, (uri, title, artist, durationMs) -> {
            loadAudio(uri);
            showStatus("Morceau importé : " + title);
            Toast.makeText(this, "Audio '" + title + "' importé avec succès !", Toast.LENGTH_SHORT).show();
        });
        currentAudioBrowserDialog.show();
    }

    private void pickAudio() {
        String[] options = {
                "Gestionnaire Audio (Écouter & Importer)",
                "Explorateur de fichiers système",
                "Charger l'extrait démo (Synthwave 120 BPM)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Sélectionner un audio")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openAudioBrowser();
                    } else if (which == 1) {
                        launchAudioSystemPicker();
                    } else {
                        createSampleDemoAudio();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    public boolean hasStorageAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestStorageAudioPermission(Runnable onGranted) {
        if (hasStorageAudioPermission()) {
            if (onGranted != null) onGranted.run();
            return;
        }
        this.pendingStoragePermissionCallback = onGranted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            audioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean audioGranted = false;
            if (permissions != null && grantResults != null) {
                int count = Math.min(permissions.length, grantResults.length);
                for (int i = 0; i < count; i++) {
                    if ((Manifest.permission.READ_MEDIA_AUDIO.equals(permissions[i]) ||
                         Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[i])) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        audioGranted = true;
                        break;
                    }
                }
            }
            if (audioGranted) {
                try {
                    Toast.makeText(this, "Accès au stockage accordé !", Toast.LENGTH_SHORT).show();
                    if (pendingStoragePermissionCallback != null) {
                        Runnable cb = pendingStoragePermissionCallback;
                        pendingStoragePermissionCallback = null;
                        try { cb.run(); } catch (Exception ignored) {}
                    } else {
                        MusicPlayerManager.getInstance(this).scanAndRefreshDeviceTracks(this, false, tracks -> {
                            if (currentMusicPlayerDialog != null && currentMusicPlayerDialog.isShowing()) {
                                try {
                                    currentMusicPlayerDialog.onPermissionRefreshed();
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                    if (currentAudioBrowserDialog != null && currentAudioBrowserDialog.isShowing()) {
                        try {
                            currentAudioBrowserDialog.checkPermissionAndScan();
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Erreur permission stockage accordee", e);
                }
            }
        }
    }

    public void launchAudioSystemPicker() {
        pickPurpose = "audio";
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            filePickerLauncher.launch(Intent.createChooser(intent, "Sélectionner un fichier audio"));
        } catch (Exception e1) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("audio/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                filePickerLauncher.launch(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "Aucun explorateur trouvé, chargement de l'extrait démo...", Toast.LENGTH_SHORT).show();
                createSampleDemoAudio();
            }
        }
    }

    public void createSampleDemoAudio() {
        showStatus("Génération de l'audio démo en cours…");
        new Thread(() -> {
            try {
                File demoFile = new File(getCacheDir(), "demo_synthwave.wav");
                int sampleRate = 44100;
                int durationSec = 16;
                int numSamples = sampleRate * durationSec;
                short[] samples = new short[numSamples];

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double beatPhase = (t % 0.5) / 0.5;
                    double kick = Math.sin(2 * Math.PI * (130 * Math.exp(-beatPhase * 12)) * t) * Math.exp(-beatPhase * 7);

                    double[] notes = {220.0, 277.18, 329.63, 440.0, 329.63, 277.18};
                    int noteIdx = (int) (t * 4) % notes.length;
                    double synth = Math.sin(2 * Math.PI * notes[noteIdx] * t) * 0.35;

                    double pad = (Math.sin(2 * Math.PI * 110 * t) + Math.sin(2 * Math.PI * 165 * t)) * 0.2;
                    double mix = (kick * 0.55 + synth * 0.45 + pad * 0.25);
                    mix = Math.max(-1.0, Math.min(1.0, mix));
                    samples[i] = (short) (mix * 32767);
                }

                try (FileOutputStream fos = new FileOutputStream(demoFile);
                     DataOutputStream dos = new DataOutputStream(fos)) {
                    dos.writeBytes("RIFF");
                    dos.writeInt(Integer.reverseBytes(36 + numSamples * 2));
                    dos.writeBytes("WAVE");
                    dos.writeBytes("fmt ");
                    dos.writeInt(Integer.reverseBytes(16));
                    dos.writeShort(Short.reverseBytes((short) 1));
                    dos.writeShort(Short.reverseBytes((short) 1));
                    dos.writeInt(Integer.reverseBytes(sampleRate));
                    dos.writeInt(Integer.reverseBytes(sampleRate * 2));
                    dos.writeShort(Short.reverseBytes((short) 2));
                    dos.writeShort(Short.reverseBytes((short) 16));
                    dos.writeBytes("data");
                    dos.writeInt(Integer.reverseBytes(numSamples * 2));
                    for (short sample : samples) {
                        dos.writeShort(Short.reverseBytes(sample));
                    }
                }

                runOnUiThread(() -> {
                    audioFile = demoFile;
                    audioMime = "audio/wav";
                    currentFileName = "Synthwave Neon (120 BPM)";
                    currentTrackTitle = "Synthwave Neon";
                    currentTrackArtist = "Studio Pro";
                    try {
                        initPlayer();
                        setAudioButtonsEnabled(true);
                        showStatus("Audio démo chargé : " + currentFileName);
                        if (homeTrackTitle != null) homeTrackTitle.setText(currentTrackArtist + " - " + currentTrackTitle);
                        if (status != null) status.setText("Audio démo prêt à être visualisé.");

                        AudioBrowserDialog.AudioTrackItem demoItem = new AudioBrowserDialog.AudioTrackItem(
                                System.currentTimeMillis(),
                                currentTrackTitle,
                                currentTrackArtist,
                                "Studio Pro",
                                16000,
                                demoFile.length(),
                                System.currentTimeMillis() / 1000,
                                Uri.fromFile(demoFile),
                                demoFile.getAbsolutePath(),
                                audioMime
                        );
                        MusicPlayerManager.getInstance(MainActivity.this).syncWithExternalTrack(demoItem);

                        addChatMessage(false, " **Extrait Démo chargé avec succès !**\nVous pouvez lancer la lecture () et tester les 9 moteurs visuels, les filtres et l'export.");
                    } catch (Exception ex) {
                        showStatus("Erreur audio démo : " + friendlyError(ex));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showStatus("Génération démo : " + friendlyError(e)));
            }
        }).start();
    }

    private void loadAudio(Uri uri) {
        try {
            currentAudioUri = uri;
            currentAudioPath = "";
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                try {
                    String[] proj = {MediaStore.Audio.Media.DATA};
                    try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int col = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                            if (col != -1) currentAudioPath = c.getString(col);
                        }
                    }
                } catch (Exception ignored) {}
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                currentAudioPath = uri.getPath();
            }

            if (audioFile != null) audioFile.delete();
            String mime = getContentResolver().getType(uri);
            audioMime = (mime == null || mime.isEmpty()) ? guessAudioMime(uri) : mime;
            String ext = extensionFromMime(audioMime);
            audioFile = new File(getCacheDir(), "current_audio" + ext);
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(audioFile)) {
                byte[] b = new byte[32768];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
            String d = uri.getLastPathSegment();
            currentFileName = (d == null || d.isEmpty()) ? "audio" : d.replaceFirst("\\.[^.]+$", "");
            extractAudioMetadata();
            initPlayer();
            setAudioButtonsEnabled(true);
            showStatus("Audio chargé : " + currentFileName);
            if (homeTrackTitle != null) homeTrackTitle.setText((currentTrackArtist.isEmpty() ? "" : currentTrackArtist + " - ") + currentTrackTitle);
            if (status != null) status.setText("Audio prêt à être analysé.");

            // Synchronisation avec MusicPlayerManager
            long durMs = (player != null && player.getDuration() > 0) ? player.getDuration() : 0;
            long fSize = (audioFile != null) ? audioFile.length() : 0;
            AudioBrowserDialog.AudioTrackItem currentItem = new AudioBrowserDialog.AudioTrackItem(
                    System.currentTimeMillis(),
                    (currentTrackTitle != null && !currentTrackTitle.isEmpty()) ? currentTrackTitle : currentFileName,
                    (currentTrackArtist != null && !currentTrackArtist.isEmpty()) ? currentTrackArtist : "Artiste Inconnu",
                    "Studio Pro",
                    durMs,
                    fSize,
                    System.currentTimeMillis() / 1000,
                    uri,
                    (audioFile != null) ? audioFile.getAbsolutePath() : currentAudioPath,
                    (audioMime != null) ? audioMime : "audio/mpeg"
            );
            MusicPlayerManager.getInstance(this).syncWithExternalTrack(currentItem);

            // Détection automatique de paroles synchronisées intégrées au fichier audio
            AudioLyricsTagger.ExtractionResult embedded = AudioLyricsTagger.readEmbeddedLyrics(audioFile);
            if (embedded == null) {
                embedded = AudioLyricsTagger.readEmbeddedLyrics(this, uri);
            }
            if (embedded == null && currentAudioPath != null && !currentAudioPath.isEmpty()) {
                try {
                    File lrcFile = new File(currentAudioPath.replaceAll("\\.[a-zA-Z0-9]+$", ".lrc"));
                    if (lrcFile.exists() && lrcFile.length() > 10) {
                        String lrcContent = readFileToString(lrcFile);
                        List<LyricLine> parsed = LyricLine.parseLrcString(lrcContent);
                        if (!parsed.isEmpty()) {
                            embedded = new AudioLyricsTagger.ExtractionResult(parsed, lrcContent, "Fichier .lrc compagnon");
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (embedded != null && !embedded.lines.isEmpty()) {
                lyricsList.clear();
                lyricsList.addAll(embedded.lines);
                quote = embedded.plainText;
                textMode = "scroll";
                if (quoteInput != null) quoteInput.setText(quote);
                if (lyricsPreviewText != null) {
                    lyricsPreviewText.setText(lyricsList.size() + " paroles intégrées (" + embedded.sourceFormat + ")");
                }
                updateKaraokeLinesView();
                visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
                Toast.makeText(this, "Paroles synchronisées détectées (" + lyricsList.size() + " lignes) !", Toast.LENGTH_SHORT).show();
                addChatMessage(false, " **Paroles intégrées détectées !**\nLe morceau contient des paroles synchronisées (" + embedded.sourceFormat + ", " + lyricsList.size() + " lignes). Chargées automatiquement pour le visualiseur et le lecteur !");
            } else {
                // Recherche automatique en ligne avec LRCLIB pour détecter les paroles du morceau
                final int songDurSec = (int) (durMs / 1000);
                final String queryTitle = currentTrackTitle;
                final String queryArtist = currentTrackArtist;
                if (queryTitle != null && !queryTitle.isEmpty()) {
                    new Thread(() -> {
                        try {
                            LrcLibClient.LyricsResult res = LrcLibClient.fetchLyrics(queryTitle, queryArtist, songDurSec);
                            if (res != null && res.hasSynced()) {
                                runOnUiThread(() -> {
                                    List<LyricLine> parsed = LyricLine.parseLrcString(res.syncedLyrics);
                                    if (!parsed.isEmpty()) {
                                        lyricsList.clear();
                                        lyricsList.addAll(parsed);
                                        quote = res.syncedLyrics;
                                        textMode = "scroll";
                                        if (quoteInput != null) quoteInput.setText(quote);
                                        if (lyricsPreviewText != null) {
                                            lyricsPreviewText.setText(lyricsList.size() + " paroles synchronisées (LRCLIB)");
                                        }
                                        updateKaraokeLinesView();
                                        visualizerView.invalidate();
                                        if (visualPagePreview != null) visualPagePreview.invalidate();
                                        Toast.makeText(MainActivity.this, "Paroles synchronisées détectées (" + lyricsList.size() + " lignes) !", Toast.LENGTH_SHORT).show();
                                        addChatMessage(false, " **Paroles trouvées sur LRCLIB !**\n" + lyricsList.size() + " lignes synchronisées ont été récupérées pour **" + queryTitle + "** et intégrées au visualiseur et au lecteur.");
                                    }
                                });
                            }
                        } catch (Exception ignored) {}
                    }).start();
                }
            }
        } catch (Exception e) {
            showStatus("Audio : " + friendlyError(e));
        }
    }

    private String readFileToString(File f) {
        if (f == null || !f.exists()) return "";
        try (FileInputStream fis = new FileInputStream(f);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void extractAudioMetadata() {
        currentAudioCoverBitmap = null;
        if (audioFile == null || !audioFile.exists()) return;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(audioFile.getAbsolutePath());
            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (title != null && !title.trim().isEmpty()) {
                currentTrackTitle = title.trim();
            } else {
                currentTrackTitle = currentFileName;
            }
            if (artist != null && !artist.trim().isEmpty()) {
                currentTrackArtist = artist.trim();
            } else {
                if (currentFileName != null && currentFileName.contains(" - ")) {
                    String[] parts = currentFileName.split(" - ", 2);
                    currentTrackArtist = parts[0].trim();
                    currentTrackTitle = parts[1].trim();
                } else {
                    currentTrackArtist = "";
                }
            }

            // Détection de la pochette d'album intégrée
            try {
                byte[] art = mmr.getEmbeddedPicture();
                if (art != null && art.length > 0) {
                    currentAudioCoverBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                }
            } catch (Exception ignored) {}

            mmr.release();
        } catch (Exception ignored) {
            currentTrackTitle = currentFileName;
            currentTrackArtist = "";
        }

        // Si pas de pochette intégrée, tenter Android 10+ loadThumbnail
        if (currentAudioCoverBitmap == null && currentAudioUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                currentAudioCoverBitmap = getContentResolver().loadThumbnail(currentAudioUri, new Size(512, 512), null);
            } catch (Exception ignored) {}
        }

        // Si pas de pochette, chercher les fichiers images associés dans le même dossier
        if (currentAudioCoverBitmap == null && currentAudioPath != null && !currentAudioPath.isEmpty()) {
            try {
                File af = new File(currentAudioPath);
                File dir = af.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    String base = af.getName().replaceAll("\\.[a-zA-Z0-9]+$", "");
                    String[] candidates = {
                            base + ".jpg", base + ".jpeg", base + ".png",
                            "cover.jpg", "cover.png", "folder.jpg", "front.jpg", "album.jpg"
                    };
                    for (String c : candidates) {
                        File cf = new File(dir, c);
                        if (cf.exists() && cf.length() > 500) {
                            currentAudioCoverBitmap = BitmapFactory.decodeFile(cf.getAbsolutePath());
                            if (currentAudioCoverBitmap != null) break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Mettre à jour l'affichage de la pochette sur la page Audio du Studio
        runOnUiThread(() -> {
            if (audioPageCoverImage != null) {
                if (currentAudioCoverBitmap != null) {
                    audioPageCoverImage.setImageBitmap(currentAudioCoverBitmap);
                    audioPageCoverImage.setVisibility(View.VISIBLE);
                    if (audioPageDiskIcon != null) audioPageDiskIcon.setVisibility(View.GONE);
                } else {
                    audioPageCoverImage.setImageBitmap(null);
                    audioPageCoverImage.setVisibility(View.GONE);
                    if (audioPageDiskIcon != null) audioPageDiskIcon.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void initPlayer() throws Exception {
        releasePlayer();
        player = new MediaPlayer();
        player.setDataSource(audioFile.getAbsolutePath());
        player.setOnPreparedListener(mp -> {
            trimEndMs = mp.getDuration();
            attachVisualizer();
            AudioEffectsManager.getInstance().attachToPlayer(mp);
            analyzeAudioBpmAndKey();
            if (audioMeta != null) audioMeta.setText(currentFileName + "  ·  " + formatDuration(mp.getDuration()));
            if (homeTrackTime != null) homeTrackTime.setText("0:00 / " + formatDuration(mp.getDuration()));
            updateAudioPageUi();
            updateSilenceTimelineUi();
            setAudioButtonsEnabled(true);
        });
        player.setOnCompletionListener(mp -> {
            playing = false;
            if (playButton != null) playButton.setText("Lecture");
            if (audioPagePlayBtn != null) audioPagePlayBtn.setText("");
            if (audioWaveformView != null) audioWaveformView.postInvalidate();
        });
        player.setOnErrorListener((mp, what, extra) -> {
            showStatus("Erreur lecteur audio (" + what + ", " + extra + ")");
            return true;
        });
        player.prepareAsync();
    }

    private String guessAudioMime(Uri u) {
        String s = u.toString().toLowerCase(Locale.US);
        if (s.endsWith(".wav")) return "audio/wav";
        if (s.endsWith(".m4a")) return "audio/mp4";
        if (s.endsWith(".flac")) return "audio/flac";
        if (s.endsWith(".ogg")) return "audio/ogg";
        if (s.endsWith(".webm")) return "audio/webm";
        return "audio/mpeg";
    }

    private String extensionFromMime(String m) {
        if ("audio/wav".equals(m) || "audio/x-wav".equals(m)) return ".wav";
        if ("audio/mp4".equals(m) || "audio/m4a".equals(m)) return ".m4a";
        if ("audio/flac".equals(m)) return ".flac";
        if ("audio/ogg".equals(m)) return ".ogg";
        if ("audio/webm".equals(m)) return ".webm";
        return ".mp3";
    }

    private void attachVisualizer() {
        try {
            if (player == null) return;
            visualizer = new Visualizer(player.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                public void onWaveFormDataCapture(Visualizer v, byte[] d, int r) {
                    waveform = d.clone();
                    if (visualizerView != null) visualizerView.postInvalidate();
                }
                public void onFftDataCapture(Visualizer v, byte[] d, int r) {
                    fft = d.clone();
                    if (visualizerView != null) visualizerView.postInvalidate();
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
        } catch (Throwable t) {
            // Visualizer can throw SecurityException or UnsupportedOperationException on some devices/emulators
        }
    }

    private void setAudioButtonsEnabled(boolean e) {
        if (playButton != null) playButton.setEnabled(e);
        if (exportButton != null) exportButton.setEnabled(e);
        if (detectButton != null) detectButton.setEnabled(e);
        if (loopButton != null) loopButton.setEnabled(e);
        if (snapshotButton != null) snapshotButton.setEnabled(e);
        boolean keyReady = !groqKey().isEmpty();
        if (transcribeButton != null) transcribeButton.setEnabled(e && keyReady);
        if (generateQuoteButton != null) generateQuoteButton.setEnabled(keyReady);
    }

    public void pauseStudioPlayer() {
        if (player != null && player.isPlaying()) {
            try { player.pause(); } catch (Exception ignored) {}
            playing = false;
            if (playButton != null) playButton.setText("Lecture");
            if (audioPagePlayIcon != null) audioPagePlayIcon.setImageResource(R.drawable.ic_play);
            if (audioPagePlayBtn != null) audioPagePlayBtn.setText("");
            if (audioWaveformView != null) audioWaveformView.postInvalidate();
            if (visualizerView != null) visualizerView.postInvalidate();
        }
    }

    public void startStudioPlayer() {
        if (player == null) return;
        // Arrêter obligatoirement le lecteur musical s'il joue pour éviter tout mélange sonore
        try {
            MusicPlayerManager pm = MusicPlayerManager.getInstance(this);
            if (pm.isPlaying()) {
                pm.pause();
            }
        } catch (Exception ignored) {}
        if (currentAudioBrowserDialog != null) {
            try { currentAudioBrowserDialog.stopPreviewPlayer(); } catch (Exception ignored) {}
        }
        try {
            if (player.getCurrentPosition() < trimStartMs || (trimEndMs > trimStartMs && player.getCurrentPosition() >= trimEndMs)) {
                player.seekTo((int) trimStartMs);
            }
            player.start();
            playing = true;
            if (playButton != null) playButton.setText("Pause");
            if (audioPagePlayIcon != null) audioPagePlayIcon.setImageResource(R.drawable.ic_pause);
            if (audioPagePlayBtn != null) audioPagePlayBtn.setText("");
            startTrackClock();
            if (audioWaveformView != null) audioWaveformView.postInvalidate();
            if (visualizerView != null) visualizerView.postInvalidate();
        } catch (Exception e) {
            showStatus("Lecture : " + friendlyError(e));
        }
    }

    private void togglePlayback() {
        if (player != null) {
            if (player.isPlaying()) {
                pauseStudioPlayer();
            } else {
                startStudioPlayer();
            }
            return;
        }

        // Si aucun audio studio n'est chargé, on contrôle le lecteur musical
        MusicPlayerManager pm = MusicPlayerManager.getInstance(this);
        if (pm.getCurrentTrack() != null) {
            pm.togglePlayPause();
        } else {
            Toast.makeText(this, "Veuillez charger un fichier audio ou ouvrir le lecteur musical.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startTrackClock() {
        handler.postDelayed(new Runnable() {
            public void run() {
                if (player != null && playing) {
                    if (homeTrackTime != null) {
                        homeTrackTime.setText(formatDuration(player.getCurrentPosition()) + " / " + formatDuration(player.getDuration()));
                    }
                    if (audioPageCurrentTime != null) {
                        audioPageCurrentTime.setText(formatDuration(player.getCurrentPosition()));
                    }
                    if (audioCoverProgressBar != null && player.getDuration() > 0) {
                        float frac = Math.max(0f, Math.min(1f, (float) player.getCurrentPosition() / (float) player.getDuration()));
                        View parent = (View) audioCoverProgressBar.getParent();
                        if (parent != null && parent.getWidth() > 0) {
                            int w = (int) (parent.getWidth() * frac);
                            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) audioCoverProgressBar.getLayoutParams();
                            lp.width = Math.max(dp(4), w);
                            audioCoverProgressBar.setLayoutParams(lp);
                        }
                    }
                    if (audioWaveformView != null) {
                        audioWaveformView.postInvalidate();
                    }
                    visualizerView.postInvalidate();
                    updateKaraokeLinesView();
                    handler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    private String groqKey() {
        if (groqKeyInput != null) {
            String k = groqKeyInput.getText().toString().trim();
            if (!k.isEmpty()) return k;
        }
        try {
            return KeyStoreUtil.decrypt(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GROQ, ""));
        } catch (Exception e) {
            return "";
        }
    }

    private void searchLrcLib(boolean interactive) {
        if (audioFile == null || !audioFile.exists()) {
            if (interactive) {
                LinearLayout dialogLayout = vertical();
                dialogLayout.setPadding(dp(16), dp(10), dp(16), dp(10));
                EditText trackEdit = edit("Titre du morceau (ex: Bohemian Rhapsody)");
                if (currentFileName != null && !currentFileName.equals("audio")) {
                    trackEdit.setText(currentFileName);
                }
                EditText artistEdit = edit("Artiste (ex: Queen)");
                dialogLayout.addView(trackEdit);
                dialogLayout.addView(gap(8));
                dialogLayout.addView(artistEdit);

                new AlertDialog.Builder(this)
                        .setTitle("Rechercher Paroles (LRCLIB)")
                        .setMessage("Indiquez le titre et l'artiste du morceau :")
                        .setView(dialogLayout)
                        .setPositiveButton("Rechercher", (d, w) -> {
                            String t = trackEdit.getText().toString().trim();
                            String a = artistEdit.getText().toString().trim();
                            if (!t.isEmpty()) {
                                performLrcLibSearch(t, a);
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            } else {
                showStatus("Chargez un audio pour rechercher les paroles.");
            }
            return;
        }

        String track = (currentTrackTitle != null && !currentTrackTitle.isEmpty()) ? currentTrackTitle : currentFileName;
        String artist = (currentTrackArtist != null) ? currentTrackArtist : "";
        performLrcLibSearch(track, artist);
    }

    private void performLrcLibSearch(String track, String artist) {
        String queryDisplay = track + (artist.isEmpty() ? "" : " - " + artist);
        addChatMessage(true, " Rechercher les paroles synchronisées sur LRCLIB pour : \"" + queryDisplay + "\"");
        addChatMessage(false, "Recherche en cours dans la base de données LRCLIB…");
        showStatus("Recherche LRCLIB : " + queryDisplay);

        new Thread(() -> {
            int durationSec = 0;
            if (player != null) {
                try {
                    durationSec = player.getDuration() / 1000;
                } catch (Exception ignored) {}
            }
            LrcLibClient.LyricsResult res = LrcLibClient.fetchLyrics(track, artist, durationSec);
            runOnUiThread(() -> {
                if (res != null && res.hasSynced()) {
                    List<LyricLine> parsed = LyricLine.parseLrcString(res.syncedLyrics);
                    if (!parsed.isEmpty()) {
                        lyricsList.clear();
                        lyricsList.addAll(parsed);
                        quote = res.plainLyrics != null && !res.plainLyrics.isEmpty() ? res.plainLyrics : res.trackName;
                        textMode = "scroll";
                        if (quoteInput != null) quoteInput.setText(quote);
                        if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes synchronisées (LRCLIB).");
                        autoSaveLrcFile();
                        visualizerView.invalidate();
                        if (visualPagePreview != null) visualPagePreview.invalidate();
                        saveSession();

                        String msg = " **Paroles synchronisées trouvées sur LRCLIB !**\n\n"
                                + " **Titre :** " + res.trackName + "\n"
                                + " **Artiste :** " + (res.artistName.isEmpty() ? "Inconnu" : res.artistName) + "\n"
                                + " **Synchronisation :** " + parsed.size() + " lignes LRC prêtes\n\n"
                                + "Les paroles s'animeront automatiquement en karaoké défilant lors de la lecture et de vos exports vidéo !";
                        updateLastAiMessage(msg);
                        Toast.makeText(MainActivity.this, "Paroles synchronisées LRCLIB chargées (" + parsed.size() + " lignes) !", Toast.LENGTH_LONG).show();
                        showStatus("Paroles LRCLIB chargées (" + parsed.size() + " lignes)");
                        return;
                    }
                } else if (res != null && res.plainLyrics != null && !res.plainLyrics.isEmpty()) {
                    quote = res.plainLyrics;
                    if (quoteInput != null) quoteInput.setText(quote);
                    visualizerView.invalidate();
                    if (visualPagePreview != null) visualPagePreview.invalidate();
                    saveSession();
                    String msg = "ℹ **Paroles textuelles trouvées sur LRCLIB** (sans synchronisation temporelle).\n\n"
                            + " **Titre :** " + res.trackName + "\n\n"
                            + " Pour synchroniser chaque mot au millième de seconde, utilisez le bouton ** Whisper LRC** !";
                    updateLastAiMessage(msg);
                    showStatus("Paroles textuelles chargées.");
                    return;
                }

                String notFoundMsg = " Aucune parole trouvée sur LRCLIB pour **\"" + track + "\"**.\n\n"
                        + " **Solution :** Utilisez le bouton ** Whisper LRC** pour que l'IA écoute et transcrive directement votre musique avec synchronisation temporelle !";
                updateLastAiMessage(notFoundMsg);
                showStatus("Aucune parole trouvée sur LRCLIB.");
            });
        }).start();
    }

    private void onUserSendChatMessage(String userMsg) {
        if (userMsg == null || userMsg.trim().isEmpty()) return;
        String cleanMsg = userMsg.trim();
        if (chatInputField != null) chatInputField.setText("");
        addChatMessage(true, cleanMsg);

        // Analyse contextuelle des commandes de projet IA Studio Pro
        AiProjectAssistant.CommandExecutionResult cmdResult = AiProjectAssistant.processUserPrompt(cleanMsg, new AiProjectAssistant.ProjectCommandCallback() {
            @Override
            public void onVisualizerLengthChanged(float scale) {
                visualizerLengthScale = scale;
                saveSession();
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }

            @Override
            public void onLyricsWidthChanged(float widthPercent) {
                lyricsMaxWidth = widthPercent;
                saveSession();
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }

            @Override
            public void onLyricsAlignmentChanged(String alignment) {
                saveSession();
                if (visualizerView != null) visualizerView.invalidate();
            }

            @Override
            public void onLyricsVerticalOffsetChanged(float offset) {
                textVerticalOffset = offset;
                saveSession();
                if (visualizerView != null) visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
            }

            @Override
            public void onLyricsModeChanged(String mode) {
                textMode = mode;
                saveSession();
                updateLyricsModeSelection(mode);
                if (visualizerView != null) visualizerView.invalidate();
            }

            @Override
            public void onExportConfigChanged(int width, int height, int fps) {
                formatW = width;
                formatH = height;
                exportFps = fps;
                saveSession();
                updateFormatSelection(width, height);
                updateFpsSelection(fps);
            }

            @Override
            public void onStyleChanged(String st) {
                style = st;
                saveSession();
                updateStyleSelection(st);
            }

            @Override
            public void onColorChanged(String colorHex) {
                activeColor = colorHex;
                saveSession();
                updateColorSelection(colorHex);
            }

            @Override
            public void onAudioEffectsToggled(boolean enabled) {
                AudioEffectsManager.getInstance().setEffectsEnabled(enabled);
                showStatus(enabled ? "Effets audio activés" : "Effets audio désactivés");
            }

            @Override
            public void onPlaybackCommand(boolean play) {
                if (play && !playing) togglePlayback();
                else if (!play && playing) togglePlayback();
            }

            @Override
            public void onOpenAudioBrowser() {
                openAudioBrowser();
            }

            @Override
            public void onTranslateLyricsCommand(String targetLanguage) {
                if (targetLanguage == null || targetLanguage.isEmpty()) {
                    openLyricsTranslationDialog();
                } else {
                    translateLyricsToLanguage(targetLanguage);
                }
            }
        });

        if (cmdResult.handled) {
            addChatMessage(false, cmdResult.feedbackMessage);
            showStatus(cmdResult.feedbackMessage);
            return;
        }

        String lower = cleanMsg.toLowerCase(Locale.ROOT);

        if (lower.contains("parole") || lower.contains("lyrics") || lower.contains("lrclib")) {
            if (lower.contains("recherch") || lower.contains("trouv") || lower.contains("cherche") || lower.contains("lrclib") || lower.contains("lrc")) {
                searchLrcLib(true);
                return;
            }
        }
        if (lower.contains("transcri") || lower.contains("whisper") || lower.contains("karaoké") || lower.contains("karaoke")) {
            transcribeFromChat();
            return;
        }
        if (lower.contains("citation") || lower.contains("phrase") || lower.contains("quote") || lower.contains("texte")) {
            generateQuoteChat(cleanMsg);
            return;
        }

        String key = groqKey();
        if (key.isEmpty()) {
            addChatMessage(false, " **Assistant Studio Pro IA**\n\nPour discuter librement avec moi ou demander des conseils créatifs, renseignez votre **Clé API Groq** dans le champ en haut puis cliquez sur **Sauvegarder**.\n\n*Note : La recherche LRCLIB reste disponible gratuitement sans clé !*");
            return;
        }

        TextView thinkingView = addChatMessage(false, " Réflexion en cours…");
        new Thread(() -> {
            try {
                String k = key;
                if (k.startsWith("Bearer ")) {
                    k = k.substring(7).trim();
                }
                String contextPrompt = "Tu es l'assistant IA créatif et technique de l'application Studio Pro Android.\n"
                        + "L'utilisateur conçoit des animations musicales avec ondes, spectres et paroles défilantes.\n"
                        + "Contexte actuel :\n"
                        + "- Morceau : " + (audioFile != null ? currentTrackTitle + " (Artiste : " + (currentTrackArtist.isEmpty() ? "Inconnu" : currentTrackArtist) + ")" : "Aucun audio chargé") + "\n"
                        + "- Style de visualiseur : " + style + "\n"
                        + "- Paroles : " + (lyricsList.isEmpty() ? "Non chargées" : lyricsList.size() + " lignes synchronisées") + "\n"
                        + "- Format export : " + formatW + "x" + formatH + " (" + exportFps + " FPS)\n\n"
                        + "Message utilisateur : " + cleanMsg + "\n\n"
                        + "Réponds de manière concise, chaleureuse, inspirante et bien structurée en français.";

                String response = GroqClient.chatInternal(k, contextPrompt, 400);
                runOnUiThread(() -> {
                    if (response != null && !response.trim().isEmpty()) {
                        if (thinkingView != null) {
                            renderMarkdownText(thinkingView, response);
                        } else {
                            updateLastAiMessage(response);
                        }
                    } else {
                        if (thinkingView != null) {
                            thinkingView.setText(" Aucune réponse reçue des serveurs Groq.");
                        }
                    }
                    scrollChatToBottom();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String err = " Erreur IA : " + friendlyError(e) + "\n\nVérifiez votre clé API Groq ou votre connexion Internet.";
                    if (thinkingView != null) {
                        thinkingView.setText(err);
                    } else {
                        updateLastAiMessage(err);
                    }
                    scrollChatToBottom();
                });
            }
        }).start();
    }

    private TextView addChatMessage(boolean isUser, String message) {
        if (chatMessagesContainer == null) return null;
        LinearLayout msgRow = new LinearLayout(this);
        msgRow.setOrientation(LinearLayout.HORIZONTAL);
        msgRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        msgRow.setPadding(0, dp(6), 0, dp(6));

        LinearLayout bubble = vertical();
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tv = new TextView(this);
        tv.setTextSize(14f);
        tv.setLineSpacing(dp(3), 1.18f);
        tv.setTextIsSelectable(true);

        if (isUser) {
            msgRow.setGravity(Gravity.END);
            shape(bubble, 0xFF3D2773, dp(16), 0xFF8362EA);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tv.setText(message);
            bubble.addView(tv);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(48);
            msgRow.addView(bubble, lp);
        } else {
            msgRow.setGravity(Gravity.START);
            shape(bubble, 0xFF141824, dp(16), 0xFF2E354A);

            LinearLayout aiHead = row();
            aiHead.setGravity(Gravity.CENTER_VERTICAL);
            TextView botTag = badge("ASSISTANT IA", 0xFF221640, 0xFFC29FFF, true);
            aiHead.addView(botTag);
            bubble.addView(aiHead);
            bubble.addView(gap(6));

            tv.setTextColor(0xFFE6E9F0);
            renderMarkdownText(tv, message);
            bubble.addView(tv);
            lastAiMessageView = tv;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(24);
            msgRow.addView(bubble, lp);
        }

        chatMessagesContainer.addView(msgRow);
        scrollChatToBottom();
        return tv;
    }

    private void renderMarkdownText(TextView tv, String raw) {
        if (raw == null) {
            tv.setText("");
            return;
        }
        String formatted = raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                .replace("\n", "<br/>");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tv.setText(Html.fromHtml(formatted, Html.FROM_HTML_MODE_COMPACT));
        } else {
            tv.setText(Html.fromHtml(formatted));
        }
    }

    private void scrollChatToBottom() {
        if (chatScrollView != null) {
            chatScrollView.postDelayed(() -> chatScrollView.fullScroll(View.FOCUS_DOWN), 60);
        }
    }

    private void updateLastAiMessage(String newText) {
        if (lastAiMessageView != null) {
            renderMarkdownText(lastAiMessageView, newText);
            scrollChatToBottom();
        } else {
            addChatMessage(false, newText);
        }
    }

    private void setupInitialChat() {
        if (chatMessagesContainer != null && chatMessagesContainer.getChildCount() == 0) {
            String greeting = " **Bonjour ! Je suis votre Assistant Musical & IA.**\n\n"
                    + "Voici tout ce que je peux accomplir pour vous :\n\n"
                    + " **1. Paroles Synchronisées LRCLIB (Gratuit) :** Recherche automatique des paroles officielles (.LRC) sur le web sans clé API.\n"
                    + " **2. Transcription Whisper :** Écoute intelligente de votre fichier audio pour en extraire des sous-titres karaoké au millième de seconde.\n"
                    + " **3. Citations & Slogans IA :** Génération de citations percutantes adaptées à votre style musical.\n"
                    + " **4. Conseils Visuels :** Suggestions de thèmes (Cyber, Particules, Glow, Miroir) et de couleurs néon.\n\n"
                    + " *Que souhaitez-vous faire ? Cliquez sur un bouton ci-dessus ou écrivez-moi votre demande !*";
            addChatMessage(false, greeting);
        }
    }

    private void updateAiUiStates() {
        boolean keyReady = !groqKey().isEmpty();

        // État de la clé API
        if (groqKeyBadge != null) {
            if (keyReady) {
                groqKeyBadge.setText("ACTIVE");
                shape(groqKeyBadge, 0x2610B981, dp(8), 0xFF10B981, false);
                groqKeyBadge.setTextColor(0xFF10B981);
            } else {
                groqKeyBadge.setText("NON CONFIGURÉE");
                shape(groqKeyBadge, 0x0DFFFFFF, dp(8), 0x33FFFFFF, false);
                groqKeyBadge.setTextColor(0x80FFFFFF);
            }
        }

        // État Carte Transcription
        if (aiTranscriptionBadge != null) {
            if (transcriptionState == 1) { // En cours
                aiTranscriptionBadge.setText("En cours…");
                shape(aiTranscriptionBadge, 0x2622D3EE, dp(10), 0xFF22D3EE, false);
                aiTranscriptionBadge.setTextColor(0xFF22D3EE);
                if (aiTranscriptionDesc != null) aiTranscriptionDesc.setText("Transcription audio Whisper en cours…");
                if (aiTranscribeActionBtn != null) {
                    aiTranscribeActionBtn.setText("Transcription en cours…");
                    aiTranscribeActionBtn.setEnabled(false);
                }
            } else if (transcriptionState == 2 && !lyricsList.isEmpty()) { // Terminé
                aiTranscriptionBadge.setText("Terminé");
                shape(aiTranscriptionBadge, 0x2610B981, dp(10), 0xFF10B981, false);
                aiTranscriptionBadge.setTextColor(0xFF10B981);
                if (aiTranscriptionDesc != null) aiTranscriptionDesc.setText(lyricsList.size() + " lignes synchronisées générées");
                if (aiTranscribeActionBtn != null) {
                    aiTranscribeActionBtn.setText("Re-transcrire l'audio");
                    aiTranscribeActionBtn.setEnabled(audioFile != null && keyReady);
                }
            } else { // Inactif
                aiTranscriptionBadge.setText("Inactif");
                shape(aiTranscriptionBadge, 0x0DFFFFFF, dp(10), 0x33FFFFFF, false);
                aiTranscriptionBadge.setTextColor(0x80FFFFFF);
                if (aiTranscriptionDesc != null) aiTranscriptionDesc.setText("Paroles auto via Whisper");
                if (aiTranscribeActionBtn != null) {
                    aiTranscribeActionBtn.setText("Lancer la transcription");
                    aiTranscribeActionBtn.setEnabled(audioFile != null && keyReady);
                }
            }
        }

        // État Carte Citation
        if (aiQuoteBadge != null) {
            if (quoteState == 1) { // En cours
                aiQuoteBadge.setText("En cours…");
                shape(aiQuoteBadge, 0x26C084FC, dp(10), 0xFFC084FC, false);
                aiQuoteBadge.setTextColor(0xFFC084FC);
                if (aiQuoteDesc != null) aiQuoteDesc.setText("Génération de la citation par Llama 3 en cours…");
                if (aiQuoteActionBtn != null) {
                    aiQuoteActionBtn.setText("Génération en cours…");
                    aiQuoteActionBtn.setEnabled(false);
                }
            } else if (quoteState == 2 && !lastAiResultContent.isEmpty() && "quote".equals(lastAiResultType)) { // Terminé
                aiQuoteBadge.setText("Terminé");
                shape(aiQuoteBadge, 0x2610B981, dp(10), 0xFF10B981, false);
                aiQuoteBadge.setTextColor(0xFF10B981);
                if (aiQuoteDesc != null) aiQuoteDesc.setText("Citation prête et intégrée au visualiseur");
                if (aiQuoteActionBtn != null) {
                    aiQuoteActionBtn.setText("Générer une autre citation");
                    aiQuoteActionBtn.setEnabled(keyReady);
                }
            } else { // Inactif
                aiQuoteBadge.setText("Inactif");
                shape(aiQuoteBadge, 0x0DFFFFFF, dp(10), 0x33FFFFFF, false);
                aiQuoteBadge.setTextColor(0x80FFFFFF);
                if (aiQuoteDesc != null) aiQuoteDesc.setText("Génère hook viral depuis lyrics");
                if (aiQuoteActionBtn != null) {
                    aiQuoteActionBtn.setText("Générer une citation");
                    aiQuoteActionBtn.setEnabled(keyReady);
                }
            }
        }

        // Zone de Résultat
        if (aiResultText != null) {
            if (!lastAiResultContent.isEmpty()) {
                aiResultText.setText(lastAiResultContent);
                aiResultText.setTextColor(Color.WHITE);
                if (aiResultTypeBadge != null) {
                    if ("transcription".equals(lastAiResultType)) {
                        aiResultTypeBadge.setText("TRANSCRIPTION (.LRC)");
                        shape(aiResultTypeBadge, 0x2622D3EE, dp(8), 0xFF22D3EE, false);
                        aiResultTypeBadge.setTextColor(0xFF22D3EE);
                    } else if ("quote".equals(lastAiResultType)) {
                        aiResultTypeBadge.setText("CITATION IA");
                        shape(aiResultTypeBadge, 0x26C084FC, dp(8), 0xFFC084FC, false);
                        aiResultTypeBadge.setTextColor(0xFFC084FC);
                    } else {
                        aiResultTypeBadge.setText("RÉSULTAT IA");
                        shape(aiResultTypeBadge, 0x2622D3EE, dp(8), 0xFF22D3EE, false);
                        aiResultTypeBadge.setTextColor(0xFF22D3EE);
                    }
                }
                if (aiResultCopyBtn != null) aiResultCopyBtn.setVisibility(View.VISIBLE);
                if (aiResultApplyBtn != null) aiResultApplyBtn.setVisibility(View.VISIBLE);
            } else {
                aiResultText.setText("Aucun résultat généré pour l'instant. Choisissez une fonction IA ci-dessus pour démarrer.");
                aiResultText.setTextColor(0x66FFFFFF);
                if (aiResultTypeBadge != null) {
                    aiResultTypeBadge.setText("EN ATTENTE");
                    shape(aiResultTypeBadge, 0x0DFFFFFF, dp(8), 0x33FFFFFF, false);
                    aiResultTypeBadge.setTextColor(0x80FFFFFF);
                }
                if (aiResultCopyBtn != null) aiResultCopyBtn.setVisibility(View.GONE);
                if (aiResultApplyBtn != null) aiResultApplyBtn.setVisibility(View.GONE);
            }
        }
    }

    private void copyAiResult() {
        if (lastAiResultContent == null || lastAiResultContent.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Résultat IA", lastAiResultContent);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Résultat copié dans le presse-papier !", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyAiResultToStudio() {
        if (lastAiResultContent == null || lastAiResultContent.isEmpty()) return;
        if ("quote".equals(lastAiResultType)) {
            quote = lastAiResultContent;
            if (quoteInput != null) quoteInput.setText(quote);
            saveSession();
            visualizerView.invalidate();
            if (visualPagePreview != null) visualPagePreview.invalidate();
            Toast.makeText(this, "Citation appliquée au visualiseur !", Toast.LENGTH_SHORT).show();
        } else if ("transcription".equals(lastAiResultType)) {
            quote = lastAiResultContent;
            textMode = "scroll";
            if (quoteInput != null) quoteInput.setText(quote);
            saveSession();
            visualizerView.invalidate();
            if (visualPagePreview != null) visualPagePreview.invalidate();
            updateKaraokeLinesView();
            Toast.makeText(this, "Paroles synchronisées appliquées !", Toast.LENGTH_SHORT).show();
        } else {
            quote = lastAiResultContent;
            if (quoteInput != null) quoteInput.setText(quote);
            saveSession();
            visualizerView.invalidate();
            Toast.makeText(this, "Texte appliqué au Studio !", Toast.LENGTH_SHORT).show();
        }
    }

    private String generateLrcPreviewString() {
        if (lyricsList.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = Math.min(lyricsList.size(), 8);
        for (int i = 0; i < count; i++) {
            LyricLine l = lyricsList.get(i);
            long min = l.startMs / 60000;
            long sec = (l.startMs % 60000) / 1000;
            long ms = (l.startMs % 1000) / 10;
            sb.append(String.format(Locale.US, "[%02d:%02d.%02d] %s\n", min, sec, ms, l.text));
        }
        if (lyricsList.size() > count) {
            sb.append("... (+").append(lyricsList.size() - count).append(" lignes synchronisées)");
        }
        return sb.toString().trim();
    }

    private void saveDirectGroqKey() {
        if (groqKeyInput == null) return;
        String k = groqKeyInput.getText().toString().trim();
        if (k.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer une clé Groq valide.", Toast.LENGTH_SHORT).show();
            if (aiStatus != null) aiStatus.setText("Clé non configurée • Requise pour Whisper & Citations");
            updateAiUiStates();
            return;
        }
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GROQ, KeyStoreUtil.encrypt(k)).apply();
            Toast.makeText(this, "Clé Groq enregistrée avec succès !", Toast.LENGTH_SHORT).show();
            if (aiStatus != null) aiStatus.setText("Clé Groq active • Whisper & Citations opérationnels");
            addChatMessage(false, " **Clé API Groq configurée.** Vous pouvez maintenant utiliser **Whisper LRC** et les fonctions IA !");
            setAudioButtonsEnabled(audioFile != null);
            updateAiUiStates();
        } catch (Exception e) {
            Toast.makeText(this, "Erreur : " + friendlyError(e), Toast.LENGTH_SHORT).show();
        }
    }

    private void testDirectGroqKey() {
        String k = groqKey();
        if (k.isEmpty()) {
            Toast.makeText(this, "Collez votre clé Groq d'abord !", Toast.LENGTH_SHORT).show();
            return;
        }
        saveDirectGroqKey();
        if (aiStatus != null) aiStatus.setText("Test de connexion en cours…");
        new Thread(() -> {
            try {
                String r = GroqClient.chat(k, "Réponds uniquement OK.");
                runOnUiThread(() -> {
                    boolean ok = r != null && r.contains("OK");
                    if (aiStatus != null) aiStatus.setText(ok ? "Connexion Groq réussie !" : "Groq répond : " + r);
                    Toast.makeText(MainActivity.this, ok ? "Connexion Groq Réussie !" : "Réponse Groq : " + r, Toast.LENGTH_LONG).show();
                    updateAiUiStates();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (aiStatus != null) aiStatus.setText("Échec test Groq : " + friendlyError(e));
                    Toast.makeText(MainActivity.this, "Échec test Groq : " + friendlyError(e), Toast.LENGTH_LONG).show();
                    updateAiUiStates();
                });
            }
        }).start();
    }

    private void showApiKeyDialog() {
        LinearLayout l = vertical();
        l.setPadding(dp(18), dp(10), dp(18), dp(10));
        TextView info = subtitle("Entrez votre clé API Groq (gsk_...) pour activer la transcription Whisper et la discussion IA interactive.");
        info.setPadding(0, 0, 0, dp(10));
        l.addView(info);

        EditText keyEdit = edit("Clé API Groq (gsk_...)");
        keyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyEdit.setText(groqKey());
        l.addView(keyEdit);

        new AlertDialog.Builder(this)
                .setTitle("Configuration Clé API Groq")
                .setView(l)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String k = keyEdit.getText().toString().trim();
                    if (!k.isEmpty()) {
                        try {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GROQ, KeyStoreUtil.encrypt(k)).apply();
                            Toast.makeText(this, "Clé Groq enregistrée !", Toast.LENGTH_SHORT).show();
                            addChatMessage(false, " **Clé API Groq enregistrée avec succès.** Whisper et le Chat IA sont entièrement opérationnels !");
                            updateAiUiStates();
                        } catch (Exception e) {
                            Toast.makeText(this, "Erreur : " + friendlyError(e), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Tester", (d, w) -> {
                    String k = keyEdit.getText().toString().trim();
                    if (!k.isEmpty()) {
                        new Thread(() -> {
                            try {
                                String r = GroqClient.chat(k, "Réponds uniquement OK.");
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Test Groq : " + (r.contains("OK") ? "Connexion Réussie !" : r), Toast.LENGTH_LONG).show();
                                    try {
                                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_GROQ, KeyStoreUtil.encrypt(k)).apply();
                                        updateAiUiStates();
                                    } catch (Exception ignored) {}
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Échec test : " + friendlyError(e), Toast.LENGTH_LONG).show());
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void generateQuoteDialog() {
        EditText themeEdit = edit("Thème (ex: Rap, Motivation, Amour, Nuit, Succès)");
        new AlertDialog.Builder(this)
                .setTitle("Générer une Citation IA")
                .setMessage("Indiquez le thème de la citation souhaitée :")
                .setView(themeEdit)
                .setPositiveButton("Générer", (d, w) -> {
                    String theme = themeEdit.getText().toString().trim();
                    generateQuoteChat(theme);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void generateQuoteChat(String theme) {
        String k = groqKey();
        if (k.isEmpty()) {
            addChatMessage(false, " **Clé Groq requise :** Veuillez configurer votre clé API Groq ci-dessus pour générer des citations.");
            return;
        }
        quoteState = 1; // En cours
        updateAiUiStates();
        addChatMessage(true, " Générer une citation sur le thème : " + (theme.isEmpty() ? "Musique et Détermination" : theme));
        addChatMessage(false, "Génération de la citation IA en cours…");
        new Thread(() -> {
            try {
                String q = GroqClient.quote(k, theme);
                runOnUiThread(() -> {
                    quote = q;
                    quoteState = 2; // Terminé
                    lastAiResultType = "quote";
                    lastAiResultContent = q;
                    if (quoteInput != null) quoteInput.setText(q);
                    saveSession();
                    visualizerView.invalidate();
                    if (visualPagePreview != null) visualPagePreview.invalidate();
                    updateAiUiStates();
                    String msg = " **Citation générée :**\n\n« *" + q + "* »\n\n*La citation a été directement appliquée à votre visualiseur !*";
                    updateLastAiMessage(msg);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    quoteState = 0; // Inactif
                    updateAiUiStates();
                    updateLastAiMessage("Erreur lors de la génération : " + friendlyError(e));
                });
            }
        }).start();
    }

    private void transcribeFromChat() {
        String k = groqKey();
        if (k.isEmpty()) {
            addChatMessage(false, " **Clé Groq manquante :** Configurez votre clé API Groq pour lancer la transcription Whisper.");
            return;
        }
        if (audioFile == null || !audioFile.exists()) {
            addChatMessage(false, " **Aucun fichier audio chargé :** Importez un fichier audio dans l'onglet **Audio** ou **Studio** d'abord !");
            return;
        }
        if (audioFile.length() > 25L * 1024L * 1024L) {
            addChatMessage(false, " **Fichier audio trop volumineux :** Le fichier dépasse 25 Mo. Veuillez utiliser un format compressé (MP3/M4A).");
            return;
        }

        transcriptionState = 1; // En cours
        updateAiUiStates();
        addChatMessage(true, " Transcrire l'audio et synchroniser les paroles (.LRC) avec Whisper IA");
        addChatMessage(false, " Analyse audio et transcription Whisper en cours (détection temporelle des segments)…");
        showStatus("Transcription Whisper en cours…");

        new Thread(() -> {
            try {
                GroqClient.Transcript t = GroqClient.transcribe(k, audioFile, audioMime, "");
                runOnUiThread(() -> {
                    lyricsList.clear();
                    lyricsList.addAll(t.lines);
                    quote = t.text;
                    textMode = "scroll";
                    transcriptionState = 2; // Terminé
                    lastAiResultType = "transcription";
                    lastAiResultContent = generateLrcPreviewString();
                    if (quoteInput != null) quoteInput.setText(quote);
                    if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes LRC générées.");
                    saveSession();
                    autoSaveLrcFile();
                    visualizerView.invalidate();
                    if (visualPagePreview != null) visualPagePreview.invalidate();
                    updateKaraokeLinesView();
                    updateAiUiStates();

                    String msg = " **Transcription Whisper réussie !**\n\n"
                            + " **Segments synchronisés :** " + lyricsList.size() + " lignes LRC extraites\n"
                            + " **Stockage temporaire :** Paroles conservées en mémoire & cache prêtes pour l'affichage ou l'intégration dans l'audio.\n\n"
                            + "Les paroles défileront en temps réel au rythme de la musique sur l'écran et vos exports vidéo !";
                    updateLastAiMessage(msg);
                    Toast.makeText(MainActivity.this, "Paroles synchronisées Whisper (" + lyricsList.size() + " lignes) prêtes !", Toast.LENGTH_LONG).show();
                    showStatus("Transcription réussie (" + lyricsList.size() + " lignes)");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    transcriptionState = 0; // Inactif
                    updateAiUiStates();
                    updateLastAiMessage(" Erreur transcription Whisper : " + friendlyError(e));
                });
            }
        }).start();
    }

    private void loadLrcFile(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<LyricLine> parsed = new ArrayList<>();
            String line;
            StringBuilder fullText = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                LyricLine l = LyricLine.parseLrcLine(line);
                if (l != null) {
                    parsed.add(l);
                    if (fullText.length() > 0) fullText.append(" ");
                    fullText.append(l.text);
                }
            }
            if (parsed.isEmpty()) throw new Exception("Aucune ligne LRC valide trouvée.");
            lyricsList.clear();
            lyricsList.addAll(parsed);
            quote = fullText.toString();
            textMode = "scroll";
            if (quoteInput != null) quoteInput.setText(quote);
            if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes LRC importées.");
            visualizerView.invalidate();
            saveSession();
            updateKaraokeLinesView();
            showStatus("LRC importé (" + lyricsList.size() + " lignes).");
            Toast.makeText(this, "Fichier .LRC importé avec succès !", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showStatus("LRC : " + friendlyError(e));
        }
    }

    private void embedLyricsDirectlyToAudio() {
        if (audioFile == null || !audioFile.exists()) { Toast.makeText(this, "Veuillez d'abord charger un fichier audio.", Toast.LENGTH_SHORT).show(); return; }
        if (lyricsList.isEmpty() && (quote == null || quote.trim().isEmpty())) { Toast.makeText(this, "Aucune parole à intégrer. Générez-les ou importez un fichier LRC.", Toast.LENGTH_SHORT).show(); return; }
        if (currentAudioUri == null) { Toast.makeText(this, "Veuillez sélectionner un fichier audio.", Toast.LENGTH_LONG).show(); return; }
        showStatus("Préparation sûre de l'intégration des paroles…");
        new Thread(() -> {
            File tagged = null;
            File verifyFile = null;
            File backup = null;
            try {
                String title = (currentTrackTitle == null || currentTrackTitle.isEmpty()) ? currentFileName : currentTrackTitle;
                String artist = currentTrackArtist == null ? "" : currentTrackArtist;
                tagged = AudioLyricsTagger.embedLyricsIntoAudio(MainActivity.this, audioFile, audioMime, title, artist, lyricsList, quote);
                if (tagged == null || !tagged.exists() || tagged.length() < 10) throw new Exception("Fichier MP3 préparé invalide.");
                AudioLyricsTagger.TagVerificationResult pre = AudioLyricsTagger.verifyEmbeddedTags(tagged);
                if (!pre.validId3 || !pre.hasUslt) throw new Exception("Les tags lyrics préparés sont invalides.");

                boolean written = false;
                if (currentAudioPath != null && !currentAudioPath.isEmpty()) {
                    File original = new File(currentAudioPath);
                    if (original.exists() && original.canWrite()) {
                        File tmp = new File(original.getParentFile(), "." + original.getName() + ".studiopro.tmp");
                        try (InputStream in = new FileInputStream(tagged); FileOutputStream fos = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[65536]; int n;
                            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                            fos.getFD().sync();
                        }
                        if (!tmp.exists() || tmp.length() != tagged.length()) throw new Exception("Copie temporaire MP3 incomplète.");
                        if (!tmp.renameTo(original)) {
                            tmp.delete();
                            throw new Exception("Remplacement atomique du MP3 impossible; fichier original conservé.");
                        }
                        verifyFile = original;
                        written = true;
                    }
                }

                if (!written) {
                    android.content.ContentResolver resolver = getContentResolver();
                    backup = new File(getCacheDir(), "mp3_backup_" + System.nanoTime());
                    try (InputStream in = resolver.openInputStream(currentAudioUri); OutputStream bout = new FileOutputStream(backup)) {
                        if (in == null) throw new Exception("Lecture de sauvegarde du fichier original impossible.");
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) != -1) bout.write(buf, 0, n);
                    }
                    try (InputStream in = new FileInputStream(tagged); OutputStream out = resolver.openOutputStream(currentAudioUri, "w")) {
                        if (out == null) throw new Exception("Écriture du fichier original impossible.");
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        out.flush();
                    } catch (Exception writeError) {
                        try (InputStream in = new FileInputStream(backup); OutputStream out = resolver.openOutputStream(currentAudioUri, "w")) {
                            if (out != null) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); out.flush(); }
                        } catch (Exception ignored) {}
                        throw writeError;
                    }
                    verifyFile = new File(getCacheDir(), "mp3_verify_" + System.nanoTime());
                    try (InputStream in = resolver.openInputStream(currentAudioUri); OutputStream out = new FileOutputStream(verifyFile)) {
                        if (in == null) throw new Exception("Impossible de relire le MP3 final.");
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }
                }

                AudioLyricsTagger.TagVerificationResult post = AudioLyricsTagger.verifyEmbeddedTags(verifyFile);
                if (!post.validId3 || !post.hasUslt) throw new Exception("Vérification du MP3 final échouée.");
                if (currentAudioPath != null && !currentAudioPath.isEmpty()) MediaScannerConnection.scanFile(MainActivity.this, new String[]{currentAudioPath}, null, null);

                File finalVerify = verifyFile;
                runOnUiThread(() -> {
                    showStatus("Paroles intégrées et vérifiées dans le fichier audio.");
                    addChatMessage(false, "Intégration des paroles terminée. " + post.getSummary() + " Le fichier final a été relu après écriture.");
                    Toast.makeText(MainActivity.this, "Paroles intégrées et vérifiées.", Toast.LENGTH_LONG).show();
                });
            } catch (SecurityException se) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && currentAudioUri != null) {
                    runOnUiThread(() -> {
                        try { PendingIntent pi = MediaStore.createWriteRequest(getContentResolver(), Collections.singletonList(currentAudioUri)); writeRequestLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build()); }
                        catch (Exception ex) { showStatus("Permission d'écriture requise : " + friendlyError(ex)); }
                    });
                } else {
                    runOnUiThread(() -> showStatus("Erreur de sécurité : " + friendlyError(se)));
                }
            } catch (Exception e) {
                File restore = backup;
                if (restore != null && restore.exists() && currentAudioUri != null) {
                    try (InputStream in = new FileInputStream(restore); OutputStream out = getContentResolver().openOutputStream(currentAudioUri, "w")) {
                        if (out != null) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); out.flush(); }
                    } catch (Exception ignored) {}
                }
                runOnUiThread(() -> showStatus("Intégration MP3 : " + friendlyError(e)));
            } finally {
                if (tagged != null && tagged.exists()) tagged.delete();
                if (verifyFile != null && verifyFile.getParentFile() != null && getCacheDir().equals(verifyFile.getParentFile()) && verifyFile.exists()) verifyFile.delete();
                if (backup != null && backup.exists()) backup.delete();
            }
        }).start();
    }


    private void openAudioEditor() {
        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(this, "Veuillez d'abord charger un fichier audio.", Toast.LENGTH_SHORT).show();
            return;
        }
        long duration = player != null && player.getDuration() > 0 ? player.getDuration() : 60000;
        AudioEditorDialog dialog = new AudioEditorDialog(this, audioFile, duration, trimStartMs, trimEndMs > 0 ? trimEndMs : duration,
                (startMs, endMs, volumeGain, speed, fadeInSec, fadeOutSec, normalize, loop) -> {
                    trimStartMs = startMs;
                    trimEndMs = endMs;
                    if (player != null) {
                        player.seekTo((int) trimStartMs);
                        player.setVolume(volumeGain, volumeGain);
                    }
                    saveSession();
                    updateAudioPageUi();
                    showStatus(String.format(Locale.FRENCH, "Édition appliquée : Découpe [%s - %s] · Gain %d%% · Vitesse %.2fx",
                            formatDuration(startMs), formatDuration(endMs), Math.round(volumeGain * 100), speed));
                    Toast.makeText(MainActivity.this, "Paramètres audio mis à jour avec succès !", Toast.LENGTH_SHORT).show();
                });
        dialog.show();
    }

    private void exportCurrentLrcFile() {
        if (lyricsList.isEmpty()) {
            Toast.makeText(this, "Aucune parole synchronisée à exporter.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String lrcContent = generateLrcString();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, currentFileName + ".lrc");
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/StudioPro");
            }
            Uri u = getContentResolver().insert(MediaStore.Files.getContentUri("external"), cv);
            if (u == null) throw new Exception("Impossible de créer le fichier LRC.");
            try (OutputStream out = getContentResolver().openOutputStream(u)) {
                out.write(lrcContent.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Fichier .LRC exporté dans Documents/StudioPro", Toast.LENGTH_LONG).show();
            showStatus("LRC exporté dans Documents/StudioPro");
        } catch (Exception e) {
            showStatus("Export LRC : " + friendlyError(e));
        }
    }

    private void openVoiceRecorderModal() {
        VoiceRecorderDialog dialog = new VoiceRecorderDialog(this, groqKey(), new VoiceRecorderDialog.OnRecordingResultListener() {
            @Override
            public void onApplyAudioAndLyrics(File recordedAudio, String mime, String trackName, List<LyricLine> lyrics, String fullText) {
                try {
                    if (audioFile != null && !audioFile.equals(recordedAudio)) {
                        audioFile.delete();
                    }
                    audioFile = recordedAudio;
                    audioMime = mime;
                    currentFileName = trackName;
                    currentTrackTitle = trackName;
                    currentTrackArtist = "Enregistrement Micro";

                    lyricsList.clear();
                    lyricsList.addAll(lyrics);
                    quote = fullText;
                    textMode = "scroll";
                    if (quoteInput != null) quoteInput.setText(quote);
                    if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes (Micro IA)");

                    initPlayer();
                    setAudioButtonsEnabled(true);
                    saveSession();
                    autoSaveLrcFile();
                    updateKaraokeLinesView();
                    visualizerView.invalidate();
                    if (visualPagePreview != null) visualPagePreview.invalidate();

                    showStatus("Enregistrement chargé (" + lyricsList.size() + " lignes LRC)");
                    Toast.makeText(MainActivity.this, "Audio et paroles synchronisées prêts !", Toast.LENGTH_SHORT).show();
                    addChatMessage(false, " **Nouvel enregistrement micro & transcription IA chargé !**\n\n " + lyricsList.size() + " lignes de paroles synchronisées générées via Groq Whisper.");
                } catch (Exception e) {
                    showStatus("Erreur : " + friendlyError(e));
                }
            }

            @Override
            public void onApplyLyricsOnly(List<LyricLine> lyrics, String fullText) {
                lyricsList.clear();
                lyricsList.addAll(lyrics);
                quote = fullText;
                textMode = "scroll";
                if (quoteInput != null) quoteInput.setText(quote);
                if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes (Micro IA)");
                saveSession();
                autoSaveLrcFile();
                updateKaraokeLinesView();
                visualizerView.invalidate();
                if (visualPagePreview != null) visualPagePreview.invalidate();
                showStatus("Paroles appliquées (" + lyricsList.size() + " lignes)");
                Toast.makeText(MainActivity.this, "Paroles synchronisées appliquées !", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void openAudioEffectsDialog() {
        if (player == null) {
            Toast.makeText(this, "Veuillez d'abord charger ou lancer un audio.", Toast.LENGTH_SHORT).show();
            return;
        }
        AudioEffectsDialog dialog = new AudioEffectsDialog(this, player);
        dialog.show();
    }

    private void openFullScreenLyrics() {
        MediaPlayer activePlayer = player != null ? player : MusicPlayerManager.getInstance(this).getPlayer();
        if (activePlayer == null) {
            Toast.makeText(this, "Veuillez d'abord charger ou lancer un audio.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<LyricLine> lyricsToDisplay = this.lyricsList;
        if (currentMusicPlayerDialog != null && currentMusicPlayerDialog.getLyricsList() != null && !currentMusicPlayerDialog.getLyricsList().isEmpty()) {
            lyricsToDisplay = currentMusicPlayerDialog.getLyricsList();
        }

        String titleToDisplay = currentTrackTitle;
        String artistToDisplay = currentTrackArtist;
        AudioBrowserDialog.AudioTrackItem curTrack = MusicPlayerManager.getInstance(this).getCurrentTrack();
        if (curTrack != null) {
            if (curTrack.title != null && !curTrack.title.trim().isEmpty()) {
                titleToDisplay = curTrack.title;
            }
            if (curTrack.artist != null && !curTrack.artist.trim().isEmpty()) {
                artistToDisplay = curTrack.artist;
            }
        }

        FullScreenLyricsDialog dialog = new FullScreenLyricsDialog(this, activePlayer, lyricsToDisplay, titleToDisplay, artistToDisplay);
        dialog.show();
    }

    public void openMusicPlayer() {
        try {
            MusicPlayerManager pm = MusicPlayerManager.getInstance(this);

            // Synchroniser le morceau actuellement actif dans MainActivity si un audio est chargé
            if (audioFile != null || currentAudioUri != null || (currentTrackTitle != null && !currentTrackTitle.isEmpty() && !currentTrackTitle.contains("Midnight Signals"))) {
                long duration = (player != null && player.getDuration() > 0) ? player.getDuration() : 0;
                long size = (audioFile != null) ? audioFile.length() : 0;
                long id = System.currentTimeMillis();
                AudioBrowserDialog.AudioTrackItem trackItem = new AudioBrowserDialog.AudioTrackItem(
                        id,
                        (currentTrackTitle != null && !currentTrackTitle.isEmpty()) ? currentTrackTitle : currentFileName,
                        (currentTrackArtist != null && !currentTrackArtist.isEmpty()) ? currentTrackArtist : "Artiste Inconnu",
                        "Studio Pro",
                        duration,
                        size,
                        System.currentTimeMillis() / 1000,
                        currentAudioUri != null ? currentAudioUri : (audioFile != null ? Uri.fromFile(audioFile) : null),
                        (audioFile != null) ? audioFile.getAbsolutePath() : currentAudioPath,
                        (audioMime != null) ? audioMime : "audio/mpeg"
                );
                AudioBrowserDialog.AudioTrackItem cur = pm.getCurrentTrack();
                if (cur == null || !TextUtils.equals(cur.title, trackItem.title)) {
                    pm.syncWithExternalTrack(trackItem);
                }
            }

            // Si le player de base du studio jouait, le mettre en pause pour éviter deux sons superposés
            pauseStudioPlayer();

            // Si le lecteur musical est déjà affiché, ne pas le réinitialiser
            if (currentMusicPlayerDialog != null && currentMusicPlayerDialog.isShowing()) {
                return;
            }

            MusicPlayerDialog dialog = new MusicPlayerDialog(this);
            AudioBrowserDialog.AudioTrackItem curTrack = pm.getCurrentTrack();
            if (curTrack != null && ((currentAudioUri != null && currentAudioUri.equals(curTrack.contentUri)) ||
                    (currentTrackTitle != null && currentTrackTitle.equalsIgnoreCase(curTrack.title)))) {
                dialog.setLyricsList(this.lyricsList);
            } else {
                dialog.setLyricsList(null);
            }
            dialog.setOnOpenLyricsListener(this::openFullScreenLyrics);
            dialog.setOnLyricsUpdatedListener(newLyrics -> {
                AudioBrowserDialog.AudioTrackItem ct = pm.getCurrentTrack();
                if (ct != null && currentAudioUri != null && currentAudioUri.equals(ct.contentUri)) {
                    this.lyricsList.clear();
                    if (newLyrics != null) {
                        this.lyricsList.addAll(newLyrics);
                    }
                    if (lyricsPreviewText != null) {
                        lyricsPreviewText.setText(lyricsList.size() + " paroles synchronisées");
                    }
                    updateKaraokeLinesView();
                    if (visualizerView != null) visualizerView.invalidate();
                    if (visualPagePreview != null) visualPagePreview.invalidate();
                }
            });
            currentMusicPlayerDialog = dialog;
            dialog.setOnDismissListener(d -> {
                if (currentMusicPlayerDialog == dialog) {
                    currentMusicPlayerDialog = null;
                }
            });
            dialog.show();

            // Si la permission est déjà présente et que la file est vide, rafraîchir en arrière-plan
            if (hasStorageAudioPermission() && (pm.getCurrentTrack() == null || pm.getQueue().isEmpty())) {
                pm.scanAndRefreshDeviceTracks(this, false, tracks -> {
                    if (dialog.isShowing()) {
                        dialog.onPermissionRefreshed();
                    }
                });
            }
        } catch (Throwable t) {
            Log.e("MainActivity", "Erreur lors de l'ouverture du lecteur audio", t);
            Toast.makeText(this, "Lecteur audio Studio Pro : " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public List<LyricLine> getLyricsList() {
        return lyricsList;
    }

    public Uri getCurrentAudioUri() {
        return currentAudioUri;
    }

    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public String getCurrentTrackArtist() {
        return currentTrackArtist;
    }

    public Bitmap getCurrentAudioCoverBitmap() {
        return currentAudioCoverBitmap;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public String getCurrentAudioPath() {
        return currentAudioPath;
    }

    @Override
    public void onTrackChanged(AudioBrowserDialog.AudioTrackItem track) {
        if (track == null) return;
        // Si le lecteur de musique lit un morceau qui n'est pas le projet ouvert dans le Studio,
        // ne pas écraser les métadonnées de travail du projet en cours (évite la confusion "comme si importé pour la traduction")
        if (currentAudioUri != null && !currentAudioUri.equals(track.contentUri)) {
            if (headerMusicPlayerBtn != null) {
                headerMusicPlayerBtn.setContentDescription("Lecteur : " + track.title);
            }
            return;
        }
        currentTrackTitle = track.title;
        currentTrackArtist = track.artist;
        currentFileName = track.title;
        if (homeTrackTitle != null) homeTrackTitle.setText(track.title);
        if (audioPageTrackTitle != null) audioPageTrackTitle.setText(track.title);
        if (audioPageTrackArtist != null) audioPageTrackArtist.setText(track.artist + " • " + formatDuration(track.durationMs));
        if (audioMeta != null) audioMeta.setText(track.title + " · " + formatDuration(track.durationMs));
        if (homeTrackTime != null) homeTrackTime.setText("00:00 / " + formatDuration(track.durationMs));
        if (audioWaveformView != null) audioWaveformView.postInvalidate();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (headerMusicPlayerBtn != null) {
            headerMusicPlayerBtn.setBackgroundResource(isPlaying ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
        }
        if (isPlaying) {
            // Le lecteur de musique démarre : arrêter obligatoirement le lecteur studio du visualiseur
            pauseStudioPlayer();
        }
        // Si aucun audio studio n'est chargé, synchroniser les boutons principaux avec le lecteur de musique
        if (player == null) {
            this.playing = isPlaying;
            if (playButton != null) playButton.setText(isPlaying ? "Pause" : "Lecture");
            if (audioPagePlayIcon != null) audioPagePlayIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            if (audioWaveformView != null) audioWaveformView.postInvalidate();
            if (visualizerView != null) visualizerView.postInvalidate();
        }
    }

    @Override
    public void onProgressUpdated(int currentPositionMs, int durationMs) {
        if (durationMs > 0) {
            if (homeTrackTime != null) homeTrackTime.setText(formatDuration(currentPositionMs) + " / " + formatDuration(durationMs));
            if (audioPageCurrentTime != null) audioPageCurrentTime.setText(formatDuration(currentPositionMs));
            if (audioPageDurationTime != null) audioPageDurationTime.setText(formatDuration(durationMs));
            if (audioCoverProgressBar != null) {
                float fraction = Math.max(0f, Math.min(1f, currentPositionMs / (float) durationMs));
                ViewGroup.LayoutParams lp = audioCoverProgressBar.getLayoutParams();
                if (lp != null) {
                    View parent = (View) audioCoverProgressBar.getParent();
                    if (parent != null && parent.getWidth() > 0) {
                        lp.width = (int) (parent.getWidth() * fraction);
                        audioCoverProgressBar.setLayoutParams(lp);
                    }
                }
            }
        }
        if (visualizerView != null) visualizerView.postInvalidate();
    }

    @Override
    public void onQueueChanged(java.util.List<AudioBrowserDialog.AudioTrackItem> queue, int currentIndex) {
        // Queue updated
    }

    @Override
    public void onPlaybackModesChanged(int repeatMode, boolean shuffleEnabled) {
        // Modes updated
    }

    @Override
    public void onSpeedChanged(float speed, float pitch) {
        // Speed/Pitch updated
    }

    private void openLyricsTimelineEditor() {
        LyricsTimelineEditorDialog dialog = new LyricsTimelineEditorDialog(this, player, lyricsList, updatedLyrics -> {
            lyricsList.clear();
            lyricsList.addAll(updatedLyrics);
            StringBuilder sb = new StringBuilder();
            for (LyricLine l : lyricsList) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(l.text);
            }
            quote = sb.toString();
            if (quoteInput != null) quoteInput.setText(quote);
            if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes synchronisées");
            saveSession();
            autoSaveLrcFile();
            updateKaraokeLinesView();
            visualizerView.invalidate();
            if (visualPagePreview != null) visualPagePreview.invalidate();
        });
        dialog.show();
    }

    private void openChatConversationScreen() {
        String key = groqKey();
        String bpmKeyStr = (audioAnalysisResult != null) ? (audioAnalysisResult.bpm + " BPM · " + audioAnalysisResult.musicalKey) : "";
        ChatConversationDialog dialog = new ChatConversationDialog(
                this,
                key,
                currentTrackTitle.isEmpty() ? currentFileName : currentTrackTitle,
                currentTrackArtist,
                bpmKeyStr,
                quote,
                new ChatConversationDialog.OnChatActionListener() {
                    @Override
                    public void onApplyAsLyrics(String text) {
                        if (text != null && !text.isEmpty()) {
                            quote = text;
                            if (quoteInput != null) quoteInput.setText(text);
                            List<LyricLine> parsed = LyricLine.parseLrcString(text);
                            if (!parsed.isEmpty()) {
                                lyricsList.clear();
                                lyricsList.addAll(parsed);
                                if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes synchronisées (Chat IA)");
                            } else {
                                if (lyricsPreviewText != null) lyricsPreviewText.setText("Texte appliqué depuis le Chat IA");
                            }
                            saveSession();
                            autoSaveLrcFile();
                            updateKaraokeLinesView();
                            visualizerView.invalidate();
                            if (visualPagePreview != null) visualPagePreview.invalidate();
                            showStatus("Texte appliqué depuis la discussion IA");
                        }
                    }

                    @Override
                    public void onApplyAsTitle(String title) {
                        if (title != null && !title.isEmpty()) {
                            currentTrackTitle = title;
                            if (audioPageTrackTitle != null) audioPageTrackTitle.setText(title);
                            saveSession();
                            visualizerView.invalidate();
                            if (visualPagePreview != null) visualPagePreview.invalidate();
                            showStatus("Titre mis à jour : " + title);
                        }
                    }
                }
        );
        dialog.show();
    }

    private void openLyricsTranslationDialog() {
        boolean hasLrc = !lyricsList.isEmpty();
        boolean hasText = (quote != null && !quote.trim().isEmpty()) || (quoteInput != null && quoteInput.getText() != null && !quoteInput.getText().toString().trim().isEmpty());
        if (!hasLrc && !hasText) {
            Toast.makeText(this, "Aucune parole chargée. Veuillez transcrire l'audio ou saisir un texte.", Toast.LENGTH_LONG).show();
            return;
        }
        final String key = groqKey();
        if (key.isEmpty()) {
            Toast.makeText(this, "Veuillez d'abord configurer votre clé Groq dans l'onglet IA.", Toast.LENGTH_SHORT).show();
            showPage(4);
            return;
        }

        final String[] languages = {
                "Haoussa (Hausa)",
                "Anglais",
                "Français",
                "Zarma (Djerma / Songhaï)",
                "Arabe"
        };
        new AlertDialog.Builder(this)
                .setTitle(hasLrc ? "Traduire les paroles synchronisées" : "Traduire le texte / paroles")
                .setItems(languages, (dialog, which) -> {
                    String targetLang = languages[which];
                    translateLyricsToLanguage(targetLang);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void translateLyricsToLanguage(String targetLanguage) {
        final String key = groqKey();
        if (key.isEmpty()) {
            Toast.makeText(this, "Clé Groq manquante.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean hasLrc = !lyricsList.isEmpty();
        String currentText = (quoteInput != null && quoteInput.getText() != null) ? quoteInput.getText().toString().trim() : (quote != null ? quote.trim() : "");

        if (!hasLrc && currentText.isEmpty()) {
            Toast.makeText(this, "Aucune parole à traduire.", Toast.LENGTH_SHORT).show();
            return;
        }

        showStatus("Traduction en " + targetLanguage + " via Groq...");
        Toast.makeText(this, "Traduction en " + targetLanguage + " en cours...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                if (hasLrc) {
                    List<LyricLine> translated = GroqClient.translateLyrics(key, lyricsList, targetLanguage);
                    runOnUiThread(() -> {
                        lyricsList.clear();
                        lyricsList.addAll(translated);
                        StringBuilder sb = new StringBuilder();
                        for (LyricLine l : lyricsList) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(l.text);
                        }
                        quote = sb.toString();
                        if (quoteInput != null) quoteInput.setText(quote);
                        if (lyricsPreviewText != null) lyricsPreviewText.setText(lyricsList.size() + " lignes (Traduit en " + targetLanguage + ")");
                        saveSession();
                        autoSaveLrcFile();
                        updateKaraokeLinesView();
                        visualizerView.invalidate();
                        if (visualPagePreview != null) visualPagePreview.invalidate();
                        showStatus("Paroles traduites en " + targetLanguage + " !");
                        Toast.makeText(MainActivity.this, "Paroles traduites en " + targetLanguage + " avec synchronisation conservée !", Toast.LENGTH_LONG).show();
                        addChatMessage(false, " **Paroles traduites en " + targetLanguage + " avec succès !**\nLes horodatages synchronisés `[mm:ss.xx]` ont été strictement préservés.");
                    });
                } else {
                    String translatedText = GroqClient.translateText(key, currentText, targetLanguage);
                    runOnUiThread(() -> {
                        quote = translatedText;
                        if (quoteInput != null) quoteInput.setText(quote);
                        if (lyricsPreviewText != null) lyricsPreviewText.setText("Texte traduit en " + targetLanguage);
                        saveSession();
                        visualizerView.invalidate();
                        if (visualPagePreview != null) visualPagePreview.invalidate();
                        showStatus("Texte traduit en " + targetLanguage + " !");
                        Toast.makeText(MainActivity.this, "Texte traduit en " + targetLanguage + " !", Toast.LENGTH_LONG).show();
                        addChatMessage(false, " **Texte traduit en " + targetLanguage + " :**\n\n" + translatedText);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showStatus("Erreur traduction : " + friendlyError(e));
                    Toast.makeText(MainActivity.this, "Erreur traduction : " + friendlyError(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void analyzeAudioBpmAndKey() {
        if (audioFile == null || !audioFile.exists() || player == null) return;
        new Thread(() -> {
            try {
                int duration = player.getDuration();
                audioAnalysisResult = BpmKeyDetector.analyzeAudio(audioFile, waveform, duration);
                runOnUiThread(() -> {
                    if (audioBpmKeyBadge != null && audioAnalysisResult != null) {
                        audioBpmKeyBadge.setText(" " + audioAnalysisResult.bpm + " BPM · " + audioAnalysisResult.musicalKey + " (" + audioAnalysisResult.tempoCategory + ")");
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void autoSaveLrcFile() {
        if (lyricsList.isEmpty()) return;
        try {
            // Sauvegarde temporaire en cache privé de l'application (pas de pollution dans Documents)
            String lrcContent = generateLrcString();
            File tempLrc = new File(getCacheDir(), "temp_lyrics.lrc");
            try (OutputStream out = new FileOutputStream(tempLrc)) {
                out.write(lrcContent.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private void cleanupDocumentLyricsAndLegacyFiles() {
        new Thread(() -> {
            try {
                File tempLrc = new File(getCacheDir(), "temp_lyrics.lrc");
                if (tempLrc.exists()) tempLrc.delete();

                File docsBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File targetDir = new File(docsBase, "StudioPro");
                if (targetDir.exists() && targetDir.isDirectory()) {
                    File[] list = targetDir.listFiles();
                    if (list != null) {
                        for (File f : list) {
                            String name = f.getName();
                            if (name.startsWith("Voix_IA_") && (name.endsWith(".lrc") || name.endsWith(".m4a"))) {
                                f.delete();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }


    private String generateLrcString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ti:").append(currentFileName).append("]\n");
        sb.append("[by:Studio Pro v2.8.4]\n");
        for (LyricLine l : lyricsList) {
            long min = l.startMs / 60000;
            long sec = (l.startMs % 60000) / 1000;
            long ms = (l.startMs % 1000) / 10;
            sb.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, ms, l.text));
        }
        return sb.toString();
    }

    private void detectSilence() {
        if (audioFile == null) return;
        detectButton.setEnabled(false);
        detectButton.setText("Analyse en cours…");
        showStatus("Analyse des silences…");
        new Thread(() -> {
            try {
                long[] r = AudioAnalyzer.detect(audioFile);
                trimStartMs = r[0];
                trimEndMs = r[1];
                runOnUiThread(() -> {
                    detectButton.setText("Détecter Silences");
                    updateSilenceTimelineUi();
                    showStatus(String.format(Locale.US, "Zone audio active : %.2fs → %.2fs", trimStartMs / 1000.0, trimEndMs / 1000.0));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    detectButton.setText("Détecter Silences");
                    showStatus("Détection : " + friendlyError(e));
                });
            } finally {
                runOnUiThread(() -> detectButton.setEnabled(audioFile != null));
            }
        }).start();
    }

    private void askLoopDuration() {
        EditText e = edit("Durée de boucle en secondes (ex: 15.0)");
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle("Créer une Boucle")
                .setView(e)
                .setPositiveButton("Appliquer", (d, w) -> {
                    try {
                        double sec = Double.parseDouble(e.getText().toString().trim());
                        if (sec <= 0) throw new Exception("Durée invalide.");
                        loopTargetMs = (long) (sec * 1000);
                        saveSession();
                        showStatus("Boucle de " + sec + "s configurée.");
                    } catch (Exception ex) {
                        showStatus("Boucle : " + friendlyError(ex));
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void loadImage(Uri uri, boolean bg) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("Image illisible.");
            final int maxDim = 2048;
            int sample = 1;
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2;
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, opts);
            }
            if (b == null) throw new Exception("Image illisible.");
            if (bg) { backgroundBitmap = b; updateBgSelection("image"); }
            else watermarkBitmap = b;
            if (visualizerView != null) visualizerView.invalidate();
            if (visualPagePreview != null) visualPagePreview.invalidate();
            showStatus("Image importée.");
        } catch (Exception e) {
            showStatus("Image : " + friendlyError(e));
        }
    }

    private void startOrStopExport() {
        if (ExportState.running) {
            ExportState.stop.set(true);
            return;
        }
        new Thread(() -> {
            try {
                exportCurrent(formatW, formatH);
            } catch (Exception e) {
                ExportState.running = false;
                ExportState.stop.set(false);
                runOnUiThread(() -> {
                    showStatus("Export : " + friendlyError(e));
                    if (exportStatusInfo != null) exportStatusInfo.setText("Erreur : " + friendlyError(e));
                });
            }
        }).start();
    }

    private void exportCurrent(int w, int h) throws Exception {
        if (audioFile == null || player == null) throw new Exception("Chargez un fichier audio avant d'exporter.");
        long calcDuration = loopTargetMs > 0 ? loopTargetMs : (trimEndMs > trimStartMs ? trimEndMs - trimStartMs : player.getDuration());
        if (calcDuration <= 0) calcDuration = 10000;
        final long duration = calcDuration;

        File videoOnly = new File(getCacheDir(), "video_" + System.nanoTime() + ".mp4");
        android.media.MediaRecorder mr = new android.media.MediaRecorder();
        mr.setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE);
        mr.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
        mr.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264);
        mr.setVideoSize(w, h);
        mr.setVideoFrameRate(exportFps);
        int bitrate = (w >= 1080) ? 12000000 : 6000000;
        mr.setVideoEncodingBitRate(bitrate);
        mr.setOutputFile(videoOnly.getAbsolutePath());
        mr.prepare();

        android.view.Surface surface = mr.getSurface();
        mr.start();
        ExportState.running = true;
        ExportState.stop.set(false);

        runOnUiThread(() -> {
            exportButton.setText(" Arrêter l'export");
            shape(exportButton, 0xFF7F1D1D, dp(24), 0xFFEF4444, true);
            if (exportStateBadge != null) {
                exportStateBadge.setText("EN COURS");
                shape(exportStateBadge, 0x2622D3EE, dp(8), 0xFF22D3EE, false);
                exportStateBadge.setTextColor(0xFF22D3EE);
            }
            if (exportProgressBar != null) {
                exportProgressBar.setVisibility(View.VISIBLE);
                exportProgressBar.setProgress(0);
            }
            if (exportPercentText != null) exportPercentText.setText("0%");
            if (exportEtaText != null) exportEtaText.setText("Démarrage…");
            if (exportDetailInfoText != null) {
                exportDetailInfoText.setText("Encodage matériel " + w + "×" + h + " @ " + exportFps + " FPS…");
            }
            showStatus("Rendu en cours (" + w + "×" + h + " @ " + exportFps + "fps)…");
        });

        CountDownLatch done = new CountDownLatch(1);
        long frameIntervalMs = 1000L / exportFps;

        runOnUiThread(() -> {
            try {
                player.seekTo((int) trimStartMs);
                player.start();
                playing = true;
                long startTime = System.currentTimeMillis();

                Runnable frameRunnable = new Runnable() {
                    public void run() {
                        long elapsed = System.currentTimeMillis() - startTime;
                        exportCurrentMs = trimStartMs + elapsed;
                        if (!ExportState.running || ExportState.stop.get() || elapsed >= duration) {
                            done.countDown();
                            return;
                        }
                        try {
                            Canvas c = surface.lockHardwareCanvas();
                            if (c != null) {
                                visualizerView.drawInto(c, w, h, exportCurrentMs, exportIncludeVisualizer, exportIncludeLyrics);
                                surface.unlockCanvasAndPost(c);
                            }
                        } catch (Exception ignored) {}

                        int progress = (int) Math.min(100, (elapsed * 100) / duration);
                        long remainingMs = Math.max(0, duration - elapsed);
                        long remSec = (remainingMs + 999) / 1000;

                        if (exportProgressBar != null) exportProgressBar.setProgress(progress);
                        if (exportPercentText != null) exportPercentText.setText(progress + "%");
                        if (exportEtaText != null) exportEtaText.setText("ETA ~" + remSec + "s");
                        if (exportDetailInfoText != null && elapsed > 200) {
                            long curFrame = (elapsed * exportFps) / 1000;
                            long totalFrames = (duration * exportFps) / 1000;
                            exportDetailInfoText.setText(String.format(Locale.US, "Frame %d / %d · %d FPS (%d%%)", curFrame, totalFrames, exportFps, progress));
                        }
                        handler.postDelayed(this, frameIntervalMs);
                    }
                };
                frameRunnable.run();
            } catch (Exception e) {
                done.countDown();
            }
        });

        done.await();
        try { player.pause(); } catch (Exception ignored) {}
        playing = false;
        try { mr.stop(); } catch (Exception ignored) {}
        mr.release();
        surface.release();
        ExportState.running = false;

        if (ExportState.stop.get()) {
            videoOnly.delete();
            runOnUiThread(() -> {
                exportButton.setText("⇈ Exporter la vidéo MP4");
                exportButton.setBackgroundResource(R.drawable.btn_gradient);
                if (exportStateBadge != null) {
                    exportStateBadge.setText("ANNULÉ");
                    shape(exportStateBadge, 0x26EF4444, dp(8), 0xFFEF4444, false);
                    exportStateBadge.setTextColor(0xFFEF4444);
                }
                if (exportEtaText != null) exportEtaText.setText("Interrompu");
                if (exportDetailInfoText != null) {
                    exportDetailInfoText.setText("Exportation annulée par l'utilisateur.");
                }
                showStatus("Export annulé.");
            });
            return;
        }

        runOnUiThread(() -> {
            if (exportStateBadge != null) {
                exportStateBadge.setText("FINALISATION");
                shape(exportStateBadge, 0x26A855F7, dp(8), 0xFFA855F7, false);
                exportStateBadge.setTextColor(0xFFA855F7);
            }
            if (exportEtaText != null) exportEtaText.setText("Muxing audio…");
            if (exportDetailInfoText != null) {
                exportDetailInfoText.setText("Multiplexage audio / vidéo en cours...");
            }
        });

        File out = new File(getCacheDir(), "final_" + System.nanoTime() + ".mp4");
        MuxerUtil.mux(MainActivity.this, videoOnly, audioFile, out, trimStartMs * 1000L, (trimEndMs > trimStartMs ? trimEndMs : player.getDuration()) * 1000L, loopTargetMs * 1000L);
        validateMp4(out);
        saveVideo(out, w, h);
        videoOnly.delete();
        out.delete();

        runOnUiThread(() -> {
            exportButton.setText("⇈ Exporter la vidéo MP4");
            exportButton.setBackgroundResource(R.drawable.btn_gradient);
            if (exportProgressBar != null) exportProgressBar.setProgress(100);
            if (exportPercentText != null) exportPercentText.setText("100%");
            if (exportEtaText != null) exportEtaText.setText("Terminé");
            if (exportStateBadge != null) {
                exportStateBadge.setText("SUCCÈS");
                shape(exportStateBadge, 0x2610B981, dp(8), 0xFF10B981, false);
                exportStateBadge.setTextColor(0xFF10B981);
            }
            if (exportDetailInfoText != null) {
                exportDetailInfoText.setText("Vidéo enregistrée dans Films/StudioPro !");
            }
            showStatus("Vidéo enregistrée dans Films/StudioPro !");
            if (snapshotButton != null) snapshotButton.setEnabled(true);
            Toast.makeText(MainActivity.this, "Export terminé : Enregistré dans Films/StudioPro", Toast.LENGTH_LONG).show();
        });
    }

    private void validateMp4(File file) throws Exception {
        if (file == null || !file.exists() || file.length() < 1024) throw new Exception("MP4 final absent ou vide.");
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        try {
            ex.setDataSource(file.getAbsolutePath());
            int video = -1, audio = -1;
            long maxDuration = 0L;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                android.media.MediaFormat mf = ex.getTrackFormat(i);
                String mime = mf.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/") && video < 0) video = i;
                if (mime != null && mime.startsWith("audio/") && audio < 0) audio = i;
                if (mf.containsKey(android.media.MediaFormat.KEY_DURATION)) maxDuration = Math.max(maxDuration, mf.getLong(android.media.MediaFormat.KEY_DURATION));
            }
            if (video < 0) throw new Exception("MP4 sans piste vidéo.");
            if (audio < 0) throw new Exception("MP4 sans piste audio.");
            if (maxDuration <= 0L) throw new Exception("Durée MP4 invalide.");
        } finally {
            try { ex.release(); } catch (Exception ignored) {}
        }
    }

    private void saveVideo(File f, int w, int h) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, currentFileName + "-" + w + "x" + h + ".mp4");
        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StudioPro");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        if (u == null) throw new Exception("Impossible de créer l'entrée vidéo dans la galerie.");
        try {
            try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(u)) {
                if (out == null) throw new Exception("Ouverture du fichier vidéo impossible.");
                byte[] b = new byte[32768];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(u, done, null, null);
            }
        } catch (Exception e) {
            try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
            throw e;
        }
    }


    private void snapshot() {
        try {
            Bitmap b = visualizerView.exportBitmap(formatW, formatH);
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, currentFileName + "-cover.png");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudioPro");
            }
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new Exception("Stockage image impossible.");
            try (OutputStream out = getContentResolver().openOutputStream(u)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            showStatus("Image PNG enregistrée dans Images/StudioPro.");
            Toast.makeText(this, "Image PNG enregistrée !", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showStatus("Capture : " + friendlyError(e));
        }
    }

    private void showStatus(String s) {
        if (status != null) status.setText(s);
        if (audioMeta != null && s.startsWith("Audio")) audioMeta.setText(s);
    }

    private String friendlyError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) return e.getClass().getSimpleName();
        try {
            if (m.startsWith("{")) {
                JSONObject o = new JSONObject(m).optJSONObject("error");
                if (o != null && !o.optString("message").isEmpty()) return o.optString("message");
            }
        } catch (Exception ignored) {}
        return m.replace("\n", " ").trim();
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void saveSession() {
        try {
            JSONObject o = new JSONObject();
            o.put("style", style);
            o.put("background", backgroundMode);
            o.put("watermark", watermarkInput == null ? watermark : watermarkInput.getText().toString());
            o.put("glow", glow);
            o.put("sensitivity", sensitivity);
            o.put("shakeIntensity", shakeIntensity);
            o.put("beatShakeEnabled", beatShakeEnabled);
            o.put("textMode", textMode);
            o.put("textPosition", textPosition);
            o.put("textSize", textSize);
            o.put("activeColor", activeColor);
            o.put("formatW", formatW);
            o.put("formatH", formatH);
            o.put("exportFps", exportFps);
            o.put("loopTargetMs", loopTargetMs);
            o.put("visualizerLengthScale", Math.max(0.50f, Math.min(1.25f, visualizerLengthScale)));
            o.put("lyricsScale", lyricsScale);
            o.put("visualizerIntensity", visualizerIntensity);
            o.put("lyricsIntensity", lyricsIntensity);
            o.put("exportIncludeVisualizer", exportIncludeVisualizer);
            o.put("exportIncludeLyrics", exportIncludeLyrics);

            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("session", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadPreferences() {
        try {
            JSONObject o = new JSONObject(getSharedPreferences(PREFS, MODE_PRIVATE).getString("session", "{}"));
            style = o.optString("style", style);
            backgroundMode = o.optString("background", backgroundMode);
            watermark = o.optString("watermark", watermark);
            glow = o.optInt("glow", glow);
            sensitivity = (float) o.optDouble("sensitivity", sensitivity);
            shakeIntensity = (float) o.optDouble("shakeIntensity", shakeIntensity);
            beatShakeEnabled = o.optBoolean("beatShakeEnabled", beatShakeEnabled);
            textMode = o.optString("textMode", textMode);
            textPosition = o.optString("textPosition", textPosition);
            textSize = o.optInt("textSize", textSize);
            activeColor = o.optString("activeColor", activeColor);
            formatW = o.optInt("formatW", formatW);
            formatH = o.optInt("formatH", formatH);
            exportFps = o.optInt("exportFps", exportFps);
            loopTargetMs = o.optLong("loopTargetMs", loopTargetMs);
            visualizerLengthScale = (float) o.optDouble("visualizerLengthScale", visualizerLengthScale);
            visualizerLengthScale = Math.max(0.50f, Math.min(1.25f, visualizerLengthScale));
            lyricsScale = (float) o.optDouble("lyricsScale", lyricsScale);
            visualizerIntensity = (float) o.optDouble("visualizerIntensity", visualizerIntensity);
            lyricsIntensity = (float) o.optDouble("lyricsIntensity", lyricsIntensity);
            exportIncludeVisualizer = o.optBoolean("exportIncludeVisualizer", exportIncludeVisualizer);
            exportIncludeLyrics = o.optBoolean("exportIncludeLyrics", exportIncludeLyrics);

            // Les paroles et citations sont éphémères et toujours réinitialisées à vide à chaque démarrage
            lyricsList.clear();
            quote = "";
            currentTrackTitle = "";
            currentTrackArtist = "";

            if (quoteInput != null) quoteInput.setText("");
            if (watermarkInput != null) watermarkInput.setText(watermark);
            if (lyricsPreviewText != null) {
                lyricsPreviewText.setText("Aucune parole chargée");
            }
            updateKaraokeLinesView();

            updateStyleSelection(style);
            updateColorSelection(activeColor);
            updateBgSelection(backgroundMode);
            updateLyricsModeSelection(textMode);
            updateFormatSelection(formatW, formatH);
            updateFpsSelection(exportFps);
        } catch (Exception ignored) {}

        try {
            if (groqKeyInput != null) {
                groqKeyInput.setText(KeyStoreUtil.decrypt(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_GROQ, "")));
            }
        } catch (Exception ignored) {}
        boolean ready = !groqKey().isEmpty();
        if (transcribeButton != null) transcribeButton.setEnabled(audioFile != null && ready);
        if (generateQuoteButton != null) generateQuoteButton.setEnabled(ready);
    }

    static final class ExportState {
        static volatile boolean running;
        static final AtomicBoolean stop = new AtomicBoolean(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingNotificationIntent(intent);
    }

    private void handleIncomingNotificationIntent(Intent intent) {
        if (intent == null) return;
        if ("OPEN_MUSIC_PLAYER".equals(intent.getAction()) || intent.getBooleanExtra("open_music_player", false)) {
            handler.postDelayed(this::openMusicPlayer, 200);
        }
    }

    @Override
    protected void onDestroy() {
        MusicPlayerManager.getInstance(this).removeListener(this);
        MusicPlayerManager.getInstance(this).setPlaybackConflictListener(null);
        cleanupDocumentLyricsAndLegacyFiles();
        ExportState.stop.set(true);
        releasePlayer();
        if (!ExportState.running) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void releasePlayer() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Exception ignored) {}
            try { visualizer.release(); } catch (Exception ignored) {}
            visualizer = null;
        }
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    // ==========================================
    // Mini Lyrics Scrubber View (Mockup Paroles)
    // ==========================================
    public class MiniLyricsScrubberView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] heights = new float[48];

        public MiniLyricsScrubberView(Context ctx) {
            super(ctx);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            barPaint.setColor(0x2EFFFFFF); // rgba(255,255,255,0.18)
            barPaint.setStyle(Paint.Style.FILL);
            needlePaint.setColor(Color.WHITE);
            needlePaint.setStrokeWidth(dp(2));
            needlePaint.setShadowLayer(dp(8), 0, 0, Color.WHITE);
            for (int i = 0; i < heights.length; i++) {
                heights[i] = (18 + (i * 13) % 82) / 100f;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int count = heights.length;
            float barWidth = dp(3);
            float totalBarSpace = count * barWidth;
            float spacing = Math.max(dp(2), (w - totalBarSpace) / (count - 1));

            float curProgress = 0.56f; // default 56%
            if (player != null && player.getDuration() > 0) {
                curProgress = Math.max(0f, Math.min(1f, player.getCurrentPosition() / (float) player.getDuration()));
            }

            for (int i = 0; i < count; i++) {
                float x = i * (barWidth + spacing);
                float barH = heights[i] * (h - dp(8));
                float top = h - barH - dp(4);
                float bottom = h - dp(4);
                c.drawRoundRect(new RectF(x, top, x + barWidth, bottom), dp(2), dp(2), barPaint);
            }

            // Aiguille blanche avec glow au niveau de la position courante
            float needleX = w * curProgress;
            c.drawLine(needleX, 0, needleX, h, needlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float frac = Math.max(0f, Math.min(1f, event.getX() / (float) getWidth()));
                if (player != null && player.getDuration() > 0) {
                    player.seekTo((int) (frac * player.getDuration()));
                    updateKaraokeLinesView();
                    postInvalidate();
                    if (audioWaveformView != null) audioWaveformView.postInvalidate();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // ==========================================
    // Interactive Waveform View & Scrubber
    // ==========================================
    public class InteractiveWaveformView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] sampleAmplitudes = new float[64];

        public InteractiveWaveformView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            for (int i = 0; i < sampleAmplitudes.length; i++) {
                sampleAmplitudes[i] = (float) ((20.0 + Math.sin(i * 0.4) * 30.0 + 40.0) / 100.0);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = Math.max(0, Math.min(getWidth(), event.getX()));
                float frac = getWidth() > 0 ? x / (float) getWidth() : 0f;
                if (player != null && player.getDuration() > 0) {
                    int targetMs = (int) (frac * player.getDuration());
                    player.seekTo(targetMs);
                    updateAudioPageUi();
                    if (homeTrackTime != null) {
                        homeTrackTime.setText(formatDuration(targetMs) + " / " + formatDuration(player.getDuration()));
                    }
                    invalidate();
                    if (visualizerView != null) visualizerView.invalidate();
                    updateKaraokeLinesView();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            float curMs = (player != null && player.getDuration() > 0) ? player.getCurrentPosition() : 45000f;
            float totalMs = (player != null && player.getDuration() > 0) ? player.getDuration() : 180000f;
            float progress = Math.max(0f, Math.min(1f, curMs / totalMs));

            int barCount = sampleAmplitudes.length;
            float spacing = dp(2);
            float barWidth = Math.max(dp(2), (w - (barCount - 1) * spacing) / (float) barCount);
            float maxBarH = h - dp(4);

            int activeColorVal = 0xFF22D3EE;
            try {
                activeColorVal = Color.parseColor(activeColor);
            } catch (Exception ignored) {}

            for (int i = 0; i < barCount; i++) {
                float x = i * (barWidth + spacing);
                float barFrac = (float) i / (float) barCount;
                boolean isPlayed = barFrac <= progress;

                float amp = sampleAmplitudes[i];
                if (playing && fft != null && fft.length > 0) {
                    int fftIdx = (int) ((float) i / (float) barCount * (fft.length / 4));
                    if (fftIdx < fft.length) {
                        float liveAmp = Math.abs(fft[fftIdx]) / 128f;
                        amp = Math.max(amp * 0.7f, liveAmp * 0.9f);
                    }
                }

                float barH = Math.max(dp(4), amp * maxBarH);
                float top = h - barH;
                float bottom = h;

                if (isPlayed) {
                    activeBarPaint.setColor(activeColorVal);
                    c.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2f, barWidth / 2f, activeBarPaint);
                } else {
                    barPaint.setColor(0x26FFFFFF); // rgba(255,255,255,0.15)
                    c.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2f, barWidth / 2f, barPaint);
                }
            }

            // Head cursor line
            float cursorX = progress * w;
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setStrokeWidth(dp(2));
            cursorPaint.setShadowLayer(dp(6), 0, 0, activeColorVal);
            c.drawLine(cursorX, dp(2), cursorX, h - dp(2), cursorPaint);
        }
    }

    // ==========================================
    // Visualizer View & Dynamic Rendering Engine
    // ==========================================
    public class VisualizerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final List<Particle> particles = new ArrayList<>();
        private float smoothedBass = 0f;

        VisualizerView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            for (int i = 0; i < 65; i++) {
                particles.add(new Particle(random.nextFloat(), random.nextFloat(), (random.nextFloat() - 0.5f) * 0.005f, -0.002f - random.nextFloat() * 0.006f, 3 + random.nextInt(6)));
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            long curMs = player != null ? player.getCurrentPosition() : 0;
            drawInto(c, getWidth(), getHeight(), curMs);
            if (playing) postInvalidateDelayed(33);
        }

        void drawInto(Canvas c, int w, int h) {
            long curMs = player != null ? player.getCurrentPosition() : 0;
            drawInto(c, w, h, curMs);
        }

        void drawInto(Canvas c, int w, int h, long currentAudioMs) {
            drawInto(c, w, h, currentAudioMs, true, true);
        }

        void drawInto(Canvas c, int w, int h, long currentAudioMs, boolean includeVisualizer, boolean includeLyrics) {
            float[] m = magnitudes();
            float bass = calculateBass(m);
            smoothedBass = smoothedBass * 0.75f + bass * 0.25f;

            // Camera Beat Shake / Pulse effect
            c.save();
            if (beatShakeEnabled && smoothedBass > 0.35f) {
                float pulseScale = 1.0f + (smoothedBass - 0.35f) * 0.06f * shakeIntensity;
                float shakeX = (float) (Math.sin(System.currentTimeMillis() / 40.0) * (smoothedBass - 0.35f) * 8f * shakeIntensity);
                float shakeY = (float) (Math.cos(System.currentTimeMillis() / 35.0) * (smoothedBass - 0.35f) * 8f * shakeIntensity);
                c.translate(w / 2f + shakeX, h / 2f + shakeY);
                c.scale(pulseScale, pulseScale);
                c.translate(-w / 2f, -h / 2f);
            }

            // Draw Background
            if ("image".equals(backgroundMode) && backgroundBitmap != null) {
                float sc = Math.max(w / (float) backgroundBitmap.getWidth(), h / (float) backgroundBitmap.getHeight());
                float bw = backgroundBitmap.getWidth() * sc, bh = backgroundBitmap.getHeight() * sc;
                c.drawBitmap(backgroundBitmap, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), paint);
                paint.setColor(0xAA060810);
                c.drawRect(0, 0, w, h, paint);
            } else if ("dark".equals(backgroundMode)) {
                paint.setShader(null);
                paint.setColor(0xFF06070B);
                c.drawRect(0, 0, w, h, paint);
            } else if ("radial".equals(backgroundMode)) {
                int outer = Color.rgb(6, 12 + (int) (smoothedBass * 18), 26 + (int) (smoothedBass * 30));
                int inner = Color.rgb(24 + (int) (smoothedBass * 20), 10, 48 + (int) (smoothedBass * 25));
                float radius = Math.max(w, h) * (0.38f + smoothedBass * 0.08f);
                paint.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, radius, inner, outer, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            } else {
                int col1 = Color.rgb(15 + (int) (smoothedBass * 20), 10, 35 + (int) (smoothedBass * 30));
                int col2 = Color.rgb(6, 18 + (int) (smoothedBass * 25), 26);
                paint.setShader(new LinearGradient(0, 0, w, h, col1, col2, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            }

            // Render visualizer styles
            int parsedCol = Color.parseColor(activeColor);
            paint.setColor(parsedCol);
            paint.setStyle(Paint.Style.FILL);
            if (glow > 0) {
                paint.setShadowLayer(dp(glow), 0, 0, parsedCol);
            }
            float cx = w / 2f, cy = h / 2f;

            if (!includeVisualizer) {
                paint.clearShadowLayer();
                if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);
                return;
            }

            int visualizerLayerSave = c.saveLayerAlpha(0, 0, w, h, alphaFor(255, visualizerIntensity));
            int visualizerLengthScaleSave = c.save();
            c.scale(1.0f, Math.max(0.50f, Math.min(1.25f, visualizerLengthScale)), cx, cy);


            if ("bars".equals(style)) {
                int n = 56;
                float bw = w / (float) n;
                for (int i = 0; i < n; i++) {
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    float bh = Math.min(v * h * 0.72f, h * 0.80f);
                    paint.setAlpha(160 + i % 90);
                    c.drawRoundRect(new RectF(i * bw + bw * 0.16f, cy - bh / 2, i * bw + bw * 0.84f, cy + bh / 2), dp(4), dp(4), paint);
                    // Glowing cap
                    if (bh > dp(10)) {
                        paint.setAlpha(255);
                        c.drawCircle(i * bw + bw * 0.5f, cy - bh / 2 - dp(3), dp(2.5f), paint);
                    }
                }
                paint.setAlpha(255);
            } else if ("mirror".equals(style)) {
                int n = 42;
                float bw = w / (float) n;
                for (int i = 0; i < n; i++) {
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    float bh = Math.min(v * h * 0.44f, h * 0.44f), x = i * bw;
                    paint.setAlpha(240);
                    c.drawRoundRect(new RectF(x + bw * 0.18f, cy - bh, x + bw * 0.82f, cy - dp(2)), dp(4), dp(4), paint);
                    paint.setAlpha(80);
                    c.drawRoundRect(new RectF(x + bw * 0.18f, cy + dp(2), x + bw * 0.82f, cy + bh * 0.75f), dp(4), dp(4), paint);
                }
                paint.setAlpha(255);
            } else if ("wave".equals(style)) {
                Path p = new Path();
                Path fillPath = new Path();
                fillPath.moveTo(0, h);
                if (waveform.length > 1) {
                    for (int i = 0; i < waveform.length; i++) {
                        float x = i * w / (float) (waveform.length - 1);
                        float y = cy + ((waveform[i] & 255) - 128) / 128f * h * 0.34f * sensitivity;
                        if (i == 0) {
                            p.moveTo(x, y);
                            fillPath.lineTo(x, y);
                        } else {
                            p.lineTo(x, y);
                            fillPath.lineTo(x, y);
                        }
                    }
                }
                fillPath.lineTo(w, h);
                fillPath.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(45);
                c.drawPath(fillPath, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(4));
                paint.setAlpha(255);
                c.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
            } else if ("circle".equals(style)) {
                int n = 72;
                float r = Math.min(w, h) * 0.18f + smoothedBass * dp(14);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3.5f));
                c.drawCircle(cx, cy, r - dp(6), paint);
                for (int i = 0; i < n; i++) {
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    double a = i * 2 * Math.PI / n;
                    float x1 = cx + (float) Math.cos(a) * r;
                    float y1 = cy + (float) Math.sin(a) * r;
                    float x2 = cx + (float) Math.cos(a) * (r + dp(6) + v * h * 0.28f);
                    float y2 = cy + (float) Math.sin(a) * (r + dp(6) + v * h * 0.28f);
                    c.drawLine(x1, y1, x2, y2, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            } else if ("particles".equals(style)) {
                for (Particle pt : particles) {
                    pt.update(smoothedBass);
                    paint.setAlpha((int) (pt.alpha * 255));
                    c.drawCircle(pt.x * w, pt.y * h, dp(pt.size) + smoothedBass * dp(4), paint);
                }
                paint.setAlpha(255);
            } else if ("glow".equals(style)) {
                int n = 48;
                float bw = w / (float) n;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(6));
                Path p = new Path();
                for (int i = 0; i < n; i++) {
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    float x = i * bw + bw / 2f;
                    float y = cy - v * h * 0.35f;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                c.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
            } else if ("cube".equals(style)) {
                int n = 36;
                for (int i = 0; i < n; i++) {
                    double a = i * 2 * Math.PI / n + System.currentTimeMillis() / 4000.0;
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    float r = Math.min(w, h) * 0.16f + v * h * 0.18f;
                    float x = cx + (float) Math.cos(a) * r;
                    float y = cy + (float) Math.sin(a) * r;
                    float size = dp(4) + v * dp(10);
                    c.drawRoundRect(new RectF(x - size, y - size, x + size, y + size), dp(3), dp(3), paint);
                }
            } else if ("halo".equals(style)) {
                paint.setStyle(Paint.Style.STROKE);
                for (int ring = 1; ring <= 4; ring++) {
                    float r = Math.min(w, h) * (0.08f * ring) + smoothedBass * dp(18 * ring);
                    paint.setStrokeWidth(dp(2 + ring));
                    paint.setAlpha(255 / ring);
                    c.drawCircle(cx, cy, r, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(255);
            } else { // cyber
                int n = 60;
                float bw = w / (float) n;
                for (int i = 0; i < n; i++) {
                    float v = m[Math.min(m.length - 1, i * m.length / n)] * sensitivity;
                    float bh = v * h * 0.5f;
                    float x = i * bw;
                    paint.setAlpha(200);
                    c.drawRect(x + 1, cy - bh, x + bw - 1, cy + bh, paint);
                    paint.setColor(Color.WHITE);
                    c.drawRect(x + 1, cy - bh - dp(3), x + bw - 1, cy - bh, paint);
                    paint.setColor(parsedCol);
                }
            }

            paint.clearShadowLayer();
            c.restoreToCount(visualizerLengthScaleSave);
            c.restoreToCount(visualizerLayerSave);
            if (includeLyrics) drawLyricsAndText(c, w, h, currentAudioMs);
            drawWatermark(c, w, h);
            c.restore();
        }

        private float calculateBass(float[] m) {
            if (m.length < 8) return 0f;
            float sum = 0;
            int count = Math.min(10, m.length);
            for (int i = 0; i < count; i++) sum += m[i];
            return Math.min(1.0f, (sum / count) * sensitivity);
        }

        private float[] magnitudes() {
            if (fft.length < 4) return new float[]{0};
            int n = fft.length / 2;
            float[] m = new float[n];
            for (int i = 0; i < n; i++) {
                float re = (fft[i * 2] & 255) - 128;
                float im = (fft[i * 2 + 1] & 255) - 128;
                m[i] = Math.min(1f, (float) Math.sqrt(re * re + im * im) / 180f);
            }
            return m;
        }

        private int alphaFor(int baseAlpha, float intensity) {
            float v = Math.max(0f, Math.min(1f, intensity));
            return Math.max(0, Math.min(255, Math.round(baseAlpha * v)));
        }

        private int colorWithIntensity(int color, float intensity) {
            return Color.argb(alphaFor(Color.alpha(color), intensity), Color.red(color), Color.green(color), Color.blue(color));
        }

        private void drawLyricsAndText(Canvas c, int w, int h, long curMs) {
            float baseY = h * 0.50f + h * textVerticalOffset;
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            // Si des paroles synchronisées existent
            if (!lyricsList.isEmpty() && !"fixed".equals(textMode)) {
                int activeIndex = -1;
                for (int i = 0; i < lyricsList.size(); i++) {
                    LyricLine line = lyricsList.get(i);
                    if (curMs >= line.startMs && curMs <= line.endMs) {
                        activeIndex = i;
                        break;
                    } else if (curMs < line.startMs) {
                        activeIndex = Math.max(0, i - 1);
                        break;
                    }
                }
                if (activeIndex == -1 && !lyricsList.isEmpty()) {
                    if (curMs > lyricsList.get(lyricsList.size() - 1).endMs) {
                        activeIndex = lyricsList.size() - 1;
                    } else {
                        activeIndex = 0;
                    }
                }

                if ("scroll".equals(textMode)) {
                    // Défilement fluide de type Karaoké avec transition verticale
                    LyricLine curLine = lyricsList.get(activeIndex);
                    float lineProgress = 0f;
                    if (curLine.endMs > curLine.startMs) {
                        lineProgress = Math.max(0f, Math.min(1f, (curMs - curLine.startMs) / (float) (curLine.endMs - curLine.startMs)));
                    }
                    float lineSpacing = Math.max(dp((int) (textSize * 1.20f * lyricsScale)), h * 0.095f);
                    float offsetY = baseY - (lineProgress * lineSpacing * 0.25f);

                    // Dessiner les lignes précédentes (ombrées)
                    for (int offset = -4; offset <= 4; offset++) {
                        int idx = activeIndex + offset;
                        if (idx < 0 || idx >= lyricsList.size()) continue;
                        LyricLine l = lyricsList.get(idx);
                        float lineY = offsetY + (offset * lineSpacing);

                        if (offset == 0) {
                            // Ligne Active (mise en valeur, plus grande, lumineuse)
                            textPaint.setTextSize(dp((int) (textSize * 1.18f * lyricsScale)));
                            textPaint.setColor(colorWithIntensity(Color.WHITE, lyricsIntensity));
                            textPaint.setShadowLayer(dp(12), 0, 0, colorWithIntensity(Color.parseColor(activeColor), lyricsIntensity));
                            drawFittedCentered(c, l.text, w / 2f, lineY, (w * 0.92f * lyricsMaxWidth / 100f));
                            textPaint.clearShadowLayer();
                        } else {
                            // Lignes au-dessus et en-dessous
                            int alpha = alphaFor((offset == 1 || offset == -1) ? 140 : 60, lyricsIntensity);
                            textPaint.setTextSize(dp((int) (textSize * 0.90f * lyricsScale)));
                            textPaint.setColor(Color.argb(alpha, 210, 215, 230));
                            c.drawText(l.text, w / 2f, lineY, textPaint);
                        }
                    }
                    return;
                } else if ("active_line".equals(textMode)) {
                    LyricLine curLine = lyricsList.get(activeIndex);
                    textPaint.setTextSize(dp((int) (textSize * 1.15f * lyricsScale)));
                    textPaint.setColor(colorWithIntensity(Color.WHITE, lyricsIntensity));
                    textPaint.setShadowLayer(dp(10), 0, 0, colorWithIntensity(Color.parseColor(activeColor), lyricsIntensity));
                    drawFittedCentered(c, curLine.text, w / 2f, baseY, (w * 0.90f * lyricsMaxWidth / 100f));
                    textPaint.clearShadowLayer();
                    return;
                } else if ("word".equals(textMode)) {
                    LyricLine curLine = lyricsList.get(activeIndex);
                    String[] words = curLine.text.split("\\s+");
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
                }
            }

            // Fallback: Afficher citation / texte fixe
            String s = quoteInput == null ? quote : quoteInput.getText().toString().trim();
            if (!s.isEmpty()) {
                textPaint.setColor(colorWithIntensity(Color.WHITE, lyricsIntensity));
                textPaint.setTextSize(dp(textSize));
                textPaint.setShadowLayer(dp(8), 0, 0, colorWithIntensity(0xCC000000, lyricsIntensity));
                c.drawText(s, w / 2f, baseY, textPaint);
                textPaint.clearShadowLayer();
            }
        }


        private void drawFittedCentered(Canvas c, String text, float centerX, float baseline, float maxWidth) {
            if (text == null || text.isEmpty()) return;
            float original = textPaint.getTextSize();
            float measured = textPaint.measureText(text);
            if (measured > maxWidth && measured > 0) textPaint.setTextSize(original * maxWidth / measured);
            c.drawText(text, centerX, baseline, textPaint);
            textPaint.setTextSize(original);
        }

        private void drawWatermark(Canvas c, int w, int h) {
            if (watermarkBitmap != null) {
                float mw = w * 0.18f;
                float sc = mw / watermarkBitmap.getWidth();
                float hh = watermarkBitmap.getHeight() * sc;
                c.drawBitmap(watermarkBitmap, null, new RectF(w - mw - dp(16), h - hh - dp(16), w - dp(16), h - dp(16)), paint);
            } else {
                String s = watermarkInput == null ? watermark : watermarkInput.getText().toString().trim();
                if (!s.isEmpty()) {
                    paint.setColor(0xB0FFFFFF);
                    paint.setTextAlign(Paint.Align.RIGHT);
                    paint.setTextSize(dp(12));
                    c.drawText(s, w - dp(16), h - dp(16), paint);
                }
            }
        }

        Bitmap exportBitmap(int w, int h) {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            drawInto(new Canvas(b), w, h, player != null ? player.getCurrentPosition() : 0);
            return b;
        }
    }

    // Classe de particule dynamique pour le style Particles
    static final class Particle {
        float x, y, vx, vy, alpha;
        int size;
        Particle(float x, float y, float vx, float vy, int size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size; this.alpha = 0.8f;
        }
        void update(float bassImpact) {
            x += vx * (1f + bassImpact * 3f);
            y += vy * (1f + bassImpact * 3f);
            alpha -= 0.005f;
            if (y < 0 || x < 0 || x > 1 || alpha <= 0) {
                y = 1f;
                x = (float) Math.random();
                alpha = 0.5f + (float) Math.random() * 0.5f;
            }
        }
    }

    // Modèle de données pour les Paroles Synchronisées (LRC)
    public static final class LyricLine {
        public final long startMs;
        public final long endMs;
        public final String text;

        public LyricLine(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text == null ? "" : text.trim();
        }

        public static LyricLine parseLrcLine(String line) {
            if (line == null) return null;
            line = line.trim();
            // Nettoyage des préfixes markdown (ex: ```lrc, **, bullet points, numéros)
            line = line.replaceAll("^[`*#\\-\\d.]+\\s*", "");
            if (line.isEmpty()) return null;
            int startBracket = line.indexOf('[');
            int endBracket = line.indexOf(']', startBracket + 1);
            if (startBracket < 0 || endBracket <= startBracket + 1) return null;
            String timeTag = line.substring(startBracket + 1, endBracket).trim();
            String text = line.substring(endBracket + 1).trim();
            // Nettoyage des balises markdown résiduelles autour du texte
            text = text.replaceAll("^[*_`]+\\s*", "").replaceAll("\\s*[*_`]+$", "").trim();
            try {
                String[] parts = timeTag.split(":");
                if (parts.length == 2) {
                    long min = Long.parseLong(parts[0].trim());
                    double sec = Double.parseDouble(parts[1].trim());
                    long startMs = (long) (min * 60000 + sec * 1000);
                    return new LyricLine(startMs, startMs + 3500, text);
                } else if (parts.length == 3) {
                    long hr = Long.parseLong(parts[0].trim());
                    long min = Long.parseLong(parts[1].trim());
                    double sec = Double.parseDouble(parts[2].trim());
                    long startMs = (long) (hr * 3600000 + min * 60000 + sec * 1000);
                    return new LyricLine(startMs, startMs + 3500, text);
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        public static List<LyricLine> parseLrcString(String lrcContent) {
            List<LyricLine> list = new ArrayList<>();
            if (lrcContent == null || lrcContent.trim().isEmpty()) return list;
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
            for (String raw : lrcContent.split("\\r?\\n")) {
                java.util.regex.Matcher matcher = pattern.matcher(raw);
                List<Long> times = new ArrayList<>();
                int textStart = -1;
                while (matcher.find()) {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String frac = matcher.group(3);
                    long ms = 0L;
                    if (frac != null && !frac.isEmpty()) {
                        if (frac.length() == 1) ms = Long.parseLong(frac) * 100L;
                        else if (frac.length() == 2) ms = Long.parseLong(frac) * 10L;
                        else ms = Long.parseLong(frac.substring(0, 3));
                    }
                    times.add(min * 60000L + sec * 1000L + ms);
                    textStart = matcher.end();
                }
                if (times.isEmpty() || textStart < 0) continue;
                String text = raw.substring(textStart).trim();
                if (text.isEmpty()) continue;
                for (Long t : times) list.add(new LyricLine(t, t + 3500L, text));
            }
            java.util.Collections.sort(list, (a, b) -> Long.compare(a.startMs, b.startMs));
            for (int i = 0; i + 1 < list.size(); i++) {
                LyricLine cur = list.get(i);
                LyricLine next = list.get(i + 1);
                long end = Math.max(cur.startMs + 250L, next.startMs);
                list.set(i, new LyricLine(cur.startMs, end, cur.text));
            }
            return list;
        }

    }

    // ==========================================
    // Chiffrement & Sécurité AndroidKeyStore
    // ==========================================
    static final class KeyStoreUtil {
        private static final String ALIAS = "StudioProGroqKey";
        private static final String LEGACY_ALIAS = "VisualiseurAudioGroqKey";

        private static SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(LEGACY_ALIAS) && !ks.containsAlias(ALIAS)) {
                try {
                    return ((KeyStore.SecretKeyEntry) ks.getEntry(LEGACY_ALIAS, null)).getSecretKey();
                } catch (Exception ignored) {}
            }
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance("AES", "AndroidKeyStore");
                kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
                kg.generateKey();
            }
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }

        static String encrypt(String s) throws Exception {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            return android.util.Base64.encodeToString(c.getIV(), android.util.Base64.NO_WRAP) + ":" +
                    android.util.Base64.encodeToString(c.doFinal(s.getBytes(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP);
        }

        static String decrypt(String s) throws Exception {
            if (s == null || s.isEmpty()) return "";
            String[] p = s.split(":", 2);
            if (p.length != 2) return "";
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, android.util.Base64.decode(p[0], android.util.Base64.NO_WRAP)));
            return new String(c.doFinal(android.util.Base64.decode(p[1], android.util.Base64.NO_WRAP)), StandardCharsets.UTF_8);
        }
    }

    // ==========================================
    // Client Groq AI & Whisper Transcription
    // ==========================================
    static final class GroqClient {
        private static final String MODELS_URL = "https://api.groq.com/openai/v1/models";
        private static final String CHAT = "https://api.groq.com/openai/v1/chat/completions";
        private static final String TRANS = "https://api.groq.com/openai/v1/audio/transcriptions";

        // Modèles actifs recommandés sur Groq API
        private static final String[] FALLBACK_MODELS = {
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-20b"
        };


        private static boolean isSupportedChatModel(String id) {
            if (id == null || id.isEmpty()) return false;
            for (String model : FALLBACK_MODELS) if (model.equals(id)) return true;
            return false;
        }
        static String chat(String key, String prompt) throws Exception {
            return chatInternal(key, "Réponds en français avec clarté et concision.", prompt, 120, 0.7);
        }

        static String quote(String key, String theme) throws Exception {
            String system = "Tu es un poète et auteur d'aphorismes percutants. Génère une seule citation sans guillemets, maximum 20 mots.";
            return chatInternal(key, system, "Thème de la citation : " + (theme == null || theme.isEmpty() ? "la détermination et la musique" : theme), 100, 0.7);
        }

        private static List<String> getAvailableModels(String key) {
            List<String> list = new ArrayList<>();
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MODELS_URL).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestMethod("GET");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code == 200) {
                    String r = read(c);
                    JSONObject json = new JSONObject(r);
                    JSONArray data = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject m = data.getJSONObject(i);
                            String id = m.optString("id", "");
                            boolean active = m.optBoolean("active", true);
                            if (active && isSupportedChatModel(id)) {
                                list.add(id);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            for (String m : FALLBACK_MODELS) {
                if (!list.contains(m)) list.add(m);
            }
            return list;
        }

        static String chatInternal(String rawKey, String prompt, int max) throws Exception {
            return chatInternal(rawKey, null, prompt, max, 0.7);
        }

        static String chatInternal(String rawKey, String systemPrompt, String prompt, int max, double temperature) throws Exception {
            final String key = (rawKey != null) ? rawKey.trim() : "";
            if (key.isEmpty()) throw new Exception("Clé API Groq manquante.");

            List<String> modelsToTry = getAvailableModels(key);
            Exception lastException = null;

            for (String modelName : modelsToTry) {
                try {
                    HttpURLConnection c = conn(CHAT, key, "application/json");
                    JSONObject body = new JSONObject();
                    body.put("model", modelName);
                    body.put("max_completion_tokens", max);
                    body.put("temperature", temperature);
                    JSONArray msg = new JSONArray();
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        msg.put(new JSONObject().put("role", "system").put("content", systemPrompt));
                    }
                    msg.put(new JSONObject().put("role", "user").put("content", prompt));
                    body.put("messages", msg);
                    write(c, body.toString());
                    String r = read(c);
                    ensure(c, r);
                    JSONObject json = new JSONObject(r);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim();
                    }
                } catch (Exception e) {
                    String msgStr = e.getMessage();
                    if (msgStr != null && (msgStr.contains("401") || msgStr.toLowerCase().contains("invalid_api_key") || msgStr.toLowerCase().contains("unauthorized"))) {
                        throw new Exception("Clé API Groq invalide ou non reconnue (HTTP 401).");
                    }
                    lastException = e;
                }
            }
            throw (lastException != null ? lastException : new Exception("Impossible de contacter Groq avec les modèles disponibles."));
        }

        public static String chatConversationHistory(String rawKey, JSONArray messages, int max) throws Exception {
            final String key = (rawKey != null) ? rawKey.trim() : "";
            if (key.isEmpty()) throw new Exception("Clé API Groq manquante.");

            List<String> modelsToTry = getAvailableModels(key);
            Exception lastException = null;

            for (String modelName : modelsToTry) {
                try {
                    HttpURLConnection c = conn(CHAT, key, "application/json");
                    JSONObject body = new JSONObject();
                    body.put("model", modelName);
                    body.put("max_tokens", max);
                    body.put("temperature", 0.7);
                    body.put("messages", messages);
                    write(c, body.toString());
                    String r = read(c);
                    ensure(c, r);
                    JSONObject json = new JSONObject(r);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim();
                    }
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("401") || msg.toLowerCase().contains("invalid_api_key") || msg.toLowerCase().contains("unauthorized"))) {
                        throw new Exception("Clé API Groq invalide ou non reconnue (HTTP 401).");
                    }
                    lastException = e;
                }
            }
            throw (lastException != null ? lastException : new Exception("Impossible de contacter Groq avec les modèles disponibles."));
        }

        static Transcript transcribe(String rawKey, File file, String mime, String language) throws Exception {
            final String key = rawKey == null ? "" : rawKey.trim();
            if (key.isEmpty()) throw new Exception("Clé API Groq manquante.");
            if (file == null || !file.exists()) throw new Exception("Fichier audio introuvable.");
            if (file.length() > 100L * 1024L * 1024L) throw new Exception("Audio > 25 MB. Utilisez une version compressée ou découpée.");
            if (file.length() == 0) throw new Exception("Fichier audio vide (0 octet).");

            String safeFileName = file.getName();
            if (!safeFileName.matches(".*\\.(mp3|m4a|wav|flac|ogg|mp4|webm|mpeg|mpga)$")) {
                safeFileName = safeFileName + ".mp3";
            }
            String safeMime = (mime == null || mime.isEmpty() || !mime.startsWith("audio/")) ? "audio/mpeg" : mime;

            String[] models = {"whisper-large-v3", "whisper-large-v3-turbo"};
            Exception last = null;

            for (String model : models) {
                try {
                    String boundary = "----VA" + System.nanoTime();
                    HttpURLConnection c = (HttpURLConnection) new URL(TRANS).openConnection();
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(180000);
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    try (OutputStream out = c.getOutputStream()) {
                        field(out, boundary, "model", model);
                        if (language != null && !language.trim().isEmpty()) {
                            field(out, boundary, "language", language.trim());
                        }
                        field(out, boundary, "prompt", "Paroles complètes fidèlement transcrites.");
                        field(out, boundary, "temperature", "0.0");
                        field(out, boundary, "response_format", "verbose_json");
                        part(out, boundary, "file", safeFileName, safeMime, new FileInputStream(file));
                        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    }

                    String raw = read(c);
                    ensure(c, raw);
                    JSONObject json = new JSONObject(raw);
                    String fullText = json.optString("text", "").replaceAll("\\s+", " ").trim();
                    ArrayList<LyricLine> segmentLines = new ArrayList<>();
                    JSONArray segments = json.optJSONArray("segments");
                    if (segments != null) {
                        for (int i = 0; i < segments.length(); i++) {
                            JSONObject seg = segments.getJSONObject(i);
                            long start = Math.max(0L, Math.round(seg.optDouble("start", 0.0) * 1000.0));
                            long end = Math.max(start + 500L, Math.round(seg.optDouble("end", 0.0) * 1000.0));
                            String value = seg.optString("text", "").replaceAll("\\s+", " ").trim();
                            if (!value.isEmpty()) segmentLines.add(new LyricLine(start, end, value));
                        }
                    }

                    // Ajuster les horodatages de fin pour une synchronisation fluide
                    for (int i = 0; i < segmentLines.size() - 1; i++) {
                        LyricLine cur = segmentLines.get(i);
                        LyricLine next = segmentLines.get(i + 1);
                        if (next.startMs > cur.startMs && next.startMs < cur.endMs + 1000L) {
                            segmentLines.set(i, new LyricLine(cur.startMs, next.startMs, cur.text));
                        }
                    }

                    if (segmentLines.isEmpty() && !fullText.isEmpty()) {
                        segmentLines.add(new LyricLine(0L, 10000L, fullText));
                    }

                    if (!segmentLines.isEmpty()) {
                        return new Transcript(fullText, segmentLines);
                    }
                } catch (Exception e) {
                    last = e;
                    String emsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (emsg.contains("401") || emsg.contains("invalid_api_key")) throw e;
                }
            }

            if (last != null) throw last;
            throw new Exception("Échec de la transcription Whisper.");
        }

        private static String joinLyricText(List<LyricLine> lines) {
            if (lines == null || lines.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (LyricLine line : lines) {
                if (line == null || line.text == null || line.text.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.text);
            }
            return sb.toString().trim();
        }

        static String translateText(String rawKey, String sourceText, String targetLanguage) throws Exception {
            if (sourceText == null || sourceText.trim().isEmpty()) return "";
            String system = "Tu es un traducteur expert linguistique multilingue. Traduis fidèlement ce texte en langue : " + targetLanguage + ".\n"
                    + "Ne renvoie STRICTEMENT que le texte traduit sans balises markdown, sans préambule ni explications.";
            return chatInternal(rawKey, system, sourceText, 1500, 0.2);
        }

        static List<LyricLine> translateLyrics(String rawKey, List<LyricLine> sourceLyrics, String targetLanguage) throws Exception {
            if (sourceLyrics == null || sourceLyrics.isEmpty()) return new ArrayList<>();
            if (targetLanguage == null || targetLanguage.trim().isEmpty()) throw new Exception("Langue cible manquante.");

            final int batchSize = 20;
            final ArrayList<String> translatedTexts = new ArrayList<>();
            for (int i = 0; i < sourceLyrics.size(); i++) translatedTexts.add(null);

            for (int batchStart = 0; batchStart < sourceLyrics.size(); batchStart += batchSize) {
                int batchEnd = Math.min(sourceLyrics.size(), batchStart + batchSize);
                StringBuilder prompt = new StringBuilder();
                for (int i = batchStart; i < batchEnd; i++) {
                    LyricLine line = sourceLyrics.get(i);
                    prompt.append(String.format(Locale.US, "LINE_%04d: %s%n", i + 1, line.text));
                }

                String systemPrompt = "Tu es un traducteur expert de paroles musicales. Traduis chaque ligne en " + targetLanguage + ". "
                        + "RÈGLES STRICTES :\n"
                        + "1. Conserve EXACTEMENT chaque identifiant LINE_XXXX.\n"
                        + "2. Retourne exactement une ligne de sortie par identifiant reçu.\n"
                        + "3. Ne fusionne, ne sépare et ne réordonne aucune ligne.\n"
                        + "4. Ne renvoie que les lignes au format LINE_XXXX: traduction.\n"
                        + "5. Aucun markdown, aucune explication, aucun texte avant ou après.\n"
                        + "6. Les identifiants sont techniques : ne les traduis jamais et ne les modifie jamais.";

                String response = chatInternal(rawKey, systemPrompt, prompt.toString(), 2500, 0.1);
                if (response == null || response.trim().isEmpty()) throw new Exception("Groq n'a renvoyé aucune traduction pour le lot.");

                Pattern markerPattern = Pattern.compile("(?m)^\\s*LINE_(\\d{1,4})\\s*:\\s*(.*?)\\s*$");
                Matcher matcher = markerPattern.matcher(response.replace("```", ""));
                int matched = 0;
                while (matcher.find()) {
                    int lineNumber;
                    try {
                        lineNumber = Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    int index = lineNumber - 1;
                    if (index < batchStart || index >= batchEnd) continue;
                    String text = matcher.group(2) == null ? "" : matcher.group(2).trim();
                    if (!text.isEmpty()) {
                        translatedTexts.set(index, text);
                        matched++;
                    }
                }

                // Retry only missing lines individually. This guarantees a 1:1 mapping without ever inventing timings.
                if (matched < (batchEnd - batchStart)) {
                    for (int i = batchStart; i < batchEnd; i++) {
                        if (translatedTexts.get(i) != null && !translatedTexts.get(i).isEmpty()) continue;
                        LyricLine src = sourceLyrics.get(i);
                        String system = "Traduis fidèlement cette seule ligne de chanson en " + targetLanguage + ". "
                                + "Retourne uniquement la traduction, sans guillemets, sans commentaire, sans markdown.";
                        String one = chatInternal(rawKey, system, src.text, 300, 0.1);
                        if (one != null) one = one.replaceAll("^\\s*LINE_\\d{1,4}\\s*:\\s*", "").trim();
                        if (one == null || one.isEmpty()) throw new Exception("Traduction incomplète pour la ligne " + (i + 1) + ".");
                        translatedTexts.set(i, one);
                    }
                }
            }

            // Critical rule: timestamps always come from the ORIGINAL lyric timeline, never from the model.
            ArrayList<LyricLine> result = new ArrayList<>(sourceLyrics.size());
            for (int i = 0; i < sourceLyrics.size(); i++) {
                LyricLine src = sourceLyrics.get(i);
                String translated = translatedTexts.get(i);
                if (translated == null || translated.trim().isEmpty()) {
                    throw new Exception("Traduction absente pour la ligne " + (i + 1) + ".");
                }
                result.add(new LyricLine(src.startMs, src.endMs, translated.trim()));
            }
            return result;
        }

        static HttpURLConnection conn(String u, String key, String type) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", type);
            return c;
        }

        static void write(HttpURLConnection c, String s) throws Exception {
            try (OutputStream o = c.getOutputStream()) {
                o.write(s.getBytes(StandardCharsets.UTF_8));
            }
        }

        static void field(OutputStream o, String b, String n, String v) throws Exception {
            o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + n + "\"\r\n\r\n" + v + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        static void part(OutputStream o, String b, String n, String fn, String mime, InputStream in) throws Exception {
            try (InputStream i = in) {
                o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + n + "\"; filename=\"" + fn.replaceAll("[^A-Za-z0-9._-]", "_") + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byte[] buf = new byte[32768];
                int z;
                while ((z = i.read(buf)) != -1) o.write(buf, 0, z);
                o.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
        }

        static String read(HttpURLConnection c) throws Exception {
            int code = c.getResponseCode();
            InputStream i = code >= 300 ? c.getErrorStream() : c.getInputStream();
            if (i == null) return "";
            try (InputStream in = i; ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) != -1) o.write(b, 0, n);
                return o.toString("UTF-8");
            }
        }

        static void ensure(HttpURLConnection c, String body) throws Exception {
            int code = c.getResponseCode();
            if (code >= 300) {
                String errorMsg = "HTTP " + code;
                try {
                    JSONObject errObj = new JSONObject(body).optJSONObject("error");
                    if (errObj != null && errObj.has("message")) {
                        errorMsg += " : " + errObj.optString("message");
                    } else {
                        errorMsg += " — " + body;
                    }
                } catch (Exception ignored) {
                    errorMsg += " — " + body;
                }
                throw new Exception(errorMsg);
            }
        }

        static final class Transcript {
            final String text;
            final List<LyricLine> lines;
            Transcript(String t, List<LyricLine> lines) {
                this.text = t;
                this.lines = lines != null ? lines : new ArrayList<>();
            }
        }
    }

    // ==========================================
    // Media Muxer & Audio Transcoder / Analyzers
    // ==========================================
    static final class MuxerUtil {
        static void mux(Context ctx, File v, File a, File out, long su, long eu, long target) throws Exception {
            android.media.MediaExtractor ve = new android.media.MediaExtractor();
            android.media.MediaExtractor ae = new android.media.MediaExtractor();
            File processedAudio = a;
            boolean temporaryAudio = false;
            android.media.MediaMuxer muxer = null;
            boolean muxerStarted = false;
            try {
                ve.setDataSource(v.getAbsolutePath());
                int vt = find(ve, "video/");
                if (vt < 0) throw new Exception("Piste vidéo introuvable.");
                ae.setDataSource(a.getAbsolutePath());
                int at = find(ae, "audio/");
                if (at < 0) throw new Exception("Piste audio introuvable.");
                android.media.MediaFormat audioFormat = ae.getTrackFormat(at);
                String audioMime = audioFormat.getString(android.media.MediaFormat.KEY_MIME);
                if (audioMime == null || !audioMime.equalsIgnoreCase("audio/mp4a-latm")) {
                    File tempAac = new File(ctx.getCacheDir(), "transcoded_" + System.nanoTime() + ".m4a");
                    transcodeToAac(ae, at, tempAac, su, eu, 0);
                    processedAudio = tempAac;
                    temporaryAudio = true;
                }
                ae.release();
                ae = null;

                android.media.MediaExtractor finalAudio = new android.media.MediaExtractor();
                finalAudio.setDataSource(processedAudio.getAbsolutePath());
                int fat = find(finalAudio, "audio/");
                if (fat < 0) throw new Exception("Piste AAC finale introuvable.");
                muxer = new android.media.MediaMuxer(out.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int vo = muxer.addTrack(ve.getTrackFormat(vt));
                int ao = muxer.addTrack(finalAudio.getTrackFormat(fat));
                muxer.start();
                muxerStarted = true;
                copy(ve, vt, muxer, vo, 0, Long.MAX_VALUE, 0);
                if (target > 0) {
                    long sourceStart = temporaryAudio ? 0 : su;
                    long sourceEnd = temporaryAudio ? Long.MAX_VALUE : eu;
                    copyLooping(finalAudio, fat, muxer, ao, sourceStart, sourceEnd, target);
                } else {
                    copy(finalAudio, fat, muxer, ao, temporaryAudio ? 0 : su, temporaryAudio ? Long.MAX_VALUE : eu, 0);
                }
                finalAudio.release();
            } finally {
                try { if (muxer != null && muxerStarted) muxer.stop(); } catch (Exception ignored) {}
                try { if (muxer != null) muxer.release(); } catch (Exception ignored) {}
                try { ve.release(); } catch (Exception ignored) {}
                try { ae.release(); } catch (Exception ignored) {}
                if (temporaryAudio) try { processedAudio.delete(); } catch (Exception ignored) {}
            }
        }


        private static void transcodeToAac(android.media.MediaExtractor extractor, int audioTrackIndex, File outFile, long startUs, long endUs, long targetUs) throws Exception {
            extractor.selectTrack(audioTrackIndex);
            extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            android.media.MediaFormat inputFormat = extractor.getTrackFormat(audioTrackIndex);
            String inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME);
            int sampleRate = inputFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channelCount = inputFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) : 2;
            int bytesPerFrame = Math.max(2, channelCount * 2);

            android.media.MediaCodec decoder = null;
            android.media.MediaCodec encoder = null;
            android.media.MediaMuxer muxer = null;
            boolean muxerStarted = false;
            int muxerAudioTrack = -1;

            try {
                decoder = android.media.MediaCodec.createDecoderByType(inputMime);
                decoder.configure(inputFormat, null, null, 0);
                decoder.start();

                android.media.MediaFormat encoderFormat = android.media.MediaFormat.createAudioFormat(
                        "audio/mp4a-latm", sampleRate, channelCount);
                encoderFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 192000);
                encoderFormat.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE,
                        android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);

                encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm");
                encoder.configure(encoderFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();

                muxer = new android.media.MediaMuxer(
                        outFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                android.media.MediaCodec.BufferInfo decInfo = new android.media.MediaCodec.BufferInfo();
                android.media.MediaCodec.BufferInfo encInfo = new android.media.MediaCodec.BufferInfo();
                java.io.ByteArrayOutputStream pendingPcm = new java.io.ByteArrayOutputStream(131072);

                boolean extractorDone = false;
                boolean decoderDone = false;
                boolean encoderInputDone = false;
                boolean encoderDone = false;
                long firstPtsUs = -1L;
                long queuedFrames = 0L;
                long maxDurationUs = targetUs > 0 ? targetUs : Math.max(0L, endUs - startUs);
                long deadline = System.currentTimeMillis() + 180000L;

                while (!encoderDone) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new Exception("Finalisation audio trop longue.");
                    }

                    boolean progressed = false;

                    // Critical ordering: always drain AAC output before waiting for input buffers.
                    while (true) {
                        int outIndex = encoder.dequeueOutputBuffer(encInfo, 0);
                        if (outIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (!muxerStarted) {
                                muxerAudioTrack = muxer.addTrack(encoder.getOutputFormat());
                                muxer.start();
                                muxerStarted = true;
                            }
                            progressed = true;
                            continue;
                        }
                        if (outIndex < 0) break;

                        ByteBuffer outBuf = encoder.getOutputBuffer(outIndex);
                        if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                && encInfo.size > 0 && outBuf != null && muxerStarted) {
                            outBuf.position(encInfo.offset);
                            outBuf.limit(encInfo.offset + encInfo.size);
                            muxer.writeSampleData(muxerAudioTrack, outBuf, encInfo);
                        }

                        if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true;
                        }
                        encoder.releaseOutputBuffer(outIndex, false);
                        progressed = true;
                        if (encoderDone) break;
                    }
                    if (encoderDone) break;

                    if (!extractorDone) {
                        int decInIndex = decoder.dequeueInputBuffer(0);
                        if (decInIndex >= 0) {
                            ByteBuffer inBuf = decoder.getInputBuffer(decInIndex);
                            if (inBuf == null) throw new Exception("Buffer décodeur indisponible.");
                            int sampleSize = extractor.readSampleData(inBuf, 0);
                            long sampleTime = extractor.getSampleTime();
                            if (sampleSize < 0 || sampleTime < 0 || sampleTime > endUs) {
                                decoder.queueInputBuffer(decInIndex, 0, 0, 0,
                                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                extractorDone = true;
                            } else {
                                decoder.queueInputBuffer(decInIndex, 0, sampleSize, sampleTime, 0);
                                extractor.advance();
                            }
                            progressed = true;
                        }
                    }

                    if (!decoderDone) {
                        while (true) {
                            int decOutIndex = decoder.dequeueOutputBuffer(decInfo, 0);
                            if (decOutIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                progressed = true;
                                continue;
                            }
                            if (decOutIndex < 0) break;

                            ByteBuffer decBuf = decoder.getOutputBuffer(decOutIndex);
                            if (decInfo.size > 0 && decBuf != null) {
                                if (firstPtsUs < 0) firstPtsUs = decInfo.presentationTimeUs;
                                long relPts = Math.max(0L, decInfo.presentationTimeUs - firstPtsUs);
                                if (maxDurationUs <= 0L || relPts < maxDurationUs) {
                                    decBuf.position(decInfo.offset);
                                    decBuf.limit(decInfo.offset + decInfo.size);
                                    byte[] pcm = new byte[decInfo.size];
                                    decBuf.get(pcm);
                                    pendingPcm.write(pcm, 0, pcm.length);
                                }
                            }

                            boolean eos = (decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                            long relNow = firstPtsUs < 0 ? 0L : decInfo.presentationTimeUs - firstPtsUs;
                            decoder.releaseOutputBuffer(decOutIndex, false);
                            progressed = true;
                            if (eos || (maxDurationUs > 0L && relNow >= maxDurationUs)) {
                                decoderDone = true;
                                break;
                            }
                        }
                    }

                    if (!encoderInputDone) {
                        int encInIndex = encoder.dequeueInputBuffer(0);
                        if (encInIndex >= 0) {
                            ByteBuffer encInBuf = encoder.getInputBuffer(encInIndex);
                            if (encInBuf == null) throw new Exception("Buffer AAC indisponible.");
                            encInBuf.clear();

                            int sendBytes = Math.min(encInBuf.remaining(), pendingPcm.size());
                            sendBytes -= sendBytes % bytesPerFrame;

                            if (sendBytes > 0) {
                                byte[] data = pendingPcm.toByteArray();
                                encInBuf.put(data, 0, sendBytes);
                                pendingPcm.reset();
                                if (data.length > sendBytes) {
                                    pendingPcm.write(data, sendBytes, data.length - sendBytes);
                                }
                                long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                                encoder.queueInputBuffer(encInIndex, 0, sendBytes, ptsUs, 0);
                                queuedFrames += sendBytes / bytesPerFrame;
                                progressed = true;
                            } else if (decoderDone && pendingPcm.size() == 0) {
                                long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                                encoder.queueInputBuffer(encInIndex, 0, 0, ptsUs,
                                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                encoderInputDone = true;
                                progressed = true;
                            }
                        }
                    }

                    if (!progressed) {
                        try {
                            Thread.sleep(2L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new Exception("Transcodage interrompu.", e);
                        }
                    }
                }

                if (!muxerStarted || muxerAudioTrack < 0) {
                    throw new Exception("Aucune sortie AAC n'a été produite.");
                }
            } finally {
                try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Exception ignored) {}
                try { if (encoder != null) { encoder.stop(); encoder.release(); } } catch (Exception ignored) {}
                try { if (muxer != null) { if (muxerStarted) muxer.stop(); muxer.release(); } } catch (Exception ignored) {}
                try { extractor.unselectTrack(audioTrackIndex); } catch (Exception ignored) {}
            }
        }

        static void copyLooping(android.media.MediaExtractor e, int t, android.media.MediaMuxer m, int out, long start, long end, long target) {
            e.selectTrack(t);
            e.seekTo(Math.max(0, start), android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            ByteBuffer b = ByteBuffer.allocate(2 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
            long sourceFirst = -1;
            long sourceLast = -1;
            long outputBase = 0;
            while (outputBase < target) {
                e.seekTo(Math.max(0, start), android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                sourceFirst = -1;
                boolean wrote = false;
                while (true) {
                    int n = e.readSampleData(b, 0);
                    long ts = e.getSampleTime();
                    if (n < 0 || ts < 0 || ts > end) break;
                    if (sourceFirst < 0) sourceFirst = ts;
                    long rel = Math.max(0, ts - sourceFirst);
                    long outPts = outputBase + rel;
                    if (outPts >= target) break;
                    bi.offset = 0; bi.size = n; bi.presentationTimeUs = outPts; bi.flags = e.getSampleFlags();
                    m.writeSampleData(out, b, bi);
                    sourceLast = rel;
                    wrote = true;
                    e.advance();
                    b.clear();
                }
                if (!wrote) break;
                outputBase += Math.max(1, sourceLast + 1);
            }
            e.unselectTrack(t);
        }

        static int find(android.media.MediaExtractor e, String p) {
            for (int i = 0; i < e.getTrackCount(); i++) {
                String m = e.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (m != null && m.startsWith(p)) return i;
            }
            return -1;
        }

        static void copy(android.media.MediaExtractor e, int t, android.media.MediaMuxer m, int out, long start, long end, long target) {
            e.selectTrack(t);
            e.seekTo(start, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            ByteBuffer b = ByteBuffer.allocate(2 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
            long first = -1;
            while (true) {
                int n = e.readSampleData(b, 0);
                long ts = e.getSampleTime();
                if (n < 0 || ts < 0 || ts > end) break;
                if (first < 0) first = ts;
                bi.offset = 0;
                bi.size = n;
                bi.presentationTimeUs = target > 0 ? (ts - first) : (ts - start);
                bi.flags = e.getSampleFlags();
                m.writeSampleData(out, b, bi);
                e.advance();
                if (target > 0 && bi.presentationTimeUs >= target) break;
                b.clear();
            }
        }
    }

    static final class AudioAnalyzer {
        static long[] detect(File f) throws Exception {
            android.media.MediaExtractor e = new android.media.MediaExtractor();
            e.setDataSource(f.getAbsolutePath());
            long d = 0;
            for (int i = 0; i < e.getTrackCount(); i++) {
                android.media.MediaFormat mf = e.getTrackFormat(i);
                String m = mf.getString(android.media.MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    d = mf.containsKey(android.media.MediaFormat.KEY_DURATION) ? mf.getLong(android.media.MediaFormat.KEY_DURATION) / 1000 : 0;
                    break;
                }
            }
            e.release();
            return new long[]{0, d};
        }
    }
}
