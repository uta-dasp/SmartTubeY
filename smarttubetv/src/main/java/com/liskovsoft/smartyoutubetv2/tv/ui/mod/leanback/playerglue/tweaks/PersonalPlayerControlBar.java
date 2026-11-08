package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Positions existing secondary action views into edge groups, leaving transport controls centered. */
public class PersonalPlayerControlBar extends ControlBar {
    public PersonalPlayerControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The legacy bar adjusts its measured width to center-space wrap-content children.
        // This bar fills its dock and positions children itself, so retain the parent's width.
        super.setChildMarginFromCenter(0);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        ViewParent parent = getParent();
        while (parent instanceof View && ((View) parent).getId() != R.id.secondary_controls_dock) {
            parent = parent.getParent();
        }
        if (!(parent instanceof View)) {
            return;
        }
        int split = Math.min(3, getChildCount());
        int gap = Math.round(getResources().getDisplayMetrics().density * 6);
        int cursor = getPaddingLeft();
        for (int i = 0; i < split; i++) {
            View child = getChildAt(i);
            int y = (getHeight() - child.getMeasuredHeight()) / 2;
            child.layout(cursor, y, cursor + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            cursor += child.getMeasuredWidth() + gap;
        }
        cursor = getWidth() - getPaddingRight();
        for (int i = getChildCount() - 1; i >= split; i--) {
            View child = getChildAt(i);
            int y = (getHeight() - child.getMeasuredHeight()) / 2;
            child.layout(cursor - child.getMeasuredWidth(), y, cursor, y + child.getMeasuredHeight());
            cursor -= child.getMeasuredWidth() + gap;
        }
    }
}
