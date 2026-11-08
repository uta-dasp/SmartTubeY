package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.leanback.widget.Presenter;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;

/** Recycled subscribed-channel rows, including their real new-content indicators. */
final class PersonalSubscriptionChannelPresenter extends LongClickPresenter {
    static final class Entry {
        final Video video;
        final String title;
        final String key;
        boolean selected;
        Entry(Video video,String title,String key){this.video=video;this.title=title;this.key=key;}
    }

    @Override public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent){
        Context context=parent.getContext();
        LinearLayout row=new LinearLayout(context);
        row.setLayoutParams(new ViewGroup.LayoutParams(dp(context,210),dp(context,39)));
        row.setGravity(Gravity.CENTER_VERTICAL);row.setFocusable(true);row.setFocusableInTouchMode(true);
        row.setPadding(dp(context,9.5f),0,dp(context,12),0);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},rounded(context,0xFFF1F1F1));
        background.addState(new int[]{android.R.attr.state_selected},rounded(context,0xFF272727));
        background.addState(new int[]{},rounded(context,0x00000000));row.setBackground(background);
        FrameLayout avatar=new FrameLayout(context);avatar.setDuplicateParentStateEnabled(true);
        ImageView image=new ImageView(context);avatar.addView(image,new FrameLayout.LayoutParams(-1,-1));
        AllIcon all=new AllIcon(context);all.setDuplicateParentStateEnabled(true);avatar.addView(all,new FrameLayout.LayoutParams(-1,-1));
        View dot=new View(context);GradientDrawable blue=new GradientDrawable();blue.setShape(GradientDrawable.OVAL);blue.setColor(0xFF3EA6FF);
        dot.setBackground(blue);FrameLayout.LayoutParams dotParams=new FrameLayout.LayoutParams(dp(context,6),dp(context,6),Gravity.BOTTOM|Gravity.RIGHT);
        avatar.addView(dot,dotParams);row.addView(avatar,new LinearLayout.LayoutParams(dp(context,27),dp(context,27)));
        TextView title=new TextView(context);title.setTextSize(15);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        title.setIncludeFontPadding(false);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFF0F0F0F,0xFFF1F1F1}));
        title.setDuplicateParentStateEnabled(true);title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-1);titleParams.leftMargin=dp(context,9);row.addView(title,titleParams);
        return new Holder(row,image,all,dot,title);
    }

    @Override public void onBindViewHolder(Presenter.ViewHolder holder,Object item){
        super.onBindViewHolder(holder,item);
        Holder row=(Holder)holder;Entry entry=(Entry)item;
        row.title.setText(entry.title);row.view.setContentDescription(entry.title);row.view.setSelected(entry.selected);
        row.image.setVisibility(entry.video==null?View.GONE:View.VISIBLE);
        row.all.setVisibility(entry.video==null?View.VISIBLE:View.GONE);
        row.dot.setVisibility(entry.video!=null&&entry.video.hasNewContent?View.VISIBLE:View.GONE);
        if(entry.video!=null)Glide.with(row.image).load(entry.video.cardImageUrl).circleCrop().into(row.image);
        else Glide.with(row.image).clear(row.image);
    }
    @Override public void onUnbindViewHolder(Presenter.ViewHolder holder){super.onUnbindViewHolder(holder);Glide.with(((Holder)holder).image).clear(((Holder)holder).image);}

    private static final class Holder extends Presenter.ViewHolder{
        final ImageView image;final AllIcon all;final View dot;final TextView title;
        Holder(View view,ImageView image,AllIcon all,View dot,TextView title){super(view);this.image=image;this.all=all;this.dot=dot;this.title=title;}
    }
    private static GradientDrawable rounded(Context context,int color){GradientDrawable value=new GradientDrawable();value.setColor(color);value.setCornerRadius(dp(context,9));return value;}
    private static int dp(Context context,float value){return PersonalVideoCardPresenter.dp(context,value);}

    private static final class AllIcon extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        AllIcon(Context context){super(context);}
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);
            boolean focused=false;for(int state:getDrawableState())if(state==android.R.attr.state_focused)focused=true;
            paint.setColor(focused?0xFF0F0F0F:0xFFF1F1F1);paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);
            for(int y:new int[]{7,12,17}){canvas.drawCircle(6,y,0.8f,paint);canvas.drawLine(10,y,18,y,paint);}canvas.restore();
        }
    }
}
