package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.PersonalPlayerPresentation;

public class AppDialogActivity extends MotherActivity {
    private static final String TAG = AppDialogActivity.class.getSimpleName();
    private AppDialogFragment mFragment;
    private GlobalKeyTranslator mGlobalKeyTranslator;
    private PlaybackFragment mPersonalPlayerHost;
    private ViewGroup.LayoutParams mPersonalOriginalLayout;
    private Drawable mPersonalOriginalBackground;
    private float mPersonalOriginalDimAmount;
    private int mPersonalOriginalDimFlag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_app_settings);
        // Can't use getSupportFragmentManager because AppDialogFragment isn't subclass of androidx fragment
        mFragment = (AppDialogFragment) getFragmentManager().findFragmentById(R.id.app_settings_fragment);

        mGlobalKeyTranslator = new PlayerKeyTranslator(this);
        mGlobalKeyTranslator.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePersonalPlayerDialog();
    }

    @Override
    protected void onStop() {
        endPersonalPlayerDialog();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        endPersonalPlayerDialog();
        super.onDestroy();
    }

    private void beginPersonalPlayerDialog() {
        if (mPersonalPlayerHost != null) {
            return;
        }
        // A retained/background player must not resize behind browsing settings or notifications.
        if (!PersonalPlayerPresentation.isEnabled(this) || this instanceof AppDialogActivityOpaque
                || mFragment == null || mFragment.isOverlay() || mFragment.isTransparent()
                || PlayerTweaksData.instance(this).isDontResizeVideoToFitDialogEnabled()
                || com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(this).isComments()
                || ViewManager.instance(this).getTopView() != PlaybackView.class) {
            return;
        }
        PlaybackView playback = PlaybackPresenter.instance(this).getView();
        if (!(playback instanceof PlaybackFragment)) {
            return;
        }
        PlaybackFragment host = (PlaybackFragment) playback;
        Activity activity = host.getActivity();
        if (activity == null || activity.isFinishing() || host.getView() == null
                || (VERSION.SDK_INT >= 26 && activity.isInPictureInPictureMode())) {
            return;
        }
        mPersonalPlayerHost = host;
        host.setPlayerDialogVisible(this, true);
        View panel = mFragment.getView();
        if (panel == null) {
            return;
        }
        mPersonalOriginalLayout = panel.getLayoutParams();
        mPersonalOriginalBackground = getWindow().getDecorView().getBackground();
        mPersonalOriginalDimAmount = getWindow().getAttributes().dimAmount;
        mPersonalOriginalDimFlag = getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().getDecorView().post(() -> {
            if (mPersonalPlayerHost == null || mFragment.getView() != panel) {
                return;
            }
            int width = getWindow().getDecorView().getWidth();
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    Math.round(width * 0.31875f), ViewGroup.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.rightMargin = Math.round(width * 0.028125f);
            panel.setLayoutParams(params);
        });
    }

    private void endPersonalPlayerDialog() {
        if (mPersonalPlayerHost != null) {
            mPersonalPlayerHost.setPlayerDialogVisible(this, false);
            mPersonalPlayerHost = null;
        }
        if (mPersonalOriginalLayout != null) {
            View panel = mFragment != null ? mFragment.getView() : null;
            if (panel != null) {
                panel.setLayoutParams(mPersonalOriginalLayout);
            }
            getWindow().setBackgroundDrawable(mPersonalOriginalBackground);
            getWindow().setFlags(mPersonalOriginalDimFlag, WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            getWindow().setDimAmount(mPersonalOriginalDimAmount);
            mPersonalOriginalLayout = null;
        }
    }

    void updatePersonalPlayerDialog() {
        if (mFragment != null && (mFragment.isOverlay() || mFragment.isTransparent()
                || com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(this).isComments())) {
            endPersonalPlayerDialog();
        } else {
            beginPersonalPlayerDialog();
        }
    }

    @Override
    protected void initTheme() {
        int settingsThemeResId = MainUIData.instance(this).getColorScheme().settingsThemeResId;
        if (settingsThemeResId > 0) {
            setTheme(settingsThemeResId);
        }
    }

    private void setupActivity() {
        // Fix crash in AppSettingsActivity: "Only fullscreen opaque activities can request orientation"
        // Error happen only on Android O (api 26) when you set "portrait" orientation in manifest
        // So, to fix the problem, set orientation here instead of manifest
        // More info: https://stackoverflow.com/questions/48072438/java-lang-illegalstateexception-only-fullscreen-opaque-activities-can-request-o
        if (VERSION.SDK_INT != 26) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //if (KeyHelpers.isConfirmKey(event.getKeyCode()) && !event.isLongPress()) {
        //    return true;
        //}

        //return mGlobalKeyTranslator.translate(event) || super.dispatchKeyEvent(event);
        KeyEvent newEvent = mGlobalKeyTranslator.translate(event);
        return handleNavigation(newEvent) || super.dispatchKeyEvent(newEvent);
    }
    
    private boolean handleNavigation(KeyEvent event) {
        if (event == null) {
            return false;
        }

        // Toggle dialog
        if (!mFragment.isOverlay() && (KeyHelpers.isLeftRightKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            return true;
        }

        // Notification dialog type. Imitate notification behavior.
        if (mFragment.isOverlay() && (KeyHelpers.isNavigationKey(event.getKeyCode()) || KeyHelpers.isMenuKey(event.getKeyCode()))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
            }
            PlaybackView view = PlaybackPresenter.instance(this).getView();
            if (view instanceof Fragment) {
                Activity activity = ((Fragment) view).getActivity();
                if (activity != null) {
                    activity.dispatchKeyEvent(event);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void finish() {
        // NOTE: Fragment's onDestroy/onDestroyView are not reliable way to catch dialog finish
        Log.d(TAG, "Dialog finish");
        if (mFragment != null) { // fragment isn't created yet (expandable = true)
            // Fix touchscreen/mouse DPAD emulation on API 28+
            // The bug on API 28+ when using touchscreen/mouse to traverse nested dialog categories.
            // In this case the back gesture closes dialog instead of return to the previous category.
            // Cause: touchscreen/mouse input flips the view hierarchy into touch mode, which back-handling
            // treats differently than real D-pad input.
            if (isBackPressed() && mFragment.canGoBack()) {
                mFragment.goBack();
                // No additional event like onResume is fired, so we should reset the state explicitly
                resetBackState();
                return;
            }

            mFragment.onFinish();
        }

        // Destroy dialog when BACK is pressed. NoHistory isn't reliable if combined with singleInstance
        endPersonalPlayerDialog();
        finishReally();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Respect PIP mode
        if (ViewManager.instance(this).getTopView() == PlaybackView.class && PlaybackPresenter.instance(this).getContext() instanceof PlaybackActivity) {
            ((PlaybackActivity) PlaybackPresenter.instance(this).getContext()).onUserLeaveHint();
        }

        finish();
    }
}
