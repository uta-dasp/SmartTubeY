package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.app.Activity;
import android.app.Application;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public class PersonalSettingsLifecycleTest {
    @Test public void destroyingUnderlyingViewLeavesFinishOwnedByChildDialog() {
        int[] calls = {0};
        AppDialogPresenter.DialogModel model = model(() -> calls[0]++);
        PersonalSettingsFragment fragment = new PersonalSettingsFragment();
        ReflectionHelpers.setField(fragment, "inlineModel", model);
        ReflectionHelpers.setField(fragment, "awaitingDialogReturn", true);

        fragment.onDestroyView();
        assertEquals("The child has not finished or selected its final values yet", 0, calls[0]);
        assertNull(ReflectionHelpers.getField(fragment, "inlineModel"));
        model.finish(); // The callback already registered with the original child dialog.
        model.finish();
        assertEquals(1, calls[0]);
    }

    @Test public void destroyingInlineViewWithoutChildFinishesItsModelOnce() {
        int[] calls = {0};
        AppDialogPresenter.DialogModel model = model(() -> calls[0]++);
        PersonalSettingsFragment fragment = new PersonalSettingsFragment();
        ReflectionHelpers.setField(fragment, "inlineModel", model);

        fragment.onDestroyView();
        model.finish();
        assertEquals(1, calls[0]);
    }

    @Test public void savedAllOptionsFocusReturnsToButtonInsteadOfEarlierRow() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            Activity activity = controller.get();
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            TextView row = new TextView(activity);
            row.setText("Option");
            row.setFocusableInTouchMode(true);
            TextView allOptions = new TextView(activity);
            allOptions.setText("All options");
            allOptions.setFocusableInTouchMode(true);
            root.addView(row);
            root.addView(allOptions);
            activity.setContentView(root);
            PersonalSettingsFragment fragment = new PersonalSettingsFragment();
            List<android.view.View> rows = ReflectionHelpers.getField(fragment, "optionRows");
            rows.add(row);
            ReflectionHelpers.setField(fragment, "openButton", allOptions);
            ReflectionHelpers.setField(fragment, "focusedOption", -1);
            assertTrue(row.requestFocus());

            Method restore = PersonalSettingsFragment.class.getDeclaredMethod("focusOption");
            restore.setAccessible(true);
            restore.invoke(fragment);
            assertTrue(allOptions.isFocused());
            assertFalse(row.isFocused());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static AppDialogPresenter.DialogModel model(Runnable finish) {
        AppDialogPresenter presenter = new AppDialogPresenter(RuntimeEnvironment.getApplication()) {
            @Override public boolean isDialogShown() { return false; }
        };
        return presenter.captureDialog(() -> {
            presenter.appendSingleButton(UiOptionItem.from("Original option"));
            presenter.showDialog("Settings", finish);
        });
    }
}
