package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Row;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseSectionFragmentFactory;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.SectionHeaderItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A compact/expanded TV rail around SmartTube's existing section and service contracts. */
public class PersonalBrowseFragment extends Fragment implements BrowseView {
    private static final int BACKGROUND = 0xFF0F0F0F;
    private static final int TEXT = 0xFFF1F1F1;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int NAV_LIBRARY = -10001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BrowseSection> sections = new ArrayList<>();
    private final Map<Integer, View> navigationItems = new HashMap<>();
    private final List<TextView> navigationLabels = new ArrayList<>();
    private final List<View> advancedItems = new ArrayList<>();
    private final Map<Integer, Video> savedVideos = new HashMap<>();
    private BrowsePresenter presenter;
    private BrowseSectionFragmentFactory factory;
    private CrashRestorer restorer;
    private RootLayout root;
    private FrameLayout rail;
    private ScrollView railScroll;
    private LinearLayout navigation;
    private FrameLayout translatedContent;
    private FrameLayout content;
    private ProgressBar progress;
    private ImageView avatar;
    private TextView accountName;
    private View accountButton;
    private View settingsButton;
    private View groupDivider;
    private View microphoneHeader;
    private View searchHeader;
    private View brandHeader;
    private int selectedSection = -1;
    private boolean expanded;
    private boolean initialResume = true;
    private boolean pendingContentFocus;
    private boolean showingError;
    private boolean consumedBack;
    private boolean showingLibrary;
    private boolean pendingLibrarySelection;
    private boolean pendingLibraryFocus;
    private boolean restoreLibrary;
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
        restoreLibrary = state != null && state.getBoolean("personal.browse.library", false);
        factory = new BrowseSectionFragmentFactory(this::onSectionReady) {
            @Override
            protected Fragment createRowsFragment() {
                return new PersonalVideoRowsFragment();
            }

            @Override
            protected Fragment createGridFragment() {
                if (showingLibrary && selectedSection == MediaGroup.TYPE_HISTORY) return new PersonalLibraryFragment();
                if (selectedSection == MediaGroup.TYPE_SUBSCRIPTIONS) return new PersonalSubscriptionsFragment();
                return new PersonalVideoGridFragment();
            }

            @Override
            protected Fragment createSettingsFragment() {
                return new PersonalSettingsFragment();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context context = requireContext();
        root = new RootLayout(context);
        root.setBackgroundColor(BACKGROUND);
        // Focus rings and channel pills extend into the gutter beside content.
        root.setClipChildren(false);

        translatedContent = new FrameLayout(context);
        translatedContent.setClipChildren(false);
        translatedContent.setClipToPadding(false);
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
        brandHeader = brand;

        rail = new FrameLayout(context);
        rail.setBackgroundColor(BACKGROUND);
        railScroll = new ScrollView(context);
        railScroll.setVerticalScrollBarEnabled(false);
        railScroll.setHorizontalScrollBarEnabled(false);
        railScroll.setFillViewport(false);
        navigation = new LinearLayout(context);
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(dp(16.5f), 0, dp(16.5f), dp(16.5f));
        railScroll.addView(navigation, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams scrollingParams = new FrameLayout.LayoutParams(-1, -1);
        scrollingParams.topMargin = dp(97.5f);
        scrollingParams.bottomMargin = dp(67.5f);
        rail.addView(railScroll, scrollingParams);
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
            if (restoreLibrary) { restoreLibrary = false; openLibrary(true); }
            else selectSection(index, true);
            selectSectionItem(video);
        });
        restorer.restorePlayback();
    }

    private void addSearchHeader(Context context) {
        ImageView microphone = new ImageView(context);
        int microphoneResource = getResources().getIdentifier("lb_ic_search_mic", "drawable", context.getPackageName());
        microphone.setImageResource(microphoneResource != 0 ? microphoneResource : R.drawable.action_search);
        tintImage(microphone);
        microphone.setPadding(dp(9), dp(9), dp(9), dp(9));
        microphone.setBackground(buttonBackground(18, 0xFF272727));
        microphone.setFocusable(true);
        microphone.setContentDescription(getString(R.string.personal_voice_search));
        microphone.setOnClickListener(view -> SearchPresenter.instance(context).startVoice());
        FrameLayout.LayoutParams micParams = new FrameLayout.LayoutParams(dp(36), dp(36));
        micParams.topMargin = dp(33);
        translatedContent.addView(microphone, micParams);
        microphoneHeader = microphone;

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
        searchHeader = search;
    }

    private void rebuildRail() {
        if (navigation == null) return;
        navigation.removeAllViews();
        if (accountButton != null) rail.removeView(accountButton);
        if (settingsButton != null) rail.removeView(settingsButton);
        navigationItems.clear();
        navigationLabels.clear();
        advancedItems.clear();
        LinearLayout account = new LinearLayout(requireContext());
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setFocusable(true);
        account.setBackground(buttonBackground(18));
        avatar = new ImageView(requireContext());
        avatar.setImageResource(R.drawable.personal_account_placeholder);
        account.addView(avatar, new LinearLayout.LayoutParams(dp(27), dp(27)));
        accountName = text(getString(R.string.personal_guest), 15);
        accountName.setSingleLine(true);
        accountName.setTextColor(textColors());
        LinearLayout.LayoutParams accountLabelParams = new LinearLayout.LayoutParams(-1, -1);
        accountLabelParams.leftMargin = dp(9);
        account.addView(accountName, accountLabelParams);
        navigationLabels.add(accountName);
        account.setOnClickListener(view -> PersonalAccountChooserFragment.show(this));
        FrameLayout.LayoutParams accountParams = new FrameLayout.LayoutParams(-1, dp(36));
        accountParams.leftMargin = dp(25.5f);
        accountParams.rightMargin = dp(16.5f);
        accountParams.topMargin = dp(33);
        rail.addView(account, accountParams);
        accountButton = account;

        View search = navigationButton(getString(R.string.personal_search), R.drawable.action_search);
        search.setOnClickListener(view -> SearchPresenter.instance(requireContext()).startSearch(null));
        navigation.addView(search, new LinearLayout.LayoutParams(-1, dp(39)));

        // Display order is independent of the presenter's section indices and pinned-item order.
        Set<Integer> displayed = new HashSet<>();
        for (int id : new int[]{MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUBSCRIPTIONS}) {
            appendNavigationSection(id, displayed);
        }
        View library = navigationButton(getString(R.string.personal_library_title), R.drawable.icon_playlist);
        library.setOnFocusChangeListener((view, focused) -> { if (focused) openLibrary(false); });
        library.setOnClickListener(view -> openLibrary(true));
        navigationItems.put(NAV_LIBRARY, library);
        navigation.addView(library, new LinearLayout.LayoutParams(-1, dp(39)));

        groupDivider = new View(requireContext());
        groupDivider.setBackgroundColor(0xFF3F3F3F);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1.5f));
        dividerParams.topMargin = dp(9);
        dividerParams.bottomMargin = dp(9);
        navigation.addView(groupDivider, dividerParams);
        for (int id : new int[]{MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_LIVE, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_SPORTS}) {
            appendNavigationSection(id, displayed);
        }

        List<Integer> extras = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            int id = sections.get(index).getId();
            if (!displayed.contains(id) && id != MediaGroup.TYPE_SETTINGS) extras.add(index);
        }
        if (!extras.isEmpty()) {
            View advanced = navigationButton(getString(R.string.personal_navigation_advanced), android.R.drawable.ic_menu_more);
            advanced.setOnClickListener(view -> {
                setExpanded(true);
                if (!advancedItems.isEmpty()) advancedItems.get(0).requestFocus();
            });
            navigation.addView(advanced, new LinearLayout.LayoutParams(-1, dp(39)));
            for (int index : extras) {
                View item = createSectionButton(index);
                advancedItems.add(item);
                navigation.addView(item, new LinearLayout.LayoutParams(-1, dp(39)));
            }
        }

        int settingsIndex = findSectionIndex(MediaGroup.TYPE_SETTINGS);
        if (settingsIndex >= 0) settingsButton = createSectionButton(settingsIndex);
        else {
            settingsButton = navigationButton(getString(R.string.header_settings), R.drawable.icon_settings);
            settingsButton.setOnClickListener(view -> showSettingsFallback());
        }
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(-1, dp(39), Gravity.BOTTOM);
        settingsParams.leftMargin = dp(16.5f);
        settingsParams.rightMargin = dp(16.5f);
        settingsParams.bottomMargin = dp(28.5f);
        rail.addView(settingsButton, settingsParams);
        updateNavigationSelection();
        updateBadge();
        applyRailWidth();
    }

    private void appendNavigationSection(int id, Set<Integer> displayed) {
        int index = findSectionIndex(id);
        if (index < 0) return;
        displayed.add(id);
        navigation.addView(createSectionButton(index), new LinearLayout.LayoutParams(-1, dp(39)));
    }

    private View createSectionButton(int index) {
        BrowseSection section = sections.get(index);
        View item = navigationButton(section.getTitle(), section.getResId());
        if (section.getIconUrl() != null) {
            ImageView icon = (ImageView) ((LinearLayout) item).getChildAt(0);
            Drawable iconDrawable = icon.getDrawable();
            if (iconDrawable != null) DrawableCompat.setTintList(iconDrawable, null);
            Glide.with(this).load(section.getIconUrl()).circleCrop().into(icon);
        }
        item.setOnFocusChangeListener((view, focused) -> {
            if (focused) activateNavigationSection(index, false, false);
        });
        item.setOnClickListener(view -> activateNavigationSection(index, true, true));
        item.setOnLongClickListener(view -> { presenter.onSectionLongPressed(section.getId()); return true; });
        item.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                presenter.onSectionLongPressed(section.getId()); return true;
            }
            return false;
        });
        navigationItems.put(section.getId(), item);
        return item;
    }

    private int findSectionIndex(int id) {
        for (int index = 0; index < sections.size(); index++) if (sections.get(index).getId() == id) return index;
        return -1;
    }

    private void activateNavigationSection(int index, boolean focus, boolean refresh) {
        showingLibrary = false;
        activateSection(index, focus, refresh);
    }

    private void openLibrary(boolean focus) {
        showingLibrary = true;
        int index = findSectionIndex(MediaGroup.TYPE_HISTORY);
        if (index >= 0) activateSection(index, focus, true);
        else {
            pendingLibrarySelection = true;
            pendingLibraryFocus = focus;
            presenter.selectSection(MediaGroup.TYPE_HISTORY);
            pendingLibrarySelection = false;
        }
    }

    void openLibraryDestination(int sectionId) {
        showingLibrary = false;
        presenter.selectSection(sectionId);
    }

    private void showSettingsFallback() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(requireContext());
        for (SettingsItem item : AppDataSourceManager.instance().getSettingItems(requireContext())) {
            dialog.appendSingleButton(UiOptionItem.from(item.title, option -> item.onClick.run()));
        }
        dialog.showDialog(getString(R.string.header_settings));
    }

    private void updateNavigationSelection() {
        for (Map.Entry<Integer, View> entry : navigationItems.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == NAV_LIBRARY ? showingLibrary : !showingLibrary && entry.getKey() == selectedSection);
        }
    }

    private View navigationButton(String title, int icon) {
        LinearLayout button = new LinearLayout(requireContext());
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setFocusable(true);
        button.setBackground(buttonBackground(9));
        button.setPadding(dp(10.5f), 0, dp(12), 0);
        ImageView image = new ImageView(requireContext());
        if (icon > 0) image.setImageResource(icon);
        tintImage(image);
        image.setDuplicateParentStateEnabled(true);
        button.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = text(title, 15);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(textColors());
        label.setSingleLine(true);
        label.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -1);
        labelParams.leftMargin = dp(10.5f);
        button.addView(label, labelParams);
        navigationLabels.add(label);
        button.setContentDescription(title);
        return button;
    }

    private void activateSection(int index, boolean focus, boolean refresh) {
        if (index < 0 || index >= sections.size() || content == null) return;
        BrowseSection section = sections.get(index);
        pendingContentFocus = focus;
        updateContentMode();
        if (section.getId() == selectedSection && factory.getCurrentFragment() != null &&
                showingLibrary == (factory.getCurrentFragment() instanceof PersonalLibraryFragment)) {
            updateNavigationSelection();
            if (refresh) presenter.onSectionFocused(selectedSection);
            if (focus) focusOnContent();
            return;
        }
        if (selectedSection != -1 && presenter.getCurrentVideo() != null) {
            savedVideos.put(selectedSection, presenter.getCurrentVideo());
        }
        selectedSection = section.getId();
        updateContentMode();
        updateNavigationSelection();
        Fragment fragment = factory.createFragment(new PageRow(new SectionHeaderItem(section)));
        showingError = false;
        showFragment(fragment);
        Video saved = savedVideos.get(selectedSection);
        if (saved != null) factory.selectCurrentFragmentItem(saved);
    }

    private void updateContentMode() {
        if (content == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) content.getLayoutParams();
        boolean fullPage = showingLibrary || selectedSection == MediaGroup.TYPE_SUBSCRIPTIONS || selectedSection == MediaGroup.TYPE_SETTINGS;
        params.topMargin = fullPage ? 0 : dp(88);
        content.setLayoutParams(params);
        int visibility = fullPage ? View.GONE : View.VISIBLE;
        if (microphoneHeader != null) microphoneHeader.setVisibility(visibility);
        if (searchHeader != null) searchHeader.setVisibility(visibility);
        if (brandHeader != null) brandHeader.setVisibility(visibility);
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
            final FrameLayout requestedContent = content;
            final int requestedSection = selectedSection;
            requestedContent.post(() -> {
                if (pendingContentFocus && content == requestedContent && selectedSection == requestedSection
                        && requestedContent.requestFocus()) {
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
        int safeIndex = Math.max(0, Math.min(index, sections.size() - 1));
        if (pendingLibrarySelection) showingLibrary = true;
        else if (sections.get(safeIndex).getId() != MediaGroup.TYPE_HISTORY) showingLibrary = false;
        boolean focus = pendingLibrarySelection ? pendingLibraryFocus : focusOnContent;
        activateSection(safeIndex, focus, true);
        if (!focus) focusNavigation();
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
                if (showingLibrary && factory.getCurrentFragment() instanceof PersonalLibraryFragment) {
                    ((PersonalLibraryFragment) factory.getCurrentFragment()).showLibraryError(data);
                    return;
                }
                if (factory.getCurrentFragment() instanceof PersonalSubscriptionsFragment) {
                    ((PersonalSubscriptionsFragment) factory.getCurrentFragment()).showSubscriptionsError(data);
                    return;
                }
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
            if (factory.getCurrentFragment() instanceof PersonalSubscriptionsFragment) {
                ((PersonalSubscriptionsFragment) factory.getCurrentFragment()).setAllFeedLoading(show);
            }
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
        Glide.with(this).clear(avatar);
        if (account != null && account.getAvatarImageUrl() != null) {
            Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.personal_account_placeholder);
        }
    }

    void onPersonalAccountChooserClosed() {
        updateBadge();
        if (factory.getCurrentFragment() instanceof PersonalSettingsFragment) {
            ((PersonalSettingsFragment) factory.getCurrentFragment()).refreshDetails();
        }
        if (accountButton != null) accountButton.requestFocus();
    }

    private void focusNavigation() {
        setExpanded(true);
        View item = navigationItems.get(showingLibrary ? NAV_LIBRARY : selectedSection);
        if (item != null) item.requestFocus();
        else if (accountButton != null) accountButton.requestFocus();
    }

    private void setExpanded(boolean value) {
        if (selectedSection == MediaGroup.TYPE_SETTINGS) value = true;
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
        rail.setBackgroundColor(expanded ? BACKGROUND : Color.TRANSPARENT);
        navigation.setPadding(dp(16.5f), 0, dp(expanded ? 28.5f : 16.5f), dp(16.5f));
        FrameLayout.LayoutParams scrollingParams = (FrameLayout.LayoutParams) railScroll.getLayoutParams();
        scrollingParams.topMargin = dp(expanded ? 78 : 97.5f);
        railScroll.setLayoutParams(scrollingParams);
        FrameLayout.LayoutParams accountParams = (FrameLayout.LayoutParams) accountButton.getLayoutParams();
        accountParams.rightMargin = dp(expanded ? 28.5f : 16.5f);
        accountButton.setLayoutParams(accountParams);
        FrameLayout.LayoutParams settingsParams = (FrameLayout.LayoutParams) settingsButton.getLayoutParams();
        settingsParams.rightMargin = dp(expanded ? 28.5f : 16.5f);
        settingsButton.setLayoutParams(settingsParams);
        if (groupDivider != null) groupDivider.setVisibility(expanded ? View.VISIBLE : View.GONE);
        for (View item : advancedItems) item.setVisibility(expanded ? View.VISIBLE : View.GONE);
        for (TextView label : navigationLabels) label.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private boolean handleKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && consumedBack) {
                consumedBack = false;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && (!expanded ||
                    (selectedSection == MediaGroup.TYPE_SETTINGS && root != null && isInside(root.findFocus(), content)))) {
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
        outState.putBoolean("personal.browse.library", showingLibrary);
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
        railScroll = null;
        navigation = null;
        content = null;
        translatedContent = null;
        progress = null;
        avatar = null;
        accountName = null;
        accountButton = null;
        settingsButton = null;
        microphoneHeader = null;
        searchHeader = null;
        brandHeader = null;
        groupDivider = null;
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
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setTextColor(TEXT);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static void tintImage(ImageView image) {
        Drawable drawable = image.getDrawable();
        if (drawable == null) return;
        Drawable tinted = DrawableCompat.wrap(drawable).mutate();
        DrawableCompat.setTintList(tinted, textColors());
        image.setImageDrawable(tinted);
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
