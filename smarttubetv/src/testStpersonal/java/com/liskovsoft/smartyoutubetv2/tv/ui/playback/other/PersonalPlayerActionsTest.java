package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.app.Application;
import android.content.Context;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.TwoStateAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public class PersonalPlayerActionsTest {
    private static final int ALL_BUTTONS = 0x0fffffff;
    private Context context;
    private PlayerTweaksData tweaks;
    private GeneralData general;
    private int savedButtons;
    private boolean savedLongClick, savedLongPressDisabled;
    private final RecordingListener listener = new RecordingListener();
    private final RecordingPlayer player = new RecordingPlayer();

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.Theme_Leanback);
        assertTrue("Run the stpersonal test variant", PersonalPlayerPresentation.isEnabled(context));
        tweaks = PlayerTweaksData.instance(context);
        general = GeneralData.instance(context);
        for (int bit = 1; bit <= (1 << 27); bit <<= 1) {
            if (tweaks.isPlayerButtonEnabled(bit)) savedButtons |= bit;
        }
        savedLongClick = tweaks.isButtonLongClickEnabled();
        savedLongPressDisabled = general.isOkButtonLongPressDisabled();
        tweaks.setPlayerButtonEnabled(ALL_BUTTONS);
    }

    @After public void restorePreferences() {
        if (tweaks != null) {
            tweaks.setPlayerButtonDisabled(ALL_BUTTONS);
            tweaks.setPlayerButtonEnabled(savedButtons);
            tweaks.setButtonLongClickEnabled(savedLongClick);
            general.setOkButtonLongPressDisabled(savedLongPressDisabled);
        }
    }

    @Test public void hiddenBarsStillExposeAllMappedActionsAndOriginalTransport() {
        tweaks.setPlayerButtonDisabled(ALL_BUTTONS);
        VideoPlayerGlue glue = createGlue();
        assertEquals(0, glue.getControlsRow().getPrimaryActionsAdapter().size());
        assertEquals(1, glue.getControlsRow().getSecondaryActionsAdapter().size()); // Settings
        List<UiOptionItem.DynamicDescription> actions = glue.createPersonalAdvancedActions();
        assertEquals(27, actions.size()); // 24 upstream mapped actions and 3 original transport actions
        Set<Long> ids = new HashSet<>();
        for (UiOptionItem item : actions) assertTrue(ids.add(((Action) item.getData()).getId()));
        int[] mapped = {R.id.lb_control_high_quality, R.id.lb_control_closed_captioning,
                R.id.action_video_speed, R.id.action_repeat, R.id.action_video_stats,
                R.id.action_content_block, R.id.action_playback_queue, R.id.action_playlist_add,
                R.id.action_afr, R.id.action_pip, R.id.action_chat, R.id.action_screen_dimming,
                R.id.action_seek_interval, R.id.action_video_zoom, R.id.action_rotate,
                R.id.action_flip, R.id.action_sound_off, R.id.action_share, R.id.action_search,
                R.id.action_channel, R.id.action_info, R.id.action_subscribe,
                R.id.action_thumbs_up, R.id.action_thumbs_down};
        for (int id : mapped) assertTrue("Missing action " + id, ids.contains((long) id));
        assertTrue(actions.get(0).getData() instanceof PlaybackControlsRow.SkipPreviousAction);
        assertTrue(actions.get(1).getData() instanceof PlaybackControlsRow.PlayPauseAction);
        assertTrue(actions.get(2).getData() instanceof PlaybackControlsRow.SkipNextAction);
        actions.get(0).onSelect(true);
        actions.get(1).onSelect(true);
        actions.get(1).onSelect(true);
        actions.get(2).onSelect(true);
        assertEquals(1, listener.previous);
        assertEquals(1, listener.next);
        assertEquals(1, player.plays);
        assertEquals(1, player.pauses);
    }

    @Test public void mappedCommandKeepsVisibleActionIdentityAndCurrentControllerState() {
        VideoPlayerGlue glue = createGlue();
        UiOptionItem item = find(glue.createPersonalAdvancedActions(), R.id.action_thumbs_up);
        Action original = (Action) item.getData();
        assertTrue(((androidx.leanback.widget.ArrayObjectAdapter)
                glue.getControlsRow().getSecondaryActionsAdapter()).indexOf(original) >= 0);
        glue.setButtonState(R.id.action_thumbs_up, TwoStateAction.INDEX_ON);
        item.onSelect(true);
        assertEquals(R.id.action_thumbs_up, listener.actionId);
        assertEquals(TwoStateAction.INDEX_ON, listener.actionState);
        assertSame(item, find(glue.createPersonalAdvancedActions(), R.id.action_thumbs_up));
        assertSame(original, find(glue.createPersonalAdvancedActions(), R.id.action_thumbs_up).getData());
    }

    @Test public void explicitOptionsRetainEveryLongCallbackWhenPhysicalLongPressIsDisabled() {
        tweaks.setButtonLongClickEnabled(false);
        general.setOkButtonLongPressDisabled(true);
        VideoPlayerGlue glue = createGlue();
        List<UiOptionItem.DynamicDescription> commands = glue.createPersonalAdvancedOptions();
        assertEquals(10, commands.size());
        Action chat = (Action) find(commands, R.id.action_chat).getData();
        assertFalse(glue.onActionLongClicked(chat));
        assertEquals(0, listener.longIds.size());
        for (UiOptionItem item : commands) item.onSelect(true);
        assertEquals(10, new HashSet<>(listener.longIds).size());
        for (UiOptionItem item : commands) {
            assertTrue(listener.longIds.contains((int) ((Action) item.getData()).getId()));
            assertSame(find(glue.createPersonalAdvancedActions(), (int) ((Action) item.getData()).getId()).getData(),
                    item.getData());
        }
    }

    @Test public void asynchronousBoundActionAndNextTitleUpdatesRefreshAdvancedModels() {
        VideoPlayerGlue glue = createGlue();
        List<UiOptionItem.DynamicDescription> actions = glue.createPersonalAdvancedActions();
        UiOptionItem.DynamicDescription like = find(actions, R.id.action_thumbs_up);
        UiOptionItem.DynamicDescription dislike = find(actions, R.id.action_thumbs_down);
        UiOptionItem.DynamicDescription next = actions.get(2);
        int[] refreshed = {0};
        dislike.addDescriptionListener(() -> refreshed[0]++);
        next.addDescriptionListener(() -> refreshed[0]++);
        glue.setButtonState(R.id.action_thumbs_down, TwoStateAction.INDEX_ON);
        glue.setButtonState(R.id.action_thumbs_up, TwoStateAction.INDEX_ON);
        assertEquals(context.getString(R.string.personal_player_on), like.getDescription());
        assertEquals(context.getString(R.string.personal_player_off), dislike.getDescription());
        glue.setNextTitle("Another queued video");
        assertEquals("Another queued video", next.getTitle());
        assertTrue(refreshed[0] >= 3);
    }

    private VideoPlayerGlue createGlue() {
        VideoPlayerGlue glue = new VideoPlayerGlue(context, player, listener);
        glue.setControlsRow(new PlaybackControlsRow(glue));
        return glue;
    }

    private static UiOptionItem.DynamicDescription find(List<UiOptionItem.DynamicDescription> items, int id) {
        for (UiOptionItem.DynamicDescription item : items) {
            if (((Action) item.getData()).getId() == id) return item;
        }
        throw new AssertionError("Missing action " + id);
    }

    private static final class RecordingPlayer extends PlayerAdapter {
        int plays, pauses;
        @Override public void play() { plays++; }
        @Override public void pause() { pauses++; }
    }

    private static final class RecordingListener implements VideoPlayerGlue.OnActionClickedListener {
        int previous, next, actionId, actionState;
        final List<Integer> longIds = new ArrayList<>();
        @Override public void onPrevious() { previous++; }
        @Override public void onNext() { next++; }
        @Override public void onPlay() { }
        @Override public void onPause() { }
        @Override public void onAction(int id, int state) { actionId = id; actionState = state; }
        @Override public void onLongAction(int id, int state) { longIds.add(id); }
        @Override public void onTopEdgeFocused() { }
        @Override public boolean onKeyDown(int code) { return false; }
    }
}
