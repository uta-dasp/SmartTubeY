package com.liskovsoft.smartyoutubetv2.tv.ui.personal.shorts;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public class PersonalShortsPlaybackTest {
    @Test public void feedOkChangesExistingEngineExactlyOnceBeforeNotifying() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            RecordingPlayback fragment = new RecordingPlayback();
            fragment.ready = true;
            Activity activity = controller.get();
            View feed = fragment.createFeed(activity);
            activity.setContentView(feed);
            assertTrue(feed.requestFocus());

            pressOk(feed);
            assertFalse(fragment.playWhenReady);
            assertEquals(Arrays.asList("engine:false", "notify:false"), fragment.calls);
            pressOk(feed);
            assertTrue(fragment.playWhenReady);
            assertEquals(Arrays.asList("engine:false", "notify:false", "engine:true", "notify:true"), fragment.calls);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test public void feedOkBeforeEngineReadyDoesNotToggleOrNotify() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            RecordingPlayback fragment = new RecordingPlayback();
            Activity activity = controller.get();
            View feed = fragment.createFeed(activity);
            activity.setContentView(feed);
            assertTrue(feed.requestFocus());

            pressOk(feed);
            assertTrue(fragment.playWhenReady);
            assertTrue(fragment.calls.isEmpty());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static void pressOk(View feed) {
        // Exercise the real focused feed's key-to-click path. The plain host Activity's
        // Robolectric window does not route dispatchKeyEvent into its content hierarchy.
        assertTrue(feed.hasFocus());
        assertTrue(feed.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
        assertTrue(feed.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class RecordingPlayback extends PersonalPlaybackFragment {
        boolean ready, playWhenReady = true;
        final List<String> calls = new ArrayList<>();
        @Override public boolean isEngineInitialized() { return ready; }
        @Override public boolean getPlayWhenReady() { return playWhenReady; }
        @Override public void setPlayWhenReady(boolean play) { playWhenReady = play; calls.add("engine:" + play); }
        @Override protected void notifyFeedPlaybackClick(boolean play) { calls.add("notify:" + play); }
    }
}
