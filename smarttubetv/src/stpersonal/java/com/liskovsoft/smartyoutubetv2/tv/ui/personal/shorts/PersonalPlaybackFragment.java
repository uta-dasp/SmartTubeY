package com.liskovsoft.smartyoutubetv2.tv.ui.personal.shorts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Build;
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
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.personal.PersonalAccountChooserFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.personal.PersonalBrowseFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Presentation of the existing full player; no engine, controller, queue or MediaSession is replaced. */
public class PersonalPlaybackFragment extends PlaybackFragment implements MediaServiceManager.AccountChangeListener {
    private ShortsRoot wrapper;
    private View playerRoot,surface,subtitles,feed;
    private FrameLayout rail,side;
    private View shortsNavigation;
    private ImageView background,authorAvatar;
    private ImageView railAvatar;
    private TextView railAccountName;
    private TextView title,author,subscription,likeCount;
    private FrameLayout like;
    private View titleCard,authorCard,subscriptionCard;
    private Video shownVideo;
    private String shownId,channelIcon;
    private boolean shortsMode,railShown,regularControls,backConsumed,exitOnBackUp,initialRailFocus,restorePending;
    private FrameLayout.LayoutParams originalSurface,originalSubtitles;
    private Drawable originalSurfaceBackground;
    private boolean originalSurfaceClipping;
    private int[] originalSubtitlePadding;
    private final Set<Object> dialogOwners=Collections.newSetFromMap(new IdentityHashMap<>());
    private final ViewTreeObserver.OnGlobalFocusChangeListener sideFocus=(oldFocus,newFocus)->updateMetadataCards(newFocus);
    private final Runnable restoreAfterDialog=new Runnable(){
        @Override public void run(){
            if(wrapper==null||!shortsMode||!isResumed())return;
            if(AppDialogPresenter.instance(requireContext()).isDialogShown())wrapper.postDelayed(this,100);
            else applyPresentation();
        }
    };

    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle state){
        playerRoot=super.onCreateView(inflater,parent,state);
        wrapper=new ShortsRoot(requireContext());wrapper.setClipChildren(false);
        background=new ImageView(requireContext());background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setColorFilter(0xBB0F0F0F,android.graphics.PorterDuff.Mode.SRC_ATOP);
        wrapper.addView(background,new FrameLayout.LayoutParams(-1,-1));background.setVisibility(View.GONE);
        wrapper.addView(playerRoot,new FrameLayout.LayoutParams(-1,-1));
        surface=playerRoot.findViewById(R.id.surface_root);subtitles=playerRoot.findViewById(R.id.leanback_subtitles);
        originalSurface=copyParams((FrameLayout.LayoutParams)surface.getLayoutParams());
        originalSurfaceBackground=surface.getBackground();originalSurfaceClipping=Build.VERSION.SDK_INT>=21&&surface.getClipToOutline();
        originalSubtitles=copyParams((FrameLayout.LayoutParams)subtitles.getLayoutParams());
        originalSubtitlePadding=new int[]{subtitles.getPaddingLeft(),subtitles.getPaddingTop(),subtitles.getPaddingRight(),subtitles.getPaddingBottom()};
        buildFeed();buildSide();buildRail();
        wrapper.getViewTreeObserver().addOnGlobalFocusChangeListener(sideFocus);
        feed.setVisibility(View.GONE);side.setVisibility(View.GONE);rail.setVisibility(View.GONE);
        MediaServiceManager.instance().addAccountListener(this);
        applyVideo(getVideo()!=null?getVideo():presenter().getVideo());
        return wrapper;
    }

    @Override public void setVideo(Video video){super.setVideo(video);applyVideo(video);}
    private void applyVideo(Video video){
        shownVideo=video;
        boolean next=video!=null&&(video.isShorts||video.belongsToShortsGroup());
        if(next&&!shortsMode&&wrapper!=null&&dialogOwners.isEmpty())snapshotNormalLayout();
        boolean changed=video!=null&&!android.text.TextUtils.equals(video.videoId,shownId);
        if(changed){shownId=video.videoId;channelIcon=null;}
        if(next)PersonalShortsSession.retain(video);
        boolean previousMode=shortsMode;boolean entered=next&&!previousMode;shortsMode=next;
        if(entered){railShown=PersonalShortsSession.consumeRailEntry(video);initialRailFocus=railShown;regularControls=false;}
        if(!next){regularControls=false;if(!previousMode)return;restorePending=true;PersonalShortsSession.clear();}
        if(wrapper==null)return;
        if(changed&&shortsMode&&video.cardImageUrl!=null)Glide.with(background).load(video.cardImageUrl).override(40,40).centerCrop().into(background);
        renderSide();applyPresentation();
        if(entered)wrapper.post(()->{if(wrapper!=null&&shortsMode){super.hideControlsOverlay(false);if(railShown)shortsNavigation.requestFocus();else feed.requestFocus();initialRailFocus=false;}});
    }

    private void buildFeed(){
        feed=new View(requireContext());feed.setFocusable(true);feed.setContentDescription(getString(R.string.personal_shorts_feed));
        StateListDrawable ring=new StateListDrawable();GradientDrawable focused=new GradientDrawable();
        focused.setColor(0x00000000);focused.setCornerRadius(dp(14));focused.setStroke(dp(4),0xFFF1F1F1);
        ring.addState(new int[]{android.R.attr.state_focused},focused);ring.addState(new int[]{},new GradientDrawable());feed.setBackground(ring);
        feed.setOnClickListener(view->togglePlayback());place(wrapper,feed,291.5f,0,313,540);
    }
    private void buildSide(){
        side=new FrameLayout(requireContext());place(wrapper,side,0,0,-1,-1);
        titleCard=new View(requireContext());place(side,titleCard,627,228,306,69);
        authorCard=new View(requireContext());place(side,authorCard,627,354,306,45);
        subscriptionCard=new View(requireContext());place(side,subscriptionCard,627,405,306,45);
        title=text("",18,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);place(side,title,639,240,285,60);
        title.setFocusable(true);title.setTextColor(focusText());title.setOnClickListener(view->action(R.id.action_info));
        authorAvatar=new ImageView(requireContext());place(side,authorAvatar,639,363,27,27);
        author=button("",15);author.setGravity(Gravity.CENTER_VERTICAL);author.setPadding(0,0,0,0);author.setBackgroundColor(0x00000000);
        author.setOnClickListener(view->action(R.id.action_channel));place(side,author,675,365,245,30);
        subscription=button(getString(R.string.action_subscribe),15);subscription.setGravity(Gravity.CENTER_VERTICAL);subscription.setPadding(dp(16),0,dp(12),0);
        Drawable plus=androidx.core.graphics.drawable.DrawableCompat.wrap(androidx.core.content.ContextCompat.getDrawable(requireContext(),R.drawable.personal_shorts_plus)).mutate();
        androidx.core.graphics.drawable.DrawableCompat.setTintList(plus,focusText());subscription.setCompoundDrawablesWithIntrinsicBounds(plus,null,null,null);subscription.setCompoundDrawablePadding(dp(14));
        subscription.setOnClickListener(view->action(R.id.action_subscribe));place(side,subscription,627,405,306,45);
        like=actionButton("like",getString(R.string.personal_shorts_like),()->action(R.id.action_thumbs_up));place(side,like,634,457,36,36);
        likeCount=text("",12,false);likeCount.setGravity(Gravity.CENTER);place(side,likeCount,622,493,60,22);
        FrameLayout comments=actionButton("comments",getString(R.string.personal_shorts_comments),()->action(R.id.action_chat));place(side,comments,682,457,36,36);
        FrameLayout more=actionButton("more",getString(R.string.personal_shorts_more),this::showRegularControls);place(side,more,730,457,36,36);
        verticalFocus(title,null,author);verticalFocus(author,title,subscription);verticalFocus(subscription,author,like);verticalFocus(like,subscription,null);verticalFocus(comments,subscription,null);verticalFocus(more,subscription,null);
        updateMetadataCards(null);
    }
    private void verticalFocus(View view,View up,View down){view.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN){View target=key==KeyEvent.KEYCODE_DPAD_UP?up:key==KeyEvent.KEYCODE_DPAD_DOWN?down:null;if(target!=null){target.requestFocus();return true;}}return false;});}
    private void updateMetadataCards(View focus){
        if(side==null||!shortsMode)return;boolean active=isInside(focus,side);
        View[] cards={titleCard,authorCard,subscriptionCard};View[] controls={title,author,subscription};
        for(int index=0;index<cards.length;index++){
            GradientDrawable shape=new GradientDrawable();shape.setColor(active?(focus==controls[index]?0xFFF1F1F1:0xCC403C39):0x00000000);shape.setCornerRadius(dp(9));cards[index].setBackground(shape);
        }
    }
    private void renderSide(){
        if(side==null||shownVideo==null)return;
        title.setText(shownVideo.getTitleFull());
        author.setText(shownVideo.author!=null?shownVideo.author:shownVideo.getAuthor());
        author.setVisibility(author.getText().length()==0?View.GONE:View.VISIBLE);
        subscription.setText(getButtonState(R.id.action_subscribe)==PlayerUI.BUTTON_ON?R.string.personal_channel_subscribed:R.string.action_subscribe);
        like.setSelected(getButtonState(R.id.action_thumbs_up)==PlayerUI.BUTTON_ON);
        likeCount.setText(shownVideo.likeCount);likeCount.setVisibility(shownVideo.likeCount==null?View.GONE:View.VISIBLE);
        if(channelIcon!=null)Glide.with(authorAvatar).load(channelIcon).circleCrop().into(authorAvatar);else Glide.with(authorAvatar).clear(authorAvatar);
    }
    private void buildRail(){
        rail=new FrameLayout(requireContext());rail.setBackgroundColor(0x880F0F0F);place(wrapper,rail,0,0,267,-1);
        Account account=MediaServiceManager.instance().getSelectedAccount();
        LinearLayout accountButton=new LinearLayout(requireContext());accountButton.setGravity(Gravity.CENTER_VERTICAL);accountButton.setFocusable(true);accountButton.setBackground(buttonBackground(18));
        ImageView avatar=new ImageView(requireContext());avatar.setImageResource(R.drawable.personal_account_placeholder);
        railAvatar=avatar;
        if(account!=null&&account.getAvatarImageUrl()!=null)Glide.with(avatar).load(account.getAvatarImageUrl()).circleCrop().into(avatar);
        accountButton.addView(avatar,new LinearLayout.LayoutParams(dp(27),dp(27)));
        TextView accountName=text(account==null?getString(R.string.personal_guest):account.getName(),15,true);LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(-1,-1);nameParams.leftMargin=dp(9);accountButton.addView(accountName,nameParams);
        railAccountName=accountName;
        accountButton.setOnClickListener(view->PersonalAccountChooserFragment.show(this));place(rail,accountButton,25.5f,33,213,36);
        ScrollView scrolling=new ScrollView(requireContext());scrolling.setVerticalScrollBarEnabled(false);
        LinearLayout items=new LinearLayout(requireContext());items.setOrientation(LinearLayout.VERTICAL);scrolling.addView(items,new ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams scrollParams=new FrameLayout.LayoutParams(-1,-1);scrollParams.topMargin=dp(78);scrollParams.bottomMargin=dp(67.5f);scrollParams.leftMargin=dp(16.5f);scrollParams.rightMargin=dp(28.5f);rail.addView(scrolling,scrollParams);
        addNavigation(items,getString(R.string.personal_search),R.drawable.action_search,()->leaveForSearch());
        addNavigation(items,getString(R.string.header_home),R.drawable.icon_home,()->leaveForSection(MediaGroup.TYPE_HOME));
        shortsNavigation=addNavigation(items,getString(R.string.header_shorts),R.drawable.icon_shorts,()->showRail(false));shortsNavigation.setSelected(true);
        addNavigation(items,getString(R.string.header_subscriptions),R.drawable.icon_subscriptions,()->leaveForSection(MediaGroup.TYPE_SUBSCRIPTIONS));
        addNavigation(items,getString(R.string.personal_library_title),R.drawable.icon_playlist,()->leaveForSection(MediaGroup.TYPE_HISTORY));
        View divider=new View(requireContext());divider.setBackgroundColor(0xFF3F3F3F);LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,dp(1.5f));separator.topMargin=dp(9);separator.bottomMargin=dp(9);items.addView(divider,separator);
        addNavigation(items,getString(R.string.header_music),R.drawable.icon_music,()->leaveForSection(MediaGroup.TYPE_MUSIC));
        addNavigation(items,getString(R.string.badge_live),R.drawable.icon_live,()->leaveForSection(MediaGroup.TYPE_LIVE));
        addNavigation(items,getString(R.string.header_gaming),R.drawable.icon_gaming,()->leaveForSection(MediaGroup.TYPE_GAMING));
        addNavigation(items,getString(R.string.header_sports),R.drawable.icon_sports,()->leaveForSection(MediaGroup.TYPE_SPORTS));
        TextView advanced=addNavigation(items,getString(R.string.personal_navigation_advanced),android.R.drawable.ic_menu_more,()->leaveForSection(-1));
        advanced.setOnFocusChangeListener(null);
        TextView settings=button(getString(R.string.header_settings),15);settings.setOnClickListener(view->leaveForSection(MediaGroup.TYPE_SETTINGS));place(rail,settings,16.5f,472.5f,222,39);
        settings.setOnFocusChangeListener((view,focused)->{if(focused&&!initialRailFocus&&isResumed())leaveForSection(MediaGroup.TYPE_SETTINGS);});
        advanced.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN&&event.getAction()==KeyEvent.ACTION_DOWN){settings.requestFocus();return true;}return false;});
        settings.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_UP&&event.getAction()==KeyEvent.ACTION_DOWN){advanced.requestFocus();return true;}return false;});
    }
    private TextView addNavigation(LinearLayout parent,String label,int icon,Runnable click){
        TextView item=button(label,15);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10.5f),0,dp(12),0);
        Drawable drawable=androidx.core.content.ContextCompat.getDrawable(requireContext(),icon);
        if(drawable!=null){drawable=androidx.core.graphics.drawable.DrawableCompat.wrap(drawable).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(drawable,focusText());drawable.setBounds(0,0,dp(24),dp(24));item.setCompoundDrawables(drawable,null,null,null);item.setCompoundDrawablePadding(dp(10.5f));}
        item.setOnClickListener(view->click.run());
        // Match official focus activation without creating a second BrowsePresenter view.
        item.setOnFocusChangeListener((view,focused)->{if(focused&&!initialRailFocus&&item!=shortsNavigation&&railShown&&isResumed())click.run();});
        parent.addView(item,new LinearLayout.LayoutParams(-1,dp(39)));return item;
    }
    private void showRail(boolean show){railShown=show;applyPresentation();if(show)shortsNavigation.requestFocus();else feed.requestFocus();}
    @Override public void onAccountChanged(Account account){
        if(wrapper==null||!shortsMode)return;
        wrapper.post(()->{if(wrapper!=null&&shortsMode){
            Account current=MediaServiceManager.instance().getSelectedAccount();
            railAccountName.setText(current==null?getString(R.string.personal_guest):current.getName());
            Glide.with(railAvatar).clear(railAvatar);railAvatar.setImageResource(R.drawable.personal_account_placeholder);
            if(current!=null&&current.getAvatarImageUrl()!=null)Glide.with(railAvatar).load(current.getAvatarImageUrl()).circleCrop().into(railAvatar);
            // Re-enter the same full controller path so reaction/subscription metadata is
            // refreshed for the selected account, rather than carrying the prior user's state.
            if(shownVideo!=null)presenter().onNewVideo(shownVideo);
        }});
    }
    private void leaveForSection(int section){
        BrowsePresenter browse=BrowsePresenter.instance(requireContext());
        finishReally();
        if(browse.getView() instanceof PersonalBrowseFragment){PersonalBrowseFragment owner=(PersonalBrowseFragment)browse.getView();if(section==-1)owner.openAdvancedFromPlayer();else owner.openNavigationFromPlayer(section);}
        else if(section>=0)browse.selectSection(section);
    }
    private void leaveForSearch(){finishReally();SearchPresenter.instance(requireContext()).startSearch(null);}
    private void togglePlayback(){if(isEngineInitialized()){if(getPlayWhenReady())presenter().onPauseClicked();else presenter().onPlayClicked();}}
    private void action(int id){presenter().onButtonClicked(id,getButtonState(id));}
    private void showRegularControls(){regularControls=true;applyPresentation();super.showControls(true);}

    private boolean activeShorts(){return shortsMode&&wrapper!=null&&!isInPIPMode();}
    private boolean hasLegacyDialog(){
        AppDialogPresenter dialog=AppDialogPresenter.instance(requireContext());
        return dialog.isDialogShown()&&(dialog.isComments()||dialog.isTransparent()||dialog.isOverlay());
    }
    private void snapshotNormalLayout(){
        if(surface==null||subtitles==null)return;
        originalSurface=copyParams((FrameLayout.LayoutParams)surface.getLayoutParams());
        originalSubtitles=copyParams((FrameLayout.LayoutParams)subtitles.getLayoutParams());
        originalSurfaceBackground=surface.getBackground();originalSurfaceClipping=Build.VERSION.SDK_INT>=21&&surface.getClipToOutline();
        originalSubtitlePadding=new int[]{subtitles.getPaddingLeft(),subtitles.getPaddingTop(),subtitles.getPaddingRight(),subtitles.getPaddingBottom()};
    }
    private void applyPresentation(){
        if(wrapper==null||(!shortsMode&&!restorePending))return;boolean active=activeShorts();
        boolean legacyDialog=hasLegacyDialog();
        boolean chrome=active&&!regularControls&&dialogOwners.isEmpty()&&!legacyDialog;
        background.setVisibility(active?View.VISIBLE:View.GONE);side.setVisibility(chrome?View.VISIBLE:View.GONE);
        feed.setVisibility(chrome?View.VISIBLE:View.GONE);rail.setVisibility(chrome&&railShown?View.VISIBLE:View.GONE);
        // Comments/transparent overlays retain their original zoom/gravity transforms.
        if(!dialogOwners.isEmpty()||legacyDialog)return;
        if(active){
            setBounds(surface,296,4.5f,304,531);setBounds(subtitles,296,4.5f,304,531);subtitles.setPadding(dp(4),0,dp(4),dp(4));
            if(Build.VERSION.SDK_INT>=21){GradientDrawable outline=new GradientDrawable();outline.setColor(0x00000000);outline.setCornerRadius(dp(10));surface.setBackground(outline);surface.setClipToOutline(true);}
        }else{
            surface.setLayoutParams(copyParams(originalSurface));surface.setBackground(originalSurfaceBackground);if(Build.VERSION.SDK_INT>=21)surface.setClipToOutline(originalSurfaceClipping);
            subtitles.setLayoutParams(copyParams(originalSubtitles));subtitles.setPadding(originalSubtitlePadding[0],originalSubtitlePadding[1],originalSubtitlePadding[2],originalSubtitlePadding[3]);
            restorePending=false;
        }
    }
    @Override public void setPlayerDialogVisible(Object owner,boolean visible){
        if(visible){
            if(!shortsMode&&dialogOwners.isEmpty())snapshotNormalLayout();
            if(shortsMode)applyPresentation();
            dialogOwners.add(owner);
        }
        super.setPlayerDialogVisible(owner,visible);
        if(!visible)dialogOwners.remove(owner);
        if(shortsMode||restorePending)applyPresentation();
    }
    @Override public void setVideoGravity(int gravity){super.setVideoGravity(gravity);if(shortsMode)applyPresentation();}
    @Override public void setChannelIcon(String url){super.setChannelIcon(url);if(shortsMode){channelIcon=url;renderSide();}}
    @Override public void setTitle(String text){super.setTitle(text);if(shortsMode)renderSide();}
    @Override public void setButtonState(int id,int state){super.setButtonState(id,state);if(shortsMode)renderSide();}
    @Override public void showControlsOverlay(boolean animate){if(!shortsMode||regularControls)super.showControlsOverlay(animate);}
    @Override public void showOverlay(boolean show){if(!show||!shortsMode||regularControls)super.showOverlay(show);}
    @Override public void hideControlsOverlay(boolean animate){super.hideControlsOverlay(animate);if(regularControls){regularControls=false;applyPresentation();if(activeShorts()&&isResumed())feed.requestFocus();}}
    @Override public void onPIPChanged(boolean pip){super.onPIPChanged(pip);if(shortsMode)applyPresentation();}
    @Override public void rewind(){if(shortsMode&&!regularControls)showRegularControls();super.rewind();}
    @Override public void fastForward(){if(shortsMode&&!regularControls)showRegularControls();super.fastForward();}
    @Override public void onDispatchKeyEvent(KeyEvent event){
        if(shortsMode&&!regularControls&&event.getAction()==KeyEvent.ACTION_DOWN&&
                (event.getKeyCode()==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||event.getKeyCode()==KeyEvent.KEYCODE_MEDIA_REWIND))showRegularControls();
        super.onDispatchKeyEvent(event);
    }
    @Override public void onResume(){super.onResume();if(wrapper!=null&&shortsMode)wrapper.post(restoreAfterDialog);}
    @Override public void onPause(){if(wrapper!=null)wrapper.removeCallbacks(restoreAfterDialog);super.onPause();}
    @Override public void onDestroyView(){
        if(wrapper!=null)wrapper.removeCallbacks(restoreAfterDialog);
        if(wrapper!=null)wrapper.getViewTreeObserver().removeOnGlobalFocusChangeListener(sideFocus);
        MediaServiceManager.instance().removeAccountListener(this);
        if(background!=null)Glide.with(background).clear(background);if(authorAvatar!=null)Glide.with(authorAvatar).clear(authorAvatar);
        if(railAvatar!=null)Glide.with(railAvatar).clear(railAvatar);
        dialogOwners.clear();wrapper=null;surface=null;subtitles=null;feed=null;rail=null;side=null;background=null;authorAvatar=null;super.onDestroyView();
    }
    private PlaybackPresenter presenter(){return PlaybackPresenter.instance(requireContext());}
    private static FrameLayout.LayoutParams copyParams(FrameLayout.LayoutParams from){
        FrameLayout.LayoutParams copy=new FrameLayout.LayoutParams(from.width,from.height,from.gravity);
        copy.setMargins(from.leftMargin,from.topMargin,from.rightMargin,from.bottomMargin);
        if(from.isMarginRelative()){copy.setMarginStart(from.getMarginStart());copy.setMarginEnd(from.getMarginEnd());}
        return copy;
    }
    private void setBounds(View view,float x,float y,float width,float height){FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(width),dp(height),Gravity.TOP|Gravity.LEFT);params.leftMargin=dp(x);params.topMargin=dp(y);view.setLayoutParams(params);}
    private void place(FrameLayout parent,View view,float x,float y,float width,float height){FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width<0?(int)width:dp(width),height<0?(int)height:dp(height));params.leftMargin=dp(x);params.topMargin=dp(y);parent.addView(view,params);}
    private int dp(float value){return Math.round(value*requireContext().getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){TextView view=new TextView(requireContext());view.setText(value);view.setTextSize(size);view.setTextColor(0xFFF1F1F1);view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView button(String value,int size){TextView view=text(value,size,true);view.setFocusable(true);view.setTextColor(focusText());view.setGravity(Gravity.CENTER);view.setPadding(dp(12),0,dp(12),0);view.setBackground(buttonBackground(18));return view;}
    private StateListDrawable buttonBackground(float radius){StateListDrawable states=new StateListDrawable();for(boolean focused:new boolean[]{true,false}){GradientDrawable shape=new GradientDrawable();shape.setColor(focused?0xFFF1F1F1:0x00000000);shape.setCornerRadius(dp(radius));states.addState(focused?new int[]{android.R.attr.state_focused}:new int[]{},shape);}return states;}
    private static ColorStateList focusText(){return new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFF0F0F0F,0xFFF1F1F1});}
    private FrameLayout actionButton(String shape,String label,Runnable click){FrameLayout button=new FrameLayout(requireContext());button.setFocusable(true);button.setContentDescription(label);StateListDrawable colors=new StateListDrawable();for(boolean focus:new boolean[]{true,false}){GradientDrawable color=new GradientDrawable();color.setColor(focus?0xFFF1F1F1:0xAA403C39);color.setCornerRadius(dp(18));colors.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},color);}button.setBackground(colors);button.setOnClickListener(view->click.run());ActionIcon icon=new ActionIcon(requireContext(),shape);icon.setDuplicateParentStateEnabled(true);button.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));return button;}
    private static boolean isInside(View view,View parent){if(view==null||parent==null)return false;for(ViewParent ancestor=view.getParent();ancestor instanceof View;ancestor=ancestor.getParent())if(ancestor==parent)return true;return view==parent;}

    private final class ShortsRoot extends FrameLayout {
        ShortsRoot(Context context){super(context);}
        @Override public boolean dispatchKeyEvent(KeyEvent event){
            if(!activeShorts()||regularControls||superOverlay()||!dialogOwners.isEmpty()||AppDialogPresenter.instance(getContext()).isDialogShown())return super.dispatchKeyEvent(event);
            if(event.getAction()==KeyEvent.ACTION_DOWN)presenter().onKeyDown(-1); // Preserve source activity/sleep timers without remapping feed navigation.
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_BACK){
                if(event.getAction()==KeyEvent.ACTION_UP&&backConsumed){backConsumed=false;if(exitOnBackUp){exitOnBackUp=false;finishReally();}return true;}
                if(event.getAction()==KeyEvent.ACTION_DOWN){if(!backConsumed){backConsumed=true;if(!railShown)showRail(true);else exitOnBackUp=true;}return true;}
            }
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(event);
            View focused=findFocus();
            if(key==KeyEvent.KEYCODE_DPAD_LEFT&&(focused==title||focused==author||focused==subscription||focused==like)){feed.requestFocus();return true;}
            if(railShown&&key==KeyEvent.KEYCODE_DPAD_RIGHT){showRail(false);return true;}
            if(focused==feed){
                if(event.getRepeatCount()>0&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN))return true;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){presenter().onNextClicked();return true;}
                // The source UI's Previous restarts after a threshold. Shorts Up must select
                // the previous source queue item; loadPrevious still dispatches onNewVideo,
                // including VideoState saving, SponsorBlock and the same format/engine path.
                if(key==KeyEvent.KEYCODE_DPAD_UP){presenter().getController(VideoLoaderController.class).loadPrevious();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){showRail(true);return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){like.requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_MENU){showRegularControls();return true;}
            }
            return super.dispatchKeyEvent(event);
        }
        private boolean superOverlay(){return PersonalPlaybackFragment.super.isOverlayShown();}
    }
    private static final class ActionIcon extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final String shape;
        ActionIcon(Context context,String shape){super(context);this.shape=shape;}
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);boolean focused=false,selected=false;
            for(int state:getDrawableState()){focused|=state==android.R.attr.state_focused;selected|=state==android.R.attr.state_selected;}
            paint.setColor(focused?0xFF0F0F0F:selected?0xFF3EA6FF:0xFFF1F1F1);paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            if("like".equals(shape)){path.moveTo(12,20);path.cubicTo(1,14,0,7,5,4);path.cubicTo(8,2,11,4,12,6);path.cubicTo(14,2,20,2,22,7);path.cubicTo(24,12,18,17,12,20);canvas.drawPath(path,paint);}
            else if("comments".equals(shape)){path.moveTo(3,3);path.lineTo(21,3);path.lineTo(21,17);path.lineTo(10,17);path.lineTo(5,21);path.lineTo(5,17);path.lineTo(3,17);path.close();canvas.drawPath(path,paint);canvas.drawLine(7,8,17,8,paint);canvas.drawLine(7,12,14,12,paint);}
            else{paint.setStyle(Paint.Style.FILL);for(int x:new int[]{5,12,19})canvas.drawCircle(x,12,1.6f,paint);}canvas.restore();
        }
    }
}
