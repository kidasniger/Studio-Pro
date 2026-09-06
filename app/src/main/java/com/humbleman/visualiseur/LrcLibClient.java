package com.kidas.studiopro;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class LrcLibClient {
    private static final String BASE_URL = "https://lrclib.net/api";
    private static final String USER_AGENT = "StudioPro/2.9.3";
    private static final String TAG = "LrcLibClient";

    public static class LyricsResult {
        public final String trackName;
        public final String artistName;
        public final String albumName;
        public final int duration;
        public final String plainLyrics;
        public final String syncedLyrics;

        public LyricsResult(String trackName, String artistName, String albumName, int duration, String plainLyrics, String syncedLyrics) {
            this.trackName = trackName != null ? trackName : "";
            this.artistName = artistName != null ? artistName : "";
            this.albumName = albumName != null ? albumName : "";
            this.duration = duration;
            this.plainLyrics = plainLyrics != null ? plainLyrics : "";
            this.syncedLyrics = syncedLyrics != null ? syncedLyrics : "";
        }

        public boolean hasSynced() {
            return syncedLyrics != null && !syncedLyrics.trim().isEmpty();
        }
    }

    public static LyricsResult fetchLyrics(String trackName, String artistName, int durationSec) {
        if (trackName == null || trackName.trim().isEmpty()) return null;

        if (artistName != null && !artistName.trim().isEmpty()) {
            try {
                String query = BASE_URL + "/get?track_name=" + URLEncoder.encode(trackName.trim(), "UTF-8")
                        + "&artist_name=" + URLEncoder.encode(artistName.trim(), "UTF-8");
                if (durationSec > 0) query += "&duration=" + durationSec;
                LyricsResult res = executeGet(query);
                if (res != null && (res.hasSynced() || !res.plainLyrics.isEmpty())) return res;
            } catch (Exception e) {
                Log.w(TAG, "Direct lyrics lookup failed", e);
            }
        }

        try {
            String q = trackName.trim() + (artistName != null && !artistName.trim().isEmpty() ? " " + artistName.trim() : "");
            String query = BASE_URL + "/search?q=" + URLEncoder.encode(q, "UTF-8");
            LyricsResult res = executeSearch(query, trackName, artistName, durationSec);
            if (res != null) return res;
        } catch (Exception e) {
            Log.w(TAG, "Lyrics search failed", e);
        }

        try {
            String cleanTrack = trackName.replaceAll("(?i)\\b(official|video|lyrics|audio|hd|4k|remix|clip|ft|feat)\\b", "")
                    .replaceAll("[\\(\\)\\[\\]\\-_]", " ").trim();
            if (!cleanTrack.isEmpty() && !cleanTrack.equalsIgnoreCase(trackName)) {
                String query = BASE_URL + "/search?q=" + URLEncoder.encode(cleanTrack, "UTF-8");
                return executeSearch(query, trackName, artistName, durationSec);
            }
        } catch (Exception e) {
            Log.w(TAG, "Normalized lyrics search failed", e);
        }

        return null;
    }

    private static LyricsResult executeGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                    return parseJson(new JSONObject(sb.toString()));
                }
            }
            Log.w(TAG, "LRCLIB GET returned HTTP " + code);
        } catch (Exception e) {
            Log.w(TAG, "LRCLIB GET request failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static LyricsResult executeSearch(String urlStr, String trackName, String artistName, int durationSec) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                    JSONArray arr = new JSONArray(sb.toString());
                    LyricsResult best = null;
                    double bestScore = 0.0;
                    String wantedTrack = normalize(trackName);
                    String wantedArtist = normalize(artistName);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        LyricsResult res = parseJson(item);
                        if (res == null) continue;
                        double score = similarity(wantedTrack, normalize(res.trackName));
                        if (!wantedArtist.isEmpty() && !normalize(res.artistName).isEmpty()) {
                            score += 0.35 * similarity(wantedArtist, normalize(res.artistName));
                        }
                        if (durationSec > 0 && res.duration > 0) {
                            double diff = Math.abs(durationSec - res.duration) / (double) Math.max(durationSec, res.duration);
                            score += 0.25 * Math.max(0.0, 1.0 - diff * 4.0);
                        }
                        if (res.hasSynced()) score += 0.15;
                        if (score > bestScore) {
                            bestScore = score;
                            best = res;
                        }
                    }
                    return bestScore >= 0.70 ? best : null;
                }
            }
            Log.w(TAG, "LRCLIB search returned HTTP " + code);
        } catch (Exception e) {
            Log.w(TAG, "LRCLIB search request failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static LyricsResult parseJson(JSONObject obj) {
        if (obj == null) return null;
        String track = obj.optString("trackName", obj.optString("name", ""));
        String artist = obj.optString("artistName", "");
        String album = obj.optString("albumName", "");
        int duration = obj.optInt("duration", 0);
        String plain = obj.optString("plainLyrics", "");
        String synced = obj.optString("syncedLyrics", "");
        return new LyricsResult(track, artist, album, duration, plain, synced);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\b(official|video|audio|lyrics|hd|4k|remix|clip|feat|ft)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.contains(b) || b.contains(a)) return 0.92;
        String[] aa = a.split(" ");
        String[] bb = b.split(" ");
        int common = 0;
        for (String x : aa) for (String y : bb) if (x.equals(y)) { common++; break; }
        return common / (double) Math.max(aa.length, bb.length);
    }
}
