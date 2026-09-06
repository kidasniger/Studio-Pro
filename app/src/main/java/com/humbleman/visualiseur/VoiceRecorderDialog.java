package com.kidas.studiopro;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VoiceRecorderDialog extends Dialog {

    public interface OnRecordingResultListener {
        void onApplyAudioAndLyrics(File recordedAudio, String mime, String trackName, List<MainActivity.LyricLine> lyrics, String fullText);
        void onApplyLyricsOnly(List<MainActivity.LyricLine> lyrics, String fullText);
    }

    private final Context context;
    private final String groqApiKey;
    private final OnRecordingResultListener listener;

    private MediaRecorder mediaRecorder;
    private SpeechRecognizer speechRecognizer;
    private File recordedFile;
    private boolean isRecording = false;
    private long recordStartTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable amplitudeRunnable;

    // UI Components
    private TextView statusBadge;
    private TextView timerText;
    private LiveWaveformView waveformView;
    private TextView liveTranscriptionText;
    private TextView resultSubtitle;
    private ScrollView lrcScrollView;
    private TextView lrcResultText;
    private ProgressBar progressBar;
    private FrameLayout recordBtnWrapper;
    private ImageView recordBtnIcon;
    private LinearLayout actionsLayout;
    private Button applyAllBtn;
    private Button applyLyricsBtn;
    private Button exportBtn;
    private Button resetBtn;

    private final List<MainActivity.LyricLine> generatedLyrics = new ArrayList<>();
    private String fullTranscribedText = "";
    private final float density;

    public VoiceRecorderDialog(@NonNull Context context, String groqApiKey, OnRecordingResultListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.groqApiKey = groqApiKey != null ? groqApiKey.trim() : "";
        this.listener = listener;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(0xEE090A0F));
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        setContentView(buildView());
        initSpeechRecognizer();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFA090A0F);

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(20), dp(24), dp(20), dp(24));
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Header : Titre & Bouton Fermer
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        
        TextView title = new TextView(context);
        title.setText("Enregistreur Micro & Live IA");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText("Reconnaissance vocale locale en direct, puis Groq Whisper à l’arrêt");
        sub.setTextSize(12);
        sub.setTextColor(0xFF9CA3AF);
        titleCol.addView(sub);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout closeBtn = new FrameLayout(context);
        GradientDrawable bgClose = new GradientDrawable();
        bgClose.setColor(0x1AFFFFFF);
        bgClose.setCornerRadius(dp(18));
        closeBtn.setBackground(bgClose);
        TextView closeTxt = new TextView(context);
        closeTxt.setText("");
        closeTxt.setTextColor(Color.WHITE);
        closeTxt.setTextSize(14);
        closeBtn.addView(closeTxt, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        mainLayout.addView(header);
        mainLayout.addView(gap(16));

        // Scrollable content
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        // Badge d'état
        statusBadge = new TextView(context);
        statusBadge.setText("PRÊT À ENREGISTRER");
        statusBadge.setTextSize(11);
        statusBadge.setLetterSpacing(0.1f);
        statusBadge.setTextColor(0xFF22D3EE);
        statusBadge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        setShape(statusBadge, 0x1A22D3EE, dp(8), 0x3322D3EE);
        statusBadge.setPadding(dp(12), dp(4), dp(12), dp(4));
        content.addView(statusBadge);
        content.addView(gap(10));

        // Chronomètre
        timerText = new TextView(context);
        timerText.setText("00:00");
        timerText.setTextSize(36);
        timerText.setTextColor(Color.WHITE);
        timerText.setTypeface(Typeface.create("sans-serif-thin", Typeface.BOLD));
        timerText.setGravity(Gravity.CENTER);
        content.addView(timerText);
        content.addView(gap(12));

        // Visualiseur de son en direct (Waveform Animée)
        waveformView = new LiveWaveformView(context);
        LinearLayout.LayoutParams wvLp = new LinearLayout.LayoutParams(dp(280), dp(70));
        waveformView.setLayoutParams(wvLp);
        content.addView(waveformView);
        content.addView(gap(16));

        // Bouton Central d'enregistrement
        recordBtnWrapper = new FrameLayout(context);
        GradientDrawable recBg = new GradientDrawable();
        recBg.setColor(0xFFEF4444); // Rouge enregistrement
        recBg.setCornerRadius(dp(40));
        recordBtnWrapper.setBackground(recBg);
        recordBtnWrapper.setElevation(dp(8));

        recordBtnIcon = new ImageView(context);
        recordBtnIcon.setImageResource(R.drawable.ic_mic);
        recordBtnIcon.setColorFilter(Color.WHITE);
        recordBtnWrapper.addView(recordBtnIcon, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));

        recordBtnWrapper.setOnClickListener(v -> toggleRecording());
        content.addView(recordBtnWrapper, new LinearLayout.LayoutParams(dp(76), dp(76)));
        content.addView(gap(16));

        // Carte : Reconnaissance vocale en direct
        LinearLayout liveCard = new LinearLayout(context);
        liveCard.setOrientation(LinearLayout.VERTICAL);
        setShape(liveCard, 0x0DFFFFFF, dp(16), 0x14FFFFFF);
        liveCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView liveHeader = new TextView(context);
        liveHeader.setText("TRANSCRIPTION EN DIRECT");
        liveHeader.setTextSize(11);
        liveHeader.setLetterSpacing(0.08f);
        liveHeader.setTextColor(0xFFA855F7);
        liveHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        liveCard.addView(liveHeader);
        liveCard.addView(gap(6));

        liveTranscriptionText = new TextView(context);
        liveTranscriptionText.setText("Appuyez sur le micro et commencez à parler ou chanter…");
        liveTranscriptionText.setTextSize(13);
        liveTranscriptionText.setTextColor(0xB3FFFFFF);
        liveTranscriptionText.setLineSpacing(dp(2), 1.15f);
        liveCard.addView(liveTranscriptionText);

        content.addView(liveCard, new LinearLayout.LayoutParams(-1, -2));
        content.addView(gap(14));

        // Zone de Progression / Résultat Groq Whisper
        progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);
        content.addView(progressBar);

        resultSubtitle = new TextView(context);
        resultSubtitle.setTextSize(12);
        resultSubtitle.setTextColor(0xFF10B981);
        resultSubtitle.setVisibility(View.GONE);
        content.addView(resultSubtitle);
        content.addView(gap(6));

        lrcScrollView = new ScrollView(context);
        lrcScrollView.setVisibility(View.GONE);
        setShape(lrcScrollView, 0x12FFFFFF, dp(14), 0x22FFFFFF);
        lrcScrollView.setPadding(dp(14), dp(10), dp(14), dp(10));
        
        lrcResultText = new TextView(context);
        lrcResultText.setTextSize(12);
        lrcResultText.setTextColor(0xFFE2E8F0);
        lrcResultText.setTypeface(Typeface.MONOSPACE);
        lrcResultText.setLineSpacing(dp(3), 1.2f);
        lrcScrollView.addView(lrcResultText);

        content.addView(lrcScrollView, new LinearLayout.LayoutParams(-1, dp(130)));
        content.addView(gap(16));

        // Actions une fois terminé
        actionsLayout = new LinearLayout(context);
        actionsLayout.setOrientation(LinearLayout.VERTICAL);
        actionsLayout.setVisibility(View.GONE);

        applyAllBtn = createAccentButton(" Utiliser comme piste & paroles", 0xFFA855F7);
        applyAllBtn.setOnClickListener(v -> applyAudioAndLyrics());
        actionsLayout.addView(applyAllBtn, new LinearLayout.LayoutParams(-1, dp(44)));
        actionsLayout.addView(gap(8));

        LinearLayout subRow = new LinearLayout(context);
        subRow.setOrientation(LinearLayout.HORIZONTAL);

        applyLyricsBtn = createOutlineButton("Paroles uniquement");
        applyLyricsBtn.setOnClickListener(v -> applyLyricsOnly());
        subRow.addView(applyLyricsBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
        subRow.addView(gapW(8));

        exportBtn = createOutlineButton(" Exporter (.LRC)");
        exportBtn.setOnClickListener(v -> exportLrcAndAudio());
        subRow.addView(exportBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        actionsLayout.addView(subRow);
        actionsLayout.addView(gap(8));

        resetBtn = createOutlineButton("Recommencer l'enregistrement");
        resetBtn.setTextColor(0xFFEF4444);
        resetBtn.setOnClickListener(v -> resetRecordingState());
        actionsLayout.addView(resetBtn, new LinearLayout.LayoutParams(-1, dp(38)));

        content.addView(actionsLayout, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(content);
        mainLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(mainLayout);
        return root;
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return;
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {
                    if (waveformView != null && isRecording) {
                        float normalized = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 10f));
                        waveformView.setLiveLevel(normalized);
                    }
                }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    // Si l'écoute continue s'arrête, on peut relancer si on enregistre toujours
                    if (isRecording) {
                        handler.postDelayed(() -> startSpeechListening(), 400);
                    }
                }
                @Override public void onResults(Bundle results) {
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String txt = matches.get(0);
                            if (fullTranscribedText.isEmpty()) {
                                fullTranscribedText = txt;
                            } else {
                                fullTranscribedText += " " + txt;
                            }
                            liveTranscriptionText.setText(fullTranscribedText);
                        }
                    }
                    if (isRecording) {
                        handler.postDelayed(() -> startSpeechListening(), 200);
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    if (partialResults != null) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String partial = matches.get(0);
                            String display = fullTranscribedText.isEmpty() ? partial : fullTranscribedText + " " + partial;
                            liveTranscriptionText.setText(display);
                        }
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception ignored) {}
    }

    private void startSpeechListening() {
        if (speechRecognizer == null || !isRecording) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().getLanguage());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speechRecognizer.startListening(intent);
        } catch (Exception ignored) {}
    }

    private void stopSpeechListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
    }

    private void toggleRecording() {
        if (!hasRecordPermission()) {
            Toast.makeText(context, "Permission micro requise pour enregistrer.", Toast.LENGTH_SHORT).show();
            if (context instanceof MainActivity) {
                ActivityCompat.requestPermissions((MainActivity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            }
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecordingAndTranscribe();
        }
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startRecording() {
        try {
            recordedFile = new File(context.getCacheDir(), "voice_rec_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(recordedFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordStartTime = SystemClock.elapsedRealtime();
            fullTranscribedText = "";
            generatedLyrics.clear();

            // UI Changes
            statusBadge.setText(" ENREGISTREMENT EN COURS…");
            statusBadge.setTextColor(0xFFEF4444);
            setShape(statusBadge, 0x22EF4444, dp(8), 0x44EF4444);

            recordBtnIcon.setImageResource(R.drawable.ic_stop);
            GradientDrawable stopBg = new GradientDrawable();
            stopBg.setColor(0xFF8B5CF6); // Violet pulse
            stopBg.setCornerRadius(dp(40));
            recordBtnWrapper.setBackground(stopBg);

            liveTranscriptionText.setText("Écoute en cours… Parlez ou chantez.");
            actionsLayout.setVisibility(View.GONE);
            lrcScrollView.setVisibility(View.GONE);
            resultSubtitle.setVisibility(View.GONE);

            // Timer task
            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isRecording) {
                        long elapsed = (SystemClock.elapsedRealtime() - recordStartTime) / 1000;
                        long min = elapsed / 60;
                        long sec = elapsed % 60;
                        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                        handler.postDelayed(this, 500);
                    }
                }
            };
            handler.post(timerRunnable);

            // Amplitude sampling
            amplitudeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isRecording && mediaRecorder != null) {
                        try {
                            int maxAmp = mediaRecorder.getMaxAmplitude();
                            float norm = Math.min(1f, maxAmp / 28000f);
                            waveformView.setLiveLevel(norm);
                        } catch (Exception ignored) {}
                        handler.postDelayed(this, 60);
                    }
                }
            };
            handler.post(amplitudeRunnable);

            // Start SpeechRecognizer for instantaneous preview
            startSpeechListening();

        } catch (Exception e) {
            Toast.makeText(context, "Erreur démarrage micro : " + e.getMessage(), Toast.LENGTH_SHORT).show();
            resetRecordingState();
        }
    }

    private void stopRecordingAndTranscribe() {
        if (!isRecording) return;
        isRecording = false;

        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(amplitudeRunnable);
        waveformView.setLiveLevel(0f);
        stopSpeechListening();

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}

        // UI : Mode Transcription
        statusBadge.setText(" TRANSCRIPTION GROQ WHISPER EN COURS…");
        statusBadge.setTextColor(0xFFA855F7);
        setShape(statusBadge, 0x22A855F7, dp(8), 0x44A855F7);
        progressBar.setVisibility(View.VISIBLE);
        recordBtnWrapper.setEnabled(false);

        // Lancement transcription Groq Whisper
        new Thread(() -> {
            try {
                if (groqApiKey.isEmpty()) {
                    throw new Exception("Clé API Groq non configurée. (Ajoutez votre clé Groq dans l'onglet IA).");
                }

                MainActivity.GroqClient.Transcript transcript = MainActivity.GroqClient.transcribe(
                        groqApiKey,
                        recordedFile,
                        "audio/mp4",
                        null // auto-détection de langue (FR, EN, etc.)
                );

                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    recordBtnWrapper.setEnabled(true);

                    generatedLyrics.clear();
                    generatedLyrics.addAll(transcript.lines);
                    fullTranscribedText = transcript.text;

                    statusBadge.setText(" TRANSCRIPTION WHISPER TERMINÉE");
                    statusBadge.setTextColor(0xFF10B981);
                    setShape(statusBadge, 0x2210B981, dp(8), 0x4410B981);

                    resultSubtitle.setVisibility(View.VISIBLE);
                    resultSubtitle.setText(" " + generatedLyrics.size() + " segments synchronisés générés avec précision !");

                    liveTranscriptionText.setText(fullTranscribedText.isEmpty() ? "(Aucune parole détectée)" : fullTranscribedText);

                    // Affichage des paroles au format LRC
                    StringBuilder lrcSb = new StringBuilder();
                    for (MainActivity.LyricLine line : generatedLyrics) {
                        long min = line.startMs / 60000;
                        double sec = (line.startMs % 60000) / 1000.0;
                        lrcSb.append(String.format(Locale.US, "[%02d:%05.2f] %s\n", min, sec, line.text));
                    }
                    lrcResultText.setText(lrcSb.toString().trim());
                    lrcScrollView.setVisibility(View.VISIBLE);

                    actionsLayout.setVisibility(View.VISIBLE);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    recordBtnWrapper.setEnabled(true);

                    statusBadge.setText(" ÉCHEC GROQ - MODE SECOURS");
                    statusBadge.setTextColor(0xFFF59E0B);
                    setShape(statusBadge, 0x22F59E0B, dp(8), 0x44F59E0B);

                    // Fallback vers les paroles locales de SpeechRecognizer si disponibles
                    if (!fullTranscribedText.isEmpty()) {
                        long durationMs = Math.max(3000, SystemClock.elapsedRealtime() - recordStartTime);
                        generatedLyrics.clear();
                        generatedLyrics.add(new MainActivity.LyricLine(0, durationMs, fullTranscribedText));

                        resultSubtitle.setVisibility(View.VISIBLE);
                        resultSubtitle.setText("Transcription locale Android utilisée (" + e.getMessage() + ")");
                        lrcResultText.setText("[00:00.00] " + fullTranscribedText);
                        lrcScrollView.setVisibility(View.VISIBLE);
                        actionsLayout.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(context, "Erreur transcription : " + e.getMessage(), Toast.LENGTH_LONG).show();
                        resetRecordingState();
                    }
                });
            }
        }).start();
    }

    private void applyAudioAndLyrics() {
        if (recordedFile != null && recordedFile.exists() && listener != null) {
            String trackName = "Enregistrement Micro " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            listener.onApplyAudioAndLyrics(recordedFile, "audio/mp4", trackName, generatedLyrics, fullTranscribedText);
            dismiss();
        }
    }

    private void applyLyricsOnly() {
        if (listener != null && !generatedLyrics.isEmpty()) {
            listener.onApplyLyricsOnly(generatedLyrics, fullTranscribedText);
            dismiss();
        }
    }

    private void exportLrcAndAudio() {
        if (generatedLyrics.isEmpty()) return;
        try {
            String baseName = "Voix_IA_" + System.currentTimeMillis();

            // Export LRC
            StringBuilder sb = new StringBuilder();
            sb.append("[ti:").append(baseName).append("]\n");
            sb.append("[re:Studio Pro - Groq Whisper]\n");
            for (MainActivity.LyricLine l : generatedLyrics) {
                long m = l.startMs / 60000;
                double s = (l.startMs % 60000) / 1000.0;
                sb.append(String.format(Locale.US, "[%02d:%05.2f] %s\n", m, s, l.text));
            }

            ContentValues cvLrc = new ContentValues();
            cvLrc.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".lrc");
            cvLrc.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            if (Build.VERSION.SDK_INT >= 29) {
                cvLrc.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/StudioPro");
            }
            Uri lrcUri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), cvLrc);
            if (lrcUri != null) {
                try (OutputStream os = context.getContentResolver().openOutputStream(lrcUri)) {
                    os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            // Export Audio
            if (recordedFile != null && recordedFile.exists()) {
                ContentValues cvAudio = new ContentValues();
                cvAudio.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".m4a");
                cvAudio.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
                if (Build.VERSION.SDK_INT >= 29) {
                    cvAudio.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/StudioPro");
                }
                Uri audioUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cvAudio);
                if (audioUri != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(audioUri);
                         FileInputStream fis = new FileInputStream(recordedFile)) {
                        byte[] b = new byte[16384];
                        int n;
                        while ((n = fis.read(b)) != -1) os.write(b, 0, n);
                    }
                }
            }

            Toast.makeText(context, "Fichiers .LRC et audio exportés avec succès !", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "Erreur export : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resetRecordingState() {
        isRecording = false;
        handler.removeCallbacks(timerRunnable);
        handler.removeCallbacks(amplitudeRunnable);
        stopSpeechListening();

        try {
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {}

        if (recordedFile != null && recordedFile.exists()) {
            recordedFile.delete();
        }

        generatedLyrics.clear();
        fullTranscribedText = "";

        statusBadge.setText("PRÊT À ENREGISTRER");
        statusBadge.setTextColor(0xFF22D3EE);
        setShape(statusBadge, 0x1A22D3EE, dp(8), 0x3322D3EE);

        timerText.setText("00:00");
        waveformView.setLiveLevel(0f);

        recordBtnIcon.setImageResource(R.drawable.ic_mic);
        GradientDrawable recBg = new GradientDrawable();
        recBg.setColor(0xFFEF4444);
        recBg.setCornerRadius(dp(40));
        recordBtnWrapper.setBackground(recBg);

        liveTranscriptionText.setText("Appuyez sur le micro et commencez à parler ou chanter…");
        progressBar.setVisibility(View.GONE);
        resultSubtitle.setVisibility(View.GONE);
        lrcScrollView.setVisibility(View.GONE);
        actionsLayout.setVisibility(View.GONE);
    }

    @Override
    public void dismiss() {
        resetRecordingState();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        super.dismiss();
    }

    private void setShape(View v, int color, int corner, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(corner);
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        v.setBackground(d);
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

    private Button createAccentButton(String text, int color) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        b.setBackground(d);
        return b;
    }

    private Button createOutlineButton(String text) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextColor(0xFFE2E8F0);
        b.setTextSize(12);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x14FFFFFF);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), 0x24FFFFFF);
        b.setBackground(d);
        return b;
    }

    // ==========================================
    // Vue Waveform Dynamique en Direct
    // ==========================================
    private static class LiveWaveformView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] barHeights = new float[28];
        private float currentLevel = 0f;
        private final float density;

        public LiveWaveformView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
        }

        public void setLiveLevel(float level) {
            this.currentLevel = Math.max(0.05f, Math.min(1f, level));
            for (int i = barHeights.length - 1; i > 0; i--) {
                barHeights[i] = barHeights[i - 1] * 0.88f;
            }
            barHeights[0] = currentLevel;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int count = barHeights.length;
            float totalBarW = w / (float) count;
            float barW = totalBarW * 0.55f;
            float cy = h / 2f;

            for (int i = 0; i < count; i++) {
                float progress = i / (float) count;
                int barColor = (progress < 0.5f) ? 0xFF22D3EE : 0xFFA855F7; // Cyan -> Violet
                barPaint.setColor(barColor);

                float barH = Math.max(4 * density, barHeights[i] * (h * 0.9f));
                float left = i * totalBarW + (totalBarW - barW) / 2f;
                float top = cy - barH / 2f;
                float right = left + barW;
                float bottom = cy + barH / 2f;

                canvas.drawRoundRect(new RectF(left, top, right, bottom), barW / 2f, barW / 2f, barPaint);
            }
        }
    }
}
