package com.kidas.studiopro;

import java.io.File;

public final class AudioEditSafety {
    private AudioEditSafety() {}

    public static File process(File source, File destination, long startMs, long endMs,
                               float gain, float speed, float fadeInSec, float fadeOutSec,
                               boolean normalize) throws Exception {
        if (source == null || !source.exists() || source.length() == 0) throw new Exception("Fichier audio source introuvable.");
        if (destination == null) throw new Exception("Destination audio invalide.");
        if (startMs < 0 || (endMs > 0 && endMs <= startMs)) throw new Exception("Découpe audio invalide.");
        if (!Float.isFinite(gain) || gain < 0f || gain > 2f) throw new Exception("Gain audio invalide.");
        if (!Float.isFinite(speed) || speed < 0.5f || speed > 2f) throw new Exception("Vitesse audio invalide.");
        if (!Float.isFinite(fadeInSec) || !Float.isFinite(fadeOutSec) || fadeInSec < 0f || fadeOutSec < 0f) throw new Exception("Fondu audio invalide.");
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Impossible de créer le dossier de sortie.");
        File temp = new File(parent == null ? destination.getAbsoluteFile().getParentFile() : parent, "." + destination.getName() + ".studiopro.tmp");
        try {
            AudioEditEngine.process(source, temp, startMs, endMs, gain, speed, fadeInSec, fadeOutSec, normalize);
            if (!temp.exists() || temp.length() < 44) throw new Exception("Résultat audio invalide.");
            if (destination.exists() && !destination.delete()) throw new Exception("Impossible de remplacer le résultat audio.");
            if (!temp.renameTo(destination)) throw new Exception("Impossible de finaliser le résultat audio.");
            return destination;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }
}
