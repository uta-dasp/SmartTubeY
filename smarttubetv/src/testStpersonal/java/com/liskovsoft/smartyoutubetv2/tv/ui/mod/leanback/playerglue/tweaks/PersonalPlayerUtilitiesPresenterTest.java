package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.ControlButtonPresenterSelector;
import java.util.concurrent.TimeUnit;
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
public class PersonalPlayerUtilitiesPresenterTest {
    @Test public void allSixteenNamedButtonsScrollAndKeepHorizontalAndVerticalBoundaries() {
        try (Fixture f = new Fixture()) {
            assertEquals(16, f.holder.buttons.size());
            assertEquals("Utility 0", f.holder.labels.get(0).getText());
            assertTrue(f.presenter.focusLast(f.holder));
            key(f.button(0), KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue(f.button(0).isFocused());
            for (int index = 0; index < 15; index++) key(f.button(index), KeyEvent.KEYCODE_DPAD_RIGHT);
            assertTrue(f.button(15).isFocused());
            assertTrue(f.holder.scroll.getScrollX() > 0);
            key(f.button(15), KeyEvent.KEYCODE_DPAD_RIGHT);
            assertTrue(f.button(15).isFocused());
            key(f.button(15), KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue(f.progress.isFocused());
            assertTrue(f.presenter.focusLast(f.holder));
            assertTrue(f.button(15).isFocused());
            key(f.button(15), KeyEvent.KEYCODE_DPAD_UP);
            assertEquals(1, f.closed[0]);
        }
    }

    @Test public void actualButtonClickAndHeldOkForwardOriginalObjectsWithoutExtraShortClick() {
        try (Fixture f = new Fixture()) {
            int[] clicks = {0}, longs = {0};
            Object[] item = {null};
            f.presenter.onClicked = (holder, action, data) -> { clicks[0]++; item[0] = action; };
            f.presenter.onLongClicked = (holder, action, data) -> { longs[0]++; item[0] = action; return true; };
            View button = f.button(0);
            assertTrue(button.requestFocus());
            key(button, KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals(1, clicks[0]);
            assertSame(f.adapter.get(0), item[0]);
            assertTrue(button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(ViewConfiguration.getLongPressTimeout() + 50, TimeUnit.MILLISECONDS);
            assertTrue(button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, longs[0]);
            assertEquals(1, clicks[0]);
            assertSame(f.adapter.get(0), item[0]);
        }
    }

    @Test public void stateRefreshRetainsFocusedViewAndScrollAndReplacementDetachesOldAdapter() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.button(15).requestFocus());
            f.holder.scroll.scrollTo(400, 0);
            View focused = f.button(15);
            int scroll = f.holder.scroll.getScrollX();
            Action action = (Action) f.adapter.get(15);
            action.setLabel1("Updated utility");
            f.adapter.notifyArrayItemRangeChanged(15, 1);
            assertSame(focused, f.button(15));
            assertTrue(focused.isFocused());
            assertEquals(scroll, f.holder.scroll.getScrollX());
            assertEquals("Updated utility", f.holder.labels.get(15).getText());
            assertTrue(f.adapter.hasObserver());
            ArrayObjectAdapter replacement = new ArrayObjectAdapter();
            replacement.add(new Action(99, "Replacement"));
            f.data.adapter = replacement; // Even a reused BoundData must unregister the cached old adapter.
            f.presenter.onBindViewHolder(f.holder, f.data);
            assertFalse(f.adapter.hasObserver());
            assertTrue(replacement.hasObserver());
            assertFalse(focused.performClick());
            f.adapter.notifyArrayItemRangeChanged(15, 1);
            assertEquals(1, f.holder.buttons.size());
            f.presenter.onUnbindViewHolder(f.holder);
            assertFalse(replacement.hasObserver());
            assertEquals(0, f.holder.buttons.size());
        }
    }

    @Test public void actualRowRoutesSeekToUtilitiesAndKeepsCoreGeometryForSeekAndEmptyStates() {
        try (Fixture f = new Fixture()) {
            Activity activity = f.controller.get();
            FrameLayout root = new FrameLayout(activity); activity.setContentView(root);
            PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter();
            rowPresenter.setPersonalUtilityActions(f.adapter);
            rowPresenter.setDescriptionPresenter(new Presenter() {
                @Override public ViewHolder onCreateViewHolder(android.view.ViewGroup parent) { return new ViewHolder(new TextView(parent.getContext())); }
                @Override public void onBindViewHolder(ViewHolder holder, Object item) { }
                @Override public void onUnbindViewHolder(ViewHolder holder) { }
            });
            Presenter.ViewHolder created = rowPresenter.onCreateViewHolder(root);
            PlaybackTransportRowPresenter.ViewHolder row = (PlaybackTransportRowPresenter.ViewHolder) rowPresenter.getRowViewHolder(created);
            root.addView(created.view);
            ControlButtonPresenterSelector selector = new ControlButtonPresenterSelector(false);
            ArrayObjectAdapter primary = new ArrayObjectAdapter(selector);
            primary.add(new PlaybackControlsRow.PlayPauseAction(activity));
            PlaybackControlsRow model = new PlaybackControlsRow(new Object());
            model.setPrimaryActionsAdapter(primary);
            model.setSecondaryActionsAdapter(new ArrayObjectAdapter(selector));
            model.setDuration(100_000); model.setCurrentPosition(0);
            rowPresenter.onBindViewHolder(created, model);
            layout(created.view);
            PlaybackTransportRowView transport = created.view.findViewById(R.id.transport_row);
            int transportTop = transport.getTop();
            int progressTop = row.mProgressBar.getTop();
            assertEquals(View.VISIBLE, row.mUtilitiesDock.getVisibility());
            assertSame(row.mTopEdge, transport.focusSearch(row.mProgressBar, View.FOCUS_UP));
            assertTrue(row.mTopEdge.requestFocus());
            assertTrue(row.mUtilitiesDock.hasFocus());
            View utilityFocus = created.view.findFocus();
            rowPresenter.onReappear(row);
            assertSame(utilityFocus, created.view.findFocus());
            key(utilityFocus, KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue(row.mProgressBar.isFocused());
            row.setControlsMode(1); // The source compact seek/storyboard presentation.
            row.mInSeek = true;
            rowPresenter.onBindViewHolder(created, model); // Metadata must not reveal utilities during seeking.
            layout(created.view);
            assertEquals(View.GONE, row.mUtilitiesDock.getVisibility());
            assertEquals(transportTop, transport.getTop());
            assertEquals(progressTop, row.mProgressBar.getTop());
            row.mInSeek = false;
            row.setControlsMode(0);
            assertEquals(View.VISIBLE, row.mUtilitiesDock.getVisibility());
            rowPresenter.setPersonalUtilityActions(new ArrayObjectAdapter());
            rowPresenter.onBindViewHolder(created, model);
            layout(created.view);
            assertEquals(View.GONE, row.mUtilitiesDock.getVisibility());
            assertEquals(transportTop, transport.getTop());
            assertEquals(progressTop, row.mProgressBar.getTop());
            assertTrue(transport.requestPersonalDefaultFocus(View.FOCUS_FORWARD, null));
            assertTrue(row.mControlsDock.hasFocus());
            rowPresenter.onUnbindViewHolder(created);
        }
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(748, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 748);
    }

    private static void key(View view, int code) {
        assertTrue(view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code)));
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        final PersonalPlayerUtilitiesPresenter presenter = new PersonalPlayerUtilitiesPresenter();
        final ArrayObjectAdapter adapter = new ArrayObjectAdapter();
        final ControlBarPresenter.BoundData data = new ControlBarPresenter.BoundData();
        final PersonalPlayerUtilitiesPresenter.ViewHolder holder;
        final View progress;
        final int[] closed = {0};

        Fixture() {
            controller.get().setTheme(R.style.Theme_Leanback);
            Activity activity = controller.setup().get();
            FrameLayout root = new FrameLayout(activity);
            activity.setContentView(root);
            holder = (PersonalPlayerUtilitiesPresenter.ViewHolder) presenter.onCreateViewHolder(root);
            root.addView(holder.view, new FrameLayout.LayoutParams(320, 136));
            progress = new View(activity); progress.setFocusableInTouchMode(true);
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(320, 20); progressParams.topMargin = 180;
            root.addView(progress, progressParams);
            for (int index = 0; index < 16; index++) {
                Action action = new Action(index + 1000, "Utility " + index);
                action.setIcon(ContextCompat.getDrawable(activity, R.drawable.action_search));
                adapter.add(action);
            }
            data.adapter = adapter;
            data.presenter = new ControlButtonPresenterSelector(false).getSecondaryPresenter();
            presenter.onBindViewHolder(holder, data);
            holder.downTarget = progress; holder.onUp = () -> closed[0]++;
            controller.visible();
            holder.scroll.setSmoothScrollingEnabled(false);
            holder.view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(136, View.MeasureSpec.EXACTLY));
            holder.view.layout(0, 0, 320, 136);
        }

        View button(int index) { return holder.buttons.get(index).view.findViewById(androidx.leanback.R.id.button); }
        @Override public void close() { presenter.onUnbindViewHolder(holder); controller.pause().stop().destroy(); }
    }
}
