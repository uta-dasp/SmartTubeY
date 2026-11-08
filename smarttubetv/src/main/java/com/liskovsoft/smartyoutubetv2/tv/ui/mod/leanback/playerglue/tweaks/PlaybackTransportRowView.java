/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.RestrictTo;
import androidx.leanback.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.PersonalPlayerPresentation;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * View for PlaybackTransportRowPresenter that has a custom focusSearch.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class PlaybackTransportRowView extends LinearLayout {

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public interface OnUnhandledKeyListener {
        /**
         * Returns true if the key event should be consumed.
         */
        boolean onUnhandledKey(KeyEvent event);
    }

    private OnUnhandledKeyListener mOnUnhandledKeyListener;
    private boolean mPersonalSeekFocusRequested;
    private boolean mPersonalSeekActive;
    private boolean mPersonalDefaultFocusPending;

    public PlaybackTransportRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlaybackTransportRowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    void setOnUnhandledKeyListener(OnUnhandledKeyListener listener) {
        mOnUnhandledKeyListener = listener;
    }

    OnUnhandledKeyListener getOnUnhandledKeyListener() {
        return mOnUnhandledKeyListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }
        return mOnUnhandledKeyListener != null && mOnUnhandledKeyListener.onUnhandledKey(event);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (PersonalPlayerPresentation.isEnabled(getContext()) && !mPersonalSeekActive) {
            if (requestPersonalDefaultFocus(direction, previouslyFocusedRect)) return true;
            mPersonalDefaultFocusPending = !mPersonalSeekFocusRequested;
        }
        final View focused = findFocus();
        if (focused != null && focused.requestFocus(direction, previouslyFocusedRect)) {
            return true;
        }
        View progress = findViewById(R.id.playback_progress);
        if (progress != null && progress.isFocusable()) {
            if (progress.requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    public void setPersonalSeekFocusRequested(boolean requested) {
        mPersonalSeekFocusRequested = requested;
        if (requested) mPersonalDefaultFocusPending = false;
    }
    void setPersonalSeekActive(boolean active) {
        mPersonalSeekActive = active;
        if (active) mPersonalDefaultFocusPending = false;
    }

    boolean requestPersonalDefaultFocus(int direction, Rect previous) {
        if (mPersonalSeekActive) return false;
        View target = mPersonalSeekFocusRequested ? findViewById(R.id.playback_progress)
                : findPlayPause(findViewById(R.id.controls_dock));
        if (target == null && !mPersonalSeekFocusRequested) target = findViewById(R.id.controls_dock);
        boolean focused = target != null && target.requestFocus(direction, previous);
        if (focused) mPersonalDefaultFocusPending = false;
        return focused;
    }

    void applyPendingPersonalFocus() {
        if (mPersonalDefaultFocusPending && hasFocus()) requestPersonalDefaultFocus(View.FOCUS_FORWARD, null);
    }

    private static View findPlayPause(View view) {
        if (view == null) return null;
        if (Boolean.TRUE.equals(view.getTag(R.id.lb_control_play_pause))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findPlayPause(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        // when focusSearch vertically, return the next immediate focusable child
        if (focused != null) {
            if (direction == View.FOCUS_UP) {
                int index = indexOfChild(getFocusedChild());
                for (index = index - 1; index >= 0; index--) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_DOWN) {
                int index = indexOfChild(getFocusedChild());
                for (index = index + 1; index < getChildCount(); index++) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                if (getFocusedChild() instanceof ViewGroup) {
                    return FocusFinder.getInstance().findNextFocus(
                            (ViewGroup) getFocusedChild(), focused, direction);
                }
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
