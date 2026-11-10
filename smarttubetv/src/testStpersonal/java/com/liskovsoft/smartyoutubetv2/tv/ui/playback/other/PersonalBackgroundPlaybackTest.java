package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowMediaController;
import org.robolectric.shadows.ShadowService;
import org.robolectric.util.ReflectionHelpers;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public class PersonalBackgroundPlaybackTest {
    private Application application;
    private ShadowApplication app;
    private MediaSessionCompat session;

    @Before public void setUp() {
        application = RuntimeEnvironment.getApplication();
        app = Shadows.shadowOf(application);
        assertTrue(PersonalPlayerPresentation.isEnabled(application));
        app.clearStartedServices();
        while (app.getNextStoppedService() != null) { }
        app.clearNextStartedActivities();
        session = new MediaSessionCompat(application, "personal-background-regression");
        session.setActive(true);
    }

    @After public void tearDown() {
        if (session != null) session.release();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void packagedManifestSeparatesPlayerTaskAndDeclaresForegroundMediaService() throws Exception {
        // Read the actual AGP packaged manifest rather than a duplicated test manifest.
        Properties config = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("com/android/tools/test_config.properties")) {
            assertNotNull("AGP unit-test configuration", input);
            config.load(input);
        }
        File manifest = new File(config.getProperty("android_merged_manifest"));
        assertTrue("Packaged manifest must exist", manifest.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(manifest);
        String ns = "http://schemas.android.com/apk/res/android";
        Element player = named(document, "activity", "com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity");
        assertEquals(document.getDocumentElement().getAttribute("package") + ".playback", player.getAttributeNS(ns, "taskAffinity"));
        assertEquals("singleInstance", player.getAttributeNS(ns, "launchMode"));
        Element service = named(document, "service", PersonalBackgroundPlaybackService.class.getName());
        assertEquals("false", service.getAttributeNS(ns, "exported"));
        assertEquals("false", service.getAttributeNS(ns, "stopWithTask"));
        assertEquals("mediaPlayback", service.getAttributeNS(ns, "foregroundServiceType"));
        assertNotNull(named(document, "uses-permission", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"));
    }

    @Test public void actualFragmentStartsOriginalSessionOnlyForReadyEngineAndCleansUpQuietly() {
        RecordingFragment fragment = new RecordingFragment(application);
        ReflectionHelpers.setField(fragment, "mMediaSession", session);
        fragment.blockEngine(true);
        assertTrue(fragment.isEngineBlocked());
        assertNull(app.getNextStartedService());
        assertNotNull(app.getNextStoppedService());

        fragment.ready = true;
        fragment.blockEngine(true);
        Intent start = app.getNextStartedService();
        assertEquals(new ComponentName(application, PersonalBackgroundPlaybackService.class), start.getComponent());
        assertEquals(session.getSessionToken(), start.getParcelableExtra(PersonalBackgroundPlaybackService.EXTRA_SESSION));
        fragment.blockEngine(false); // The existing onResume path calls this.
        assertFalse(fragment.isEngineBlocked());
        assertNotNull(app.getNextStoppedService());

        fragment.blockEngine(true);
        app.clearStartedServices();
        ReflectionHelpers.callInstanceMethod(fragment, "releasePlayer");
        assertNotNull("Actual release must also stop an already started service", app.getNextStoppedService());

        fragment.blockEngine(true);
        app.clearStartedServices();
        fragment.stopPersonalBackgroundPlayback();
        assertFalse(fragment.isEngineBlocked());
        assertNotNull(app.getNextStoppedService());
        assertNull("Notification Stop must not open Browse or another Activity", app.getNextStartedActivity());
    }

    @Test
    @Config(shadows = RecordingTransportControls.class)
    public void foregroundNotificationUsesOriginalSessionAndExplicitTransportCommands() {
        state(PlaybackStateCompat.STATE_PLAYING);
        ServiceController<RecordingService> owner = Robolectric.buildService(RecordingService.class).create();
        try {
            RecordingService service = owner.get();
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(startIntent(), 0, 1));
            MediaControllerCompat compat = ReflectionHelpers.getField(service, "controller");
            assertNotNull("Service must connect to the supplied original session", compat);
            assertNotNull(session.getSessionToken().getToken());
            assertEquals(session.getSessionToken(), compat.getSessionToken());
            MediaController nativeController = (MediaController) compat.getMediaController();
            ShadowMediaController controller = Shadows.shadowOf(nativeController);
            RecordingTransportControls transport = Shadow.extract(nativeController.getTransportControls());
            // Robolectric4.6.1 does not bridge live MediaSession metadata or transport IPC.
            // Deliver platform events through its supported controller shadow, and record
            // native transport calls without replacing the service or compat controller.
            controller.executeOnPlaybackStateChanged((PlaybackState) state(PlaybackStateCompat.STATE_PLAYING).getPlaybackState());
            controller.executeOnMetadataChanged(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Existing media title").build());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ShadowService shadow = Shadows.shadowOf(service);
            assertNotNull(shadow.getLastForegroundNotification());
            NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification notification = Shadows.shadowOf(manager).getNotification(PersonalBackgroundPlaybackService.NOTIFICATION_ID);
            assertNotNull(notification);
            assertTrue(notification.icon != 0);
            assertEquals(Notification.CATEGORY_TRANSPORT, notification.category);
            assertEquals("Existing media title", notification.extras.getCharSequence(Notification.EXTRA_TITLE));
            assertEquals("Notification must refer to the existing MediaSession", session.getSessionToken().getToken(),
                    notification.extras.getParcelable("android.mediaSession"));
            Intent open = Shadows.shadowOf(notification.contentIntent).getSavedIntent();
            assertEquals(SplashActivity.class.getName(), open.getComponent().getClassName());
            NotificationChannel channel = manager.getNotificationChannel(PersonalBackgroundPlaybackService.CHANNEL_ID);
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.getImportance());
            assertNull(channel.getSound());
            assertEquals(0, transport.plays);
            assertEquals(0, transport.pauses);

            assertEquals(PersonalBackgroundPlaybackService.ACTION_PAUSE,
                    Shadows.shadowOf(notification.actions[0].actionIntent).getSavedIntent().getAction());
            service.onStartCommand(new Intent().setAction(PersonalBackgroundPlaybackService.ACTION_PAUSE), 0, 2);
            assertEquals(1, transport.pauses);
            assertEquals(0, transport.plays);
            PlaybackStateCompat pausedState = state(PlaybackStateCompat.STATE_PAUSED);
            controller.executeOnPlaybackStateChanged((PlaybackState) pausedState.getPlaybackState());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Notification paused = Shadows.shadowOf(manager).getNotification(PersonalBackgroundPlaybackService.NOTIFICATION_ID);
            assertNotNull(paused);
            assertEquals(PersonalBackgroundPlaybackService.ACTION_PLAY,
                    Shadows.shadowOf(paused.actions[0].actionIntent).getSavedIntent().getAction());
            service.onStartCommand(new Intent().setAction(PersonalBackgroundPlaybackService.ACTION_PLAY), 0, 3);
            assertEquals(1, transport.plays);
            assertEquals(1, transport.pauses);
            PlaybackStateCompat playingState = state(PlaybackStateCompat.STATE_PLAYING);
            controller.executeOnPlaybackStateChanged((PlaybackState) playingState.getPlaybackState());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Notification playing = Shadows.shadowOf(manager).getNotification(PersonalBackgroundPlaybackService.NOTIFICATION_ID);
            assertEquals(PersonalBackgroundPlaybackService.ACTION_PAUSE,
                    Shadows.shadowOf(playing.actions[0].actionIntent).getSavedIntent().getAction());
        } finally { owner.destroy(); }
    }

    @Test public void stopAndNullRestartNeverRecreateOrResumePlayback() {
        ServiceController<RecordingService> owner = Robolectric.buildService(RecordingService.class).create();
        RecordingService service = owner.get();
        service.onStartCommand(startIntent(), 0, 1);
        assertEquals(Service.START_NOT_STICKY,
                service.onStartCommand(new Intent().setAction(PersonalBackgroundPlaybackService.ACTION_STOP), 0, 2));
        assertEquals(1, service.stops);
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
        owner.destroy();
        assertTrue(Shadows.shadowOf(service).isForegroundStopped());
        assertNull(app.getNextStartedActivity());

        ServiceController<RecordingService> restart = Robolectric.buildService(RecordingService.class).create();
        try {
            assertEquals(Service.START_NOT_STICKY, restart.get().onStartCommand(null, 0, 3));
            assertTrue(Shadows.shadowOf(restart.get()).isStoppedBySelf());
            assertNull(Shadows.shadowOf(restart.get()).getLastForegroundNotification());
            assertEquals(0, restart.get().stops);
            assertNull(app.getNextStartedActivity());
        } finally { restart.destroy(); }
    }

    private Intent startIntent() {
        return new Intent(application, PersonalBackgroundPlaybackService.class)
                .putExtra(PersonalBackgroundPlaybackService.EXTRA_SESSION, session.getSessionToken())
                .putExtra(PersonalBackgroundPlaybackService.EXTRA_TITLE, "Fallback title");
    }

    private PlaybackStateCompat state(int state) {
        PlaybackStateCompat value = new PlaybackStateCompat.Builder().setState(state, 0, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE).build();
        session.setPlaybackState(value);
        return value;
    }

    private static Element named(Document document, String tag, String name) {
        NodeList elements = document.getElementsByTagName(tag);
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (name.equals(element.getAttributeNS("http://schemas.android.com/apk/res/android", "name"))) return element;
        }
        fail("Missing " + tag + ": " + name);
        return null;
    }

    public static final class RecordingService extends PersonalBackgroundPlaybackService {
        int stops;
        @Override protected void stopRetainedPlayer() { stops++; }
    }

    @Implements(MediaController.TransportControls.class)
    public static final class RecordingTransportControls {
        int plays, pauses;
        @Implementation protected void play() { plays++; }
        @Implementation protected void pause() { pauses++; }
    }

    private static final class RecordingFragment extends PlaybackFragment {
        private final Context context;
        boolean ready;
        RecordingFragment(Context context) { this.context = context; }
        @Override public Context getContext() { return context; }
        @Override public boolean isEngineInitialized() { return ready; }
        @Override public Video getVideo() { return null; }
    }
}
