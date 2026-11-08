package androidx.leanback.app;

import android.app.Application;

import androidx.fragment.app.FragmentActivity;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public class StandaloneRowsFragmentTest {
    @Test
    public void initializedAdapterCanAttachWithoutBrowseHost() {
        ActivityController<FragmentActivity> controller = Robolectric.buildActivity(FragmentActivity.class);
        controller.get().setTheme(R.style.Theme_Leanback);
        FragmentActivity activity = controller.setup().get();
        try {
            RowsSupportFragment fragment = new RowsSupportFragment();
            assertNull(fragment.getMainFragmentAdapter().getFragmentHost());
            activity.getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commitNow();
            assertNotNull(fragment.getView());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void rowSelectionCanRunWithoutBrowseHost() {
        RowsSupportFragment fragment = new RowsSupportFragment();
        assertNull(fragment.getMainFragmentAdapter().getFragmentHost());
        fragment.onRowSelected(null, null, 0, 0);
    }
}
