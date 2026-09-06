package com.kidas.studiopro;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LyricsTimelineEditorDialog extends Dialog {

    public interface OnLyricsUpdatedListener {
        void onLyricsUpdated(List<MainActivity.LyricLine> updatedLyrics);
    }

    private final Context context;
    private final MediaPlayer player;
    private final List<MainActivity.LyricLine> editableLyrics = new ArrayList<>();
    private final OnLyricsUpdatedListener listener;
    private final float density;

    private LinearLayout linesContainer;
    private TextView countBadge;

    public LyricsTimelineEditorDialog(@NonNull Context context, MediaPlayer player, List<MainActivity.LyricLine> originalLyrics, OnLyricsUpdatedListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.player = player;
        if (originalLyrics != null) {
            for (MainActivity.LyricLine l : originalLyrics) {
                this.editableLyrics.add(new MainActivity.LyricLine(l.startMs, l.endMs, l.text));
            }
        }
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
        refreshLinesList();
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

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText("Éditeur Temporel de Paroles");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText("Ajustez les timings au millième de seconde ou calez en direct");
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
        mainLayout.addView(gap(12));

        // Barre d'actions globales (Décalage global + Bouton Ajouter Ligne)
        LinearLayout globalBar = new LinearLayout(context);
        globalBar.setOrientation(LinearLayout.HORIZONTAL);
        globalBar.setGravity(Gravity.CENTER_VERTICAL);
        setShape(globalBar, 0x0DFFFFFF, dp(14), 0x1AFFFFFF);
        globalBar.setPadding(dp(12), dp(8), dp(12), dp(8));

        countBadge = new TextView(context);
        countBadge.setText(editableLyrics.size() + " lignes");
        countBadge.setTextColor(0xFF22D3EE);
        countBadge.setTextSize(12);
        countBadge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        globalBar.addView(countBadge, new LinearLayout.LayoutParams(0, -2, 1));

        Button shiftBack = createMiniBtn("-0.5s Global");
        shiftBack.setOnClickListener(v -> shiftAllTimestamps(-500));
        globalBar.addView(shiftBack);
        globalBar.addView(gapW(6));

        Button shiftFwd = createMiniBtn("+0.5s Global");
        shiftFwd.setOnClickListener(v -> shiftAllTimestamps(500));
        globalBar.addView(shiftFwd);
        globalBar.addView(gapW(6));

        Button addLineBtn = createMiniBtn("+ Ligne");
        addLineBtn.setTextColor(0xFF10B981);
        addLineBtn.setOnClickListener(v -> addNewLinePrompt());
        globalBar.addView(addLineBtn);

        mainLayout.addView(globalBar);
        mainLayout.addView(gap(12));

        // Liste des lignes éditables
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        linesContainer = new LinearLayout(context);
        linesContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(linesContainer);

        mainLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        mainLayout.addView(gap(12));

        // Boutons de sauvegarde en bas
        Button saveBtn = new Button(context);
        saveBtn.setText(" Enregistrer les synchronisations");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(14);
        saveBtn.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0xFFA855F7);
        saveBg.setCornerRadius(dp(14));
        saveBtn.setBackground(saveBg);
        saveBtn.setOnClickListener(v -> {
            if (listener != null) {
                // Tri des lignes par ordre chronologique
                Collections.sort(editableLyrics, (a, b) -> Long.compare(a.startMs, b.startMs));
                listener.onLyricsUpdated(editableLyrics);
            }
            Toast.makeText(context, "Paroles synchronisées mises à jour !", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        mainLayout.addView(saveBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        root.addView(mainLayout);
        return root;
    }

    private void refreshLinesList() {
        linesContainer.removeAllViews();
        countBadge.setText(editableLyrics.size() + " lignes");

        if (editableLyrics.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Aucune parole à éditer.");
            empty.setTextColor(0x80FFFFFF);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            linesContainer.addView(empty);
            return;
        }

        for (int i = 0; i < editableLyrics.size(); i++) {
            final int index = i;
            final MainActivity.LyricLine line = editableLyrics.get(i);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            setShape(card, 0x0AFFFFFF, dp(14), 0x14FFFFFF);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));

            // Ligne 1 : Timestamp + Boutons de décalage fin
            LinearLayout timeRow = new LinearLayout(context);
            timeRow.setOrientation(LinearLayout.HORIZONTAL);
            timeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView timeTv = new TextView(context);
            timeTv.setText(formatTime(line.startMs) + " → " + formatTime(line.endMs));
            timeTv.setTextColor(0xFF22D3EE);
            timeTv.setTextSize(12);
            timeTv.setTypeface(Typeface.MONOSPACE);
            timeRow.addView(timeTv, new LinearLayout.LayoutParams(0, -2, 1));

            Button minus100 = createMiniBtn("-0.1s");
            minus100.setOnClickListener(v -> {
                long newStart = Math.max(0L, Math.min(line.startMs - 100L, line.endMs - 250L));
                editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));
                refreshLinesList();
            });
            timeRow.addView(minus100);
            timeRow.addView(gapW(4));

            Button plus100 = createMiniBtn("+0.1s");
            plus100.setOnClickListener(v -> {
                long newStart = Math.min(line.startMs + 100L, Math.max(0L, line.endMs - 250L));
                editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));
                refreshLinesList();
            });
            timeRow.addView(plus100);
            timeRow.addView(gapW(4));

            Button snapPlayer = createMiniBtn(" Caler ici");
            snapPlayer.setTextColor(0xFFF59E0B);
            snapPlayer.setOnClickListener(v -> {
                if (player != null) {
                    try {
                        long newStart = Math.min(player.getCurrentPosition(), Math.max(0L, line.endMs - 250L));
                        editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));
                        refreshLinesList();
                        Toast.makeText(context, "Calé à " + formatTime(player.getCurrentPosition()), Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }
            });
            timeRow.addView(snapPlayer);
            card.addView(timeRow);
            card.addView(gap(6));

            // Ligne 2 : Texte éditable + Poubelle
            LinearLayout textRow = new LinearLayout(context);
            textRow.setOrientation(LinearLayout.HORIZONTAL);
            textRow.setGravity(Gravity.CENTER_VERTICAL);

            EditText textEdit = new EditText(context);
            textEdit.setText(line.text);
            textEdit.setTextColor(Color.WHITE);
            textEdit.setTextSize(13);
            textEdit.setBackground(null);
            textEdit.setPadding(0, 0, 0, 0);
            textEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    editableLyrics.set(index, new MainActivity.LyricLine(line.startMs, line.endMs, textEdit.getText().toString().trim()));
                }
            });
            textRow.addView(textEdit, new LinearLayout.LayoutParams(0, -2, 1));

            TextView deleteBtn = new TextView(context);
            deleteBtn.setText("");
            deleteBtn.setTextSize(14);
            deleteBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
            deleteBtn.setOnClickListener(v -> {
                editableLyrics.remove(index);
                refreshLinesList();
            });
            textRow.addView(deleteBtn);

            card.addView(textRow);
            linesContainer.addView(card);
            linesContainer.addView(gap(8));
        }
    }

    private void shiftAllTimestamps(long deltaMs) {
        for (int i = 0; i < editableLyrics.size(); i++) {
            MainActivity.LyricLine l = editableLyrics.get(i);
            long start = Math.max(0L, l.startMs + deltaMs);
            long end = Math.max(start + 250L, l.endMs + deltaMs);
            editableLyrics.set(i, new MainActivity.LyricLine(start, end, l.text));
        }
        refreshLinesList();
    }


    private void addNewLinePrompt() {
        long currentPos = (player != null) ? player.getCurrentPosition() : 0;
        editableLyrics.add(new MainActivity.LyricLine(currentPos, currentPos + 3000, "Nouvelle phrase"));
        Collections.sort(editableLyrics, (a, b) -> Long.compare(a.startMs, b.startMs));
        refreshLinesList();
    }

    private String formatTime(long ms) {
        long m = ms / 60000;
        double s = (ms % 60000) / 1000.0;
        return String.format(Locale.US, "%02d:%05.2f", m, s);
    }

    private Button createMiniBtn(String text) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextColor(0xFFE2E8F0);
        b.setTextSize(10);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x14FFFFFF);
        d.setCornerRadius(dp(8));
        d.setStroke(dp(1), 0x24FFFFFF);
        b.setBackground(d);
        return b;
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
