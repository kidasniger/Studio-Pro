package com.kidas.studiopro;

import java.util.Locale;

/**
 * Assistant IA Studio Pro Contextuel :
 * Analyse et exécute les commandes textuelles en langage naturel pour piloter
 * les réglages du projet (visualiseur, paroles, formats d'export, effets audio).
 */
public final class AiProjectAssistant {

    public interface ProjectCommandCallback {
        void onVisualizerLengthChanged(float scale);
        void onLyricsWidthChanged(float widthPercent);
        void onLyricsAlignmentChanged(String alignment);
        void onLyricsVerticalOffsetChanged(float offset);
        void onLyricsModeChanged(String mode);
        void onExportConfigChanged(int width, int height, int fps);
        void onStyleChanged(String style);
        void onColorChanged(String colorHex);
        void onAudioEffectsToggled(boolean enabled);
        void onPlaybackCommand(boolean play);
        void onOpenAudioBrowser();
        void onTranslateLyricsCommand(String targetLanguage);
    }

    public static final class CommandExecutionResult {
        public final boolean handled;
        public final String feedbackMessage;

        public CommandExecutionResult(boolean handled, String feedbackMessage) {
            this.handled = handled;
            this.feedbackMessage = feedbackMessage;
        }
    }

    private AiProjectAssistant() {}

    /**
     * Analyse l'intention de l'utilisateur et exécute la commande sur le projet.
     */
    public static CommandExecutionResult processUserPrompt(String prompt, ProjectCommandCallback callback) {
        if (prompt == null || prompt.trim().isEmpty() || callback == null) {
            return new CommandExecutionResult(false, null);
        }

        String p = prompt.toLowerCase(Locale.ROOT).trim();

        // 1. Longueur / Hauteur du visualiseur
        if (p.contains("réduis") && (p.contains("longueur") || p.contains("taille") || p.contains("zone")) && p.contains("visualiseur")) {
            callback.onVisualizerLengthChanged(0.65f);
            return new CommandExecutionResult(true, "Longueur du visualiseur réduite à 65% pour aérer la composition.");
        }
        if (p.contains("augmente") && (p.contains("longueur") || p.contains("taille") || p.contains("zone")) && p.contains("visualiseur")) {
            callback.onVisualizerLengthChanged(1.15f);
            return new CommandExecutionResult(true, "Longueur du visualiseur augmentée à 115% pour une occupation maximale du cadre.");
        }
        if (p.contains("visualiseur") && (p.contains("longueur standard") || p.contains("taille normale") || p.contains("réinitialise visualiseur"))) {
            callback.onVisualizerLengthChanged(1.00f);
            return new CommandExecutionResult(true, "Longueur du visualiseur réinitialisée à 100%.");
        }

        // 2. Largeur / Zone des paroles
        if (p.contains("augmente") && (p.contains("zone") || p.contains("largeur")) && (p.contains("parole") || p.contains("texte"))) {
            callback.onLyricsWidthChanged(95.0f);
            return new CommandExecutionResult(true, "Largeur de la zone des paroles ajustée à 95% pour éviter les coupures de texte.");
        }
        if (p.contains("réduis") && (p.contains("zone") || p.contains("largeur")) && (p.contains("parole") || p.contains("texte"))) {
            callback.onLyricsWidthChanged(70.0f);
            return new CommandExecutionResult(true, "Largeur de la zone des paroles réduite à 70% pour un cadrage plus compact.");
        }

        // 3. Positionnement & Alignement des paroles
        if (p.contains("centre") && (p.contains("parole") || p.contains("texte"))) {
            callback.onLyricsAlignmentChanged("center");
            callback.onLyricsVerticalOffsetChanged(0.0f);
            return new CommandExecutionResult(true, "Paroles centrées horizontalement et verticalement sur la vidéo.");
        }
        if ((p.contains("parole") || p.contains("texte")) && (p.contains("en haut") || p.contains("vers le haut"))) {
            callback.onLyricsVerticalOffsetChanged(-0.25f);
            return new CommandExecutionResult(true, "Position des paroles déplacée vers le tiers supérieur (-25%).");
        }
        if ((p.contains("parole") || p.contains("texte")) && (p.contains("en bas") || p.contains("vers le bas"))) {
            callback.onLyricsVerticalOffsetChanged(0.25f);
            return new CommandExecutionResult(true, "Position des paroles déplacée vers le bas (+25%).");
        }

        // 4. Configuration d'Export (Ratio & FPS)
        if (p.contains("export") || p.contains("format") || p.contains("vidéo")) {
            if (p.contains("9:16") || p.contains("vertical") || p.contains("tiktok") || p.contains("reels") || p.contains("shorts")) {
                int fps = p.contains("60") ? 60 : 30;
                callback.onExportConfigChanged(1080, 1920, fps);
                return new CommandExecutionResult(true, "Format d'export configuré en 9:16 Vertical (1080×1920) @ " + fps + " FPS.");
            }
            if (p.contains("1:1") || p.contains("carré") || p.contains("square") || p.contains("instagram")) {
                int fps = p.contains("60") ? 60 : 30;
                callback.onExportConfigChanged(1080, 1080, fps);
                return new CommandExecutionResult(true, "Format d'export configuré en 1:1 Carré (1080×1080) @ " + fps + " FPS.");
            }
            if (p.contains("16:9") || p.contains("horizontal") || p.contains("paysage") || p.contains("youtube")) {
                int fps = p.contains("60") ? 60 : 30;
                callback.onExportConfigChanged(1920, 1080, fps);
                return new CommandExecutionResult(true, "Format d'export configuré en 16:9 Horizontal (1920×1080) @ " + fps + " FPS.");
            }
        }

        // 5. Styles du visualiseur
        if (p.contains("style") || p.contains("effet") || p.contains("mode visuel")) {
            if (p.contains("barre")) {
                callback.onStyleChanged("bars");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Barres'.");
            }
            if (p.contains("vague") || p.contains("onde")) {
                callback.onStyleChanged("wave");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Vague'.");
            }
            if (p.contains("miroir")) {
                callback.onStyleChanged("mirror");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Miroir'.");
            }
            if (p.contains("cercle") || p.contains("circulaire")) {
                callback.onStyleChanged("circle");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Cercle'.");
            }
            if (p.contains("particule")) {
                callback.onStyleChanged("particles");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Particules'.");
            }
            if (p.contains("glow") || p.contains("néon")) {
                callback.onStyleChanged("glow");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Glow Néon'.");
            }
            if (p.contains("cube") || p.contains("3d")) {
                callback.onStyleChanged("cube");
                return new CommandExecutionResult(true, "Style visuel basculé sur '3D Cubes'.");
            }
            if (p.contains("halo")) {
                callback.onStyleChanged("halo");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Halo'.");
            }
            if (p.contains("cyber") || p.contains("matrice")) {
                callback.onStyleChanged("cyber");
                return new CommandExecutionResult(true, "Style visuel basculé sur 'Cyber'.");
            }
        }

        // 6. Mode des paroles (Karaoké défilant, Mot à mot, Bloc fixe)
        if (p.contains("karaoké") || p.contains("défilant")) {
            callback.onLyricsModeChanged("scroll");
            return new CommandExecutionResult(true, "Mode des paroles défini sur 'Karaoké défilant'.");
        }
        if (p.contains("mot à mot") || p.contains("mot par mot")) {
            callback.onLyricsModeChanged("word");
            return new CommandExecutionResult(true, "Mode des paroles défini sur 'Mot à mot'.");
        }
        if (p.contains("bloc fixe") || p.contains("fixe")) {
            callback.onLyricsModeChanged("fixed");
            return new CommandExecutionResult(true, "Mode des paroles défini sur 'Bloc fixe'.");
        }

        // 7. Lecture / Transport
        if (p.equals("joue") || p.equals("play") || p.contains("lance la lecture") || p.contains("joue la musique")) {
            callback.onPlaybackCommand(true);
            return new CommandExecutionResult(true, "Lecture audio démarrée.");
        }
        if (p.equals("pause") || p.equals("stop") || p.contains("arrête la lecture") || p.contains("mets en pause")) {
            callback.onPlaybackCommand(false);
            return new CommandExecutionResult(true, "Lecture audio mise en pause.");
        }

        // 8. Effets Audio
        if (p.contains("active les effets") || p.contains("boost basse") || p.contains("égaliseur")) {
            callback.onAudioEffectsToggled(true);
            return new CommandExecutionResult(true, "Effets audio (Equalizer & BassBoost) activés.");
        }

        // 9. Gestionnaire & Explorateur Audio
        if (p.contains("gestionnaire audio") || p.contains("explore les musiques") || p.contains("parcours les audio") || p.contains("importe une musique") || p.contains("bibliothèque audio") || p.contains("trouve des musique") || p.contains("fichiers audio")) {
            callback.onOpenAudioBrowser();
            return new CommandExecutionResult(true, "Ouverture du Gestionnaire des Audios de l'appareil…");
        }

        // 10. Traduction des Paroles (Haoussa, Anglais, Français, Zarma, Arabe)
        if (p.contains("tradui") || p.contains("traduction") || p.contains("translate")) {
            if (p.contains("haoussa") || p.contains("hausa")) {
                callback.onTranslateLyricsCommand("Haoussa (Hausa)");
                return new CommandExecutionResult(true, "Lancement de la traduction des paroles synchronisées en Haoussa...");
            }
            if (p.contains("anglais") || p.contains("english")) {
                callback.onTranslateLyricsCommand("Anglais");
                return new CommandExecutionResult(true, "Lancement de la traduction des paroles en Anglais...");
            }
            if (p.contains("français") || p.contains("francais") || p.contains("french")) {
                callback.onTranslateLyricsCommand("Français");
                return new CommandExecutionResult(true, "Lancement de la traduction des paroles en Français...");
            }
            if (p.contains("zarma") || p.contains("djerma") || p.contains("songhai") || p.contains("songhaï")) {
                callback.onTranslateLyricsCommand("Zarma (Djerma / Songhaï)");
                return new CommandExecutionResult(true, "Lancement de la traduction des paroles synchronisées en langue Zarma (Djerma)...");
            }
            if (p.contains("arabe") || p.contains("arabic")) {
                callback.onTranslateLyricsCommand("Arabe");
                return new CommandExecutionResult(true, "Lancement de la traduction des paroles en Arabe...");
            }
            // Sans langue explicite -> ouvrir la sélection
            callback.onTranslateLyricsCommand("");
            return new CommandExecutionResult(true, "Ouverture du menu de sélection des 5 langues de traduction (Haoussa, Anglais, Français, Zarma, Arabe)...");
        }

        return new CommandExecutionResult(false, null);
    }
}
