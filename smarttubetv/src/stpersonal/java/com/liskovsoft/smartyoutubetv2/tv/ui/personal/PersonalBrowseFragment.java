package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Row;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseSectionFragmentFactory;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.SectionHeaderItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A compact/expanded TV rail around SmartTube's existing section and service contracts. */
public class PersonalBrowseFragment extends Fragment implements BrowseView {
    private static final int BACKGROUND = 0xFF0F0F0F;
    private static final int TEXT = 0xFFF1F1F1;
    private static final int MUTED = 0xFFAAAAAA;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BrowseSection> sections = new ArrayList<>();
    private final Map<Integer, View> navigationItems = new HashMap<>();
    private final List<TextView> navigationLabels = new ArrayList<>();
    private final Map<Integer, Video> savedVideos = new HashMap<>();
    private BrowsePresenter presenter;
    private BrowseSectionFragmentFactory factory;
    private CrashRestorer restorer;
    private RootLayout root;
    private ScrollView rail;
    private LinearLayout navigation;
    private FrameLayout translatedContent;
    private FrameLayout content;
    private ProgressBar progress;
    private ImageView avatar;
    private TextView accountName;
    private View accountButton;
    private int selectedSection = -1;
    private boolean expanded;
    private boolean initialResume = true;
    private boolean pendingContentFocus;
    private boolean showingError;
    private boolean consumedBack;
    private Video pausedVideo;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> {
        if (newFocus != null && root != null) {
            if (isInside(newFocus, rail)) setExpanded(true);
            else if (isInside(newFocus, translatedContent)) setExpanded(false);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(null);
        presenter = BrowsePresenter.instance(requireContext());
        presenter.setView(this);
        restorer = new CrashRestorer(requireContext(), state);
        factory = new BrowseSectionFragmentFactory(this::onSectionReady) {
            @Override
            protected Fragment createRowsFragment() {
                return new PersonalVideoRowsFragment();
            }

            @Override
            protected Fragment createGridFragment() {
                return new PersonalVideoGridFragment();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context context = requireContext();
        root = new RootLayout(context);
        root.setBackgroundColor(BACKGROUND);
        root.setClipChildren(true);

        translatedContent = new FrameLayout(context);
        FrameLayout.LayoutParams translatedParams = new FrameLayout.LayoutParams(-1, -1);
        translatedParams.leftMargin = dp(78);
        root.addView(translatedContent, translatedParams);

        content = new FrameLayout(context);
        content.setId(R.id.personal_browse_content);
        content.setClipChildren(false);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -1);
        contentParams.topMargin = dp(88);
        translatedContent.addView(content, contentParams);
        addSearchHeader(context);

        TextView brand = text(getString(R.string.personal_brand), 21);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(-2, dp(36), Gravity.TOP | Gravity.END);
        brandParams.topMargin = dp(33);
        brandParams.rightMargin = dp(54);
        root.addView(brand, brandParams);

        rail = new ScrollView(context);
        rail.setBackgroundColor(BACKGROUND);
        rail.setVerticalScrollBarEnabled(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setFillViewport(true);
        navigation = new LinearLayout(context);
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(dp(16.5f), dp(33), dp(16.5f), dp(20));
        rail.addView(navigation, new ScrollView.LayoutParams(-1, -2));
        root.addView(rail, new FrameLayout.LayoutParams(dp(78), -1));

        progress = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER);
        root.addView(progress, progressParams);
        progress.setVisibility(View.GONE);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
        rebuildRail();
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle state) {
        super.onActivityCreated(state);
        presenter.onViewInitialized();
        restorer.restoreHeader((index, video) -> {
            selectSection(index, true);
            selectSectionItem(video);
        });
        restorer.restorePlayback();
    }

    private void addSearchHeader(Context context) {
        ImageView microphone = new ImageView(context);
        int microphoneResource = getResources().getIdentifier("lb_ic_search_mic", "drawable", context.getPackageName());
        microphone.setImageResource(microphoneResource != 0 ? microphoneResource : R.drawable.action_search);
        microphone.setImageTintList(textColors());
        microphone.setPadding(dp(9), dp(9), dp(9), dp(9));
        microphone.setBackground(buttonBackground(18, 0xFF272727));
        microphone.setFocusable(true);
        microphone.setContentDescription(getString(R.string.personal_voice_search));
        microphone.setOnClickListener(view -> SearchPresenter.instance(context).startVoice());
        FrameLayout.LayoutParams micParams = new FrameLayout.LayoutParams(dp(36), dp(36));
        micParams.topMargin = dp(33);
        translatedContent.addView(microphone, micParams);

        TextView search = text(getString(R.string.personal_search), 18);
        search.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(buttonBackground(18, 0xFF272727));
        search.setTextColor(textColors());
        search.setFocusable(true);
        search.setOnClickListener(view -> SearchPresenter.instance(context).startSearch(null));
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(dp(315), dp(36));
        searchParams.topMargin = dp(33);
        searchParams.leftMargin = dp(45);
        translatedContent.addView(search, searchParams);
    }

    private void rebuildRail() {
        if (navigation == null) return;
        navigation.removeAllViews();
        navigationItems.clear();
        navigationLabels.clear();
        LinearLayout account = new LinearLayout(requireContext());
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setFocusable(true);
        account.setBackground(buttonBackground(18));
        avatar = new ImageView(requireContext());
        avatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        account.addView(avatar, new LinearLayout.LayoutParams(dp(27), dp(27)));
        accountName = text(getString(R.string.personal_guest), 16);
        accountName.setSingleLine(true);
        accountName.setTextColor(textColors());
        LinearLayout.LayoutParams accountLabelParams = new LinearLayout.LayoutParams(-1, -1);
        accountLabelParams.leftMargin = dp(9);
        account.addView(accountName, accountLabelParams);
        navigationLabels.add(accountName);
        account.setOnClickListener(view -> AccountSelectionPresenter.instance(requireContext()).show(true));
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(-1, dp(36));
        accountParams.leftMargin = dp(9);
        accountParams.bottomMargin = dp(9);
        navigation.addView(account, accountParams);
        accountButton = account;

        View search = navigationButton(getString(R.string.personal_search), R.drawable.action_search);
        search.setOnClickListener(view -> SearchPresenter.instance(requireContext()).startSearch(null));
        navigation.addView(search, new LinearLayout.LayoutParams(-1, dp(39)));

        for (int index = 0; index < sections.size(); index++) {
            BrowseSection section = sections.get(index);
            final int sectionIndex = index;
            View item = navigationButton(section.getTitle(), section.getResId());
            item.setSelected(section.getId() == selectedSection);
            item.setOnFocusChangeListener((view, focused) -> {
                if (focused) activateSection(sectionIndex, false, false);
            });
            item.setOnClickListener(view -> activateSection(sectionIndex, true, true));
            item.setOnLongClickListener(view -> {
                presenter.onSectionLongPressed(section.getId());
                return true;
            });
            item.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    presenter.onSectionLongPressed(section.getId());
                    return true;
                }
                return false;
            });
            navigationItems.put(section.getId(), item);
            navigation.addView(item, new LinearLayout.LayoutParams(-1, dp(39)));
        }
        updateBadge();
        applyRailWidth();
    }

    private View navigationButton(String title, int icon) {
        LinearLayout button = new LinearLayout(requireContext());
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setFocusable(true);
        button.setBackground(buttonBackground(9));
        button.setPadding(dp(14), 0, dp(12), 0);
        ImageView image = new ImageView(requireContext());
        if (icon > 0) image.setImageResource(icon);
        image.setImageTintList(textColors());
        image.setDuplicateParentStateEnabled(true);
        button.addView(image, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView label = text(title, 16);
        label.setTextColor(textColors());
        label.setSingleLine(true);
        label.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -1);
        labelParams.leftMargin = dp(13.5f);
        button.addView(label, labelParams);
        navigationLabels.add(label);
        button.setContentDescription(title);
        return button;
    }

    private void activateSection(int index, boolean focus, boolean refresh) {
        if (index < 0 || index >= sections.size() || content == null) return;
        BrowseSection section = sections.get(index);
        pendingContentFocus = focus;
        if (section.getId() == selectedSection && factory.getCurrentFragment() != null) {
            if (refresh) presenter.onSectionFocused(selectedSection);
            if (focus) focusOnContent();
            return;
        }
        if (selectedSection != -1 && presenter.getCurrentVideo() != null) {
            savedVideos.put(selectedSection, presenter.getCurrentVideo());
        }
        selectedSection = section.getId();
        for (Map.Entry<Integer, View> entry : navigationItems.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == selectedSection);
        }
        Fragment fragment = factory.createFragment(new PageRow(new SectionHeaderItem(section)));
        showingError = false;
        showFragment(fragment);
        Video saved = savedVideos.get(selectedSection);
        if (saved != null) factory.selectCurrentFragmentItem(saved);
    }

    private void onSectionReady(Row row) {
        if (root == null || row.getHeaderItem().getId() != selectedSection) return;
        presenter.onSectionFocused(selectedSection);
        requestContentFocusIfNeeded();
    }

    private void showFragment(Fragment fragment) {
        if (!isAdded() || getChildFragmentManager().isDestroyed()) return;
        getChildFragmentManager().beginTransaction()
                .replace(R.id.personal_browse_content, fragment)
                .runOnCommit(this::requestContentFocusIfNeeded)
                .commitAllowingStateLoss();
    }

    private void requestContentFocusIfNeeded() {
        if (pendingContentFocus && content != null) {
            content.post(() -> {
                if (content != null && content.requestFocus()) {
                    pendingContentFocus = false;
                    setExpanded(false);
                }
            });
        }
    }

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) return;
        sections.remove(section);
        if (index < 0 || index > sections.size()) sections.add(section);
        else sections.add(index, section);
        rebuildRail();
    }

    @Override
    public void removeSection(BrowseSection section) {
        sections.remove(section);
        rebuildRail();
    }

    @Override
    public void removeAllSections() {
        sections.clear();
        savedVideos.clear();
        pausedVideo = null;
        selectedSection = -1;
        rebuildRail();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (sections.isEmpty()) return;
        activateSection(Math.max(0, Math.min(index, sections.size() - 1)), focusOnContent, true);
        if (!focusOnContent) focusNavigation();
    }

    @Override
    public void updateSection(VideoGroup group) {
        restoreContentFragment();
        factory.updateCurrentFragment(group);
        requestContentFocusIfNeeded();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        restoreContentFragment();
        factory.updateCurrentFragment(group);
        requestContentFocusIfNeeded();
    }

    @Override
    public void clearSection(BrowseSection section) { factory.clearCurrentFragment(); }

    @Override
    public void selectSectionItem(int index) { factory.setCurrentFragmentItemIndex(index); }

    @Override
    public void selectSectionItem(Video item) { factory.selectCurrentFragmentItem(item); }

    @Override
    public void showError(ErrorFragmentData data) {
        handler.post(() -> {
            if (root != null) {
                showingError = true;
                showFragment(new ErrorDialogFragment(data));
            }
        });
    }

    private void restoreContentFragment() {
        if (showingError && factory.getCurrentFragment() != null) {
            showingError = false;
            showFragment(factory.getCurrentFragment());
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        handler.post(() -> {
            if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public boolean isProgressBarShowing() { return progress != null && progress.getVisibility() == View.VISIBLE; }

    @Override
    public void focusOnContent() {
        pendingContentFocus = true;
        requestContentFocusIfNeeded();
    }

    @Override
    public boolean isEmpty() { return factory == null || factory.getCurrentFragment() == null || factory.isEmpty(); }

    @Override
    public void updateBadge() {
        if (avatar == null) return;
        Account account = MediaServiceManager.instance().getSelectedAccount();
        accountName.setText(account != null ? account.getName() : getString(R.string.personal_guest));
        if (account != null && account.getAvatarImageUrl() != null) {
            Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }
    }

    private void focusNavigation() {
        setExpanded(true);
        View item = navigationItems.get(selectedSection);
        if (item != null) item.requestFocus();
        else if (accountButton != null) accountButton.requestFocus();
    }

    private void setExpanded(boolean value) {
        if (expanded == value || rail == null) return;
        expanded = value;
        applyRailWidth();
        translatedContent.animate().translationX(dp(expanded ? 189 : 0)).setDuration(150).start();
    }

    private void applyRailWidth() {
        if (rail == null) return;
        ViewGroup.LayoutParams params = rail.getLayoutParams();
        params.width = dp(expanded ? 267 : 78);
        rail.setLayoutParams(params);
        for (TextView label : navigationLabels) label.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private boolean handleKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && consumedBack) {
                consumedBack = false;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && !expanded) {
                focusNavigation();
                consumedBack = true;
                return true;
            }
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || root == null) return false;
        View focus = root.findFocus();
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && isInside(focus, rail)) {
            focusOnContent();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && isInside(focus, content)) {
            ViewParent ancestor = focus != null ? focus.getParent() : null;
            while (ancestor instanceof View && ancestor != content) {
                if (ancestor instanceof HorizontalGridView) {
                    if (((HorizontalGridView) ancestor).getSelectedPosition() == 0) {
                        focusNavigation();
                        return true;
                    }
                    return false;
                }
                ancestor = ancestor.getParent();
            }
            View next = focus != null ? focus.focusSearch(View.FOCUS_LEFT) : null;
            if (!isInside(next, content)) {
                focusNavigation();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        int index = 0;
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).getId() == selectedSection) index = i;
        restorer.persistHeaderIndex(outState, index);
        restorer.persistVideo(outState, presenter.getCurrentVideo());
    }

    @Override
    public void onPause() {
        pausedVideo = presenter.getCurrentVideo();
        if (!initialResume) presenter.onViewPaused();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!initialResume) {
            presenter.onViewResumed();
            if (pausedVideo != null) factory.selectCurrentFragmentItem(pausedVideo);
        }
        initialResume = false;
        updateBadge();
    }

    @Override
    public void onDestroyView() {
        if (root != null) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        handler.removeCallbacksAndMessages(null);
        factory.cleanup();
        root = null;
        rail = null;
        navigation = null;
        content = null;
        translatedContent = null;
        progress = null;
        avatar = null;
        accountName = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        presenter.onViewDestroyed();
        super.onDestroy();
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(TEXT);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static ColorStateList textColors() {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_selected}, new int[]{}}, new int[]{BACKGROUND, TEXT, MUTED});
    }

    private StateListDrawable buttonBackground(float radius) {
        return buttonBackground(radius, Color.TRANSPARENT);
    }

    private StateListDrawable buttonBackground(float radius, int defaultColor) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, rounded(TEXT, radius));
        drawable.addState(new int[]{}, rounded(defaultColor, radius));
        return drawable;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) { return PersonalVideoCardPresenter.dp(requireContext(), value); }

    private static boolean isInside(View view, View ancestor) {
        if (view == null || ancestor == null) return false;
        if (view == ancestor) return true;
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private final class RootLayout extends FrameLayout {
        RootLayout(Context context) { super(context); }
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return handleKey(event) || super.dispatchKeyEvent(event);
        }
    }
}
