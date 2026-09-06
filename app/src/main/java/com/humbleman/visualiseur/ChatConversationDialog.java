package com.kidas.studiopro;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatConversationDialog extends Dialog {

    public interface OnChatActionListener {
        void onApplyAsLyrics(String text);
        void onApplyAsTitle(String title);
    }

    public static class MessageItem {
        public final boolean isUser;
        public final String text;
        public final String time;

        public MessageItem(boolean isUser, String text) {
            this.isUser = isUser;
            this.text = text;
            this.time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        }
    }

    private final Context context;
    private final String apiKey;
    private final String trackTitle;
    private final String trackArtist;
    private final String trackBpmKey;
    private final String currentLyricsText;
    private final OnChatActionListener actionListener;
    private final float density;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<MessageItem> messageList = new ArrayList<>();
    private final JSONArray conversationHistory = new JSONArray();

    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;
    private EditText inputEditText;
    private FrameLayout sendButton;
    private ProgressBar sendProgressBar;
    private ImageView sendIcon;
    private TextView emptyHintView;
    private boolean isWaitingResponse = false;

    public ChatConversationDialog(
            @NonNull Context context,
            String apiKey,
            String trackTitle,
            String trackArtist,
            String trackBpmKey,
            String currentLyricsText,
            OnChatActionListener actionListener
    ) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.trackTitle = trackTitle != null && !trackTitle.isEmpty() ? trackTitle : "Piste sans titre";
        this.trackArtist = trackArtist != null && !trackArtist.isEmpty() ? trackArtist : "Artiste";
        this.trackBpmKey = trackBpmKey != null ? trackBpmKey : "";
        this.currentLyricsText = currentLyricsText != null ? currentLyricsText : "";
        this.actionListener = actionListener;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0xFF0A0C14));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        initSystemPrompt();
        setContentView(buildView());

        // Message d'accueil initial
        addInitialWelcome();
    }

    private void initSystemPrompt() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Tu es un assistant IA conversationnel expert, créatif et chaleureux, intégré à l'application 'Studio Pro Visualiseur & Audio Studio'.\n");
            sb.append("Tu peux discuter LIBREMENT et naturellement de TOUT sujet sans restriction :\n");
            sb.append("- Écriture de paroles et rimes dans toutes les langues (Français, Haoussa / Hausa, Anglais, Arabe, Espagnol, etc.)\n");
            sb.append("- Idées de refrains, hooks viraux, punchlines, thèmes musicaux et storytelling\n");
            sb.append("- Conseils de production audio, mixage, mastering, tempo (BPM) et harmonie\n");
            sb.append("- Traduction, adaptation culturelle et discussion générale et philosophique\n\n");
            sb.append("CONTEXTE DU MORCEAU ACTUEL DE L'UTILISATEUR :\n");
            sb.append("• Titre : ").append(trackTitle).append("\n");
            sb.append("• Artiste : ").append(trackArtist).append("\n");
            if (!trackBpmKey.isEmpty()) {
                sb.append("• Analyse audio : ").append(trackBpmKey).append("\n");
            }
            if (!currentLyricsText.isEmpty()) {
                sb.append("• Paroles chargées : ").append(currentLyricsText.length() > 400 ? currentLyricsText.substring(0, 400) + "..." : currentLyricsText).append("\n");
            }
            sb.append("\nINSTRUCTIONS :\n");
            sb.append("- Réponds toujours dans la langue utilisée par l'utilisateur (si l'utilisateur te parle en Haoussa, réponds en Haoussa ; en français, réponds en français).\n");
            sb.append("- Sois direct, constructif, créatif et utilise une mise en page claire avec des puces et du gras quand c'est pertinent.\n");

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", sb.toString());
            conversationHistory.put(sysMsg);
        } catch (Exception ignored) {}
    }

    private void addInitialWelcome() {
        String welcome = "👋 **Bonjour ! Je suis votre copilote IA.**\n\n"
                + "Discutez librement avec moi sur n'importe quel sujet ! Je peux vous aider à :\n"
                + "• 🇳🇬 **Écrire ou traduire en Haoussa (Hausa)** des refrains ou couplets\n"
                + "• 🎤 **Trouver des rimes**, refrains accrocheurs et citations percutantes\n"
                + "• 🎵 **Optimiser votre morceau actuel** (*" + trackTitle + "*)\n"
                + "• 💬 **Converser naturellement** sur la musique, la culture ou vos projets\n\n"
                + "*Posez-moi votre question ci-dessous ou cliquez sur une suggestion rapide !*";
        addMessage(new MessageItem(false, welcome));
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0C14);

        // 1. Barre d'en-tête (Header)
        root.addView(createHeader(), new LinearLayout.LayoutParams(-1, -2));

        // 2. Badge Contexte Morceau Actuel
        root.addView(createContextBanner(), new LinearLayout.LayoutParams(-1, -2));

        // 3. Zone de Chat avec ScrollView
        FrameLayout chatFrame = new FrameLayout(context);
        LinearLayout.LayoutParams cfLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(chatFrame, cfLp);

        chatScrollView = new ScrollView(context);
        chatScrollView.setFillViewport(true);
        chatMessagesContainer = new LinearLayout(context);
        chatMessagesContainer.setOrientation(LinearLayout.VERTICAL);
        chatMessagesContainer.setPadding(dp(16), dp(12), dp(16), dp(16));
        chatScrollView.addView(chatMessagesContainer, new FrameLayout.LayoutParams(-1, -2));
        chatFrame.addView(chatScrollView, new FrameLayout.LayoutParams(-1, -1));

        // 4. Barre de suggestions rapides
        root.addView(createQuickSuggestions(), new LinearLayout.LayoutParams(-1, -2));

        // 5. Barre de saisie inférieure (Input Dock)
        root.addView(createInputBar(), new LinearLayout.LayoutParams(-1, -2));

        return root;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(0x99111422);

        // Bouton Retour
        FrameLayout backBtn = createIconButton(R.drawable.ic_nav_back, 0xFFE2E3EA, v -> dismiss());
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        // Titre + Statut IA
        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, dp(12), 0);

        TextView title = new TextView(context);
        title.setText("Discussion Libre IA");
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(title);

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(!apiKey.isEmpty() ? 0xFF10B981 : 0xFFF59E0B);
        dot.setBackground(dotBg);
        statusRow.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView statusText = new TextView(context);
        statusText.setText(!apiKey.isEmpty() ? " Groq LLaMA-3 · Prêt à échanger" : " Clé API non configurée");
        statusText.setTextSize(11);
        statusText.setTextColor(!apiKey.isEmpty() ? 0xFF10B981 : 0xFFF59E0B);
        statusRow.addView(statusText);
        titleCol.addView(statusRow);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Bouton Vider la discussion
        FrameLayout clearBtn = createIconButton(R.drawable.ic_trash, 0xFF9CA3AF, v -> clearConversation());
        header.addView(clearBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        return header;
    }

    private View createContextBanner() {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(16), dp(6), dp(16), dp(6));
        banner.setBackgroundColor(0x331E2337);

        ImageView musicIcon = new ImageView(context);
        musicIcon.setImageResource(R.drawable.ic_nav_audio);
        musicIcon.setColorFilter(0xFF22D3EE);
        banner.addView(musicIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));

        TextView trackInfo = new TextView(context);
        trackInfo.setPadding(dp(8), 0, 0, 0);
        String info = "Morceau : " + trackTitle + (!trackArtist.isEmpty() ? " · " + trackArtist : "");
        if (!trackBpmKey.isEmpty()) info += " (" + trackBpmKey + ")";
        trackInfo.setText(info);
        trackInfo.setTextSize(11);
        trackInfo.setTextColor(0xFF94A3B8);
        trackInfo.setSingleLine(true);
        banner.addView(trackInfo, new LinearLayout.LayoutParams(0, -2, 1));

        return banner;
    }

    private View createQuickSuggestions() {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setPadding(dp(12), dp(4), dp(12), dp(4));

        LinearLayout chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, dp(12), 0);

        String[] prompts = {
                "🇳🇬 Écris un refrain percutant en Haoussa",
                "🔥 Trouve 4 rimes pour mon prochain couplet",
                "💡 Donne-moi 5 idées de titres pour ce morceau",
                "🌍 Traduis mes paroles en Haoussa",
                "🎚️ Conseils pour structurer un morceau rap/afrobeat",
                "✨ Écris une citation inspirante sur la persévérance",
                "💬 Sannu! Yaya kake? (Discussion en Haoussa)"
        };

        for (String p : prompts) {
            TextView chip = new TextView(context);
            chip.setText(p);
            chip.setTextSize(12);
            chip.setTextColor(0xFFE2E8F0);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));

            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(16));
            gd.setColor(0x22FFFFFF);
            gd.setStroke(dp(1), 0x3322D3EE);
            chip.setBackground(gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                if (inputEditText != null) {
                    inputEditText.setText(p);
                    sendMessage(p);
                }
            });
            chipRow.addView(chip);
        }

        hsv.addView(chipRow);
        return hsv;
    }

    private View createInputBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(14), dp(10), dp(14), dp(14));
        bar.setBackgroundColor(0xFF111422);

        // Zone de saisie
        FrameLayout inputWrap = new FrameLayout(context);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(dp(22));
        inputBg.setColor(0x33000000);
        inputBg.setStroke(dp(1), 0x26FFFFFF);
        inputWrap.setBackground(inputBg);
        inputWrap.setPadding(dp(14), dp(4), dp(14), dp(4));

        inputEditText = new EditText(context);
        inputEditText.setHint("Discutez, demandez des paroles, en Haoussa ou Français…");
        inputEditText.setHintTextColor(0x55FFFFFF);
        inputEditText.setTextColor(Color.WHITE);
        inputEditText.setTextSize(14);
        inputEditText.setBackground(null);
        inputEditText.setMaxLines(4);
        inputEditText.setLineSpacing(dp(2), 1.1f);
        inputWrap.addView(inputEditText, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));

        bar.addView(inputWrap, new LinearLayout.LayoutParams(0, -2, 1));

        // Bouton Envoyer
        sendButton = new FrameLayout(context);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setCornerRadius(dp(22));
        sendBg.setColor(0xFF22D3EE);
        sendButton.setBackground(sendBg);

        sendIcon = new ImageView(context);
        sendIcon.setImageResource(R.drawable.ic_check);
        sendIcon.setColorFilter(0xFF0F172A);
        sendButton.addView(sendIcon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));

        sendProgressBar = new ProgressBar(context);
        sendProgressBar.setIndeterminate(true);
        sendProgressBar.setVisibility(View.GONE);
        sendButton.addView(sendProgressBar, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        sbLp.leftMargin = dp(10);
        sendButton.setLayoutParams(sbLp);

        sendButton.setOnClickListener(v -> {
            if (isWaitingResponse) return;
            String text = inputEditText != null ? inputEditText.getText().toString().trim() : "";
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });

        bar.addView(sendButton);
        return bar;
    }

    private FrameLayout createIconButton(int iconRes, int tintColor, View.OnClickListener listener) {
        FrameLayout btn = new FrameLayout(context);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(20));
        gd.setColor(0x1AFFFFFF);
        btn.setBackground(gd);

        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(tintColor);
        btn.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        btn.setOnClickListener(listener);
        return btn;
    }

    private void sendMessage(String userText) {
        if (apiKey.isEmpty()) {
            Toast.makeText(context, "Veuillez d'abord configurer votre clé Groq dans l'onglet IA.", Toast.LENGTH_LONG).show();
            return;
        }

        if (inputEditText != null) inputEditText.setText("");

        // Ajout message utilisateur
        MessageItem userMsg = new MessageItem(true, userText);
        addMessage(userMsg);

        try {
            JSONObject userJson = new JSONObject();
            userJson.put("role", "user");
            userJson.put("content", userText);
            conversationHistory.put(userJson);
        } catch (Exception ignored) {}

        setLoading(true);

        new Thread(() -> {
            try {
                String reply = MainActivity.GroqClient.chatConversationHistory(apiKey, conversationHistory, 2048);

                JSONObject assistantJson = new JSONObject();
                assistantJson.put("role", "assistant");
                assistantJson.put("content", reply);
                conversationHistory.put(assistantJson);

                mainHandler.post(() -> {
                    setLoading(false);
                    addMessage(new MessageItem(false, reply));
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "Erreur inconnue";
                mainHandler.post(() -> {
                    setLoading(false);
                    String errMsg = "⚠️ **Erreur de communication :**\n" + err + "\n\n*Vérifiez votre clé API Groq ou votre connexion Internet.*";
                    addMessage(new MessageItem(false, errMsg));
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        isWaitingResponse = loading;
        if (sendProgressBar != null) sendProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (sendIcon != null) sendIcon.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (sendButton != null) sendButton.setAlpha(loading ? 0.6f : 1.0f);
    }

    private void addMessage(MessageItem item) {
        messageList.add(item);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(item.isUser ? Gravity.END : Gravity.START);

        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));

        if (item.isUser) {
            bg.setColor(0x3322D3EE);
            bg.setStroke(dp(1), 0x6622D3EE);
        } else {
            bg.setColor(0xFF1E2337);
            bg.setStroke(dp(1), 0x1AFFFFFF);
        }
        bubble.setBackground(bg);

        // Nom & Heure
        LinearLayout infoRow = new LinearLayout(context);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView author = new TextView(context);
        author.setText(item.isUser ? "Vous" : "🤖 IA Visualiseur");
        author.setTextSize(11);
        author.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        author.setTextColor(item.isUser ? 0xFF22D3EE : 0xFFA78BFA);
        infoRow.addView(author);

        TextView time = new TextView(context);
        time.setPadding(dp(6), 0, 0, 0);
        time.setText(" · " + item.time);
        time.setTextSize(10);
        time.setTextColor(0x66FFFFFF);
        infoRow.addView(time);

        bubble.addView(infoRow);

        // Corps du message avec mise en forme simple
        TextView body = new TextView(context);
        body.setPadding(0, dp(6), 0, 0);
        body.setText(formatMarkdownText(item.text));
        body.setTextSize(14);
        body.setTextColor(Color.WHITE);
        body.setLineSpacing(dp(3), 1.2f);
        body.setTextIsSelectable(true);
        bubble.addView(body);

        // Boutons d'action pour les réponses de l'IA
        if (!item.isUser && !item.text.startsWith("⚠️")) {
            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setPadding(0, dp(10), 0, 0);

            // Bouton Copier
            TextView copyBtn = createActionPill("📋 Copier", v -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("IA Response", item.text));
                    Toast.makeText(context, "Texte copié dans le presse-papiers !", Toast.LENGTH_SHORT).show();
                }
            });
            actionRow.addView(copyBtn);

            // Bouton Utiliser comme paroles / citation
            if (actionListener != null) {
                TextView applyBtn = createActionPill("✨ Insérer dans Studio", v -> {
                    actionListener.onApplyAsLyrics(item.text);
                    Toast.makeText(context, "Inséré dans le Studio Visualiseur !", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
                LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(-2, -2);
                abLp.leftMargin = dp(8);
                actionRow.addView(applyBtn, abLp);
            }

            bubble.addView(actionRow);
        }

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(10);
        row.addView(bubble, new LinearLayout.LayoutParams(item.isUser ? dp(290) : -1, -2));

        chatMessagesContainer.addView(row, rowLp);

        // Scroll tout en bas
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private TextView createActionPill(String label, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(0xFFE2E8F0);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(12));
        gd.setColor(0x26FFFFFF);
        gd.setStroke(dp(1), 0x33FFFFFF);
        tv.setBackground(gd);
        tv.setOnClickListener(listener);
        return tv;
    }

    private CharSequence formatMarkdownText(String raw) {
        if (raw == null) return "";
        String html = raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                .replace("\n", "<br>");
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
    }

    private void clearConversation() {
        chatMessagesContainer.removeAllViews();
        messageList.clear();
        while (conversationHistory.length() > 0) {
            conversationHistory.remove(0);
        }
        initSystemPrompt();
        addInitialWelcome();
        Toast.makeText(context, "Discussion réinitialisée", Toast.LENGTH_SHORT).show();
    }
}
