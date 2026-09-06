package com.kidas.studiopro;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.WindowManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gestionnaire et Explorateur Audio Studio Pro :
 * - Analyse et liste tous les fichiers audio présents sur l'appareil (MediaStore)
 * - Écoute et prévisualisation instantanée (Play / Pause / Scrub / Repères) avant importation
 * - Recherche instantanée multi-critères (Titre, Artiste, Album, Dossier)
 * - Filtres par catégories (Musique, Téléchargements, Enregistrements, Tous)
 * - Tri par date d'ajout, nom, artiste, durée
 * - Importation directe et transparente dans le projet Studio Pro
 */
public class AudioBrowserDialog extends Dialog {

    public interface OnAudioSelectedListener {
        void onAudioSelected(Uri uri, String title, String artist, long durationMs);
    }

    public static class AudioTrackItem {
        public final long id;
        public final String title;
        public final String artist;
        public final String album;
        public final long durationMs;
        public final long sizeBytes;
        public final long dateAdded;
        public final Uri contentUri;
        public final String dataPath;
        public final String mimeType;

        public AudioTrackItem(long id, String title, String artist, String album, long durationMs,
                              long sizeBytes, long dateAdded, Uri contentUri, String dataPath, String mimeType) {
            this.id = id;
            this.title = (title == null || title.trim().isEmpty() || title.equalsIgnoreCase("<unknown>")) ? "Audio sans titre" : title.trim();
            
            String cleanedArtist = (artist == null) ? "" : artist.trim();
            if (cleanedArtist.isEmpty() || cleanedArtist.equalsIgnoreCase("<unknown>") || cleanedArtist.equalsIgnoreCase("unknown") || cleanedArtist.contains("@gmail.com")) {
                this.artist = "Artiste inconnu";
            } else {
                this.artist = cleanedArtist;
            }

            this.album = (album == null || album.contains("unknown") || album.equalsIgnoreCase("<unknown>")) ? "" : album.trim();
            this.durationMs = durationMs;
            this.sizeBytes = sizeBytes;
            this.dateAdded = dateAdded;
            this.contentUri = contentUri;
            this.dataPath = dataPath != null ? dataPath : "";
            this.mimeType = mimeType != null ? mimeType : "audio/mpeg";
        }

        public String getFormattedDuration() {
            if (durationMs <= 0) return "--:--";
            long s = (durationMs / 1000) % 60;
            long m = (durationMs / (1000 * 60)) % 60;
            long h = durationMs / (1000 * 60 * 60);
            if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
            return String.format(Locale.US, "%02d:%02d", m, s);
        }

        public String getFormattedSize() {
            if (sizeBytes <= 0) return "";
            if (sizeBytes < 1024 * 1024) {
                return String.format(Locale.US, "%.1f Ko", sizeBytes / 1024f);
            }
            return String.format(Locale.US, "%.1f Mo", sizeBytes / (1024f * 1024f));
        }
    }

    private final Activity activity;
    private final OnAudioSelectedListener listener;
    private final Runnable requestPermissionAction;

    private final List<AudioTrackItem> allTracks = new ArrayList<>();
    private final List<AudioTrackItem> displayedTracks = new ArrayList<>();

    private TrackAdapter adapter;
    private ListView trackListView;
    private ProgressBar loadingProgress;
    private TextView emptyStateText;
    private TextView statsCountText;
    private EditText searchInput;
    private LinearLayout permissionBanner;

    // Mini Player Preview
    private MediaPlayer previewPlayer;
    private AudioTrackItem currentlyPlayingTrack = null;
    private boolean isPlaying = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private LinearLayout previewBottomBar;
    private TextView previewTrackTitle;
    private TextView previewTrackArtist;
    private TextView previewTimeCurrent;
    private TextView previewTimeTotal;
    private ImageView previewPlayPauseIcon;
    private SeekBar previewSeekBar;
    private boolean isUserSeeking = false;

    // Filters & Sorting
    private String currentCategory = "ALL"; // ALL, DOWNLOADS, MUSIC, RECORDINGS
    private String currentSort = "DATE"; // DATE, TITLE, ARTIST, DURATION
    private String searchKeyword = "";

    public AudioBrowserDialog(Activity activity, Runnable requestPermissionAction, OnAudioSelectedListener listener) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.requestPermissionAction = requestPermissionAction;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xF00A0B10));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        setContentView(buildLayout());
        checkPermissionAndScan();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0B10);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            root.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });

        // 1. Top Header Bar
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));

        FrameLayout btnClose = new FrameLayout(getContext());
        shape(btnClose, 0x14FFFFFF, dp(18), 0x22FFFFFF, true);
        ImageView icClose = new ImageView(getContext());
        icClose.setImageResource(R.drawable.ic_stop);
        icClose.setColorFilter(0xB3FFFFFF);
        btnClose.addView(icClose, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btnClose.setOnClickListener(v -> dismiss());
        header.addView(btnClose, new LinearLayout.LayoutParams(dp(36), dp(36)));

        header.addView(gapW(12));

        LinearLayout titleCol = new LinearLayout(getContext());
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(getContext());
        title.setText("Gestionnaire des Audios");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleCol.addView(title);

        statsCountText = new TextView(getContext());
        statsCountText.setText("Recherche des morceaux sur l'appareil…");
        statsCountText.setTextSize(11);
        statsCountText.setTextColor(0xFF9CA3AF);
        titleCol.addView(statsCountText);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout btnOpenPlayer = new FrameLayout(getContext());
        shape(btnOpenPlayer, 0x1A6366F1, dp(18), 0x336366F1, true);
        ImageView icPlayer = new ImageView(getContext());
        icPlayer.setImageResource(R.drawable.ic_nav_audio);
        icPlayer.setColorFilter(0xFF8BE9FD);
        btnOpenPlayer.addView(icPlayer, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btnOpenPlayer.setOnClickListener(v -> {
            stopPreviewPlayer();
            MusicPlayerDialog mpd = new MusicPlayerDialog(getContext());
            mpd.show();
        });
        header.addView(btnOpenPlayer, new LinearLayout.LayoutParams(dp(36), dp(36)));

        header.addView(gapW(6));

        FrameLayout btnRefresh = new FrameLayout(getContext());
        shape(btnRefresh, 0x14FFFFFF, dp(18), 0x22FFFFFF, true);
        ImageView icRefresh = new ImageView(getContext());
        icRefresh.setImageResource(R.drawable.ic_refresh);
        icRefresh.setColorFilter(0xB3FFFFFF);
        btnRefresh.addView(icRefresh, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        btnRefresh.setOnClickListener(v -> scanDeviceAudio());
        header.addView(btnRefresh, new LinearLayout.LayoutParams(dp(36), dp(36)));

        root.addView(header);

        // 2. Permission Banner (if needed)
        permissionBanner = new LinearLayout(getContext());
        permissionBanner.setOrientation(LinearLayout.HORIZONTAL);
        permissionBanner.setGravity(Gravity.CENTER_VERTICAL);
        shape(permissionBanner, 0x33A855F7, dp(14), 0x66A855F7, false);
        permissionBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(-1, -2);
        pbLp.setMargins(dp(16), 0, dp(16), dp(10));
        permissionBanner.setLayoutParams(pbLp);

        TextView permTxt = new TextView(getContext());
        permTxt.setText("Autorisez l'accès audio pour afficher toutes les musiques.");
        permTxt.setTextSize(12);
        permTxt.setTextColor(0xFFE2E3EA);
        permissionBanner.addView(permTxt, new LinearLayout.LayoutParams(0, -2, 1));

        Button btnGrant = new Button(getContext());
        btnGrant.setText("Autoriser");
        btnGrant.setTextSize(11);
        btnGrant.setAllCaps(false);
        btnGrant.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnGrant.setBackgroundResource(R.drawable.btn_gradient);
        btnGrant.setTextColor(Color.WHITE);
        btnGrant.setOnClickListener(v -> {
            if (requestPermissionAction != null) requestPermissionAction.run();
        });
        permissionBanner.addView(btnGrant, new LinearLayout.LayoutParams(-2, dp(34)));
        permissionBanner.setVisibility(View.GONE);
        root.addView(permissionBanner);

        // 3. Search Field Card
        LinearLayout searchCard = card(0x14FFFFFF, 0x22FFFFFF, 14);
        searchCard.setOrientation(LinearLayout.HORIZONTAL);
        searchCard.setGravity(Gravity.CENTER_VERTICAL);
        searchCard.setPadding(dp(12), dp(2), dp(12), dp(2));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(-1, dp(44));
        scLp.setMargins(dp(16), 0, dp(16), dp(10));
        searchCard.setLayoutParams(scLp);

        ImageView searchIcon = new ImageView(getContext());
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(0x80FFFFFF);
        searchCard.addView(searchIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        searchInput = new EditText(getContext());
        searchInput.setHint("Rechercher un titre, un artiste, un dossier…");
        searchInput.setHintTextColor(0x66FFFFFF);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(13);
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(10), 0, dp(10), 0);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchKeyword = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                filterAndSortTracks();
            }
            public void afterTextChanged(Editable s) {}
        });
        searchCard.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1));

        FrameLayout btnClearSearch = new FrameLayout(getContext());
        ImageView icClear = new ImageView(getContext());
        icClear.setImageResource(R.drawable.ic_stop);
        icClear.setColorFilter(0x80FFFFFF);
        btnClearSearch.addView(icClear, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        btnClearSearch.setOnClickListener(v -> searchInput.setText(""));
        searchCard.addView(btnClearSearch, new LinearLayout.LayoutParams(dp(28), dp(28)));

        root.addView(searchCard);

        // 4. Category Filters Horizontal Scroll
        HorizontalScrollView catScroll = new HorizontalScrollView(getContext());
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = new LinearLayout(getContext());
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(dp(16), 0, dp(16), dp(8));

        String[] catLabels = {"Tous les audios", "Musiques", "Téléchargements", "Enregistrements"};
        String[] catKeys = {"ALL", "MUSIC", "DOWNLOADS", "RECORDINGS"};

        for (int i = 0; i < catLabels.length; i++) {
            final String key = catKeys[i];
            Button chip = new Button(getContext());
            chip.setText(catLabels[i]);
            chip.setTextSize(11);
            chip.setAllCaps(false);
            chip.setPadding(dp(12), dp(4), dp(12), dp(4));
            updateChipStyle(chip, currentCategory.equals(key));
            chip.setOnClickListener(v -> {
                currentCategory = key;
                for (int j = 0; j < catRow.getChildCount(); j++) {
                    View child = catRow.getChildAt(j);
                    if (child instanceof Button) {
                        updateChipStyle((Button) child, j == (child.getTag() != null ? (int) child.getTag() : -1));
                    }
                }
                filterAndSortTracks();
            });
            chip.setTag(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
            if (i > 0) lp.leftMargin = dp(6);
            catRow.addView(chip, lp);
        }
        catScroll.addView(catRow);
        root.addView(catScroll);

        // 5. Sorting Chips
        HorizontalScrollView sortScroll = new HorizontalScrollView(getContext());
        sortScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout sortRow = new LinearLayout(getContext());
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortRow.setPadding(dp(16), 0, dp(16), dp(8));

        String[] sortLabels = {"Plus récents", "Titre (A-Z)", "Artiste", "Plus longs"};
        String[] sortKeys = {"DATE", "TITLE", "ARTIST", "DURATION"};

        for (int i = 0; i < sortLabels.length; i++) {
            final String key = sortKeys[i];
            Button sortChip = new Button(getContext());
            sortChip.setText(sortLabels[i]);
            sortChip.setTextSize(11);
            sortChip.setAllCaps(false);
            sortChip.setPadding(dp(10), dp(2), dp(10), dp(2));
            updateSortChipStyle(sortChip, currentSort.equals(key));
            sortChip.setOnClickListener(v -> {
                currentSort = key;
                for (int j = 0; j < sortRow.getChildCount(); j++) {
                    View child = sortRow.getChildAt(j);
                    if (child instanceof Button) {
                        updateSortChipStyle((Button) child, currentSort.equals(sortKeys[j]));
                    }
                }
                filterAndSortTracks();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(28));
            if (i > 0) lp.leftMargin = dp(6);
            sortRow.addView(sortChip, lp);
        }
        sortScroll.addView(sortRow);
        root.addView(sortScroll);

        // 6. Main List Area
        FrameLayout listContainer = new FrameLayout(getContext());

        trackListView = new ListView(getContext());
        trackListView.setDivider(new ColorDrawable(0x14FFFFFF));
        trackListView.setDividerHeight(dp(1));
        trackListView.setSelector(new ColorDrawable(0x00000000));
        trackListView.setVerticalScrollBarEnabled(true);
        adapter = new TrackAdapter();
        trackListView.setAdapter(adapter);
        listContainer.addView(trackListView, new FrameLayout.LayoutParams(-1, -1));

        loadingProgress = new ProgressBar(getContext());
        FrameLayout.LayoutParams lpProg = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        listContainer.addView(loadingProgress, lpProg);

        emptyStateText = new TextView(getContext());
        emptyStateText.setText("Aucun fichier audio trouvé.\nUtilisez le bouton Rafraîchir ou l'explorateur système.");
        emptyStateText.setTextSize(13);
        emptyStateText.setTextColor(0x80FFFFFF);
        emptyStateText.setGravity(Gravity.CENTER);
        emptyStateText.setVisibility(View.GONE);
        listContainer.addView(emptyStateText, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        root.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        // 7. Sticky Bottom Preview Player Bar
        previewBottomBar = buildPreviewBottomBar();
        previewBottomBar.setVisibility(View.GONE);
        root.addView(previewBottomBar);

        return root;
    }

    private LinearLayout buildPreviewBottomBar() {
        LinearLayout bar = card(0xF513141F, 0x4DA855F7, 18);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(12), dp(6), dp(12), dp(12));
        bar.setLayoutParams(lp);

        // Info & Playback Controls Row
        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Music icon badge
        FrameLayout iconBadge = new FrameLayout(getContext());
        shape(iconBadge, 0x22A855F7, dp(18), 0x44A855F7, false);
        ImageView icM = new ImageView(getContext());
        icM.setImageResource(R.drawable.ic_volume);
        icM.setColorFilter(0xFFA855F7);
        iconBadge.addView(icM, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        topRow.addView(iconBadge, new LinearLayout.LayoutParams(dp(36), dp(36)));

        topRow.addView(gapW(10));

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        previewTrackTitle = new TextView(getContext());
        previewTrackTitle.setText("Aperçu en cours");
        previewTrackTitle.setTextSize(13);
        previewTrackTitle.setTextColor(Color.WHITE);
        previewTrackTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        previewTrackTitle.setSingleLine(true);
        textCol.addView(previewTrackTitle);

        previewTrackArtist = new TextView(getContext());
        previewTrackArtist.setText("Artiste");
        previewTrackArtist.setTextSize(11);
        previewTrackArtist.setTextColor(0xFF22D3EE);
        previewTrackArtist.setSingleLine(true);
        textCol.addView(previewTrackArtist);

        topRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Transport: -5s, Play/Pause, +5s
        FrameLayout btnRew = new FrameLayout(getContext());
        shape(btnRew, 0x14FFFFFF, dp(16), 0x22FFFFFF, true);
        ImageView icRew = new ImageView(getContext());
        icRew.setImageResource(R.drawable.ic_skip_back);
        icRew.setColorFilter(0xB3FFFFFF);
        btnRew.addView(icRew, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        btnRew.setOnClickListener(v -> seekRelative(-5000));
        topRow.addView(btnRew, new LinearLayout.LayoutParams(dp(32), dp(32)));

        topRow.addView(gapW(6));

        FrameLayout btnPlayPause = new FrameLayout(getContext());
        btnPlayPause.setBackgroundResource(R.drawable.bg_chip_active);
        previewPlayPauseIcon = new ImageView(getContext());
        previewPlayPauseIcon.setImageResource(R.drawable.ic_pause);
        previewPlayPauseIcon.setColorFilter(Color.WHITE);
        btnPlayPause.addView(previewPlayPauseIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        btnPlayPause.setOnClickListener(v -> togglePreviewPlayback());
        topRow.addView(btnPlayPause, new LinearLayout.LayoutParams(dp(40), dp(40)));

        topRow.addView(gapW(6));

        FrameLayout btnFwd = new FrameLayout(getContext());
        shape(btnFwd, 0x14FFFFFF, dp(16), 0x22FFFFFF, true);
        ImageView icFwd = new ImageView(getContext());
        icFwd.setImageResource(R.drawable.ic_skip_forward);
        icFwd.setColorFilter(0xB3FFFFFF);
        btnFwd.addView(icFwd, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        btnFwd.setOnClickListener(v -> seekRelative(5000));
        topRow.addView(btnFwd, new LinearLayout.LayoutParams(dp(32), dp(32)));

        bar.addView(topRow);
        bar.addView(gap(6));

        // SeekBar & Timestamps Row
        LinearLayout seekRow = new LinearLayout(getContext());
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);

        previewTimeCurrent = new TextView(getContext());
        previewTimeCurrent.setText("00:00");
        previewTimeCurrent.setTextSize(10);
        previewTimeCurrent.setTextColor(0x80FFFFFF);
        seekRow.addView(previewTimeCurrent, new LinearLayout.LayoutParams(-2, -2));

        previewSeekBar = new SeekBar(getContext());
        previewSeekBar.setMax(1000);
        previewSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && previewPlayer != null && currentlyPlayingTrack != null && currentlyPlayingTrack.durationMs > 0) {
                    long target = (long) ((progress / 1000.0f) * currentlyPlayingTrack.durationMs);
                    previewTimeCurrent.setText(formatMs(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (previewPlayer != null && currentlyPlayingTrack != null && currentlyPlayingTrack.durationMs > 0) {
                    long target = (long) ((seekBar.getProgress() / 1000.0f) * currentlyPlayingTrack.durationMs);
                    previewPlayer.seekTo((int) target);
                }
            }
        });
        seekRow.addView(previewSeekBar, new LinearLayout.LayoutParams(0, -2, 1));

        previewTimeTotal = new TextView(getContext());
        previewTimeTotal.setText("00:00");
        previewTimeTotal.setTextSize(10);
        previewTimeTotal.setTextColor(0x80FFFFFF);
        seekRow.addView(previewTimeTotal, new LinearLayout.LayoutParams(-2, -2));

        bar.addView(seekRow);
        bar.addView(gap(8));

        // Action button: Importer au Studio
        LinearLayout actionBtns = new LinearLayout(getContext());
        actionBtns.setOrientation(LinearLayout.HORIZONTAL);

        Button btnImportSelected = new Button(getContext());
        btnImportSelected.setText("Importer au Studio");
        btnImportSelected.setTextSize(13);
        btnImportSelected.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnImportSelected.setBackgroundResource(R.drawable.btn_gradient);
        btnImportSelected.setTextColor(Color.WHITE);
        btnImportSelected.setAllCaps(false);
        btnImportSelected.setOnClickListener(v -> {
            if (currentlyPlayingTrack != null) {
                selectAndImportTrack(currentlyPlayingTrack);
            }
        });
        actionBtns.addView(btnImportSelected, new LinearLayout.LayoutParams(-1, dp(44)));

        bar.addView(actionBtns, new LinearLayout.LayoutParams(-1, -2));

        return bar;
    }

    public void checkPermissionAndScan() {
        boolean hasPermission = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(getContext(), "android.permission.READ_MEDIA_AUDIO") == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ContextCompat.checkSelfPermission(getContext(), "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
        }

        if (hasPermission) {
            if (permissionBanner != null) permissionBanner.setVisibility(View.GONE);
            scanDeviceAudio();
        } else {
            if (permissionBanner != null) permissionBanner.setVisibility(View.VISIBLE);
            if (requestPermissionAction != null) {
                requestPermissionAction.run();
            }
            scanDeviceAudio(); // Still attempt scanning MediaStore public audio
        }
    }

    public void scanDeviceAudio() {
        if (loadingProgress != null) loadingProgress.setVisibility(View.VISIBLE);
        if (emptyStateText != null) emptyStateText.setVisibility(View.GONE);

        new Thread(() -> {
            List<AudioTrackItem> found = new ArrayList<>();
            try {
                Uri collection;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                } else {
                    collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH}
                        : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA};

                // Filter audio tracks (duration > 1s)
                String selection = MediaStore.Audio.Media.DURATION + " >= 1000";
                String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

                try (Cursor cursor = getContext().getContentResolver().query(collection, projection, selection, null, sortOrder)) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                        int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                        int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                        int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                        int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                        int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
                        int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
                        int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                        int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                        int relativePathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) : -1;

                        while (cursor.moveToNext()) {
                            long id = idCol >= 0 ? cursor.getLong(idCol) : 0;
                            String title = titleCol >= 0 ? cursor.getString(titleCol) : "Inconnu";
                            String artist = artistCol >= 0 ? cursor.getString(artistCol) : "Artiste Inconnu";
                            String album = albumCol >= 0 ? cursor.getString(albumCol) : "";
                            long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                            long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                            long date = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                            String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : "audio/mpeg";
                            String dataPath = "";
                            if (relativePathCol >= 0) dataPath = cursor.getString(relativePathCol);
                            else if (dataCol >= 0) dataPath = cursor.getString(dataCol);

                            Uri contentUri = ContentUris.withAppendedId(collection, id);
                            found.add(new AudioTrackItem(
                                    id,
                                    title != null ? title : "Inconnu",
                                    artist != null ? artist : "Artiste Inconnu",
                                    album != null ? album : "",
                                    duration,
                                    size,
                                    date,
                                    contentUri,
                                    dataPath != null ? dataPath : "",
                                    mime != null ? mime : "audio/mpeg"
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(found);
                    filterAndSortTracks();
                    if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void filterAndSortTracks() {
        displayedTracks.clear();
        for (AudioTrackItem item : allTracks) {
            // Category check
            if ("DOWNLOADS".equals(currentCategory) && !item.dataPath.toLowerCase(Locale.ROOT).contains("download")) {
                continue;
            }
            if ("RECORDINGS".equals(currentCategory) && !item.dataPath.toLowerCase(Locale.ROOT).contains("recording") && !item.dataPath.toLowerCase(Locale.ROOT).contains("voice")) {
                continue;
            }
            if ("MUSIC".equals(currentCategory) && (item.dataPath.toLowerCase(Locale.ROOT).contains("recording") || item.dataPath.toLowerCase(Locale.ROOT).contains("whatsapp"))) {
                continue;
            }

            // Search keyword check
            if (!searchKeyword.isEmpty()) {
                boolean matchTitle = item.title.toLowerCase(Locale.ROOT).contains(searchKeyword);
                boolean matchArtist = item.artist.toLowerCase(Locale.ROOT).contains(searchKeyword);
                boolean matchAlbum = item.album.toLowerCase(Locale.ROOT).contains(searchKeyword);
                boolean matchPath = item.dataPath.toLowerCase(Locale.ROOT).contains(searchKeyword);
                if (!matchTitle && !matchArtist && !matchAlbum && !matchPath) {
                    continue;
                }
            }
            displayedTracks.add(item);
        }

        // Sort
        if ("TITLE".equals(currentSort)) {
            Collections.sort(displayedTracks, (a, b) -> a.title.compareToIgnoreCase(b.title));
        } else if ("ARTIST".equals(currentSort)) {
            Collections.sort(displayedTracks, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
        } else if ("DURATION".equals(currentSort)) {
            Collections.sort(displayedTracks, (a, b) -> Long.compare(b.durationMs, a.durationMs));
        } else {
            // Date added
            Collections.sort(displayedTracks, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
        }

        if (statsCountText != null) {
            statsCountText.setText(displayedTracks.size() + " audio" + (displayedTracks.size() > 1 ? "s" : "") + " disponible" + (displayedTracks.size() > 1 ? "s" : ""));
        }

        if (emptyStateText != null) {
            emptyStateText.setVisibility(displayedTracks.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void playTrackPreview(AudioTrackItem track) {
        if (currentlyPlayingTrack != null && currentlyPlayingTrack.id == track.id && previewPlayer != null) {
            togglePreviewPlayback();
            return;
        }

        stopPreviewPlayer();
        currentlyPlayingTrack = track;

        // Arrêter obligatoirement le lecteur musical et le studio pour éviter tout mélange sonore
        try {
            MusicPlayerManager.getInstance(getContext()).pause();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).pauseStudioPlayer();
            }
        } catch (Exception ignored) {}

        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(getContext(), track.contentUri);
            previewPlayer.prepare();
            previewPlayer.start();
            isPlaying = true;

            if (previewBottomBar != null) previewBottomBar.setVisibility(View.VISIBLE);
            if (previewTrackTitle != null) previewTrackTitle.setText(track.title);
            if (previewTrackArtist != null) previewTrackArtist.setText(track.artist + (track.album.isEmpty() ? "" : " • " + track.album));
            if (previewTimeTotal != null) previewTimeTotal.setText(track.getFormattedDuration());
            if (previewPlayPauseIcon != null) previewPlayPauseIcon.setImageResource(R.drawable.ic_pause);

            startProgressUpdates();
            if (adapter != null) adapter.notifyDataSetChanged();

            previewPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                if (previewPlayPauseIcon != null) previewPlayPauseIcon.setImageResource(R.drawable.ic_play);
                if (adapter != null) adapter.notifyDataSetChanged();
            });

        } catch (Exception e) {
            Toast.makeText(getContext(), "Impossible de lire l'aperçu : " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopPreviewPlayer();
        }
    }

    private void togglePreviewPlayback() {
        if (previewPlayer == null) return;
        if (isPlaying) {
            previewPlayer.pause();
            isPlaying = false;
            if (previewPlayPauseIcon != null) previewPlayPauseIcon.setImageResource(R.drawable.ic_play);
        } else {
            // Arrêter obligatoirement les autres lecteurs avant de relancer l'aperçu
            try {
                MusicPlayerManager.getInstance(getContext()).pause();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).pauseStudioPlayer();
                }
            } catch (Exception ignored) {}
            previewPlayer.start();
            isPlaying = true;
            if (previewPlayPauseIcon != null) previewPlayPauseIcon.setImageResource(R.drawable.ic_pause);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void seekRelative(int deltaMs) {
        if (previewPlayer == null || currentlyPlayingTrack == null) return;
        int cur = previewPlayer.getCurrentPosition();
        int target = Math.max(0, Math.min((int) currentlyPlayingTrack.durationMs, cur + deltaMs));
        previewPlayer.seekTo(target);
    }

    private void selectAndImportTrack(AudioTrackItem track) {
        stopPreviewPlayer();
        dismiss();
        if (listener != null) {
            listener.onAudioSelected(track.contentUri, track.title, track.artist, track.durationMs);
        }
    }

    public void stopPreviewPlayer() {
        progressHandler.removeCallbacksAndMessages(null);
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
        isPlaying = false;
    }

    private void startProgressUpdates() {
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (previewPlayer != null && isPlaying && !isUserSeeking) {
                    int pos = previewPlayer.getCurrentPosition();
                    int dur = previewPlayer.getDuration();
                    if (dur > 0 && previewSeekBar != null) {
                        previewSeekBar.setProgress((int) ((pos / (float) dur) * 1000));
                    }
                    if (previewTimeCurrent != null) {
                        previewTimeCurrent.setText(formatMs(pos));
                    }
                }
                progressHandler.postDelayed(this, 150);
            }
        });
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPreviewPlayer();
    }

    private String formatMs(long ms) {
        long s = (ms / 1000) % 60;
        long m = (ms / (1000 * 60)) % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    // ==========================================
    // Adaptateur de Liste Personnalisé
    // ==========================================
    private class TrackAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return displayedTracks.size();
        }

        @Override
        public AudioTrackItem getItem(int position) {
            return displayedTracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TrackViewHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));

                // Left: Circular Play Preview Button
                FrameLayout playBtn = new FrameLayout(getContext());
                shape(playBtn, 0x1A22D3EE, dp(20), 0x3322D3EE, true);
                ImageView playIcon = new ImageView(getContext());
                playIcon.setImageResource(R.drawable.ic_play);
                playIcon.setColorFilter(0xFF22D3EE);
                playBtn.addView(playIcon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
                row.addView(playBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

                row.addView(gapW(12));

                // Center: Title & Subtitles
                LinearLayout infoCol = new LinearLayout(getContext());
                infoCol.setOrientation(LinearLayout.VERTICAL);

                TextView titleTv = new TextView(getContext());
                titleTv.setTextSize(13);
                titleTv.setTextColor(Color.WHITE);
                titleTv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                titleTv.setSingleLine(true);
                infoCol.addView(titleTv);

                TextView subtitleTv = new TextView(getContext());
                subtitleTv.setTextSize(11);
                subtitleTv.setTextColor(0xFF9CA3AF);
                subtitleTv.setSingleLine(true);
                infoCol.addView(subtitleTv);

                TextView metaTv = new TextView(getContext());
                metaTv.setTextSize(10);
                metaTv.setTextColor(0x80FFFFFF);
                metaTv.setSingleLine(true);
                infoCol.addView(metaTv);

                row.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1));

                row.addView(gapW(10));

                // Right: Import Button
                Button importBtn = new Button(getContext());
                importBtn.setText("Importer");
                importBtn.setTextSize(11);
                importBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                importBtn.setAllCaps(false);
                shape(importBtn, 0x2210B981, dp(14), 0x4410B981, true);
                importBtn.setTextColor(0xFF10B981);
                row.addView(importBtn, new LinearLayout.LayoutParams(dp(76), dp(34)));

                holder = new TrackViewHolder(row, playBtn, playIcon, titleTv, subtitleTv, metaTv, importBtn);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (TrackViewHolder) convertView.getTag();
            }

            AudioTrackItem item = getItem(position);
            boolean isThisPlaying = currentlyPlayingTrack != null && currentlyPlayingTrack.id == item.id && isPlaying;

            holder.titleTv.setText(item.title);
            holder.titleTv.setTextColor(isThisPlaying ? 0xFF22D3EE : Color.WHITE);

            String sub = item.artist + (item.album.isEmpty() ? "" : " • " + item.album);
            holder.subtitleTv.setText(sub);

            String meta = item.getFormattedDuration() + "  •  " + item.getFormattedSize();
            holder.metaTv.setText(meta);

            // Play Icon update
            if (isThisPlaying) {
                holder.playIcon.setImageResource(R.drawable.ic_pause);
                shape(holder.playBtn, 0x3322D3EE, dp(20), 0xFF22D3EE, true);
                holder.playIcon.setColorFilter(0xFF22D3EE);
            } else {
                holder.playIcon.setImageResource(R.drawable.ic_play);
                shape(holder.playBtn, 0x14FFFFFF, dp(20), 0x22FFFFFF, true);
                holder.playIcon.setColorFilter(0xB3FFFFFF);
            }

            holder.playBtn.setOnClickListener(v -> playTrackPreview(item));
            holder.rootView.setOnClickListener(v -> playTrackPreview(item));
            holder.importBtn.setOnClickListener(v -> selectAndImportTrack(item));

            return convertView;
        }
    }

    private static class TrackViewHolder {
        final View rootView;
        final FrameLayout playBtn;
        final ImageView playIcon;
        final TextView titleTv;
        final TextView subtitleTv;
        final TextView metaTv;
        final Button importBtn;

        TrackViewHolder(View rootView, FrameLayout playBtn, ImageView playIcon,
                        TextView titleTv, TextView subtitleTv, TextView metaTv, Button importBtn) {
            this.rootView = rootView;
            this.playBtn = playBtn;
            this.playIcon = playIcon;
            this.titleTv = titleTv;
            this.subtitleTv = subtitleTv;
            this.metaTv = metaTv;
            this.importBtn = importBtn;
        }
    }

    // ==========================================
    // UI Helpers
    // ==========================================
    private void updateChipStyle(Button b, boolean active) {
        if (active) {
            b.setBackgroundResource(R.drawable.bg_chip_active);
            b.setTextColor(Color.WHITE);
            b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        } else {
            shape(b, 0x14FFFFFF, dp(14), 0x22FFFFFF, true);
            b.setTextColor(0xB3FFFFFF);
            b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    private void updateSortChipStyle(Button b, boolean active) {
        if (active) {
            shape(b, 0x22A855F7, dp(12), 0xFFA855F7, true);
            b.setTextColor(0xFFA855F7);
            b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        } else {
            shape(b, 0x0AFFFFFF, dp(12), 0x14FFFFFF, true);
            b.setTextColor(0x80FFFFFF);
            b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    private LinearLayout card(int bg, int stroke, int radiusDp) {
        LinearLayout l = new LinearLayout(getContext());
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
