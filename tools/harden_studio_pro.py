from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/humbleman/visualiseur/MainActivity.java"
EDITOR = ROOT / "app/src/main/java/com/humbleman/visualiseur/AudioEditorDialog.java"
LYRICS_EDITOR = ROOT / "app/src/main/java/com/humbleman/visualiseur/LyricsTimelineEditorDialog.java"
BROWSER = ROOT / "app/src/main/java/com/humbleman/visualiseur/AudioBrowserDialog.java"
EFFECTS = ROOT / "app/src/main/java/com/humbleman/visualiseur/AudioEffectsManager.java"
GROQ = MAIN
BUILD = ROOT / "app/build.gradle"
README = ROOT / "README.md"
UPDATE = ROOT / "update.md"

EMOJI_RANGES = ((0x1F000, 0x1FAFF), (0x1FC00, 0x1FFFF), (0x2600, 0x27FF), (0x2B00, 0x2BFF))

def strip_emojis(text: str) -> str:
    return "".join(ch for ch in text if not any(a <= ord(ch) <= b for a, b in EMOJI_RANGES))

def replace_method(text: str, signature_fragment: str, new_method: str) -> str:
    start = text.find(signature_fragment)
    if start < 0:
        raise SystemExit(f"Method not found: {signature_fragment}")
    brace = text.find("{", start)
    if brace < 0:
        raise SystemExit(f"Opening brace not found: {signature_fragment}")
    depth = 0
    in_str = False
    esc = False
    in_line = False
    in_block = False
    i = brace
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_line:
            if ch == "\n": in_line = False
        elif in_block:
            if ch == "*" and nxt == "/": in_block = False; i += 1
        elif in_str:
            if esc: esc = False
            elif ch == "\\": esc = True
            elif ch == '"': in_str = False
        else:
            if ch == '"': in_str = True
            elif ch == "/" and nxt == "/": in_line = True; i += 1
            elif ch == "/" and nxt == "*": in_block = True; i += 1
            elif ch == "{": depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + new_method + text[i + 1:]
        i += 1
    raise SystemExit(f"Unbalanced braces: {signature_fragment}")


def patch_main(text: str) -> str:
    # Persist the actual visualizer-zone setting instead of hard-resetting it.
    text = text.replace('o.put("visualizerLengthScale", 1.0f);', 'o.put("visualizerLengthScale", Math.max(0.50f, Math.min(1.25f, visualizerLengthScale)));')
    text = text.replace('visualizerLengthScale = 1.0f;', 'visualizerLengthScale = (float) o.optDouble("visualizerLengthScale", visualizerLengthScale);\n            visualizerLengthScale = Math.max(0.50f, Math.min(1.25f, visualizerLengthScale));')

    # Vertical-only visualizer scaling: preserve full video width.
    old = 'c.scale(visualizerLengthScale, visualizerLengthScale, cx, cy);'
    new = 'c.scale(1.0f, Math.max(0.50f, Math.min(1.25f, visualizerLengthScale)), cx, cy);'
    if old not in text:
        raise SystemExit("Visualizer scale line not found")
    text = text.replace(old, new, 1)

    # Implement the advertised radial background instead of falling through to linear gradient.
    old_bg = '''            } else if ("dark".equals(backgroundMode)) {\n                paint.setColor(0xFF06070B);\n                c.drawRect(0, 0, w, h, paint);\n            } else {\n                int col1 = Color.rgb(15 + (int) (smoothedBass * 20), 10, 35 + (int) (smoothedBass * 30));\n                int col2 = Color.rgb(6, 18 + (int) (smoothedBass * 25), 26);\n                paint.setShader(new LinearGradient(0, 0, w, h, col1, col2, Shader.TileMode.CLAMP));\n                c.drawRect(0, 0, w, h, paint);\n                paint.setShader(null);\n            }'''
    new_bg = '''            } else if ("dark".equals(backgroundMode)) {\n                paint.setShader(null);\n                paint.setColor(0xFF06070B);\n                c.drawRect(0, 0, w, h, paint);\n            } else if ("radial".equals(backgroundMode)) {\n                int outer = Color.rgb(6, 12 + (int) (smoothedBass * 18), 26 + (int) (smoothedBass * 30));\n                int inner = Color.rgb(24 + (int) (smoothedBass * 20), 10, 48 + (int) (smoothedBass * 25));\n                float radius = Math.max(w, h) * (0.38f + smoothedBass * 0.08f);\n                paint.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, radius, inner, outer, Shader.TileMode.CLAMP));\n                c.drawRect(0, 0, w, h, paint);\n                paint.setShader(null);\n            } else {\n                int col1 = Color.rgb(15 + (int) (smoothedBass * 20), 10, 35 + (int) (smoothedBass * 30));\n                int col2 = Color.rgb(6, 18 + (int) (smoothedBass * 25), 26);\n                paint.setShader(new LinearGradient(0, 0, w, h, col1, col2, Shader.TileMode.CLAMP));\n                c.drawRect(0, 0, w, h, paint);\n                paint.setShader(null);\n            }'''
    if old_bg not in text:
        raise SystemExit("Background rendering block not found")
    text = text.replace(old_bg, new_bg, 1)

    # Never globally delete user LRC files. Only remove Studio Pro generated artifacts with our own prefix.
    cleanup = '''    private void cleanupDocumentLyricsAndLegacyFiles() {\n        new Thread(() -> {\n            try {\n                File tempLrc = new File(getCacheDir(), "temp_lyrics.lrc");\n                if (tempLrc.exists()) tempLrc.delete();\n\n                File docsBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);\n                File targetDir = new File(docsBase, "StudioPro");\n                if (targetDir.exists() && targetDir.isDirectory()) {\n                    File[] list = targetDir.listFiles();\n                    if (list != null) {\n                        for (File f : list) {\n                            String name = f.getName();\n                            if (name.startsWith("Voix_IA_") && (name.endsWith(".lrc") || name.endsWith(".m4a"))) {\n                                f.delete();\n                            }\n                        }\n                    }\n                }\n            } catch (Exception ignored) {}\n        }).start();\n    }\n'''
    text = replace_method(text, '    private void cleanupDocumentLyricsAndLegacyFiles()', cleanup)

    # Parse multiple LRC timestamps on one line and recompute end times safely.
    parse_lrc = '''        public static List<LyricLine> parseLrcString(String lrcContent) {\n            List<LyricLine> list = new ArrayList<>();\n            if (lrcContent == null || lrcContent.trim().isEmpty()) return list;\n            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\\\[(\\\\d{1,3}):(\\\\d{1,2})(?:[.:](\\\\d{1,3}))?\\\\]");\n            for (String raw : lrcContent.split("\\\\r?\\\\n")) {\n                java.util.regex.Matcher matcher = pattern.matcher(raw);\n                List<Long> times = new ArrayList<>();\n                int textStart = -1;\n                while (matcher.find()) {\n                    long min = Long.parseLong(matcher.group(1));\n                    long sec = Long.parseLong(matcher.group(2));\n                    String frac = matcher.group(3);\n                    long ms = 0L;\n                    if (frac != null && !frac.isEmpty()) {\n                        if (frac.length() == 1) ms = Long.parseLong(frac) * 100L;\n                        else if (frac.length() == 2) ms = Long.parseLong(frac) * 10L;\n                        else ms = Long.parseLong(frac.substring(0, 3));\n                    }\n                    times.add(min * 60000L + sec * 1000L + ms);\n                    textStart = matcher.end();\n                }\n                if (times.isEmpty() || textStart < 0) continue;\n                String text = raw.substring(textStart).trim();\n                if (text.isEmpty()) continue;\n                for (Long t : times) list.add(new LyricLine(t, t + 3500L, text));\n            }\n            java.util.Collections.sort(list, (a, b) -> Long.compare(a.startMs, b.startMs));\n            for (int i = 0; i + 1 < list.size(); i++) {\n                LyricLine cur = list.get(i);\n                LyricLine next = list.get(i + 1);\n                long end = Math.max(cur.startMs + 250L, next.startMs);\n                list.set(i, new LyricLine(cur.startMs, end, cur.text));\n            }\n            return list;\n        }\n'''
    text = replace_method(text, '        public static List<LyricLine> parseLrcString(String lrcContent)', parse_lrc)

    # Avoid export deadlocks when the activity is destroyed while a worker waits for the UI callback.
    text = text.replace('        handler.removeCallbacksAndMessages(null);\n        super.onDestroy();', '        if (!ExportState.running) handler.removeCallbacksAndMessages(null);\n        super.onDestroy();')
    text = re.sub(r'\n    @Override\n    protected void onResume\(\) \{.*?\n    \}\n\n    @Override\n    protected void onStop\(\) \{.*?\n    \}', '\n    @Override\n    protected void onResume() {\n        super.onResume();\n    }\n\n    @Override\n    protected void onStop() {\n        super.onStop();\n    }', text, count=1, flags=re.S)

    # Do not leave the exporter marked running after an exception.
    text = text.replace('            } catch (Exception e) {\n                runOnUiThread(() -> {\n                    showStatus("Export : " + friendlyError(e));', '            } catch (Exception e) {\n                ExportState.running = false;\n                ExportState.stop.set(false);\n                runOnUiThread(() -> {\n                    showStatus("Export : " + friendlyError(e));', 1)

    # Add post-mux validation and fail closed before inserting the final video in MediaStore.
    marker = '    private void saveVideo(File f, int w, int h) throws Exception {'
    validator = '''    private void validateMp4(File file) throws Exception {\n        if (file == null || !file.exists() || file.length() < 1024) throw new Exception("MP4 final absent ou vide.");\n        android.media.MediaExtractor ex = new android.media.MediaExtractor();\n        try {\n            ex.setDataSource(file.getAbsolutePath());\n            int video = -1, audio = -1;\n            long maxDuration = 0L;\n            for (int i = 0; i < ex.getTrackCount(); i++) {\n                android.media.MediaFormat mf = ex.getTrackFormat(i);\n                String mime = mf.getString(android.media.MediaFormat.KEY_MIME);\n                if (mime != null && mime.startsWith("video/") && video < 0) video = i;\n                if (mime != null && mime.startsWith("audio/") && audio < 0) audio = i;\n                if (mf.containsKey(android.media.MediaFormat.KEY_DURATION)) maxDuration = Math.max(maxDuration, mf.getLong(android.media.MediaFormat.KEY_DURATION));\n            }\n            if (video < 0) throw new Exception("MP4 sans piste vidéo.");\n            if (audio < 0) throw new Exception("MP4 sans piste audio.");\n            if (maxDuration <= 0L) throw new Exception("Durée MP4 invalide.");\n        } finally {\n            try { ex.release(); } catch (Exception ignored) {}\n        }\n    }\n\n'''
    if marker not in text:
        raise SystemExit("saveVideo marker not found")
    text = text.replace(marker, validator + marker, 1)
    # Validation immediately after mux and before saveVideo.
    text = text.replace('        MuxerUtil.mux(MainActivity.this, videoOnly, audioFile, out, trimStartMs * 1000L, (trimEndMs > trimStartMs ? trimEndMs : player.getDuration()) * 1000L, loopTargetMs * 1000L);\n        saveVideo(out, w, h);', '        MuxerUtil.mux(MainActivity.this, videoOnly, audioFile, out, trimStartMs * 1000L, (trimEndMs > trimStartMs ? trimEndMs : player.getDuration()) * 1000L, loopTargetMs * 1000L);\n        validateMp4(out);\n        saveVideo(out, w, h);', 1)

    # Replace saveVideo with a MediaStore-safe pending flow and cleanup on failure.
    save_video = '''    private void saveVideo(File f, int w, int h) throws Exception {\n        ContentValues cv = new ContentValues();\n        cv.put(MediaStore.Video.Media.DISPLAY_NAME, currentFileName + "-" + w + "x" + h + ".mp4");\n        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");\n        if (Build.VERSION.SDK_INT >= 29) {\n            cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StudioPro");\n            cv.put(MediaStore.Video.Media.IS_PENDING, 1);\n        }\n        Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);\n        if (u == null) throw new Exception("Impossible de créer l'entrée vidéo dans la galerie.");\n        try {\n            try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(u)) {\n                if (out == null) throw new Exception("Ouverture du fichier vidéo impossible.");\n                byte[] b = new byte[32768];\n                int n;\n                while ((n = in.read(b)) != -1) out.write(b, 0, n);\n            }\n            if (Build.VERSION.SDK_INT >= 29) {\n                ContentValues done = new ContentValues();\n                done.put(MediaStore.Video.Media.IS_PENDING, 0);\n                getContentResolver().update(u, done, null, null);\n            }\n        } catch (Exception e) {\n            try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}\n            throw e;\n        }\n    }\n'''
    text = replace_method(text, '    private void saveVideo(File f, int w, int h)', save_video)

    # Keep Groq within current production chat models and modern token parameter.
    old_models = '''        private static final String[] FALLBACK_MODELS = {\n            "llama-3.3-70b-versatile",\n            "llama-3.1-8b-instant",\n            "meta-llama/llama-3.3-70b-instruct",\n            "llama3-70b-8192",\n            "llama3-8b-8192",\n            "gemma2-9b-it",\n            "mixtral-8x7b-32768",\n            "qwen/qwen3.6-27b",\n            "deepseek-r1-distill-llama-70b"\n        };'''
    new_models = '''        private static final String[] FALLBACK_MODELS = {\n            "openai/gpt-oss-120b",\n            "llama-3.3-70b-versatile",\n            "llama-3.1-8b-instant",\n            "openai/gpt-oss-20b"\n        };'''
    if old_models in text: text = text.replace(old_models, new_models, 1)
    text = text.replace('if (active && !id.isEmpty() && !id.toLowerCase().contains("whisper") && !id.toLowerCase().contains("tts")) {\n                                list.add(id);\n                            }', 'if (active && isSupportedChatModel(id)) {\n                                list.add(id);\n                            }', 1)
    model_helper = '''\n        private static boolean isSupportedChatModel(String id) {\n            if (id == null || id.isEmpty()) return false;\n            for (String model : FALLBACK_MODELS) if (model.equals(id)) return true;\n            return false;\n        }\n'''
    insert_at = text.find('        static String chat(String key, String prompt)')
    if insert_at >= 0 and 'private static boolean isSupportedChatModel' not in text:
        text = text[:insert_at] + model_helper + text[insert_at:]
    text = text.replace('body.put("max_tokens", max);', 'body.put("max_completion_tokens", max);', 1)
    text = text.replace('if (file.length() > 25L * 1024L * 1024L)', 'if (file.length() > 100L * 1024L * 1024L)', 1)

    return strip_emojis(text)


def patch_editor(text: str) -> str:
    auto = '''    private void autoTrimSilence() {\n        if (audioFile == null || !audioFile.exists()) return;\n        Toast.makeText(getContext(), "Analyse réelle des silences en cours…", Toast.LENGTH_SHORT).show();\n        new Thread(() -> {\n            try {\n                final AudioEditEngine.SilenceRange range = AudioEditEngine.detectSilence(audioFile);\n                handler.post(() -> {\n                    currentStartMs = Math.max(0L, Math.min(range.startMs, totalDurationMs));\n                    currentEndMs = Math.max(currentStartMs + 500L, Math.min(range.endMs, totalDurationMs));\n                    updateTimeLabels();\n                    if (waveformView != null) waveformView.invalidate();\n                    if (previewPlayer != null) previewPlayer.seekTo((int) currentStartMs);\n                });\n            } catch (Exception e) {\n                handler.post(() -> Toast.makeText(getContext(), "Détection des silences impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());\n            }\n        }).start();\n    }\n'''
    text = replace_method(text, '    private void autoTrimSilence()', auto)
    return strip_emojis(text)


def patch_timeline(text: str) -> str:
    text = text.replace('editableLyrics.set(index, new MainActivity.LyricLine(Math.max(0, line.startMs - 100), line.endMs, line.text));', 'long newStart = Math.max(0L, Math.min(line.startMs - 100L, line.endMs - 250L));\n                editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));')
    text = text.replace('editableLyrics.set(index, new MainActivity.LyricLine(line.startMs + 100, line.endMs, line.text));', 'long newStart = Math.min(line.startMs + 100L, Math.max(0L, line.endMs - 250L));\n                editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));')
    text = text.replace('editableLyrics.set(index, new MainActivity.LyricLine(player.getCurrentPosition(), line.endMs, line.text));', 'long newStart = Math.min(player.getCurrentPosition(), Math.max(0L, line.endMs - 250L));\n                        editableLyrics.set(index, new MainActivity.LyricLine(newStart, Math.max(newStart + 250L, line.endMs), line.text));')
    shift = '''    private void shiftAllTimestamps(long deltaMs) {\n        for (int i = 0; i < editableLyrics.size(); i++) {\n            MainActivity.LyricLine l = editableLyrics.get(i);\n            long start = Math.max(0L, l.startMs + deltaMs);\n            long end = Math.max(start + 250L, l.endMs + deltaMs);\n            editableLyrics.set(i, new MainActivity.LyricLine(start, end, l.text));\n        }\n        refreshLinesList();\n    }\n'''
    text = replace_method(text, '    private void shiftAllTimestamps(long deltaMs)', shift)
    return strip_emojis(text)


def patch_browser(text: str) -> str:
    old_projection = '''                String[] projection = new String[]{\n                        MediaStore.Audio.Media._ID,\n                        MediaStore.Audio.Media.TITLE,\n                        MediaStore.Audio.Media.ARTIST,\n                        MediaStore.Audio.Media.ALBUM,\n                        MediaStore.Audio.Media.DURATION,\n                        MediaStore.Audio.Media.SIZE,\n                        MediaStore.Audio.Media.DATE_ADDED,\n                        MediaStore.Audio.Media.MIME_TYPE,\n                        MediaStore.Audio.Media.DATA\n                };'''
    new_projection = '''                String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q\n                        ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH}\n                        : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA};'''
    if old_projection not in text: raise SystemExit("AudioBrowser projection block not found")
    text = text.replace(old_projection, new_projection, 1)
    text = text.replace('int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);', 'int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);\n                        int relativePathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) : -1;', 1)
    text = text.replace('String dataPath = dataCol != -1 ? cursor.getString(dataCol) : "";', 'String dataPath = "";\n                            if (relativePathCol != -1) dataPath = cursor.getString(relativePathCol);\n                            else if (dataCol != -1) dataPath = cursor.getString(dataCol);', 1)
    text = text.replace('Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);', 'Uri contentUri = ContentUris.withAppendedId(collection, id);', 1)
    return strip_emojis(text)


def patch_effects(text: str) -> str:
    old = '''    public void release() {\n        try {\n            if (equalizer != null) { equalizer.release(); equalizer = null; }\n            if (bassBoost != null) { bassBoost.release(); bassBoost = null; }\n            if (virtualizer != null) { virtualizer.release(); virtualizer = null; }\n            if (presetReverb != null) { presetReverb.release(); presetReverb = null; }\n        } catch (Exception ignored) {}\n    }'''
    new = '''    public void release() {\n        try { if (equalizer != null) equalizer.release(); } catch (Exception ignored) {} finally { equalizer = null; }\n        try { if (bassBoost != null) bassBoost.release(); } catch (Exception ignored) {} finally { bassBoost = null; }\n        try { if (virtualizer != null) virtualizer.release(); } catch (Exception ignored) {} finally { virtualizer = null; }\n        try { if (presetReverb != null) presetReverb.release(); } catch (Exception ignored) {} finally { presetReverb = null; }\n    }'''
    if old not in text: raise SystemExit("AudioEffectsManager release block not found")
    return strip_emojis(text.replace(old, new, 1))


def patch_audio_editor_callback(main: str) -> str:
    sig = '            public void onAudioEdited(long startMs, long endMs, float volumeGain, float speed, float fadeInSec, float fadeOutSec, boolean normalize, boolean loop)'
    start = main.find(sig)
    if start < 0: raise SystemExit("MainActivity audio edit callback not found")
    brace = main.find('{', start)
    depth = 0
    i = brace
    while i < len(main):
        if main[i] == '{': depth += 1
        elif main[i] == '}':
            depth -= 1
            if depth == 0: break
        i += 1
    body = '''            public void onAudioEdited(long startMs, long endMs, float volumeGain, float speed, float fadeInSec, float fadeOutSec, boolean normalize, boolean loop) {\n                if (audioFile == null || !audioFile.exists()) return;\n                showStatus("Traitement audio réel en cours…");\n                new Thread(() -> {\n                    File processed = new File(getCacheDir(), "edited_" + System.nanoTime() + ".wav");\n                    try {\n                        AudioEditEngine.process(audioFile, processed, startMs, endMs, volumeGain, speed, fadeInSec, fadeOutSec, normalize);\n                        runOnUiThread(() -> {\n                            try {\n                                ContentValues cv = new ContentValues();\n                                cv.put(MediaStore.Audio.Media.DISPLAY_NAME, currentFileName + "-edited.wav");\n                                cv.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");\n                                if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/StudioPro");\n                                Uri editedUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);\n                                if (editedUri == null) throw new Exception("Impossible de créer le fichier audio édité.");\n                                try (InputStream in = new FileInputStream(processed); OutputStream out = getContentResolver().openOutputStream(editedUri)) {\n                                    if (out == null) throw new Exception("Ouverture du fichier édité impossible.");\n                                    byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);\n                                }\n                                audioFile = processed;\n                                audioMime = "audio/wav";\n                                currentAudioUri = editedUri;\n                                currentAudioPath = "";\n                                currentFileName = currentFileName + "-edited";\n                                currentTrackTitle = currentFileName;\n                                currentTrackArtist = "";\n                                initPlayer();\n                                setAudioButtonsEnabled(true);\n                                showStatus("Édition audio appliquée et enregistrée dans Musique/StudioPro.");\n                            } catch (Exception e) {\n                                showStatus("Édition audio : " + friendlyError(e));\n                            }\n                        });\n                    } catch (Exception e) {\n                        runOnUiThread(() -> showStatus("Édition audio : " + friendlyError(e)));\n                        if (processed.exists()) processed.delete();\n                    }\n                }).start();\n            }'''
    return main[:start] + body + main[i + 1:]


def patch_build(text: str) -> str:
    text = re.sub(r'versionCode\s+\d+', 'versionCode 27', text, count=1)
    text = re.sub(r'versionName\s+"[^"]+"', 'versionName "2.9.0"', text, count=1)
    # Release signing becomes environment-driven; CI can still use the previous debug key only when no release secret is configured.
    marker = '    signingConfigs {\n'
    if marker in text and 'studioRelease' not in text:
        block = '''    signingConfigs {\n        studioRelease {\n            def releaseStore = System.getenv("STUDIO_PRO_KEYSTORE")\n            def releaseStoreBase64 = System.getenv("STUDIO_PRO_KEYSTORE_BASE64")\n            def releasePassword = System.getenv("STUDIO_PRO_KEYSTORE_PASSWORD")\n            def releaseAlias = System.getenv("STUDIO_PRO_KEY_ALIAS")\n            def releaseKeyPassword = System.getenv("STUDIO_PRO_KEY_PASSWORD")\n            if (releaseStoreBase64 && releasePassword && releaseAlias && releaseKeyPassword) {\n                def outFile = file("${rootDir}/studio-release.keystore")\n                if (!outFile.exists()) {\n                    byte[] decoded = java.util.Base64.getDecoder().decode(releaseStoreBase64.replaceAll("\\\\s", ""))\n                    outFile.withOutputStream { it.write(decoded) }\n                }\n                storeFile outFile\n                storePassword releasePassword\n                keyAlias releaseAlias\n                keyPassword releaseKeyPassword\n            } else if (releaseStore) {\n                storeFile file(releaseStore)\n                storePassword releasePassword\n                keyAlias releaseAlias\n                keyPassword releaseKeyPassword\n            }\n        }\n\n'''
        text = text.replace(marker, block + marker, 1)
        text = text.replace('signingConfig signingConfigs.release', 'signingConfig (System.getenv("STUDIO_PRO_KEYSTORE_BASE64") || System.getenv("STUDIO_PRO_KEYSTORE") ? signingConfigs.studioRelease : signingConfigs.debug)', 1)
    return text


def patch_docs(text: str) -> str:
    return strip_emojis(text)


def main():
    main = patch_main(MAIN.read_text(encoding="utf-8"))
    MAIN.write_text(main, encoding="utf-8")
    EDITOR.write_text(patch_editor(EDITOR.read_text(encoding="utf-8")), encoding="utf-8")
    LYRICS_EDITOR.write_text(patch_timeline(LYRICS_EDITOR.read_text(encoding="utf-8")), encoding="utf-8")
    BROWSER.write_text(patch_browser(BROWSER.read_text(encoding="utf-8")), encoding="utf-8")
    EFFECTS.write_text(patch_effects(EFFECTS.read_text(encoding="utf-8")), encoding="utf-8")
    BUILD.write_text(patch_build(BUILD.read_text(encoding="utf-8")), encoding="utf-8")
    for p in ROOT.rglob("*.java"):
        p.write_text(strip_emojis(p.read_text(encoding="utf-8")), encoding="utf-8")
    for p in [README, UPDATE]:
        if p.exists(): p.write_text(strip_emojis(p.read_text(encoding="utf-8")), encoding="utf-8")
    print("Studio Pro hardening applied")

if __name__ == "__main__":
    main()
