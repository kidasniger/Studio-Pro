from pathlib import Path

P = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/humbleman/visualiseur/MainActivity.java'
s = P.read_text(encoding='utf-8')

def replace_method(text, signature, replacement):
    start = text.find(signature)
    if start < 0: raise SystemExit(f'method not found: {signature}')
    brace = text.find('{', start)
    depth = 0
    in_str = False
    esc = False
    i = brace
    while i < len(text):
        c = text[i]
        if in_str:
            if esc: esc = False
            elif c == '\\': esc = True
            elif c == '"': in_str = False
        else:
            if c == '"': in_str = True
            elif c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0: return text[:start] + replacement + text[i+1:]
        i += 1
    raise SystemExit('unbalanced method')

if 'static void copyLooping(' in s and 'boolean temporaryAudio = false;' in s:
    print('loop-aware audio muxing already patched')
else:
    old_mux_sig = '        static void mux(Context ctx, File v, File a, File out, long su, long eu, long target) throws Exception'
    new_mux = '''        static void mux(Context ctx, File v, File a, File out, long su, long eu, long target) throws Exception {\n            android.media.MediaExtractor ve = new android.media.MediaExtractor();\n            android.media.MediaExtractor ae = new android.media.MediaExtractor();\n            File processedAudio = a;\n            boolean temporaryAudio = false;\n            android.media.MediaMuxer muxer = null;\n            boolean muxerStarted = false;\n            try {\n                ve.setDataSource(v.getAbsolutePath());\n                int vt = find(ve, "video/");\n                if (vt < 0) throw new Exception("Piste vidéo introuvable.");\n                ae.setDataSource(a.getAbsolutePath());\n                int at = find(ae, "audio/");\n                if (at < 0) throw new Exception("Piste audio introuvable.");\n                android.media.MediaFormat audioFormat = ae.getTrackFormat(at);\n                String audioMime = audioFormat.getString(android.media.MediaFormat.KEY_MIME);\n                if (audioMime == null || !audioMime.equalsIgnoreCase("audio/mp4a-latm")) {\n                    File tempAac = new File(ctx.getCacheDir(), "transcoded_" + System.nanoTime() + ".m4a");\n                    transcodeToAac(ae, at, tempAac, su, eu, 0);\n                    processedAudio = tempAac;\n                    temporaryAudio = true;\n                }\n                ae.release();\n                ae = null;\n                android.media.MediaExtractor finalAudio = new android.media.MediaExtractor();\n                finalAudio.setDataSource(processedAudio.getAbsolutePath());\n                int fat = find(finalAudio, "audio/");\n                if (fat < 0) throw new Exception("Piste AAC finale introuvable.");\n                muxer = new android.media.MediaMuxer(out.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);\n                int vo = muxer.addTrack(ve.getTrackFormat(vt));\n                int ao = muxer.addTrack(finalAudio.getTrackFormat(fat));\n                muxer.start();\n                muxerStarted = true;\n                copy(ve, vt, muxer, vo, 0, Long.MAX_VALUE, 0);\n                if (target > 0) {\n                    long sourceStart = temporaryAudio ? 0 : su;\n                    long sourceEnd = temporaryAudio ? Long.MAX_VALUE : eu;\n                    copyLooping(finalAudio, fat, muxer, ao, sourceStart, sourceEnd, target);\n                } else {\n                    copy(finalAudio, fat, muxer, ao, temporaryAudio ? 0 : su, temporaryAudio ? Long.MAX_VALUE : eu, 0);\n                }\n                finalAudio.release();\n            } finally {\n                try { if (muxer != null && muxerStarted) muxer.stop(); } catch (Exception ignored) {}\n                try { if (muxer != null) muxer.release(); } catch (Exception ignored) {}\n                try { ve.release(); } catch (Exception ignored) {}\n                try { ae.release(); } catch (Exception ignored) {}\n                if (temporaryAudio) try { processedAudio.delete(); } catch (Exception ignored) {}\n            }\n        }\n'''
    s = replace_method(s, old_mux_sig, new_mux)
    insert_sig = '        static int find(android.media.MediaExtractor e, String p) {'
    helper = '''        static void copyLooping(android.media.MediaExtractor e, int t, android.media.MediaMuxer m, int out, long start, long end, long target) {\n            e.selectTrack(t);\n            e.seekTo(Math.max(0, start), android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);\n            ByteBuffer b = ByteBuffer.allocate(2 * 1024 * 1024);\n            android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();\n            long sourceLast = 0;\n            long outputBase = 0;\n            while (outputBase < target) {\n                e.seekTo(Math.max(0, start), android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);\n                long sourceFirst = -1;\n                boolean wrote = false;\n                while (true) {\n                    int n = e.readSampleData(b, 0);\n                    long ts = e.getSampleTime();\n                    if (n < 0 || ts < 0 || ts > end) break;\n                    if (sourceFirst < 0) sourceFirst = ts;\n                    long rel = Math.max(0, ts - sourceFirst);\n                    long outPts = outputBase + rel;\n                    if (outPts >= target) break;\n                    bi.offset = 0; bi.size = n; bi.presentationTimeUs = outPts; bi.flags = e.getSampleFlags();\n                    m.writeSampleData(out, b, bi);\n                    sourceLast = rel;\n                    wrote = true;\n                    e.advance();\n                    b.clear();\n                }\n                if (!wrote) break;\n                outputBase += Math.max(1, sourceLast + 1);\n            }\n            e.unselectTrack(t);\n        }\n\n'''
    if 'static void copyLooping(' not in s:
        s = s.replace(insert_sig, helper + insert_sig, 1)
    P.write_text(s, encoding='utf-8')
    print('loop-aware audio muxing patched')
