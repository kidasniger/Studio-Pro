package com.kidas.studiopro;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Build;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioEffectsDialog extends Dialog {

    private final Context context;
    private final MediaPlayer player;
    private final AudioEffectsManager effectsManager;
    private final float density;

    public AudioEffectsDialog(@NonNull Context context, MediaPlayer player) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.player = player;
        this.effectsManager = AudioEffectsManager.getInstance();
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xEE090A0F));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        setContentView(buildView());
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFA0B0E14);

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(20), dp(16), dp(20), dp(16));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            mainLayout.setPadding(
                    Math.max(dp(16), insets.left),
                    Math.max(dp(16), insets.top),
                    Math.max(dp(16), insets.right),
                    Math.max(dp(16), insets.bottom)
            );
            return windowInsets;
        });

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText("Égaliseur & Effets Audio FX");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText("Bass Boost, Reverb 3D, Vitesse & Égalisation");
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
        closeTxt.setText("✕");
        closeTxt.setTextColor(Color.WHITE);
        closeTxt.setTextSize(14);
        closeBtn.addView(closeTxt, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        mainLayout.addView(header);
        mainLayout.addView(gap(16));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // Switch Activation globale
        LinearLayout switchRow = new LinearLayout(context);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        setShape(switchRow, 0x0DFFFFFF, dp(14), 0x1AFFFFFF);
        switchRow.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView switchLabel = new TextView(context);
        switchLabel.setText("Activer le moteur d'effets DSP");
        switchLabel.setTextColor(Color.WHITE);
        switchLabel.setTextSize(14);
        switchLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0, -2, 1));

        Switch effectsSwitch = new Switch(context);
        effectsSwitch.setChecked(effectsManager.isEffectsEnabled());
        effectsSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            effectsManager.setEffectsEnabled(isChecked);
        });
        switchRow.addView(effectsSwitch);
        content.addView(switchRow);
        content.addView(gap(16));

        // 1. Presets Égaliseur
        content.addView(sectionTitle("PRESETS ÉGALISEUR"));
        content.addView(gap(8));

        HorizontalScrollView eqScroll = new HorizontalScrollView(context);
        eqScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout eqPresetsRow = new LinearLayout(context);
        eqPresetsRow.setOrientation(LinearLayout.HORIZONTAL);

        Equalizer eq = effectsManager.getEqualizer();
        List<Button> eqButtons = new ArrayList<>();
        if (eq != null) {
            short numPresets = eq.getNumberOfPresets();
            short currentPreset = effectsManager.getEqPreset();
            for (short i = 0; i < numPresets; i++) {
                final short presetIdx = i;
                String name = eq.getPresetName(presetIdx);
                Button btn = createChipButton(name, currentPreset == presetIdx);
                btn.setOnClickListener(v -> {
                    effectsManager.setEqPreset(presetIdx);
                    for (int j = 0; j < eqButtons.size(); j++) {
                        updateChipButton(eqButtons.get(j), j == presetIdx);
                    }
                });
                eqButtons.add(btn);
                eqPresetsRow.addView(btn);
                eqPresetsRow.addView(gapW(8));
            }
        } else {
            // Fallback presets
            String[] defaults = {"Normal", "Bass Boost", "Vocal", "Rock", "Pop", "Jazz"};
            for (String name : defaults) {
                Button btn = createChipButton(name, "Normal".equals(name));
                eqPresetsRow.addView(btn);
                eqPresetsRow.addView(gapW(8));
            }
        }
        eqScroll.addView(eqPresetsRow);
        content.addView(eqScroll);
        content.addView(gap(16));

        // 2. Bass Boost & Virtualizer 3D Sliders
        content.addView(sectionTitle("ENHANCERS AUDIO (BASS & 3D)"));
        content.addView(gap(8));

        LinearLayout cardEnhancers = new LinearLayout(context);
        cardEnhancers.setOrientation(LinearLayout.VERTICAL);
        setShape(cardEnhancers, 0x0DFFFFFF, dp(16), 0x14FFFFFF);
        cardEnhancers.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Bass Boost
        TextView bassLabel = new TextView(context);
        bassLabel.setText("Bass Boost : " + (effectsManager.getBassStrength() / 10) + "%");
        bassLabel.setTextColor(0xFF22D3EE);
        bassLabel.setTextSize(13);
        bassLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        cardEnhancers.addView(bassLabel);

        SeekBar bassBar = new SeekBar(context);
        bassBar.setMax(1000);
        bassBar.setProgress(effectsManager.getBassStrength());
        bassBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    effectsManager.setBassStrength(p);
                    bassLabel.setText("Bass Boost : " + (p / 10) + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        cardEnhancers.addView(bassBar);
        cardEnhancers.addView(gap(12));

        // 3D Virtualizer
        TextView virtLabel = new TextView(context);
        virtLabel.setText("Spatialisation 3D (Virtualizer) : " + (effectsManager.getVirtualizerStrength() / 10) + "%");
        virtLabel.setTextColor(0xFFA855F7);
        virtLabel.setTextSize(13);
        virtLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        cardEnhancers.addView(virtLabel);

        SeekBar virtBar = new SeekBar(context);
        virtBar.setMax(1000);
        virtBar.setProgress(effectsManager.getVirtualizerStrength());
        virtBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    effectsManager.setVirtualizerStrength(p);
                    virtLabel.setText("Spatialisation 3D (Virtualizer) : " + (p / 10) + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        cardEnhancers.addView(virtBar);
        content.addView(cardEnhancers);
        content.addView(gap(16));

        // 3. Reverb Room Presets
        content.addView(sectionTitle("RÉVERBÉRATION & AMBIANCE"));
        content.addView(gap(8));

        HorizontalScrollView revScroll = new HorizontalScrollView(context);
        revScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout revRow = new LinearLayout(context);
        revRow.setOrientation(LinearLayout.HORIZONTAL);

        short[] revPresets = {
                PresetReverb.PRESET_NONE,
                PresetReverb.PRESET_SMALLROOM,
                PresetReverb.PRESET_MEDIUMROOM,
                PresetReverb.PRESET_LARGEROOM,
                PresetReverb.PRESET_MEDIUMHALL,
                PresetReverb.PRESET_LARGEHALL,
                PresetReverb.PRESET_PLATE
        };
        String[] revNames = {"Aucun", "Small Room", "Medium Room", "Large Room", "Medium Hall", "Large Hall", "Plate"};
        List<Button> revButtons = new ArrayList<>();
        short currentRev = effectsManager.getReverbPreset();

        for (int i = 0; i < revPresets.length; i++) {
            final short p = revPresets[i];
            final int idx = i;
            Button btn = createChipButton(revNames[i], currentRev == p);
            btn.setOnClickListener(v -> {
                effectsManager.setReverbPreset(p);
                for (int j = 0; j < revButtons.size(); j++) {
                    updateChipButton(revButtons.get(j), j == idx);
                }
            });
            revButtons.add(btn);
            revRow.addView(btn);
            revRow.addView(gapW(8));
        }
        revScroll.addView(revRow);
        content.addView(revScroll);
        content.addView(gap(16));

        // 4. Vitesse & Pitch
        content.addView(sectionTitle("VITESSE DE LECTURE & PITCH"));
        content.addView(gap(8));

        LinearLayout cardSpeed = new LinearLayout(context);
        cardSpeed.setOrientation(LinearLayout.VERTICAL);
        setShape(cardSpeed, 0x0DFFFFFF, dp(16), 0x14FFFFFF);
        cardSpeed.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView speedLabel = new TextView(context);
        speedLabel.setText(String.format(Locale.getDefault(), "Vitesse : %.2fx", effectsManager.getPlaybackSpeed()));
        speedLabel.setTextColor(0xFF10B981);
        speedLabel.setTextSize(13);
        speedLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        cardSpeed.addView(speedLabel);

        SeekBar speedBar = new SeekBar(context);
        speedBar.setMax(150); // 0.5x à 2.0x (50 à 200 / 100)
        speedBar.setProgress(Math.round((effectsManager.getPlaybackSpeed() - 0.5f) * 100));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    float s = 0.5f + (p / 100.0f);
                    effectsManager.setPlaybackSpeed(s, player);
                    speedLabel.setText(String.format(Locale.getDefault(), "Vitesse : %.2fx", s));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        cardSpeed.addView(speedBar);
        cardSpeed.addView(gap(12));

        TextView pitchLabel = new TextView(context);
        pitchLabel.setText(String.format(Locale.getDefault(), "Hauteur (Pitch) : %.2fx", effectsManager.getPlaybackPitch()));
        pitchLabel.setTextColor(0xFFF59E0B);
        pitchLabel.setTextSize(13);
        pitchLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        cardSpeed.addView(pitchLabel);

        SeekBar pitchBar = new SeekBar(context);
        pitchBar.setMax(150); // 0.5x à 2.0x
        pitchBar.setProgress(Math.round((effectsManager.getPlaybackPitch() - 0.5f) * 100));
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    float pitch = 0.5f + (p / 100.0f);
                    effectsManager.setPlaybackPitch(pitch, player);
                    pitchLabel.setText(String.format(Locale.getDefault(), "Hauteur (Pitch) : %.2fx", pitch));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        cardSpeed.addView(pitchBar);
        content.addView(cardSpeed);
        content.addView(gap(20));

        scroll.addView(content);
        mainLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(mainLayout);
        return root;
    }

    private TextView sectionTitle(String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(0xFF9CA3AF);
        tv.setTextSize(11);
        tv.setLetterSpacing(0.08f);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return tv;
    }

    private Button createChipButton(String text, boolean active) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(12);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        updateChipButton(b, active);
        return b;
    }

    private void updateChipButton(Button b, boolean active) {
        GradientDrawable d = new GradientDrawable();
        if (active) {
            b.setTextColor(Color.WHITE);
            d.setColor(0x3322D3EE);
            d.setStroke(dp(1), 0xFF22D3EE);
        } else {
            b.setTextColor(0xFF94A3B8);
            d.setColor(0x0DFFFFFF);
            d.setStroke(dp(1), 0x1AFFFFFF);
        }
        d.setCornerRadius(dp(12));
        b.setBackground(d);
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
}
