from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/humbleman/visualiseur/MainActivity.java')
s = MAIN.read_text(encoding='utf-8')
start = s.index('        static List<LyricLine> translateLyrics(')
end = s.index('\n        static HttpURLConnection conn(', start)

new_method = r'''        static List<LyricLine> translateLyrics(String rawKey, List<LyricLine> sourceLyrics, String targetLanguage) throws Exception {
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
'''

s = s[:start] + new_method + s[end:]
MAIN.write_text(s, encoding='utf-8')
print('translation synchronization patch applied')
