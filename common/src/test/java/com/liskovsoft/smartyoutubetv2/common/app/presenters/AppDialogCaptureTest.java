package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Capture must preserve real option behavior without consuming other pending dialogs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppDialogCaptureTest {
    private static final class CapturePresenter extends AppDialogPresenter {
        boolean dialogShown;
        CapturePresenter(Context context) { super(context); }
        @Override public boolean isDialogShown() { return dialogShown; }
    }

    @Test public void captureRetainsOriginalOptionsAndDefersCallbacksUntilPresentation() {
        CapturePresenter presenter = new CapturePresenter(RuntimeEnvironment.getApplication());
        int[] calls = new int[3];
        OptionItem item = UiOptionItem.from("Original switch", option -> calls[0]++, false);
        OptionCategory category = OptionCategory.singleSwitch(item);
        AppDialogPresenter.DialogModel model = presenter.captureDialog(() -> {
            presenter.appendCategory(category);
            presenter.setOnStart(() -> calls[1]++);
            presenter.showDialog("Original title", () -> calls[2]++);
        });
        assertNotNull(model);
        assertEquals("Original title", model.title);
        assertSame(category, model.categories.get(0));
        assertSame(item, model.categories.get(0).options.get(0));
        assertArrayEquals(new int[]{0, 0, 0}, calls);
        assertTrue(presenter.isEmpty());
        model.start(); model.start();
        item.onSelect(true);
        assertTrue(item.isSelected());
        model.finish(); model.finish(); model.start();
        assertArrayEquals(new int[]{1, 1, 1}, calls);
    }

    @Test public void captureRestoresPendingDataAndFinishCallbacks() {
        CapturePresenter presenter = new CapturePresenter(RuntimeEnvironment.getApplication());
        int[] calls = new int[2];
        presenter.appendRadioCategory("Pending", Collections.singletonList(UiOptionItem.from("Keep me")));
        presenter.setId(41);
        presenter.setCloseTimeoutMs(10_000);
        presenter.enableOverlay(true);
        presenter.setOnFinish(() -> calls[0]++);
        AppDialogPresenter.DialogModel model = presenter.captureDialog(() -> {
            presenter.appendSingleButton(UiOptionItem.from("Captured"));
            presenter.showDialog("Settings", () -> calls[1]++);
        });
        assertNotNull(model);
        assertEquals(1, model.categories.size());
        assertEquals("Captured", model.categories.get(0).options.get(0).getTitle());
        assertTrue(model.supportsInlineSettings());
        assertFalse(presenter.isEmpty());
        assertEquals(41, presenter.getId());
        model.finish();
        assertArrayEquals(new int[]{0, 1}, calls);
        presenter.onFinish();
        assertArrayEquals(new int[]{1, 1}, calls);
    }

    @Test public void throwingActionCannotLeakItsPendingModelOrCaptureState() {
        CapturePresenter presenter = new CapturePresenter(RuntimeEnvironment.getApplication());
        presenter.setId(41);
        try {
            presenter.captureDialog(() -> {
                presenter.setId(17);
                presenter.appendSingleButton(UiOptionItem.from("Discard"));
                throw new IllegalStateException("Expected test failure");
            });
            fail("Action exception must propagate");
        } catch (IllegalStateException expected) {
            assertEquals("Expected test failure", expected.getMessage());
        }
        assertTrue(presenter.isEmpty());
        assertEquals(41, presenter.getId());
        AppDialogPresenter.DialogModel next = presenter.captureDialog(() -> {
            presenter.appendSingleButton(UiOptionItem.from("Next"));
            presenter.showDialog();
        });
        assertNotNull(next);
        assertEquals(1, next.categories.size());
        assertEquals("Next", next.categories.get(0).options.get(0).getTitle());
    }

    @Test public void activeDialogPreventsCaptureAndSpecialPresentationKeepsFallback() {
        CapturePresenter presenter = new CapturePresenter(RuntimeEnvironment.getApplication());
        presenter.dialogShown = true;
        int[] calls = new int[1];
        assertNull(presenter.captureDialog(() -> calls[0]++));
        assertEquals(0, calls[0]);
        presenter.dialogShown = false;
        AppDialogPresenter.DialogModel special = presenter.captureDialog(() -> {
            presenter.appendSingleButton(UiOptionItem.from("Special"));
            presenter.enableOverlay(true);
            presenter.showDialog();
        });
        assertNotNull(special);
        assertFalse(special.supportsInlineSettings());
    }
}
