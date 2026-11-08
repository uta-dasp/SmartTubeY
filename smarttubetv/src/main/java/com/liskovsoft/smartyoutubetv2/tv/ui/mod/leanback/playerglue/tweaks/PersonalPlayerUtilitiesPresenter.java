package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.leanback.R;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.ControlButtonPresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.TooltipCompatHandler;
import java.util.ArrayList;
import java.util.List;

/** A scrolling third bar that binds original actions without the two core bars' item limit. */
final class PersonalPlayerUtilitiesPresenter extends Presenter {
    ControlBarPresenter.OnControlClickedListener onClicked;
    ControlBarPresenter.OnControlLongClickedListener onLongClicked;
    ControlBarPresenter.OnControlSelectedListener onSelected;

    final class ViewHolder extends Presenter.ViewHolder {
        final HorizontalScrollView scroll;
        final LinearLayout controls;
        final List<Presenter.ViewHolder> buttons = new ArrayList<>();
        final List<LinearLayout> cells = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        ControlBarPresenter.BoundData data;
        ObjectAdapter adapter;
        Presenter presenter;
        Runnable onUp;
        View downTarget;
        int lastFocus;
        final ObjectAdapter.DataObserver observer = new ObjectAdapter.DataObserver() {
            @Override public void onChanged() { bindAll(); }
            @Override public void onItemRangeChanged(int start, int count) {
                for (int index = start; index < start + count; index++) bindButton(index);
            }
            @Override public void onItemRangeInserted(int start, int count) { bindAll(); }
            @Override public void onItemRangeRemoved(int start, int count) { bindAll(); }
        };

        ViewHolder(HorizontalScrollView scroll, LinearLayout controls) {
            super(scroll);
            this.scroll = scroll;
            this.controls = controls;
        }

        void bindAll() {
            int size = adapter == null ? 0 : adapter.size();
            while (buttons.size() > size) {
                Presenter.ViewHolder removed = buttons.remove(buttons.size() - 1);
                releaseButton(removed);
                controls.removeView(cells.remove(cells.size() - 1));
                labels.remove(labels.size() - 1);
            }
            for (int index = 0; index < size; index++) bindButton(index);
        }

        void bindButton(int index) {
            if (data == null || adapter == null || index >= adapter.size()) return;
            if (index >= buttons.size()) {
                Presenter.ViewHolder button = presenter.onCreateViewHolder(controls);
                float density = controls.getResources().getDisplayMetrics().density;
                LinearLayout cell = new LinearLayout(controls.getContext());
                cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                int padding = Math.round(4 * density); cell.setPadding(padding, 0, padding, 0);
                cell.addView(button.view, new LinearLayout.LayoutParams(-2, -2));
                TextView label = new TextView(controls.getContext());
                label.setTextColor(0xFFF1F1F1); label.setTextSize(11.5f); label.setGravity(Gravity.CENTER);
                label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END); label.setIncludeFontPadding(false);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
                labelParams.topMargin = Math.round(2 * density); cell.addView(label, labelParams);
                controls.addView(cell, new LinearLayout.LayoutParams(Math.round(96 * density), -1));
                cells.add(cell); labels.add(label);
                buttons.add(button);
                presenter.setOnClickListener(button, view -> {
                    if (data != null && onClicked != null) onClicked.onControlClicked(button, adapter.get(index), data);
                });
                ((ControlButtonPresenterSelector.ControlButtonPresenter) presenter)
                        .setOnLongClickListener(button, view -> data != null && onLongClicked != null &&
                                onLongClicked.onControlLongClicked(button, adapter.get(index), data));
            }
            Presenter.ViewHolder button = buttons.get(index);
            presenter.onBindViewHolder(button, adapter.get(index));
            labels.get(index).setText(((androidx.leanback.widget.Action) adapter.get(index)).getLabel1());
            View focus = button.view.findViewById(R.id.button);
            View.OnFocusChangeListener style = focus.getOnFocusChangeListener();
            focus.setOnFocusChangeListener((view, focused) -> {
                if (style != null) style.onFocusChange(view, focused);
                if (focused) {
                    lastFocus = index;
                    View cell = cells.get(index);
                    cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight()), true);
                    if (data != null && onSelected != null) onSelected.onControlSelected(button, adapter.get(index), data);
                }
            });
            focus.setOnKeyListener((view, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_DOWN && downTarget != null) return downTarget.requestFocus();
                if (key == KeyEvent.KEYCODE_DPAD_UP && onUp != null) { onUp.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    if (next >= 0 && next < buttons.size()) buttons.get(next).view.requestFocus();
                    return true; // Keep the first/last boundary inside this scrolling bar.
                }
                return false;
            });
        }

        void releaseButton(Presenter.ViewHolder button) {
            View focus = button.view.findViewById(R.id.button);
            TooltipCompatHandler.setTooltipText(focus, null);
            focus.setOnClickListener(null); focus.setOnLongClickListener(null);
            focus.setOnFocusChangeListener(null); focus.setOnKeyListener(null);
            presenter.onUnbindViewHolder(button);
        }
    }

    @Override public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        HorizontalScrollView scroll = new HorizontalScrollView(parent.getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout controls = new LinearLayout(parent.getContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(controls, new HorizontalScrollView.LayoutParams(-2, -1));
        return new ViewHolder(scroll, controls);
    }

    @Override public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
        ViewHolder vh = (ViewHolder) holder;
        ControlBarPresenter.BoundData next = (ControlBarPresenter.BoundData) item;
        if (vh.data != next || vh.adapter != next.adapter || vh.presenter != next.presenter) {
            onUnbindViewHolder(holder);
            vh.data = next; vh.adapter = next.adapter; vh.presenter = next.presenter;
            if (vh.adapter != null) vh.adapter.registerObserver(vh.observer);
        }
        vh.bindAll();
    }

    @Override public void onUnbindViewHolder(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        if (vh.data != null) {
            if (vh.adapter != null) vh.adapter.unregisterObserver(vh.observer);
            for (Presenter.ViewHolder button : vh.buttons) vh.releaseButton(button);
        }
        vh.controls.removeAllViews();
        vh.buttons.clear();
        vh.cells.clear(); vh.labels.clear();
        vh.data = null;
        vh.adapter = null; vh.presenter = null;
        vh.downTarget = null; vh.onUp = null;
    }

    boolean focusLast(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        return !vh.buttons.isEmpty() && vh.buttons.get(Math.min(vh.lastFocus, vh.buttons.size() - 1)).view.requestFocus();
    }
}
