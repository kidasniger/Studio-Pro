from pathlib import Path
import re

# Studio Pro AAC export fix: preserve PCM remainder and drain encoder output first.
MAIN = Path('app/src/main/java/com/humbleman/visualiseur/MainActivity.java')
GRADLE = Path('app/build.gradle')

s = MAIN.read_text(encoding='utf-8')
start = s.find('        private static void transcodeToAac(')
end = s.find('        static int find(android.media.MediaExtractor e, String p) {', start)
if start < 0 or end < 0:
    raise SystemExit('AAC transcoder method not found')

new_method = r'''        private static void transcodeToAac(android.media.MediaExtractor extractor, int audioTrackIndex, File outFile, long startUs, long endUs, long targetUs) throws Exception {
            extractor.selectTrack(audioTrackIndex);
            extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            android.media.MediaFormat inputFormat = extractor.getTrackFormat(audioTrackIndex);
            String inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME);
            int sampleRate = inputFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channelCount = inputFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) : 2;
            int bytesPerFrame = Math.max(2, channelCount * 2);

            android.media.MediaCodec decoder = null;
            android.media.MediaCodec encoder = null;
            android.media.MediaMuxer muxer = null;
            boolean muxerStarted = false;
            int muxerAudioTrack = -1;

            try {
                decoder = android.media.MediaCodec.createDecoderByType(inputMime);
                decoder.configure(inputFormat, null, null, 0);
                decoder.start();

                android.media.MediaFormat encoderFormat = android.media.MediaFormat.createAudioFormat(
                        "audio/mp4a-latm", sampleRate, channelCount);
                encoderFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 192000);
                encoderFormat.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE,
                        android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);

                encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm");
                encoder.configure(encoderFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoder.start();

                muxer = new android.media.MediaMuxer(
                        outFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                android.media.MediaCodec.BufferInfo decInfo = new android.media.MediaCodec.BufferInfo();
                android.media.MediaCodec.BufferInfo encInfo = new android.media.MediaCodec.BufferInfo();
                java.io.ByteArrayOutputStream pendingPcm = new java.io.ByteArrayOutputStream(131072);

                boolean extractorDone = false;
                boolean decoderDone = false;
                boolean encoderInputDone = false;
                boolean encoderDone = false;
                long firstPtsUs = -1L;
                long queuedFrames = 0L;
                long maxDurationUs = targetUs > 0 ? targetUs : Math.max(0L, endUs - startUs);
                long deadline = System.currentTimeMillis() + 180000L;

                while (!encoderDone) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new Exception("Finalisation audio trop longue.");
                    }

                    boolean progressed = false;

                    // Critical ordering: always drain AAC output before waiting for input buffers.
                    while (true) {
                        int outIndex = encoder.dequeueOutputBuffer(encInfo, 0);
                        if (outIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (!muxerStarted) {
                                muxerAudioTrack = muxer.addTrack(encoder.getOutputFormat());
                                muxer.start();
                                muxerStarted = true;
                            }
                            progressed = true;
                            continue;
                        }
                        if (outIndex < 0) break;

                        ByteBuffer outBuf = encoder.getOutputBuffer(outIndex);
                        if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                && encInfo.size > 0 && outBuf != null && muxerStarted) {
                            outBuf.position(encInfo.offset);
                            outBuf.limit(encInfo.offset + encInfo.size);
                            muxer.writeSampleData(muxerAudioTrack, outBuf, encInfo);
                        }

                        if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true;
                        }
                        encoder.releaseOutputBuffer(outIndex, false);
                        progressed = true;
                        if (encoderDone) break;
                    }
                    if (encoderDone) break;

                    if (!extractorDone) {
                        int decInIndex = decoder.dequeueInputBuffer(0);
                        if (decInIndex >= 0) {
                            ByteBuffer inBuf = decoder.getInputBuffer(decInIndex);
                            if (inBuf == null) throw new Exception("Buffer décodeur indisponible.");
                            int sampleSize = extractor.readSampleData(inBuf, 0);
                            long sampleTime = extractor.getSampleTime();
                            if (sampleSize < 0 || sampleTime < 0 || sampleTime > endUs) {
                                decoder.queueInputBuffer(decInIndex, 0, 0, 0,
                                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                extractorDone = true;
                            } else {
                                decoder.queueInputBuffer(decInIndex, 0, sampleSize, sampleTime, 0);
                                extractor.advance();
                            }
                            progressed = true;
                        }
                    }

                    if (!decoderDone) {
                        while (true) {
                            int decOutIndex = decoder.dequeueOutputBuffer(decInfo, 0);
                            if (decOutIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                progressed = true;
                                continue;
                            }
                            if (decOutIndex < 0) break;

                            ByteBuffer decBuf = decoder.getOutputBuffer(decOutIndex);
                            if (decInfo.size > 0 && decBuf != null) {
                                if (firstPtsUs < 0) firstPtsUs = decInfo.presentationTimeUs;
                                long relPts = Math.max(0L, decInfo.presentationTimeUs - firstPtsUs);
                                if (maxDurationUs <= 0L || relPts < maxDurationUs) {
                                    decBuf.position(decInfo.offset);
                                    decBuf.limit(decInfo.offset + decInfo.size);
                                    byte[] pcm = new byte[decInfo.size];
                                    decBuf.get(pcm);
                                    pendingPcm.write(pcm, 0, pcm.length);
                                }
                            }

                            boolean eos = (decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                            long relNow = firstPtsUs < 0 ? 0L : decInfo.presentationTimeUs - firstPtsUs;
                            decoder.releaseOutputBuffer(decOutIndex, false);
                            progressed = true;
                            if (eos || (maxDurationUs > 0L && relNow >= maxDurationUs)) {
                                decoderDone = true;
                                break;
                            }
                        }
                    }

                    if (!encoderInputDone) {
                        int encInIndex = encoder.dequeueInputBuffer(0);
                        if (encInIndex >= 0) {
                            ByteBuffer encInBuf = encoder.getInputBuffer(encInIndex);
                            if (encInBuf == null) throw new Exception("Buffer AAC indisponible.");
                            encInBuf.clear();

                            int sendBytes = Math.min(encInBuf.remaining(), pendingPcm.size());
                            sendBytes -= sendBytes % bytesPerFrame;

                            if (sendBytes > 0) {
                                byte[] data = pendingPcm.toByteArray();
                                encInBuf.put(data, 0, sendBytes);
                                pendingPcm.reset();
                                if (data.length > sendBytes) {
                                    pendingPcm.write(data, sendBytes, data.length - sendBytes);
                                }
                                long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                                encoder.queueInputBuffer(encInIndex, 0, sendBytes, ptsUs, 0);
                                queuedFrames += sendBytes / bytesPerFrame;
                                progressed = true;
                            } else if (decoderDone && pendingPcm.size() == 0) {
                                long ptsUs = (queuedFrames * 1000000L) / sampleRate;
                                encoder.queueInputBuffer(encInIndex, 0, 0, ptsUs,
                                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                encoderInputDone = true;
                                progressed = true;
                            }
                        }
                    }

                    if (!progressed) {
                        try {
                            Thread.sleep(2L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new Exception("Transcodage interrompu.", e);
                        }
                    }
                }

                if (!muxerStarted || muxerAudioTrack < 0) {
                    throw new Exception("Aucune sortie AAC n'a été produite.");
                }
            } finally {
                try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Exception ignored) {}
                try { if (encoder != null) { encoder.stop(); encoder.release(); } } catch (Exception ignored) {}
                try { if (muxer != null) { if (muxerStarted) muxer.stop(); muxer.release(); } } catch (Exception ignored) {}
                try { extractor.unselectTrack(audioTrackIndex); } catch (Exception ignored) {}
            }
        }

'''

s = s[:start] + new_method + s[end:]
MAIN.write_text(s, encoding='utf-8')

t = GRADLE.read_text(encoding='utf-8')
t = re.sub(r'(?m)^\s*versionCode\s+\d+\s*$', '        versionCode 26', t, count=1)
t = re.sub(r'(?m)^\s*versionName\s+"[^"]+"\s*$', '        versionName "2.8.4"', t, count=1)
GRADLE.write_text(t, encoding='utf-8')
