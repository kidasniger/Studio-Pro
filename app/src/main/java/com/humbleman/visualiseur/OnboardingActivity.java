package com.kidas.studiopro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "visualiseur_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_completed";

    private final String[] stepTags = {
            "ÉTAPE 1 / 3 • AUDIO & SYNCHRO",
            "ÉTAPE 2 / 3 • MOTEURS VISUELS",
            "ÉTAPE 3 / 3 • EXPORT ULTRA HD"
    };

    private final String[] titles = {
            "Donne vie à ton son",
            "Presets cinématiques",
            "Prêt à exporter"
    };

    private final String[] descriptions = {
            "Transforme n'importe quel audio en vidéo immersive, synchronisée à la milliseconde.",
            "Barres, halo, particules néon — 9 moteurs visuels propulsés par le signal audio.",
            "Vertical 9:16, carré, 60 FPS. Optimisé pour Reels, TikTok, Shorts."
    };

    private int currentPage = 0;

    private TextView onbStepTag, title, desc, skipBtn;
    private Button nextBtn;
    private View dot1, dot2, dot3;
    private FrameLayout previewCard, previewContent;
    private View onboardingRoot, onbContentContainer;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        onboardingRoot = findViewById(R.id.onboardingRoot);
        onbContentContainer = findViewById(R.id.onbContentContainer);
        onbStepTag = findViewById(R.id.onbStepTag);
        title = findViewById(R.id.onbTitle);
        desc = findViewById(R.id.onbDesc);
        skipBtn = findViewById(R.id.skipBtn);
        nextBtn = findViewById(R.id.nextBtn);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        previewCard = findViewById(R.id.onbPreviewCard);
        previewContent = findViewById(R.id.onbPreviewContent);

        // Style du conteneur de prévisualisation Glassmorphism
        if (previewCard != null) {
            GradientDrawable cardShape = new GradientDrawable();
            cardShape.setColor(0x12FFFFFF);
            cardShape.setCornerRadius(dp(26));
            cardShape.setStroke(dp(1), 0x26FFFFFF);
            previewCard.setBackground(cardShape);
        }

        // Style du badge d'étape
        if (onbStepTag != null) {
            GradientDrawable tagShape = new GradientDrawable();
            tagShape.setColor(0x2222D3EE);
            tagShape.setCornerRadius(dp(10));
            tagShape.setStroke(dp(1), 0x4D22D3EE);
            onbStepTag.setBackground(tagShape);
        }

        // Style du bouton Passer
        if (skipBtn != null) {
            GradientDrawable skipShape = new GradientDrawable();
            skipShape.setColor(0x14FFFFFF);
            skipShape.setCornerRadius(dp(14));
            skipShape.setStroke(dp(1), 0x26FFFFFF);
            skipBtn.setBackground(skipShape);
            skipBtn.setOnClickListener(v -> completeOnboarding());
        }

        // Interactivité des points de pagination
        if (dot1 != null) dot1.setOnClickListener(v -> switchPage(0));
        if (dot2 != null) dot2.setOnClickListener(v -> switchPage(1));
        if (dot3 != null) dot3.setOnClickListener(v -> switchPage(2));

        // Bouton Suivant / Commencer
        if (nextBtn != null) {
            nextBtn.setOnClickListener(v -> {
                if (currentPage < titles.length - 1) {
                    switchPage(currentPage + 1);
                } else {
                    completeOnboarding();
                }
            });
        }

        // Détecteur de gestes swipe gauche / droite
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            // Swipe vers la gauche -> page suivante
                            if (currentPage < titles.length - 1) {
                                switchPage(currentPage + 1);
                                return true;
                            }
                        } else {
                            // Swipe vers la droite -> page précédente
                            if (currentPage > 0) {
                                switchPage(currentPage - 1);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        if (onboardingRoot != null) {
            onboardingRoot.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }

        renderPage(false, true);
    }

    private void switchPage(int newPage) {
        if (newPage == currentPage) return;
        boolean forward = newPage > currentPage;
        currentPage = newPage;
        renderPage(true, forward);
    }

    private void renderPage(boolean animate, boolean forward) {
        if (animate && onbContentContainer != null) {
            AnimationSet animSet = new AnimationSet(true);
            animSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animSet.setDuration(260);

            AlphaAnimation alpha = new AlphaAnimation(0.3f, 1f);
            TranslateAnimation trans = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, forward ? 0.08f : -0.08f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f
            );

            animSet.addAnimation(alpha);
            animSet.addAnimation(trans);
            onbContentContainer.startAnimation(animSet);
        }

        if (onbStepTag != null) {
            onbStepTag.setText(stepTags[currentPage]);
            int tagColor = (currentPage == 0) ? 0xFF22D3EE : (currentPage == 1 ? 0xFFA855F7 : 0xFF10B981);
            int tagBg = (currentPage == 0) ? 0x2222D3EE : (currentPage == 1 ? 0x22A855F7 : 0x2210B981);
            int tagStroke = (currentPage == 0) ? 0x4D22D3EE : (currentPage == 1 ? 0x4DA855F7 : 0x4D10B981);

            GradientDrawable tagShape = new GradientDrawable();
            tagShape.setColor(tagBg);
            tagShape.setCornerRadius(dp(10));
            tagShape.setStroke(dp(1), tagStroke);
            onbStepTag.setBackground(tagShape);
            onbStepTag.setTextColor(tagColor);
        }

        if (title != null) title.setText(titles[currentPage]);
        if (desc != null) desc.setText(descriptions[currentPage]);

        updateDot(dot1, currentPage == 0);
        updateDot(dot2, currentPage == 1);
        updateDot(dot3, currentPage == 2);

        if (nextBtn != null) {
            nextBtn.setText(currentPage == titles.length - 1 ? "Commencer" : "Continuer");
        }

        if (skipBtn != null) {
            if (currentPage == titles.length - 1) {
                skipBtn.animate().alpha(0f).setDuration(180).withEndAction(() -> skipBtn.setVisibility(View.INVISIBLE)).start();
            } else {
                skipBtn.setVisibility(View.VISIBLE);
                skipBtn.animate().alpha(1f).setDuration(180).start();
            }
        }

        renderPreviewContent(currentPage);
    }

    private void renderPreviewContent(int page) {
        if (previewContent == null) return;
        previewContent.removeAllViews();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        if (page == 0) {
            // Slide 1 : Spectre Audio Dynamique & Synchro
            TextView badge = new TextView(this);
            badge.setText("ANALYSE DU SIGNAL • 60 FPS");
            badge.setTextSize(10);
            badge.setTextColor(0xFF22D3EE);
            badge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            badge.setLetterSpacing(0.06f);
            GradientDrawable bShape = new GradientDrawable();
            bShape.setColor(0x1F22D3EE);
            bShape.setCornerRadius(dp(8));
            bShape.setStroke(dp(1), 0x3322D3EE);
            badge.setBackground(bShape);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            root.addView(badge);

            // Barres d'ondes audio animées
            LinearLayout barsRow = new LinearLayout(this);
            barsRow.setOrientation(LinearLayout.HORIZONTAL);
            barsRow.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            barsRow.setPadding(0, dp(20), 0, dp(16));

            int[] barHeights = {18, 38, 65, 42, 85, 105, 75, 48, 88, 55, 32, 16};
            for (int h : barHeights) {
                View bar = new View(this);
                GradientDrawable barDraw = new GradientDrawable(
                        GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{0xFF22D3EE, 0xFFA855F7}
                );
                barDraw.setCornerRadius(dp(4));
                bar.setBackground(barDraw);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(h));
                lp.setMargins(dp(3), 0, dp(3), 0);
                barsRow.addView(bar, lp);
            }
            root.addView(barsRow);

            TextView subTag = new TextView(this);
            subTag.setText("Synchronisation Audio FFT Haute Précision");
            subTag.setTextSize(11);
            subTag.setTextColor(0x99FFFFFF);
            root.addView(subTag);

        } else if (page == 1) {
            // Slide 2 : Presets cinématiques (Halo, Cercle, Particules)
            TextView badge = new TextView(this);
            badge.setText("9 MOTEURS VISUELS RÉACTIFS");
            badge.setTextSize(10);
            badge.setTextColor(0xFFA855F7);
            badge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            badge.setLetterSpacing(0.06f);
            GradientDrawable bShape = new GradientDrawable();
            bShape.setColor(0x1FA855F7);
            bShape.setCornerRadius(dp(8));
            bShape.setStroke(dp(1), 0x33A855F7);
            badge.setBackground(bShape);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            root.addView(badge);

            // Cercle halo néon central
            FrameLayout haloWrap = new FrameLayout(this);
            haloWrap.setPadding(0, dp(14), 0, dp(14));

            View ringOuter = new View(this);
            GradientDrawable ringOuterDraw = new GradientDrawable();
            ringOuterDraw.setShape(GradientDrawable.OVAL);
            ringOuterDraw.setStroke(dp(2), 0x4D22D3EE);
            ringOuterDraw.setColor(0x10A855F7);
            ringOuter.setBackground(ringOuterDraw);
            haloWrap.addView(ringOuter, new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER));

            View ringInner = new View(this);
            GradientDrawable ringInnerDraw = new GradientDrawable();
            ringInnerDraw.setShape(GradientDrawable.OVAL);
            ringInnerDraw.setStroke(dp(2), 0xFF22D3EE);
            ringInnerDraw.setColor(0x3322D3EE);
            ringInner.setBackground(ringInnerDraw);
            haloWrap.addView(ringInner, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));

            ImageView ic = new ImageView(this);
            ic.setImageResource(R.drawable.ic_nav_visual);
            ic.setColorFilter(Color.WHITE);
            haloWrap.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

            root.addView(haloWrap);

            // Chips de presets
            LinearLayout chipsRow = new LinearLayout(this);
            chipsRow.setOrientation(LinearLayout.HORIZONTAL);
            chipsRow.setGravity(Gravity.CENTER);

            String[] presetChips = {"Spectre", "Cercle", "Néon", "Onde"};
            for (int i = 0; i < presetChips.length; i++) {
                TextView chip = new TextView(this);
                chip.setText(presetChips[i]);
                chip.setTextSize(10);
                chip.setTextColor(i == 1 ? Color.WHITE : 0x80FFFFFF);
                GradientDrawable cShape = new GradientDrawable();
                cShape.setColor(i == 1 ? 0xFF352569 : 0x14FFFFFF);
                cShape.setCornerRadius(dp(8));
                cShape.setStroke(dp(1), i == 1 ? 0xFF8B69FF : 0x1AFFFFFF);
                chip.setBackground(cShape);
                chip.setPadding(dp(8), dp(4), dp(8), dp(4));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMargins(dp(2), 0, dp(2), 0);
                chipsRow.addView(chip, lp);
            }
            root.addView(chipsRow);

        } else {
            // Slide 3 : Formats d'Export & GPU
            TextView badge = new TextView(this);
            badge.setText("ENCODAGE MATÉRIEL • H.264");
            badge.setTextSize(10);
            badge.setTextColor(0xFF10B981);
            badge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            badge.setLetterSpacing(0.06f);
            GradientDrawable bShape = new GradientDrawable();
            bShape.setColor(0x1F10B981);
            bShape.setCornerRadius(dp(8));
            bShape.setStroke(dp(1), 0x3310B981);
            badge.setBackground(bShape);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            root.addView(badge);

            // Mockup 3 formats vidéo (9:16, 1:1, 16:9)
            LinearLayout formatsRow = new LinearLayout(this);
            formatsRow.setOrientation(LinearLayout.HORIZONTAL);
            formatsRow.setGravity(Gravity.CENTER);
            formatsRow.setPadding(0, dp(16), 0, dp(12));

            // Format 9:16 (Actif)
            LinearLayout f1 = new LinearLayout(this);
            f1.setOrientation(LinearLayout.VERTICAL);
            f1.setGravity(Gravity.CENTER);
            GradientDrawable f1Shape = new GradientDrawable();
            f1Shape.setColor(0xFF1D263B);
            f1Shape.setCornerRadius(dp(8));
            f1Shape.setStroke(dp(2), 0xFF22D3EE);
            f1.setBackground(f1Shape);
            TextView t1 = new TextView(this);
            t1.setText("9:16\nReels");
            t1.setTextSize(9);
            t1.setTextColor(0xFF22D3EE);
            t1.setGravity(Gravity.CENTER);
            t1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            f1.addView(t1);
            formatsRow.addView(f1, new LinearLayout.LayoutParams(dp(44), dp(72)));

            // Format 1:1
            LinearLayout f2 = new LinearLayout(this);
            f2.setOrientation(LinearLayout.VERTICAL);
            f2.setGravity(Gravity.CENTER);
            GradientDrawable f2Shape = new GradientDrawable();
            f2Shape.setColor(0x14FFFFFF);
            f2Shape.setCornerRadius(dp(8));
            f2Shape.setStroke(dp(1), 0x26FFFFFF);
            f2.setBackground(f2Shape);
            TextView t2 = new TextView(this);
            t2.setText("1:1\nPost");
            t2.setTextSize(9);
            t2.setTextColor(0x80FFFFFF);
            t2.setGravity(Gravity.CENTER);
            f2.addView(t2);
            LinearLayout.LayoutParams f2Lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            f2Lp.setMargins(dp(8), 0, dp(8), 0);
            formatsRow.addView(f2, f2Lp);

            // Format 16:9
            LinearLayout f3 = new LinearLayout(this);
            f3.setOrientation(LinearLayout.VERTICAL);
            f3.setGravity(Gravity.CENTER);
            GradientDrawable f3Shape = new GradientDrawable();
            f3Shape.setColor(0x14FFFFFF);
            f3Shape.setCornerRadius(dp(8));
            f3Shape.setStroke(dp(1), 0x26FFFFFF);
            f3.setBackground(f3Shape);
            TextView t3 = new TextView(this);
            t3.setText("16:9\nYT");
            t3.setTextSize(9);
            t3.setTextColor(0x80FFFFFF);
            t3.setGravity(Gravity.CENTER);
            f3.addView(t3);
            formatsRow.addView(f3, new LinearLayout.LayoutParams(dp(62), dp(38)));

            root.addView(formatsRow);

            TextView subTag = new TextView(this);
            subTag.setText("Résolution 1080p • 60 FPS • Rendu Instantané");
            subTag.setTextSize(11);
            subTag.setTextColor(0x99FFFFFF);
            root.addView(subTag);
        }

        previewContent.addView(root, new FrameLayout.LayoutParams(-1, -1));
    }

    private void updateDot(View dot, boolean active) {
        if (dot == null) return;
        dot.setBackgroundResource(active ? R.drawable.dot_active : R.drawable.dot_inactive);
        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        if (lp != null) {
            lp.width = dp(active ? 24 : 6);
            lp.height = dp(6);
            dot.setLayoutParams(lp);
        }
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void completeOnboarding() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_DONE, true)
                .apply();
        goToMain();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}


