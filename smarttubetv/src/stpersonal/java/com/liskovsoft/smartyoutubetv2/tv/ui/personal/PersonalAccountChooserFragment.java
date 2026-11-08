package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.ArrayList;
import java.util.List;

/** A TV profile carousel containing only the accounts saved through SmartTube's sign-in service. */
public class PersonalAccountChooserFragment extends DialogFragment {
    private static final String TAG = "personal_account_chooser";
    private final List<ProfileCard> cards = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout carousel;
    private TextView guest;
    private TextView empty;
    private String focusedName;
    private boolean initialFocus = true;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> {
        if (newFocus == guest) focusedName = "guest";
        for (ProfileCard card : cards) {
            boolean focused = isInside(newFocus, card.view);
            card.showFocus(focused);
            if (focused && guest != null) {
                focusedName = card.key;
                guest.setNextFocusUpId(card.view.getId());
            }
        }
    };

    static void show(PersonalBrowseFragment parent) {
        FragmentManager manager = parent.getChildFragmentManager();
        if (!manager.isStateSaved() && manager.findFragmentByTag(TAG) == null) {
            new PersonalAccountChooserFragment().show(manager, TAG);
        }
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (state != null) focusedName = state.getString("personal.accounts.focus");
    }

    @Override public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        Context context = requireContext();
        root = new FrameLayout(context); root.addView(new Backdrop(context), new FrameLayout.LayoutParams(-1, -1));
        root.setClipChildren(false); root.setClipToPadding(false);
        TextView brand = label(getString(R.string.personal_brand), 24, true);
        brand.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(-1, dp(40));
        brandParams.topMargin = dp(64); root.addView(brand, brandParams);
        empty = label(getString(R.string.personal_accounts_empty), 14, false);
        empty.setTextColor(0xFFAAAAAA); empty.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(-1, dp(26));
        emptyParams.topMargin = dp(112); root.addView(empty, emptyParams);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false); scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        carousel = new LinearLayout(context); carousel.setOrientation(LinearLayout.HORIZONTAL);
        carousel.setGravity(Gravity.CENTER_HORIZONTAL); carousel.setPadding(dp(32), 0, dp(32), 0);
        carousel.setClipChildren(false); carousel.setClipToPadding(false);
        scroll.addView(carousel, new HorizontalScrollView.LayoutParams(-1, -1));
        FrameLayout.LayoutParams carouselParams = new FrameLayout.LayoutParams(-1, dp(292));
        carouselParams.topMargin = dp(157); root.addView(scroll, carouselParams);

        guest = label(getString(R.string.personal_accounts_guest), 12, true);
        guest.setId(View.generateViewId()); guest.setFocusable(true); guest.setGravity(Gravity.CENTER);
        guest.setTextColor(buttonColors()); guest.setBackground(pill()); guest.setPadding(dp(18), 0, dp(18), 0);
        guest.setOnClickListener(view -> choose(null));
        FrameLayout.LayoutParams guestParams = new FrameLayout.LayoutParams(-2, dp(36), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        guestParams.bottomMargin = dp(48); root.addView(guest, guestParams);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        MediaServiceManager.instance().loadAccounts(accounts -> {
            if (isAdded() && root != null) {
                List<Account> snapshot = accounts != null ? new ArrayList<>(accounts) : null;
                root.post(() -> { if (isAdded() && root != null) renderAccounts(snapshot); });
            }
        });
    }

    private void renderAccounts(List<Account> accounts) {
        boolean restoreFocus = carousel.hasFocus();
        carousel.removeAllViews(); cards.clear();
        Account current = MediaServiceManager.instance().getSelectedAccount();
        ProfileCard preferred = null;
        ProfileCard selectedAccount = null;
        if (accounts != null) for (Account account : accounts) {
            ProfileCard card = new ProfileCard(account);
            cards.add(card); carousel.addView(card.view, new LinearLayout.LayoutParams(dp(176), -1));
            if (focusedName != null && focusedName.equals(card.key)) preferred = card;
            if (account.isSelected() || account.equals(current)) selectedAccount = card;
        }
        ProfileCard add = new ProfileCard(null); cards.add(add);
        carousel.addView(add.view, new LinearLayout.LayoutParams(dp(176), -1));
        if (add.key.equals(focusedName)) preferred = add;
        carousel.setGravity(cards.size() < 4 ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        empty.setVisibility(accounts == null || accounts.isEmpty() ? View.VISIBLE : View.GONE);
        View destination = "guest".equals(focusedName) ? guest : preferred != null ? preferred.view :
                selectedAccount != null ? selectedAccount.view : guest;
        guest.setNextFocusUpId(preferred != null ? preferred.view.getId() : add.view.getId());
        if (initialFocus || restoreFocus || root.findFocus() == null) {
            initialFocus = false;
            FrameLayout owner = root;
            root.post(() -> { if (root == owner) destination.requestFocus(); });
        }
    }

    private void choose(Account account) {
        AccountSelectionPresenter.instance(requireContext()).selectAccount(account);
        dismiss();
    }

    private void addAccount() {
        Context context = requireContext();
        runSettingsAction(() -> YTSignInPresenter.instance(context).start());
    }

    private void manageAccounts() {
        Context context = requireContext();
        runSettingsAction(() -> AccountSettingsPresenter.instance(context).show());
    }

    private void runSettingsAction(Runnable action) {
        String password = GeneralData.instance(requireContext()).getSettingsPassword();
        Runnable open = () -> { dismiss(); action.run(); };
        if (password == null) open.run();
        else SimpleEditDialog.showPassword(requireContext(), getString(R.string.enter_settings_password), null, value -> {
            if (Utils.passwordMatch(password, value)) { open.run(); return true; }
            return false;
        });
    }

    @Override public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getParentFragment() instanceof PersonalBrowseFragment) {
            ((PersonalBrowseFragment) getParentFragment()).onPersonalAccountChooserClosed();
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putString("personal.accounts.focus", focusedName);
    }

    @Override public void onDestroyView() {
        if (root != null) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        root = null; carousel = null; guest = null; empty = null; cards.clear();
        super.onDestroyView();
    }

    private final class ProfileCard {
        final Account account;
        final String name;
        final String key;
        final FrameLayout view;
        final FrameLayout circle;
        final TextView title;
        final TextView subtitle;
        final TextView status;
        final TextView manage;

        ProfileCard(@Nullable Account saved) {
            account = saved;
            name = account == null ? getString(R.string.personal_accounts_add) :
                    !TextUtils.isEmpty(account.getName()) ? account.getName() :
                            !TextUtils.isEmpty(account.getEmail()) ? account.getEmail() : getString(R.string.settings_accounts);
            key = account == null ? "add" : !TextUtils.isEmpty(account.getEmail()) ? account.getEmail() : name + "|" + account.getAvatarImageUrl();
            view = new FrameLayout(requireContext()); view.setId(View.generateViewId()); view.setFocusable(true);
            view.setClipChildren(false); view.setClipToPadding(false);
            view.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            view.setContentDescription(name); view.setOnClickListener(ignored -> { if (account == null) addAccount(); else choose(account); });
            circle = new FrameLayout(requireContext());
            FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(dp(148), dp(148), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            circleParams.topMargin = dp(20); view.addView(circle, circleParams);
            GradientDrawable placeholder = new GradientDrawable(); placeholder.setShape(GradientDrawable.OVAL);
            placeholder.setColor(account == null ? 0xFF303030 : new int[]{0xFF14516C,0xFF654842,0xFF416253,0xFF594666}[(name.hashCode() & 0x7FFFFFFF) % 4]);
            TextView initial = label(account == null ? "+" : new String(Character.toChars(name.codePointAt(0))), 58, false);
            initial.setGravity(Gravity.CENTER); initial.setBackground(placeholder);
            circle.addView(initial, new FrameLayout.LayoutParams(-1, -1));
            if (account != null && !TextUtils.isEmpty(account.getAvatarImageUrl())) {
                ImageView image = new ImageView(requireContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                circle.addView(image, new FrameLayout.LayoutParams(-1, -1));
                Glide.with(PersonalAccountChooserFragment.this).load(account.getAvatarImageUrl()).circleCrop().into(image);
            }
            title = label(name, 18, true); title.setGravity(Gravity.CENTER); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, dp(24)); titleParams.topMargin = dp(184); view.addView(title, titleParams);
            subtitle = label(account != null && account.getEmail() != null ? account.getEmail() : getString(R.string.personal_accounts_add_hint), 12, false);
            subtitle.setGravity(Gravity.CENTER); subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams subtitleParams = new FrameLayout.LayoutParams(-1, dp(18)); subtitleParams.topMargin = dp(210); view.addView(subtitle, subtitleParams);
            status = label(account != null && account.isSelected() ? getString(R.string.personal_accounts_selected) : getString(R.string.personal_accounts_saved), 12, false);
            status.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, dp(18)); statusParams.topMargin = dp(229); view.addView(status, statusParams);
            manage = label("⚙", 22, false); manage.setGravity(Gravity.CENTER); manage.setFocusable(true);
            manage.setContentDescription(getString(R.string.personal_accounts_manage)); manage.setTextColor(buttonColors()); manage.setBackground(pill());
            manage.setOnClickListener(ignored -> manageAccounts());
            FrameLayout.LayoutParams manageParams = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            manageParams.topMargin = dp(247); view.addView(manage, manageParams);
            manage.setNextFocusDownId(guest.getId());
            manage.setOnKeyListener((ignored, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { if (event.getAction() == KeyEvent.ACTION_DOWN) view.requestFocus(); return true; }
                return false;
            });
            view.setOnKeyListener((ignored, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) { if (account == null) guest.requestFocus(); else manage.requestFocus(); }
                    return true;
                }
                return false;
            });
            showFocus(false);
        }

        void showFocus(boolean focused) {
            GradientDrawable outline = new GradientDrawable(); outline.setShape(GradientDrawable.OVAL);
            outline.setColor(0xFFF1F1F1); circle.setBackground(outline);
            circle.setPadding(focused ? dp(2) : 0, focused ? dp(2) : 0, focused ? dp(2) : 0, focused ? dp(2) : 0);
            circle.animate().scaleX(focused ? 1.16f : 1).scaleY(focused ? 1.16f : 1)
                    .translationY(focused ? dp(-7.5f) : 0).alpha(focused ? 1 : .45f).setDuration(150).start();
            title.setTextColor(focused ? 0xFFF1F1F1 : 0xFF777777);
            subtitle.setTextColor(0xFFAAAAAA); status.setTextColor(0xFFAAAAAA);
            subtitle.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            status.setVisibility(focused && account != null ? View.VISIBLE : View.INVISIBLE);
            manage.setVisibility(focused && account != null ? View.VISIBLE : View.GONE);
        }
    }

    private static boolean isInside(View view, View ancestor) {
        while (view != null) { if (view == ancestor) return true; view = view.getParent() instanceof View ? (View) view.getParent() : null; }
        return false;
    }
    private TextView label(String value, float size, boolean medium) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(size); view.setTextColor(0xFFF1F1F1);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); view.setIncludeFontPadding(false); return view;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private ColorStateList buttonColors() { return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF0F0F0F, 0xFFF1F1F1}); }
    private StateListDrawable pill() {
        StateListDrawable states = new StateListDrawable();
        for (int color : new int[]{0xFFF1F1F1, 0xAA4A3038}) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(24)); states.addState(color == 0xFFF1F1F1 ? new int[]{android.R.attr.state_focused} : new int[]{}, shape); }
        return states;
    }

    /** Original native gradients/shapes; account images come only from the selected service data. */
    private static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        Backdrop(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;
            paint.setShader(new LinearGradient(0, 0, width, height, new int[]{0xFF101010, 0xFF251318, 0xFF580E23}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(width * .7f, height * .7f, width, height, 0xCCB34772, 0xCC7D153D, Shader.TileMode.CLAMP));
            oval.set(width * .72f, height * .74f, width * .94f, height * 1.25f); canvas.drawOval(oval, paint);
            oval.set(width * .87f, height * .8f, width * 1.08f, height * 1.27f); canvas.drawOval(oval, paint);
            paint.setShader(new LinearGradient(width * .6f, height * .9f, width, height, 0xCCDB5562, 0xCC941A47, Shader.TileMode.CLAMP));
            oval.set(width * .59f, height * .91f, width * .99f, height * 1.3f); canvas.drawOval(oval, paint);
        }
    }
}
