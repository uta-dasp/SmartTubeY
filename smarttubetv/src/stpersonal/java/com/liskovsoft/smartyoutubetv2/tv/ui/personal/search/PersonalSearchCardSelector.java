package com.liskovsoft.smartyoutubetv2.tv.ui.personal.search;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.personal.PersonalVideoCardPresenter;

/** Search keeps channel identities circular and uses the shared compact video presentation. */
final class PersonalSearchCardSelector extends PresenterSelector {
    private final Presenter video;
    private final Presenter channel;
    private final ShortsCardPresenter shorts = new ShortsCardPresenter();

    PersonalSearchCardSelector(OnItemLongPressedListener listener) {
        CompactVideoPresenter video = new CompactVideoPresenter();
        ChannelPresenter channel = new ChannelPresenter();
        video.setOnItemViewLongPressedListener(listener);
        channel.setOnItemViewLongPressedListener(listener);
        shorts.setOnItemViewLongPressedListener(listener);
        this.video = video;
        this.channel = channel;
    }

    @Override public Presenter getPresenter(Object item) {
        Video value = (Video) item;
        return value.isChannel() ? channel : value.isShorts ? shorts : video;
    }
    @Override public Presenter[] getPresenters() { return new Presenter[]{video,channel,shorts}; }

    private static class CompactVideoPresenter extends PersonalVideoCardPresenter {
        @Override protected Pair<Integer,Integer> getCardDimensPx(Context context) {
            return new Pair<>(dp(context,264),dp(context,148.5f));
        }
        @Override protected boolean isLargeCard(Video video) { return false; }
    }

    private static class ChannelPresenter extends LongClickPresenter {
        @Override public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
            Context context = parent.getContext();
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setLayoutParams(new ViewGroup.LayoutParams(dp(context,148.5f), ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            FrameLayout circle = new FrameLayout(context);
            StateListDrawable background = new StateListDrawable();
            GradientDrawable focused = circle(0xFF0F0F0F);
            focused.setStroke(dp(context,4),0xFFF1F1F1);
            background.addState(new int[]{android.R.attr.state_selected},focused);
            background.addState(new int[]{android.R.attr.state_focused},focused);
            background.addState(new int[]{},circle(0xFF0F0F0F));
            circle.setBackground(background);
            circle.setDuplicateParentStateEnabled(true);
            circle.setPadding(dp(context,4),dp(context,4),dp(context,4),dp(context,4));
            ImageView image = new ImageView(context);
            circle.addView(image,new FrameLayout.LayoutParams(-1,-1));
            card.addView(circle,new LinearLayout.LayoutParams(dp(context,148.5f),dp(context,148.5f)));
            TextView title = text(context,18,true);
            title.setPadding(0,dp(context,12),0,0);
            title.setMaxLines(2);
            card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView metadata = text(context,12,false);
            metadata.setPadding(0,dp(context,6),0,0);
            metadata.setMaxLines(3);
            card.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
            return new ChannelHolder(card,image,title,metadata);
        }
        @Override public void onBindViewHolder(Presenter.ViewHolder holder,Object item) {
            super.onBindViewHolder(holder,item);
            ChannelHolder channel = (ChannelHolder) holder;
            Video video = (Video) item;
            channel.title.setText(video.getTitle());
            channel.metadata.setText(video.getSecondTitle());
            Glide.with(channel.image).load(video.cardImageUrl).circleCrop().into(channel.image);
        }
        @Override public void onUnbindViewHolder(Presenter.ViewHolder holder) {
            super.onUnbindViewHolder(holder);
            ChannelHolder channel = (ChannelHolder) holder;
            Glide.with(channel.image).clear(channel.image);
        }
        private static GradientDrawable circle(int color) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL); drawable.setColor(color); return drawable;
        }
        private static TextView text(Context context,int size,boolean bold) {
            TextView view = new TextView(context);
            view.setTextSize(size); view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
            view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
            view.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_selected},{android.R.attr.state_focused},{}},
                    new int[]{0xFFF1F1F1,0xFFF1F1F1,0xFFAAAAAA}));
            view.setDuplicateParentStateEnabled(true); return view;
        }
    }

    private static class ChannelHolder extends Presenter.ViewHolder {
        final ImageView image; final TextView title; final TextView metadata;
        ChannelHolder(View view,ImageView image,TextView title,TextView metadata) {
            super(view); this.image=image;this.title=title;this.metadata=metadata;
        }
    }
    private static int dp(Context context,float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
}
