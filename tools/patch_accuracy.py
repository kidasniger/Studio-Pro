from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

bpm = ROOT / 'app/src/main/java/com/humbleman/visualiseur/BpmKeyDetector.java'
s = bpm.read_text(encoding='utf-8')
if 'Tonalité non déterminée' not in s:
    s = re.sub(r'\s*long hash = audioFile\.getName\(\)\.hashCode\(\) \^ \(long\) durationMs \^ \(long\) \(avg \* 1000\);\n\s*int keyIdx = Math\.abs\(\(int\) \(hash % MUSICAL_KEYS\.length\)\);\n\s*String detectedKey = MUSICAL_KEYS\[keyIdx\];', '\n            String detectedKey = "Tonalité non déterminée";', s, count=1)
    s = s.replace('return new AudioAnalysisResult(estimatedBpm, key, 0.65f, getTempoName(estimatedBpm));', 'return new AudioAnalysisResult(estimatedBpm, "Tonalité non déterminée", 0.65f, getTempoName(estimatedBpm));')
    s = s.replace('return new AudioAnalysisResult(120, "A Mineur", 0.6f, "Modéré (120 BPM)");', 'return new AudioAnalysisResult(120, "Tonalité non déterminée", 0.6f, "Modéré (120 BPM)");')
bpm.write_text(s, encoding='utf-8')

lrc = ROOT / 'app/src/main/java/com/humbleman/visualiseur/LrcLibClient.java'
s = lrc.read_text(encoding='utf-8')
if 'double bestScore = 0.0;' not in s:
    s = s.replace('StudioPro/2.8.4', 'StudioPro/2.9.0')
    old = '''                    LyricsResult fallback = null;\n                    for (int i = 0; i < arr.length(); i++) {\n                        JSONObject item = arr.getJSONObject(i);\n                        LyricsResult res = parseJson(item);\n                        if (res != null) {\n                            if (res.hasSynced()) return res;\n                            if (fallback == null && !res.plainLyrics.isEmpty()) fallback = res;\n                        }\n                    }\n                    return fallback;'''
    new = '''                    LyricsResult best = null;\n                    double bestScore = 0.0;\n                    String wantedTrack = normalize(trackName);\n                    String wantedArtist = normalize(artistName);\n                    for (int i = 0; i < arr.length(); i++) {\n                        JSONObject item = arr.getJSONObject(i);\n                        LyricsResult res = parseJson(item);\n                        if (res == null) continue;\n                        double score = similarity(wantedTrack, normalize(res.trackName));\n                        if (!wantedArtist.isEmpty() && !normalize(res.artistName).isEmpty()) {\n                            score += 0.35 * similarity(wantedArtist, normalize(res.artistName));\n                        }\n                        if (durationSec > 0 && res.duration > 0) {\n                            double diff = Math.abs(durationSec - res.duration) / (double) Math.max(durationSec, res.duration);\n                            score += 0.25 * Math.max(0.0, 1.0 - diff * 4.0);\n                        }\n                        if (res.hasSynced()) score += 0.15;\n                        if (score > bestScore) { bestScore = score; best = res; }\n                    }\n                    return bestScore >= 0.70 ? best : null;'''
    if old not in s: raise SystemExit('LRCLIB search block not found')
    s = s.replace(old, new, 1)
    helpers = '''\n    private static String normalize(String s) {\n        if (s == null) return "";\n        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)\n                .replaceAll("\\\\p{M}", "")\n                .toLowerCase(java.util.Locale.ROOT)\n                .replaceAll("\\\\b(official|video|audio|lyrics|hd|4k|remix|clip|feat|ft)\\\\b", " ")\n                .replaceAll("[^a-z0-9]+", " ")\n                .trim();\n    }\n\n    private static double similarity(String a, String b) {\n        if (a.isEmpty() || b.isEmpty()) return 0.0;\n        if (a.equals(b)) return 1.0;\n        if (a.contains(b) || b.contains(a)) return 0.92;\n        String[] aa = a.split(" ");\n        String[] bb = b.split(" ");\n        int common = 0;\n        for (String x : aa) for (String y : bb) if (x.equals(y)) { common++; break; }\n        return common / (double) Math.max(aa.length, bb.length);\n    }\n'''
    insert = s.rfind('\n}')
    s = s[:insert] + helpers + s[insert:]
lrc.write_text(s, encoding='utf-8')
print('accuracy safeguards checked')
