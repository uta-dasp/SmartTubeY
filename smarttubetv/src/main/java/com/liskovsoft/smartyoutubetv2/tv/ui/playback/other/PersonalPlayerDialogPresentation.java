package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resizes the existing video/subtitle hosts while a player-origin settings activity is visible.
 * It never replaces the surface, detaches a player, or changes playback/track callbacks.
 */
public final class PersonalPlayerDialogPresentation {
    private final View mRoot;
    private final View mSurface;
    private final View mSubtitles;
    private final Map<Object, Boolean> mOwners = new WeakHashMap<>();
    private final Map<View, Integer> mVisibility = new HashMap<>();
    private ViewGroup.LayoutParams mSurfaceParams;
    private ViewGroup.LayoutParams mSubtitleParams;
    private Drawable mRootBackground;
    private Drawable mSurfaceBackground;
    private ViewOutlineProvider mOutlineProvider;
    private boolean mClipToOutline;
    private int[] mSubtitlePadding;
    private boolean mSaved;
    private final View.OnLayoutChangeListener mLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!mOwners.isEmpty() && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                    applyPreview();
                }
            };

    public PersonalPlayerDialogPresentation(View root) {
        mRoot = root;
        mSurface = root.findViewById(R.id.surface_root);
        mSubtitles = root.findViewById(R.id.leanback_subtitles);
        mRoot.addOnLayoutChangeListener(mLayoutListener);
    }

    public void setVisible(Object owner, boolean visible) {
        if (visible) {
            mOwners.put(owner, Boolean.TRUE);
            if (mRoot.getWidth() > 0) {
                applyPreview();
            } else {
                mRoot.post(() -> {
                    if (!mOwners.isEmpty()) {
                        applyPreview();
                    }
                });
            }
        } else {
            mOwners.remove(owner);
            if (mOwners.isEmpty()) {
                restore();
            }
        }
    }

    public void release() {
        mOwners.clear();
        restore();
        mRoot.removeOnLayoutChangeListener(mLayoutListener);
    }

    private void applyPreview() {
        if (mSurface == null || mRoot.getWidth() <= 0 || mRoot.getHeight() <= 0) {
            return;
        }
        if (!mSaved) {
            mSurfaceParams = mSurface.getLayoutParams();
            mRootBackground = mRoot.getBackground();
            mSurfaceBackground = mSurface.getBackground();
            if (Build.VERSION.SDK_INT >= 21) {
                mClipToOutline = mSurface.getClipToOutline();
                mOutlineProvider = mSurface.getOutlineProvider();
            }
            if (mSubtitles != null) {
                mSubtitleParams = mSubtitles.getLayoutParams();
                mSubtitlePadding = new int[]{mSubtitles.getPaddingLeft(), mSubtitles.getPaddingTop(),
                        mSubtitles.getPaddingRight(), mSubtitles.getPaddingBottom()};
            }
            int[] hidden = {R.id.playback_fragment_background, R.id.playback_controls_dock,
                    R.id.live_chat_wrapper, R.id.player_overlay_wrapper, R.id.debug_view_group};
            for (int id : hidden) {
                View view = mRoot.findViewById(id);
                if (view != null) {
                    mVisibility.put(view, view.getVisibility());
                    view.setVisibility(View.INVISIBLE);
                }
            }
            mSaved = true;
        }
        int width = Math.round(mRoot.getWidth() * 0.596875f);
        int height = Math.min(Math.round(width * 9f / 16f), mRoot.getHeight());
        int left = Math.round(mRoot.getWidth() * 0.028125f);
        mRoot.setBackgroundColor(Color.rgb(15, 15, 15));
        mSurface.setLayoutParams(previewParams(width, height, left));
        if (Build.VERSION.SDK_INT >= 21) {
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(Color.BLACK);
            outline.setCornerRadius(mRoot.getWidth() * 0.021875f);
            mSurface.setBackground(outline);
            mSurface.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            mSurface.setClipToOutline(true);
        }
        if (mSubtitles != null) {
            mSubtitles.setLayoutParams(previewParams(width, height, left));
            mSubtitles.setPadding(Math.round(mSubtitlePadding[0] * 0.596875f), mSubtitlePadding[1],
                    Math.round(mSubtitlePadding[2] * 0.596875f), mSubtitlePadding[3]);
        }
    }

    private FrameLayout.LayoutParams previewParams(int width, int height, int left) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        params.leftMargin = left;
        return params;
    }

    private void restore() {
        if (!mSaved) {
            return;
        }
        mSurface.setLayoutParams(mSurfaceParams);
        mSurface.setBackground(mSurfaceBackground);
        mRoot.setBackground(mRootBackground);
        if (Build.VERSION.SDK_INT >= 21) {
            mSurface.setClipToOutline(mClipToOutline);
            mSurface.setOutlineProvider(mOutlineProvider);
        }
        if (mSubtitles != null) {
            mSubtitles.setLayoutParams(mSubtitleParams);
            mSubtitles.setPadding(mSubtitlePadding[0], mSubtitlePadding[1], mSubtitlePadding[2], mSubtitlePadding[3]);
        }
        for (Map.Entry<View, Integer> entry : mVisibility.entrySet()) {
            // A player resume/chat callback may already have applied a newer visibility.
            if (entry.getKey().getVisibility() == View.INVISIBLE) {
                entry.getKey().setVisibility(entry.getValue());
            }
        }
        mVisibility.clear();
        mSaved = false;
    }
}
