package com.kidas.studiopro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.io.File;
import java.util.List;

/**
 * Service d'arrière-plan et Barre de Notification Interactive Studio Pro :
 * - Notification multimédia persistante avec style MediaStyle
 * - Contrôles natifs : Lecture / Pause, Suivant, Précédent, Fermer / Stop
 * - Barre de progression et métadonnées complètes (Titre, Artiste, Pochette)
 * - Intégration MediaSessionCompat pour l'écran de verrouillage, casques Bluetooth et Android Auto
 */
public class MusicPlaybackService extends Service implements MusicPlayerManager.PlaybackStateListener {

    private static final String TAG = "MusicPlaybackService";

    public static final String CHANNEL_ID = "studiopro_music_playback_channel";
    public static final int NOTIFICATION_ID = 4040;

    public static final String ACTION_PLAY = "com.kidas.studiopro.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.kidas.studiopro.ACTION_PAUSE";
    public static final String ACTION_TOGGLE = "com.kidas.studiopro.ACTION_TOGGLE";
    public static final String ACTION_PREV = "com.kidas.studiopro.ACTION_PREV";
    public static final String ACTION_NEXT = "com.kidas.studiopro.ACTION_NEXT";
    public static final String ACTION_STOP = "com.kidas.studiopro.ACTION_STOP";
    public static final String ACTION_SEEK_TO = "com.kidas.studiopro.ACTION_SEEK_TO";
    public static final String ACTION_UPDATE_NOTIFICATION = "com.kidas.studiopro.ACTION_UPDATE_NOTIFICATION";
    public static final String EXTRA_SEEK_POSITION = "extra_seek_pos";

    private final IBinder binder = new LocalBinder();
    private MusicPlayerManager playerManager;
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    private Bitmap defaultAlbumArt;

    public class LocalBinder extends Binder {
        public MusicPlaybackService getService() {
            return MusicPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        playerManager = MusicPlayerManager.getInstance(this);
        playerManager.addListener(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel();
        initMediaSession();
        defaultAlbumArt = createStudioProLogoBitmap(this, 256);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lecteur de Musique Studio Pro",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Contrôles de lecture audio et barre de notification");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "StudioProMediaSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playerManager.play();
            }

            @Override
            public void onPause() {
                playerManager.pause();
            }

            @Override
            public void onSkipToNext() {
                playerManager.next();
            }

            @Override
            public void onSkipToPrevious() {
                playerManager.previous();
            }

            @Override
            public void onStop() {
                playerManager.stop();
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                playerManager.seekTo((int) pos);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY:
                    playerManager.play();
                    break;
                case ACTION_PAUSE:
                    playerManager.pause();
                    break;
                case ACTION_TOGGLE:
                    playerManager.togglePlayPause();
                    break;
                case ACTION_PREV:
                    playerManager.previous();
                    break;
                case ACTION_NEXT:
                    playerManager.next();
                    break;
                case ACTION_STOP:
                    playerManager.stop();
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                case ACTION_SEEK_TO:
                    int pos = intent.getIntExtra(EXTRA_SEEK_POSITION, 0);
                    playerManager.seekTo(pos);
                    break;
                case ACTION_UPDATE_NOTIFICATION:
                    buildAndPostNotification();
                    break;
            }
        } else {
            buildAndPostNotification();
        }
        return START_NOT_STICKY;
    }

    private void updateMediaSessionMetadata(AudioBrowserDialog.AudioTrackItem track, Bitmap artwork) {
        if (mediaSession == null || track == null) return;
        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs);

        if (artwork != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        }
        mediaSession.setMetadata(metaBuilder.build());

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO;

        int state = playerManager.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, playerManager.getCurrentPosition(), playerManager.getPlaybackSpeed());
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void buildAndPostNotification() {
        AudioBrowserDialog.AudioTrackItem track = playerManager.getCurrentTrack();
        boolean isPlaying = playerManager.isPlaying();

        String title = (track != null) ? track.title : "Studio Pro";
        String artist = (track != null) ? track.artist : "Lecteur de Musique";
        Bitmap artwork = (track != null) ? extractArtwork(track) : defaultAlbumArt;
        if (artwork == null) artwork = defaultAlbumArt;

        updateMediaSessionMetadata(track, artwork);

        // Intent pour rouvrir MainActivity au clic et ouvrir le lecteur musical
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction("OPEN_MUSIC_PLAYER");
        openIntent.putExtra("open_music_player", true);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Actions de notification
        PendingIntent prevPendingIntent = buildActionPendingIntent(ACTION_PREV, 1);
        PendingIntent togglePendingIntent = buildActionPendingIntent(ACTION_TOGGLE, 2);
        PendingIntent nextPendingIntent = buildActionPendingIntent(ACTION_NEXT, 3);
        PendingIntent stopPendingIntent = buildActionPendingIntent(ACTION_STOP, 4);

        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        String playPauseTitle = isPlaying ? "Pause" : "Lecture";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setLargeIcon(artwork)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText("Studio Pro Lecteur")
                .setContentIntent(contentPendingIntent)
                .setDeleteIntent(stopPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(R.drawable.ic_skip_back, "Précédent", prevPendingIntent)
                .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
                .addAction(R.drawable.ic_skip_forward, "Suivant", nextPendingIntent)
                .addAction(R.drawable.ic_stop, "Arrêter", stopPendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession != null ? mediaSession.getSessionToken() : null)
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopPendingIntent));

        Notification notification = builder.build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int serviceType = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
                }
                startForeground(NOTIFICATION_ID, notification, serviceType);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground notification", e);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private PendingIntent buildActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
    }

    private Bitmap extractArtwork(AudioBrowserDialog.AudioTrackItem track) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            if (track.dataPath != null && !track.dataPath.isEmpty() && new File(track.dataPath).exists()) {
                mmr.setDataSource(track.dataPath);
            } else if (track.contentUri != null) {
                mmr.setDataSource(this, track.contentUri);
            } else {
                return null;
            }
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            if (art != null && art.length > 0) {
                Bitmap bmp = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (bmp != null) {
                    return Bitmap.createScaledBitmap(bmp, 256, 256, true);
                }
            }
        } catch (Exception ignored) {}
        return createStudioProLogoBitmap(this, 256);
    }

    public static Bitmap createStudioProLogoBitmap(Context context, int size) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Fond sombre #0A0A0C avec coins arrondis
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(0xFF0A0A0C);
            RectF rect = new RectF(0, 0, size, size);
            float radius = size * 0.22f;
            canvas.drawRoundRect(rect, radius, radius, bgPaint);

            // Halo d'ambiance radial violet / cyan
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setShader(new RadialGradient(size * 0.5f, size * 0.35f, size * 0.55f,
                    new int[]{0x44A855F7, 0x2222D3EE, 0x00000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, glowPaint);

            // Bordure glassmorphic fine
            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(2f, size * 0.015f));
            strokePaint.setColor(0x33FFFFFF);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);

            // Dessin du logo officiel Studio Pro en premier plan
            Drawable fg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground);
            if (fg != null) {
                fg.setBounds(0, 0, size, size);
                fg.draw(canvas);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onTrackChanged(AudioBrowserDialog.AudioTrackItem track) {
        buildAndPostNotification();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        buildAndPostNotification();
    }

    @Override
    public void onProgressUpdated(int currentPositionMs, int durationMs) {
        // Met à jour la position dans la MediaSession pour la seekbar du système
        if (mediaSession != null && playerManager != null) {
            int state = playerManager.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            long actions = PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_SEEK_TO;

            PlaybackStateCompat stateCompat = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, currentPositionMs, playerManager.getPlaybackSpeed())
                    .build();
            mediaSession.setPlaybackState(stateCompat);
        }
    }

    @Override
    public void onQueueChanged(List<AudioBrowserDialog.AudioTrackItem> queue, int currentIndex) {
        // Queue changée
    }

    @Override
    public void onPlaybackModesChanged(int repeatMode, boolean shuffleEnabled) {
        // Modes changés
    }

    @Override
    public void onSpeedChanged(float speed, float pitch) {
        // Vitesse changée
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (playerManager != null) {
            playerManager.removeListener(this);
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
