package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

/** Reference presentation around the existing thumbnail, preview and watched-state bindings. */
public class PersonalVideoCardPresenter extends VideoCardPresenter {
    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return new Pair<>(dp(context, 360), dp(context, 202.5f));
    }

    @Override
    protected boolean isCardMultilineTitleEnabled(Context context) { return true; }

    @Override
    protected boolean isCardMultilineSubtitleEnabled(Context context) { return true; }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        Presenter.ViewHolder holder = super.onCreateViewHolder(parent);
        ComplexImageCardView card = (ComplexImageCardView) holder.view;
        Context context = parent.getContext();
        card.setBackgroundColor(Color.TRANSPARENT);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(0, 0, 0, 0);
        View image = card.findViewById(R.id.main_image_wrapper);
        if (image != null) {
            GradientDrawable rounded = shape(context, Color.TRANSPARENT, false);
            image.setBackground(rounded);
            if (android.os.Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            StateListDrawable ring = new StateListDrawable();
            ring.addState(new int[]{android.R.attr.state_selected}, shape(context, Color.TRANSPARENT, true));
            ring.addState(new int[]{android.R.attr.state_focused}, shape(context, Color.TRANSPARENT, true));
            ring.addState(new int[]{}, shape(context, Color.TRANSPARENT, false));
            if (android.os.Build.VERSION.SDK_INT >= 23) image.setForeground(ring);
        }
        TextView title = card.findViewById(R.id.title_text);
        if (title != null) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setIncludeFontPadding(false);
            title.setPadding(0, dp(context, 12), 0, 0);
        }
        TextView subtitle = card.findViewById(R.id.content_text);
        if (subtitle != null) {
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            subtitle.setIncludeFontPadding(false);
            subtitle.setPadding(0, dp(context, 4), 0, 0);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
        super.onBindViewHolder(holder, item);
        ComplexImageCardView card = (ComplexImageCardView) holder.view;
        Video video = (Video) item;
        boolean large = isLargeCard(video);
        card.setMainImageDimensions(dp(card.getContext(), large ? 360 : 264),
                dp(card.getContext(), large ? 202.5f : 148.5f));
        View info = card.findViewById(R.id.info_field);
        if (info != null) {
            info.setBackgroundColor(0xFF0F0F0F);
            info.setPadding(0, 0, 0, 0);
        }
    }

    protected boolean isLargeCard(Video video) {
        return video.getGroup() == null || video.getGroup().getPosition() <= 0;
    }

    private static GradientDrawable shape(Context context, int color, boolean focused) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(context, 9));
        if (focused) result.setStroke(dp(context, 4), 0xFFF1F1F1);
        return result;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
