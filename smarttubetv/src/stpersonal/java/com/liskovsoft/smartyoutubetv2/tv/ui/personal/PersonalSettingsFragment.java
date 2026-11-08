package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Three-column TV settings shell, backed by the existing SettingsGroup action contracts. */
public class PersonalSettingsFragment extends Fragment implements SettingsSection {
    private final List<SettingsItem> items = new ArrayList<>();
    private final List<TextView> categories = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout categoryList;
    private TextView detailTitle;
    private TextView detailDescription;
    private TextView detailStatus;
    private ImageView detailIcon;
    private TextView openButton;
    private int selected;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) selected = state.getInt("personal.settings.selection", 0);
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context context = requireContext();
        root = new FrameLayout(context) {
            @Override protected boolean onRequestFocusInDescendants(int direction, Rect previous) {
                if (selected >= 0 && selected < categories.size() && categories.get(selected).requestFocus(direction, previous)) return true;
                return super.onRequestFocusInDescendants(direction, previous);
            }
        };
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(0xFF0F0F0F);
        TextView title = text(getString(R.string.header_settings), 23, true);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, dp(42));
        titleParams.leftMargin = dp(8); titleParams.topMargin = dp(35);
        root.addView(title, titleParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        categoryList = new LinearLayout(context);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(categoryList, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(dp(210), -1);
        listParams.leftMargin = dp(-4.5f); listParams.topMargin = dp(96);
        root.addView(scroll, listParams);
        View divider = new View(context); divider.setBackgroundColor(0xFF272727);
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(dp(1), -1);
        dividerParams.leftMargin = dp(216.5f); dividerParams.topMargin = dp(96);
        root.addView(divider, dividerParams);

        ScrollView detailScroll = new ScrollView(context);
        detailScroll.setVerticalScrollBarEnabled(false);
        LinearLayout details = new LinearLayout(context);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(30), dp(12), dp(30), dp(36));
        detailScroll.addView(details, new ScrollView.LayoutParams(-1, -2));
        detailIcon = new ImageView(context); detailIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(120), dp(120));
        iconParams.bottomMargin = dp(22); details.addView(detailIcon, iconParams);
        detailTitle = text("", 26, true); detailTitle.setGravity(Gravity.CENTER);
        details.addView(detailTitle, new LinearLayout.LayoutParams(-1, -2));
        detailDescription = text("", 15, false); detailDescription.setTextColor(0xFFAAAAAA);
        detailDescription.setGravity(Gravity.CENTER); detailDescription.setLineSpacing(dp(4), 1);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(dp(260), -2);
        descriptionParams.topMargin = dp(12); details.addView(detailDescription, descriptionParams);
        detailStatus = text("", 12, false); detailStatus.setTextColor(0xFFAAAAAA); detailStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(16); details.addView(detailStatus, statusParams);
        openButton = text(getString(R.string.personal_settings_open), 15, true);
        openButton.setFocusable(true); openButton.setGravity(Gravity.CENTER);
        openButton.setTextColor(colors()); openButton.setBackground(background(18, 0xFF272727));
        openButton.setPadding(dp(20), 0, dp(20), 0);
        openButton.setOnClickListener(view -> openSelected());
        openButton.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !categories.isEmpty()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) categories.get(selected).requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-2, dp(38));
        openParams.topMargin = dp(25); details.addView(openButton, openParams);
        FrameLayout.LayoutParams detailParams = new FrameLayout.LayoutParams(-1, -1);
        detailParams.leftMargin = dp(232); detailParams.topMargin = dp(78);
        root.addView(detailScroll, detailParams);
        renderCategories();
        return root;
    }

    @Override public void update(SettingsGroup group) {
        String previous = selected < items.size() ? items.get(selected).title : null;
        items.clear();
        if (group != null && group.getItems() != null) items.addAll(group.getItems());
        selected = Math.min(selected, Math.max(0, items.size() - 1));
        if (previous != null) for (int i = 0; i < items.size(); i++) if (previous.equals(items.get(i).title)) selected = i;
        renderCategories();
    }

    private void renderCategories() {
        if (categoryList == null) return;
        boolean restoreFocus = categoryList.hasFocus();
        categoryList.removeAllViews(); categories.clear();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            SettingsItem item = items.get(i);
            TextView category = text(item.title, 15, true);
            category.setId(View.generateViewId()); category.setFocusable(true); category.setSingleLine(true);
            category.setEllipsize(TextUtils.TruncateAt.END); category.setPadding(dp(9), 0, dp(12), 0);
            category.setTextColor(colors()); category.setBackground(background(9, 0x00000000));
            category.setContentDescription(item.title);
            category.setOnFocusChangeListener((view, focused) -> { if (focused) select(index); });
            category.setOnClickListener(view -> { select(index); openSelected(); });
            category.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) openButton.requestFocus();
                    return true;
                }
                return false;
            });
            categoryList.addView(category, new LinearLayout.LayoutParams(-1, dp(40.5f)));
            categories.add(category);
        }
        select(selected);
        if (restoreFocus && selected < categories.size()) categories.get(selected).requestFocus();
    }

    private void select(int index) {
        if (items.isEmpty()) {
            if (openButton != null) {
                openButton.setVisibility(View.GONE); detailTitle.setText(""); detailDescription.setText("");
                detailStatus.setText(""); Glide.with(this).clear(detailIcon); detailIcon.setImageDrawable(null);
            }
            return;
        }
        selected = Math.max(0, Math.min(index, items.size() - 1));
        for (int i = 0; i < categories.size(); i++) categories.get(i).setSelected(i == selected);
        refreshDetails();
    }

    void refreshDetails() {
        if (detailTitle == null || items.isEmpty()) return;
        SettingsItem item = items.get(selected);
        detailTitle.setText(item.title); detailDescription.setText(description(item));
        Glide.with(this).clear(detailIcon);
        detailIcon.setImageResource(item.imageResId > 0 ? item.imageResId : android.R.drawable.ic_menu_preferences);
        detailStatus.setText(status(item)); detailStatus.setVisibility(detailStatus.length() == 0 ? View.GONE : View.VISIBLE);
        openButton.setVisibility(View.VISIBLE);
        if ("settings_account".equals(key(item))) {
            Account account = MediaServiceManager.instance().getSelectedAccount();
            detailIcon.setImageResource(R.drawable.personal_account_placeholder);
            if (account != null && account.getAvatarImageUrl() != null) Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(detailIcon);
        }
    }

    private int description(SettingsItem item) {
        switch (key(item)) {
            case "settings_account": return R.string.personal_settings_account_description;
            case "settings_cast": return R.string.personal_settings_remote_description;
            case "settings_language": return R.string.personal_settings_language_description;
            case "settings_app": return R.string.personal_settings_general_description;
            case "settings_main_ui": return R.string.personal_settings_interface_description;
            case "settings_player": return R.string.personal_settings_player_description;
            case "settings_afr": return R.string.personal_settings_afr_description;
            case "settings_subtitles": return R.string.personal_settings_subtitles_description;
            case "settings_search": return R.string.personal_settings_search_description;
            case "settings_block": return R.string.personal_settings_sponsor_description;
            case "settings_dearrow": return R.string.personal_settings_dearrow_description;
            case "settings_backup": return R.string.personal_settings_backup_description;
            case "settings_about": return R.string.personal_settings_about_description;
            default: return R.string.personal_settings_future_description;
        }
    }

    private String status(SettingsItem item) {
        if ("settings_account".equals(key(item))) {
            Account account = MediaServiceManager.instance().getSelectedAccount();
            return getString(R.string.personal_settings_account_status,
                    account != null && account.getName() != null ? account.getName() : getString(R.string.personal_guest));
        }
        if ("settings_language".equals(key(item))) {
            Locale locale = getResources().getConfiguration().locale;
            return getString(R.string.personal_settings_language_status, locale.getDisplayName(locale));
        }
        if ("settings_about".equals(key(item))) return getString(R.string.personal_settings_version_status,
                getString(R.string.personal_brand), BuildConfig.VERSION_NAME);
        return "";
    }

    private String key(SettingsItem item) {
        return item.imageResId > 0 ? getResources().getResourceEntryName(item.imageResId) : "";
    }

    private void openSelected() {
        if (items.isEmpty()) return;
        SettingsItem item = items.get(selected);
        String password = GeneralData.instance(requireContext()).getSettingsPassword();
        if (password == null) item.onClick.run();
        else SimpleEditDialog.showPassword(requireContext(), getString(R.string.enter_settings_password), null, value -> {
            if (Utils.passwordMatch(password, value)) { item.onClick.run(); return true; }
            return false;
        });
    }

    @Override public void onResume() { super.onResume(); refreshDetails(); }
    @Override public void clear() { items.clear(); renderCategories(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public void onSaveInstanceState(@NonNull Bundle state) { super.onSaveInstanceState(state); state.putInt("personal.settings.selection", selected); }
    @Override public void onDestroyView() { root = null; categoryList = null; detailTitle = null; detailIcon = null; detailStatus = null; detailDescription = null; openButton = null; categories.clear(); super.onDestroyView(); }

    private TextView text(String value, float size, boolean medium) {
        TextView text = new TextView(requireContext()); text.setText(value); text.setTextSize(size);
        text.setTextColor(0xFFF1F1F1); text.setGravity(Gravity.CENTER_VERTICAL); text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return text;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private ColorStateList colors() { return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF0F0F0F, 0xFFF1F1F1}); }
    private StateListDrawable background(float radius, int normal) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(radius, 0xFFF1F1F1));
        states.addState(new int[]{android.R.attr.state_selected}, shape(radius, 0xFF272727));
        states.addState(new int[]{}, shape(radius, normal)); return states;
    }
    private GradientDrawable shape(float radius, int color) { GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(radius)); shape.setColor(color); return shape; }
}
