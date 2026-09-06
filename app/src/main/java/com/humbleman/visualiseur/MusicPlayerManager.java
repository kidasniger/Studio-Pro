package com.kidas.studiopro;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestionnaire Centralisé du Lecteur de Musique Studio Pro :
 * - File d'attente (Playlist / Queue) dynamique avec modes Aléatoire (Shuffle) et Répétition (Off, All, One)
 * - Contrôle de lecture en arrière-plan avec service Foreground et Barre de Notification interactive
 * - Synchronisation temps-réel avec le Visualiseur et l'Equalizer / Effets Audio
 * - Vitesse de lecture variable (0.5x à 2.0x) et Pitch
 */
public class MusicPlayerManager {

    private static final String TAG = "MusicPlayerManager";

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    public interface PlaybackStateListener {
        void onTrackChanged(AudioBrowserDialog.AudioTrackItem track);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdated(int currentPositionMs, int durationMs);
        void onQueueChanged(List<AudioBrowserDialog.AudioTrackItem> queue, int currentIndex);
        void onPlaybackModesChanged(int repeatMode, boolean shuffleEnabled);
        void onSpeedChanged(float speed, float pitch);
    }

    public interface PlaybackConflictListener {
        void onPlaybackConflict();
    }
    private PlaybackConflictListener playbackConflictListener;

    public void setPlaybackConflictListener(PlaybackConflictListener listener) {
        this.playbackConflictListener = listener;
    }

    public interface OnTracksRefreshedListener {
        void onTracksRefreshed(List<AudioBrowserDialog.AudioTrackItem> tracks);
    }

    private volatile boolean isScanning = false;

    public boolean isScanning() {
        return isScanning;
    }

    private static MusicPlayerManager instance;

    public static synchronized MusicPlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicPlayerManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PlaybackStateListener> listeners = new CopyOnWriteArrayList<>();

    private MediaPlayer player;
    private Visualizer visualizer;
    private byte[] currentFft = new byte[0];
    private byte[] currentWaveform = new byte[0];

    // File d'attente & liste de lecture
    private final List<AudioBrowserDialog.AudioTrackItem> queue = new ArrayList<>();
    private final List<Integer> shuffleIndices = new ArrayList<>();
    private int currentIndex = -1;
    private AudioBrowserDialog.AudioTrackItem currentTrack = null;

    // Fichier audio local actuel mis en cache
    private File currentCachedAudioFile = null;

    // Modes & Paramètres
    private int repeatMode = REPEAT_ALL;
    private boolean shuffleEnabled = false;
    private float playbackSpeed = 1.0f;
    private float playbackPitch = 1.0f;

    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private int currentDurationMs = 0;

    // Polling d'avancement
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && isPlaying && isPrepared) {
                try {
                    int pos = player.getCurrentPosition();
                    int dur = player.getDuration();
                    currentDurationMs = dur;
                    for (PlaybackStateListener l : listeners) {
                        try {
                            l.onProgressUpdated(pos, dur);
                        } catch (Exception e) {
                            Log.e(TAG, "Error notifying progress listener", e);
                        }
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 50);
            }
        }
    };

    private MusicPlayerManager(Context context) {
        this.context = context;
    }

    public void addListener(PlaybackStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            // Notification immédiate de l'état actuel
            if (currentTrack != null) {
                listener.onTrackChanged(currentTrack);
            }
            listener.onPlaybackStateChanged(isPlaying);
            listener.onPlaybackModesChanged(repeatMode, shuffleEnabled);
            listener.onSpeedChanged(playbackSpeed, playbackPitch);
            listener.onQueueChanged(new ArrayList<>(queue), currentIndex);
            if (player != null && isPrepared) {
                try {
                    listener.onProgressUpdated(player.getCurrentPosition(), player.getDuration());
                } catch (Exception ignored) {}
            }
        }
    }

    public void removeListener(PlaybackStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public MediaPlayer getPlayer() {
        return player;
    }

    public AudioBrowserDialog.AudioTrackItem getCurrentTrack() {
        return currentTrack;
    }

    public File getCurrentAudioFile() {
        return currentCachedAudioFile;
    }

    public boolean isPlaying() {
        return isPlaying && player != null && player.isPlaying();
    }

    public int getCurrentPosition() {
        if (player != null && isPrepared) {
            try {
                return player.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public int getDuration() {
        if (player != null && isPrepared) {
            try {
                return player.getDuration();
            } catch (Exception ignored) {}
        }
        return currentDurationMs;
    }

    public List<AudioBrowserDialog.AudioTrackItem> getQueue() {
        return new ArrayList<>(queue);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public float getPlaybackPitch() {
        return playbackPitch;
    }

    public byte[] getFftData() {
        return currentFft;
    }

    public byte[] getWaveformData() {
        return currentWaveform;
    }

    /**
     * Définit la file d'attente complète et démarre la lecture du morceau sélectionné
     */
    public void setQueue(List<AudioBrowserDialog.AudioTrackItem> newQueue, int startIndex) {
        setQueue(newQueue, startIndex, true);
    }

    public void setQueue(List<AudioBrowserDialog.AudioTrackItem> newQueue, int startIndex, boolean autoPlay) {
        queue.clear();
        if (newQueue != null) {
            queue.addAll(newQueue);
        }
        rebuildShuffleIndices();
        if (queue.isEmpty()) {
            currentIndex = -1;
            stop();
            currentTrack = null;
            notifyTrackChanged(null);
            notifyQueueChanged();
            return;
        }

        if (startIndex < 0 || startIndex >= queue.size()) {
            startIndex = 0;
        }
        currentIndex = startIndex;
        notifyQueueChanged();
        loadAndPlayTrack(queue.get(currentIndex), autoPlay);
    }

    /**
     * Scanne tous les fichiers audio présents sur le téléphone (MediaStore) exactement comme l'explorateur
     * et actualise la file d'attente du lecteur.
     */
    public void scanAndRefreshDeviceTracks(Context scanContext, boolean autoPlayIfEmpty, OnTracksRefreshedListener listener) {
        if (isScanning) return;
        isScanning = true;
        Context ctx = (scanContext != null) ? scanContext.getApplicationContext() : context;

        new Thread(() -> {
            List<AudioBrowserDialog.AudioTrackItem> found = queryDeviceAudio(ctx);
            isScanning = false;

            handler.post(() -> {
                boolean wasEmpty = queue.isEmpty();
                if (wasEmpty && !found.isEmpty()) {
                    queue.clear();
                    queue.addAll(found);
                    rebuildShuffleIndices();
                    if (autoPlayIfEmpty) {
                        currentIndex = 0;
                        playTrackAtIndex(0, true);
                    } else {
                        // Ne PAS démarrer la lecture automatique lors d'un scan passif
                        // et ne pas écraser le titre du projet en cours sur la page Accueil
                        currentIndex = 0;
                        currentTrack = found.get(0);
                        notifyQueueChanged();
                    }
                } else if (!found.isEmpty()) {
                    AudioBrowserDialog.AudioTrackItem current = currentTrack;
                    queue.clear();
                    queue.addAll(found);
                    rebuildShuffleIndices();

                    int newIdx = 0;
                    if (current != null) {
                        for (int i = 0; i < queue.size(); i++) {
                            if (queue.get(i).id == current.id ||
                                (queue.get(i).contentUri != null && queue.get(i).contentUri.equals(current.contentUri))) {
                                newIdx = i;
                                break;
                            }
                        }
                    }
                    currentIndex = newIdx;
                    if (currentIndex >= 0 && currentIndex < queue.size()) {
                        currentTrack = queue.get(currentIndex);
                    }
                    notifyQueueChanged();
                    if (current != null) {
                        notifyTrackChanged(currentTrack);
                    }
                }

                if (listener != null) {
                    listener.onTracksRefreshed(found);
                }
            });
        }).start();
    }

    /**
     * Requête MediaStore pour extraire toutes les pistes audio du stockage téléphone.
     */
    public static List<AudioBrowserDialog.AudioTrackItem> queryDeviceAudio(Context ctx) {
        List<AudioBrowserDialog.AudioTrackItem> list = new ArrayList<>();
        if (ctx == null) return list;

        try {
            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.MediaColumns.RELATIVE_PATH}
                    : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA};

            String selection = MediaStore.Audio.Media.DURATION + " >= 1000";
            String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = ctx.getContentResolver().query(collection, projection, selection, null, sortOrder)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                    int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                    int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                    int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
                    int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
                    int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                    int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    int relativePathCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) : -1;

                    while (cursor.moveToNext()) {
                        long id = idCol >= 0 ? cursor.getLong(idCol) : 0;
                        String title = titleCol >= 0 ? cursor.getString(titleCol) : "Inconnu";
                        String artist = artistCol >= 0 ? cursor.getString(artistCol) : "Artiste Inconnu";
                        String album = albumCol >= 0 ? cursor.getString(albumCol) : "";
                        long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                        long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                        long date = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                        String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : "audio/mpeg";
                        String dataPath = "";
                        if (relativePathCol >= 0) dataPath = cursor.getString(relativePathCol);
                        else if (dataCol >= 0) dataPath = cursor.getString(dataCol);

                        Uri contentUri = ContentUris.withAppendedId(collection, id);
                        list.add(new AudioBrowserDialog.AudioTrackItem(
                                id,
                                title != null ? title : "Inconnu",
                                artist != null ? artist : "Artiste Inconnu",
                                album != null ? album : "",
                                duration,
                                size,
                                date,
                                contentUri,
                                dataPath != null ? dataPath : "",
                                mime != null ? mime : "audio/mpeg"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying device audio in MusicPlayerManager", e);
        }
        return list;
    }

    /**
     * Charge un morceau unique ou le place en tête de lecture
     */
    public void playSingleTrack(AudioBrowserDialog.AudioTrackItem track, boolean autoPlay) {
        if (track == null) return;
        int foundIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).contentUri != null && queue.get(i).contentUri.equals(track.contentUri)) {
                foundIndex = i;
                break;
            }
        }
        if (foundIndex != -1) {
            currentIndex = foundIndex;
        } else {
            queue.add(0, track);
            currentIndex = 0;
            rebuildShuffleIndices();
            notifyQueueChanged();
        }
        loadAndPlayTrack(track, autoPlay);
    }

    /**
     * Synchronise la piste actuellement chargée (par exemple depuis MainActivity ou l'explorateur)
     * dans la file d'attente et en tant que piste active sans relancer la lecture.
     */
    public void syncWithExternalTrack(AudioBrowserDialog.AudioTrackItem track) {
        if (track == null) return;
        currentTrack = track;
        int found = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == track.id ||
                (queue.get(i).contentUri != null && queue.get(i).contentUri.equals(track.contentUri)) ||
                (queue.get(i).title != null && queue.get(i).title.equals(track.title))) {
                found = i;
                break;
            }
        }
        if (found != -1) {
            currentIndex = found;
        } else {
            queue.add(0, track);
            currentIndex = 0;
            rebuildShuffleIndices();
            notifyQueueChanged();
        }
        notifyTrackChanged(currentTrack);
    }

    /**
     * Ajoute un morceau à la fin de la file d'attente
     */
    public void addToQueue(AudioBrowserDialog.AudioTrackItem track) {
        if (track == null) return;
        queue.add(track);
        rebuildShuffleIndices();
        notifyQueueChanged();
        if (currentTrack == null) {
            currentIndex = 0;
            currentTrack = track;
            notifyTrackChanged(currentTrack);
        }
    }

    public void removeFromQueue(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            if (index == currentIndex) {
                if (queue.isEmpty()) {
                    stop();
                    currentIndex = -1;
                    currentTrack = null;
                    notifyTrackChanged(null);
                } else {
                    if (currentIndex >= queue.size()) {
                        currentIndex = 0;
                    }
                    playTrackAtIndex(currentIndex, isPlaying);
                }
            } else if (index < currentIndex) {
                currentIndex--;
            }
            rebuildShuffleIndices();
            notifyQueueChanged();
        }
    }

    public void clearQueue() {
        queue.clear();
        shuffleIndices.clear();
        currentIndex = -1;
        stop();
        currentTrack = null;
        notifyTrackChanged(null);
        notifyQueueChanged();
    }

    public void playTrackAtIndex(int index, boolean autoPlay) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            loadAndPlayTrack(queue.get(index), autoPlay);
            notifyQueueChanged();
        }
    }

    private void rebuildShuffleIndices() {
        shuffleIndices.clear();
        for (int i = 0; i < queue.size(); i++) {
            shuffleIndices.add(i);
        }
        if (shuffleEnabled) {
            Collections.shuffle(shuffleIndices, new Random());
        }
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        notifyPlaybackModesChanged();
        updateNotification();
    }

    public void cycleRepeatMode() {
        int next = (repeatMode + 1) % 3;
        setRepeatMode(next);
    }

    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
        rebuildShuffleIndices();
        notifyPlaybackModesChanged();
        updateNotification();
    }

    public void toggleShuffle() {
        setShuffleEnabled(!shuffleEnabled);
    }

    public void setPlaybackSpeed(float speed, float pitch) {
        this.playbackSpeed = Math.max(0.25f, Math.min(3.0f, speed));
        this.playbackPitch = Math.max(0.25f, Math.min(2.0f, pitch));
        applySpeedAndPitchToPlayer();
        notifySpeedChanged();
    }

    private void applySpeedAndPitchToPlayer() {
        if (player != null && isPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = player.getPlaybackParams();
                if (params == null) params = new PlaybackParams();
                params.setSpeed(playbackSpeed);
                params.setPitch(playbackPitch);
                player.setPlaybackParams(params);
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply PlaybackParams", e);
            }
        }
    }

    public synchronized void loadAndPlayTrack(AudioBrowserDialog.AudioTrackItem track, boolean autoPlay) {
        if (track == null) return;
        this.currentTrack = track;
        notifyTrackChanged(track);

        new Thread(() -> {
            try {
                releasePlayer();

                MediaPlayer newPlayer = new MediaPlayer();
                newPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());

                boolean sourceSet = false;
                if (track.dataPath != null && !track.dataPath.isEmpty() && new File(track.dataPath).exists()) {
                    try {
                        newPlayer.setDataSource(track.dataPath);
                        sourceSet = true;
                    } catch (Exception ignored) {}
                }

                if (!sourceSet && track.contentUri != null) {
                    try {
                        newPlayer.setDataSource(context, track.contentUri);
                        sourceSet = true;
                    } catch (Exception ignored) {}
                }

                if (!sourceSet && track.contentUri != null) {
                    // Fallback vers le cache local uniquement si nécessaire
                    try {
                        String ext = ".mp3";
                        if (track.mimeType != null) {
                            if (track.mimeType.contains("wav")) ext = ".wav";
                            else if (track.mimeType.contains("m4a") || track.mimeType.contains("mp4")) ext = ".m4a";
                            else if (track.mimeType.contains("flac")) ext = ".flac";
                            else if (track.mimeType.contains("ogg")) ext = ".ogg";
                        }
                        currentCachedAudioFile = new File(context.getCacheDir(), "player_audio" + ext);
                        try (InputStream in = context.getContentResolver().openInputStream(track.contentUri);
                             OutputStream out = new FileOutputStream(currentCachedAudioFile)) {
                            byte[] b = new byte[32768];
                            int n;
                            while ((n = in.read(b)) != -1) out.write(b, 0, n);
                        }
                        newPlayer.setDataSource(currentCachedAudioFile.getAbsolutePath());
                        sourceSet = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed fallback cache copying", e);
                    }
                }

                if (!sourceSet) {
                    Log.e(TAG, "No valid audio source found for track: " + track.title);
                    return;
                }

                player = newPlayer;

                player.setOnPreparedListener(mp -> {
                    isPrepared = true;
                    currentDurationMs = mp.getDuration();
                    attachVisualizer();
                    applySpeedAndPitchToPlayer();
                    AudioEffectsManager.getInstance().attachToPlayer(mp);

                    if (autoPlay) {
                        play();
                    } else {
                        notifyPlaybackStateChanged(false);
                        updateNotification();
                    }
                });

                player.setOnCompletionListener(mp -> onTrackCompleted());

                player.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    isPrepared = false;
                    isPlaying = false;
                    notifyPlaybackStateChanged(false);
                    return true;
                });

                player.prepareAsync();

            } catch (Exception e) {
                Log.e(TAG, "Error loading track", e);
                isPrepared = false;
            }
        }).start();
    }

    public synchronized void play() {
        if (playbackConflictListener != null) {
            try {
                playbackConflictListener.onPlaybackConflict();
            } catch (Exception ignored) {}
        }
        if (player != null && isPrepared) {
            try {
                player.start();
                isPlaying = true;
                notifyPlaybackStateChanged(true);
                startForegroundPlaybackService();
                handler.removeCallbacks(progressRunnable);
                handler.post(progressRunnable);
            } catch (Exception e) {
                Log.e(TAG, "Error starting playback", e);
            }
        } else if (currentTrack != null) {
            loadAndPlayTrack(currentTrack, true);
        } else if (!queue.isEmpty()) {
            playTrackAtIndex(0, true);
        }
    }

    public synchronized void pause() {
        if (player != null && isPlaying) {
            try {
                player.pause();
                isPlaying = false;
                notifyPlaybackStateChanged(false);
                handler.removeCallbacks(progressRunnable);
                updateNotification();
            } catch (Exception e) {
                Log.e(TAG, "Error pausing playback", e);
            }
        }
    }

    public synchronized void togglePlayPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public synchronized void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {}
        }
        isPlaying = false;
        notifyPlaybackStateChanged(false);
        handler.removeCallbacks(progressRunnable);
        stopForegroundPlaybackService();
    }

    public synchronized void seekTo(int positionMs) {
        if (player != null && isPrepared) {
            try {
                player.seekTo(positionMs);
                for (PlaybackStateListener l : listeners) {
                    l.onProgressUpdated(positionMs, player.getDuration());
                }
                updateNotification();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void seekRelative(int offsetMs) {
        if (player != null && isPrepared) {
            int target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + offsetMs));
            seekTo(target);
        }
    }

    public synchronized void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == REPEAT_ONE) {
            seekTo(0);
            play();
            return;
        }

        int nextIndex;
        if (shuffleEnabled && !shuffleIndices.isEmpty()) {
            int currentShufflePos = shuffleIndices.indexOf(currentIndex);
            int nextShufflePos = (currentShufflePos + 1) % shuffleIndices.size();
            if (nextShufflePos == 0 && repeatMode == REPEAT_OFF) {
                pause();
                seekTo(0);
                return;
            }
            nextIndex = shuffleIndices.get(nextShufflePos);
        } else {
            nextIndex = currentIndex + 1;
            if (nextIndex >= queue.size()) {
                if (repeatMode == REPEAT_OFF) {
                    pause();
                    seekTo(0);
                    return;
                }
                nextIndex = 0;
            }
        }
        playTrackAtIndex(nextIndex, true);
    }

    public synchronized void previous() {
        if (queue.isEmpty()) return;
        if (player != null && isPrepared && player.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }

        int prevIndex;
        if (shuffleEnabled && !shuffleIndices.isEmpty()) {
            int currentShufflePos = shuffleIndices.indexOf(currentIndex);
            int prevShufflePos = (currentShufflePos - 1 + shuffleIndices.size()) % shuffleIndices.size();
            prevIndex = shuffleIndices.get(prevShufflePos);
        } else {
            prevIndex = currentIndex - 1;
            if (prevIndex < 0) {
                prevIndex = Math.max(0, queue.size() - 1);
            }
        }
        playTrackAtIndex(prevIndex, true);
    }

    private void onTrackCompleted() {
        if (repeatMode == REPEAT_ONE) {
            seekTo(0);
            play();
        } else {
            next();
        }
    }

    private void attachVisualizer() {
        try {
            if (visualizer != null) {
                visualizer.release();
                visualizer = null;
            }
            if (player == null) return;
            visualizer = new Visualizer(player.getAudioSessionId());
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    currentWaveform = waveform.clone();
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    currentFft = fft.clone();
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
        } catch (Throwable ignored) {}
    }

    private void releasePlayer() {
        if (visualizer != null) {
            try {
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
        isPrepared = false;
    }

    // Gestion du Service de Lecture en Arrière-Plan & Barre de notification
    private void startForegroundPlaybackService() {
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_UPDATE_NOTIFICATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MusicPlaybackService", e);
        }
    }

    private void updateNotification() {
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_UPDATE_NOTIFICATION);
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    private void stopForegroundPlaybackService() {
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_STOP);
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    private void notifyTrackChanged(AudioBrowserDialog.AudioTrackItem track) {
        handler.post(() -> {
            for (PlaybackStateListener l : listeners) {
                try {
                    l.onTrackChanged(track);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyPlaybackStateChanged(boolean isPlaying) {
        handler.post(() -> {
            for (PlaybackStateListener l : listeners) {
                try {
                    l.onPlaybackStateChanged(isPlaying);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyQueueChanged() {
        handler.post(() -> {
            List<AudioBrowserDialog.AudioTrackItem> copy = new ArrayList<>(queue);
            for (PlaybackStateListener l : listeners) {
                try {
                    l.onQueueChanged(copy, currentIndex);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyPlaybackModesChanged() {
        handler.post(() -> {
            for (PlaybackStateListener l : listeners) {
                try {
                    l.onPlaybackModesChanged(repeatMode, shuffleEnabled);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifySpeedChanged() {
        handler.post(() -> {
            for (PlaybackStateListener l : listeners) {
                try {
                    l.onSpeedChanged(playbackSpeed, playbackPitch);
                } catch (Exception ignored) {}
            }
        });
    }

    public static String formatDuration(int ms) {
        if (ms <= 0) return "0:00";
        int s = (ms / 1000) % 60;
        int m = (ms / (1000 * 60)) % 60;
        int h = ms / (1000 * 60 * 60);
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
