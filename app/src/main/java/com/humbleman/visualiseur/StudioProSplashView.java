package com.kidas.studiopro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Vue Splash animée Studio Pro fidèle à logo_splash_studio_pro.html.
 * - Fond #0A0A0C avec radial gradients multiples (violet #A855F7, cyan #22D3EE, deep #150B29)
 * - Carte squircle glassmorphism 96x96dp avec icône centrale et glow 20px
 * - Typographie Studio Pro (28sp bold) et sous-titre VISUALISEUR AUDIO (11sp letter-spacing 0.2em)
 * - Égaliseur 5 barres barWave 850ms easeInOut avec décalages 110ms par barre
 * - Label "Chargement..."
 */
public class StudioProSplashView extends FrameLayout {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FrameLayout logoCard;
    private LinearLayout textBlock;
    private TextView titleView;
    private TextView subtitleView;
    private BarWaveView barWaveView;
    private TextView loadingTextView;
    private LinearLayout contentContainer;
    private boolean animationsStarted = false;

    public StudioProSplashView(@NonNull Context context) {
        super(context);
        init();
    }

    public StudioProSplashView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setBackgroundColor(Color.parseColor("#0A0A0C"));

        int dp16 = dp(16);
        int dp24 = dp(24);

        contentContainer = new LinearLayout(getContext());
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams containerLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        contentContainer.setPadding(dp16, dp(80), dp16, dp(60));
        addView(contentContainer, containerLp);

        // Section centrale : Logo + Textes
        LinearLayout centerSection = new LinearLayout(getContext());
        centerSection.setOrientation(LinearLayout.VERTICAL);
        centerSection.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1.0f);
        centerLp.gravity = Gravity.CENTER;
        centerSection.setGravity(Gravity.CENTER);
        contentContainer.addView(centerSection, centerLp);

        // Carte squircle glassmorphism (96x96dp)
        int cardSize = dp(96);
        logoCard = new FrameLayout(getContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardSize, cardSize);
        cardLp.gravity = Gravity.CENTER_HORIZONTAL;
        cardLp.bottomMargin = dp24;

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(24));
        cardBg.setColor(Color.parseColor("#121216"));
        cardBg.setStroke(dp(1), Color.parseColor("#26FFFFFF"));
        logoCard.setBackground(cardBg);

        ImageView iconView = new ImageView(getContext());
        int iconSize = dp(68);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        iconView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_launcher_foreground));
        logoCard.addView(iconView, iconLp);
        centerSection.addView(logoCard, cardLp);

        // Textes
        textBlock = new LinearLayout(getContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_HORIZONTAL);

        titleView = new TextView(getContext());
        titleView.setText("Studio Pro");
        titleView.setTextColor(Color.parseColor("#F5F5F7"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleView.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            titleView.setLetterSpacing(-0.02f);
        }
        textBlock.addView(titleView);

        subtitleView = new TextView(getContext());
        subtitleView.setText("VISUALISEUR AUDIO");
        subtitleView.setTextColor(Color.parseColor("#9CA3AF"));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(8), 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            subtitleView.setLetterSpacing(0.20f);
        }
        textBlock.addView(subtitleView);
        centerSection.addView(textBlock);

        // Section inférieure : BarWave Loader (5 barres) + "Chargement..."
        LinearLayout bottomSection = new LinearLayout(getContext());
        bottomSection.setOrientation(LinearLayout.VERTICAL);
        bottomSection.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bottomLp.bottomMargin = dp(20);
        contentContainer.addView(bottomSection, bottomLp);

        barWaveView = new BarWaveView(getContext());
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(dp(50), dp(32));
        waveLp.gravity = Gravity.CENTER_HORIZONTAL;
        waveLp.bottomMargin = dp(14);
        bottomSection.addView(barWaveView, waveLp);

        loadingTextView = new TextView(getContext());
        loadingTextView.setText("Chargement...");
        loadingTextView.setTextColor(Color.argb(102, 255, 255, 255)); // 40% opacity
        loadingTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        loadingTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        loadingTextView.setGravity(Gravity.CENTER);
        bottomSection.addView(loadingTextView);

        // Initialiser l'état transparent avant animation d'entrée
        logoCard.setAlpha(0f);
        logoCard.setScaleX(0.85f);
        logoCard.setScaleY(0.85f);

        textBlock.setAlpha(0f);
        textBlock.setTranslationY(dp(20));

        barWaveView.setAlpha(0f);
        loadingTextView.setAlpha(0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }

        // Fond #0A0A0C
        canvas.drawColor(Color.parseColor("#0A0A0C"));

        // 1. Halo supérieur violet (#A855F7)
        RadialGradient topViolet = new RadialGradient(
                w * 0.5f, 0f, h * 0.55f,
                new int[]{Color.parseColor("#60A855F7"), Color.parseColor("#2EEB4899"), Color.TRANSPARENT},
                new float[]{0f, 0.35f, 1f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(topViolet);
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 2. Halo haut-droite cyan (#22D3EE)
        RadialGradient topCyan = new RadialGradient(
                w * 0.85f, h * 0.18f, w * 0.70f,
                new int[]{Color.parseColor("#4722D3EE"), Color.TRANSPARENT},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(topCyan);
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 3. Halo bas-gauche violet profond (#150B29 / #5B21B6)
        RadialGradient bottomDeep = new RadialGradient(
                w * 0.20f, h * 0.88f, h * 0.60f,
                new int[]{Color.parseColor("#E6150B29"), Color.TRANSPARENT},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(bottomDeep);
        canvas.drawRect(0, 0, w, h, bgPaint);

        bgPaint.setShader(null);
        super.onDraw(canvas);
    }

    /**
     * Déclenche la séquence d'animation d'entrée du Splash Studio Pro.
     */
    public void startSplashAnimation() {
        if (animationsStarted) return;
        animationsStarted = true;

        // Étape 1 : Entrée du Logo Card (0 -> 500ms, DecelerateInterpolator)
        ObjectAnimator cardAlpha = ObjectAnimator.ofFloat(logoCard, "alpha", 0f, 1f);
        ObjectAnimator cardScaleX = ObjectAnimator.ofFloat(logoCard, "scaleX", 0.85f, 1f);
        ObjectAnimator cardScaleY = ObjectAnimator.ofFloat(logoCard, "scaleY", 0.85f, 1f);
        AnimatorSet cardSet = new AnimatorSet();
        cardSet.playTogether(cardAlpha, cardScaleX, cardScaleY);
        cardSet.setDuration(500);
        cardSet.setInterpolator(new DecelerateInterpolator(1.5f));

        // Étape 2 : Entrée des textes (200 -> 600ms, translationY + fade-in)
        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(textBlock, "alpha", 0f, 1f);
        ObjectAnimator textTransY = ObjectAnimator.ofFloat(textBlock, "translationY", dp(20), 0f);
        AnimatorSet textSet = new AnimatorSet();
        textSet.playTogether(textAlpha, textTransY);
        textSet.setDuration(400);
        textSet.setStartDelay(200);
        textSet.setInterpolator(new DecelerateInterpolator());

        // Étape 3 : Entrée du loader d'égaliseur (300 -> 700ms)
        ObjectAnimator waveAlpha = ObjectAnimator.ofFloat(barWaveView, "alpha", 0f, 1f);
        ObjectAnimator labelAlpha = ObjectAnimator.ofFloat(loadingTextView, "alpha", 0f, 1f);
        AnimatorSet waveSet = new AnimatorSet();
        waveSet.playTogether(waveAlpha, labelAlpha);
        waveSet.setDuration(400);
        waveSet.setStartDelay(300);

        AnimatorSet fullEntrance = new AnimatorSet();
        fullEntrance.playTogether(cardSet, textSet, waveSet);
        fullEntrance.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                barWaveView.startWaveAnimation();
            }
        });
        fullEntrance.start();
    }

    /**
     * Anime la sortie fluide du Splash vers l'écran d'accueil.
     * Scale doux 1.0 -> 1.06, Alpha 1.0 -> 0.0 en 350ms (AccelerateDecelerateInterpolator).
     */
    public void dismissSplash(@Nullable final Runnable onDismissed) {
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(contentContainer, "scaleX", 1f, 1.06f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(contentContainer, "scaleY", 1f, 1.06f);

        AnimatorSet exitSet = new AnimatorSet();
        exitSet.playTogether(alphaAnim, scaleXAnim, scaleYAnim);
        exitSet.setDuration(350);
        exitSet.setInterpolator(new AccelerateDecelerateInterpolator());
        exitSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                barWaveView.stopWaveAnimation();
                setVisibility(View.GONE);
                if (getParent() instanceof FrameLayout) {
                    ((FrameLayout) getParent()).removeView(StudioProSplashView.this);
                }
                if (onDismissed != null) {
                    onDismissed.run();
                }
            }
        });
        exitSet.start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Vue interne d'égaliseur 5 barres animées barWave (cycle 850ms, décalage 110ms par barre, ease-in-out).
     */
    private static class BarWaveView extends View {
        private static final int BAR_COUNT = 5;
        private static final int DURATION = 850; // Durée exacte de barWave (0.85s)
        private static final int STAGGER_DELAY = 110; // Décalage exact entre barres (0.11s)

        private final float[] scaleY = new float[]{0.35f, 0.35f, 0.35f, 0.35f, 0.35f};
        private final float[] baseHeights = new float[]{18f, 24f, 28f, 24f, 18f}; // en dp
        private final int[] barColors = new int[]{
                Color.parseColor("#22D3EE"), // Cyan
                Color.parseColor("#A855F7"), // Violet
                Color.parseColor("#22D3EE"), // Cyan
                Color.parseColor("#A855F7"), // Violet
                Color.parseColor("#22D3EE")  // Cyan
        };
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF barRect = new RectF();
        private final ValueAnimator[] animators = new ValueAnimator[BAR_COUNT];

        public BarWaveView(Context context) {
            super(context);
        }

        public void startWaveAnimation() {
            stopWaveAnimation();
            for (int i = 0; i < BAR_COUNT; i++) {
                final int index = i;
                ValueAnimator va = ValueAnimator.ofFloat(0.35f, 1.0f, 0.35f);
                va.setDuration(DURATION);
                va.setStartDelay(i * STAGGER_DELAY);
                va.setRepeatCount(ValueAnimator.INFINITE);
                va.setRepeatMode(ValueAnimator.RESTART);
                va.setInterpolator(new AccelerateDecelerateInterpolator()); // ease-in-out
                va.addUpdateListener(animation -> {
                    scaleY[index] = (float) animation.getAnimatedValue();
                    invalidate();
                });
                animators[i] = va;
                va.start();
            }
        }

        public void stopWaveAnimation() {
            for (int i = 0; i < BAR_COUNT; i++) {
                if (animators[i] != null) {
                    animators[i].cancel();
                    animators[i] = null;
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float density = getResources().getDisplayMetrics().density;
            float barWidth = 5f * density;
            float barSpacing = 5f * density;
            float totalWidth = (BAR_COUNT * barWidth) + ((BAR_COUNT - 1) * barSpacing);
            float startX = (w - totalWidth) / 2f;
            float centerY = h / 2f;

            for (int i = 0; i < BAR_COUNT; i++) {
                barPaint.setColor(barColors[i]);
                float hDp = baseHeights[i];
                float actualH = (hDp * density) * scaleY[i];
                float left = startX + i * (barWidth + barSpacing);
                float top = centerY - (actualH / 2f);
                float right = left + barWidth;
                float bottom = centerY + (actualH / 2f);
                float radius = barWidth / 2f;

                barRect.set(left, top, right, bottom);
                canvas.drawRoundRect(barRect, radius, radius, barPaint);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stopWaveAnimation();
        }
    }
}
