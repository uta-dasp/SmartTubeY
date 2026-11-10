package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

/** Foreground lifetime and original-session controls for the personal retained player. */
public class PersonalBackgroundPlaybackService extends Service {
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_TITLE = "title";
    static final String ACTION_PLAY = "smarttubey.background.PLAY";
    static final String ACTION_PAUSE = "smarttubey.background.PAUSE";
    static final String ACTION_STOP = "smarttubey.background.STOP";
    static final String CHANNEL_ID = "smarttubey_background_playback";
    static final int NOTIFICATION_ID = 0x535459;
    private MediaControllerCompat controller;
    private String title;
    private boolean foreground;
    private final MediaControllerCompat.Callback callback = new MediaControllerCompat.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackStateCompat state) { updateNotification(); }
        @Override public void onMetadataChanged(MediaMetadataCompat metadata) { updateNotification(); }
        @Override public void onSessionDestroyed() { stopSelf(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.category_background_playback), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Playback is never reconstructed or started after an OS service restart.
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRetainedPlayer();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action)) {
            if (controller != null) {
                if (ACTION_PLAY.equals(action)) controller.getTransportControls().play();
                else controller.getTransportControls().pause();
            } else stopSelf();
            return START_NOT_STICKY;
        }
        title = intent.getStringExtra(EXTRA_TITLE);
        MediaSessionCompat.Token token = intent.getParcelableExtra(EXTRA_SESSION);
        detachController();
        if (token != null) {
            try {
                controller = new MediaControllerCompat(this, token);
                controller.registerCallback(callback);
            } catch (RuntimeException e) {
                Log.e("PersonalBackgroundPlayback", "The original media session is unavailable");
            }
        }
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else startForeground(NOTIFICATION_ID, notification);
        foreground = true;
        return START_NOT_STICKY;
    }

    private void updateNotification() {
        if (foreground) ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, createNotification());
    }

    private Notification createNotification() {
        Intent open = new Intent(this, SplashActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title == null || title.isEmpty() ? getApplicationInfo().loadLabel(getPackageManager()) : title)
                .setContentText(getString(R.string.category_background_playback))
                .setContentIntent(PendingIntent.getActivity(this, 0, open, pendingFlags))
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true);
        if (controller != null) {
            MediaMetadataCompat metadata = controller.getMetadata();
            if (metadata != null && metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE) != null)
                builder.setContentTitle(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
            PlaybackStateCompat state = controller.getPlaybackState();
            boolean playing = state != null && (state.getState() == PlaybackStateCompat.STATE_PLAYING ||
                    state.getState() == PlaybackStateCompat.STATE_BUFFERING);
            builder.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    getString(playing ? androidx.leanback.R.string.lb_playback_controls_pause :
                            androidx.leanback.R.string.lb_playback_controls_play),
                    command(playing ? ACTION_PAUSE : ACTION_PLAY, 1, pendingFlags));
            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(controller.getSessionToken()).setShowActionsInCompactView(0));
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.personal_background_stop),
                command(ACTION_STOP, 2, pendingFlags));
        return builder.build();
    }

    private PendingIntent command(String action, int request, int flags) {
        return PendingIntent.getService(this, request,
                new Intent(this, PersonalBackgroundPlaybackService.class).setAction(action), flags);
    }

    protected void stopRetainedPlayer() {
        PlaybackView player = PlaybackPresenter.instance(getApplicationContext()).getView();
        if (player instanceof PlaybackFragment) ((PlaybackFragment) player).stopPersonalBackgroundPlayback();
    }

    private void detachController() {
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }

    @Override public void onDestroy() {
        foreground = false;
        detachController();
        stopForeground(true);
        super.onDestroy();
    }

    public static void start(Context context, MediaSessionCompat.Token token, String title) {
        if (!PersonalPlayerPresentation.isEnabled(context)) return;
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, PersonalBackgroundPlaybackService.class)
                .putExtra(EXTRA_SESSION, token).putExtra(EXTRA_TITLE, title);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
            else app.startService(intent);
        } catch (RuntimeException e) {
            Log.e("PersonalBackgroundPlayback", "Unable to start the background playback service");
        }
    }

    public static void stop(Context context) {
        if (PersonalPlayerPresentation.isEnabled(context)) context.getApplicationContext()
                .stopService(new Intent(context, PersonalBackgroundPlaybackService.class));
    }
}
