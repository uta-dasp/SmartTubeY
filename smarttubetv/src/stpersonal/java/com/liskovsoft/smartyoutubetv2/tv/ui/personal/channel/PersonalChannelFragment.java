package com.liskovsoft.smartyoutubetv2.tv.ui.personal.channel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.channel.ChannelFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.personal.PersonalVideoCardPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Native channel hero around the original ChannelPresenter shelves, menus and continuation flow. */
public class PersonalChannelFragment extends ChannelFragment implements MediaServiceManager.AccountChangeListener {
    private final HeroRow hero=new HeroRow();
    private FrameLayout wrapper;
    private ImageView banner;
    private EditText search;
    private TextView microphone,empty;
    private HeroHolder heroHolder;
    private PersonalChannelVoiceController voice;
    private PersonalChannelMetadata details;
    private MediaItemMetadata videoMetadata;
    private Video firstVideo,liveVideo;
    private String requestedChannel;
    private int generation,accountGeneration;
    private boolean collapsed,loading,stateRequested,isSubscribed;
    private Disposable detailsRequest,stateRequest,subscriptionRequest,uploadsRequest;

    @Override protected boolean shouldShowSearchHeader(){return false;}

    @Override public void onCreate(@Nullable Bundle state){
        super.onCreate(state);
        voice=new PersonalChannelVoiceController(this);
        ClassPresenterSelector selector=(ClassPresenterSelector)getAdapter().getPresenterSelector();
        selector.addClassPresenter(HeroRow.class,new HeroPresenter());
        ensureHero();
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){
        View rows=super.onCreateView(inflater,parent,state);
        wrapper=new FrameLayout(requireContext());wrapper.setBackgroundColor(0xFF0F0F0F);
        banner=new ImageView(requireContext());banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wrapper.addView(banner,new FrameLayout.LayoutParams(-1,-1));
        View horizontalShade=new View(requireContext());horizontalShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xEF0F0F0F,0x600F0F0F,0x000F0F0F}));
        wrapper.addView(horizontalShade,new FrameLayout.LayoutParams(-1,-1));
        View verticalShade=new View(requireContext());verticalShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x000F0F0F,0x150F0F0F,0xE60F0F0F}));
        wrapper.addView(verticalShade,new FrameLayout.LayoutParams(-1,-1));
        wrapper.addView(rows,new FrameLayout.LayoutParams(-1,-1));
        microphone=button("",36);
        microphone.setContentDescription(getString(R.string.personal_voice_search));microphone.setOnClickListener(view->voice.start());
        // Reuse the app's vector; no official application resources are copied.
        Drawable microphoneIcon=DrawableCompat.wrap(ContextCompat.getDrawable(requireContext(),R.drawable.personal_channel_mic)).mutate();
        DrawableCompat.setTintList(microphoneIcon,focusText());microphone.setCompoundDrawablesWithIntrinsicBounds(microphoneIcon,null,null,null);
        microphone.setCompoundDrawablePadding(0);microphone.setPadding(dp(9),0,dp(9),0);
        place(wrapper,microphone,48,33,36,36);
        search=new EditText(requireContext());search.setId(View.generateViewId());search.setSingleLine(true);
        search.setHint(R.string.personal_search);search.setTextSize(18);search.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        search.setTextColor(focusText());search.setHintTextColor(focusText());search.setIncludeFontPadding(false);
        search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(16),0,dp(16),0);search.setBackground(background(18));
        search.setSelectAllOnFocus(true);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);if(Build.VERSION.SDK_INT>=21)search.setShowSoftInputOnFocus(false);
        search.setOnClickListener(view->{if(Build.VERSION.SDK_INT>=21)search.setShowSoftInputOnFocus(true);InputMethodManager manager=(InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(manager!=null)manager.showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);});
        search.setOnEditorActionListener((view,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH||(event!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){submitSearch();return true;}return false;
        });
        channelOptionsOnHold(search);
        place(wrapper,search,93,33,315,36);
        empty=button(getString(R.string.personal_channel_no_content),15);empty.setVisibility(View.GONE);
        empty.setOnClickListener(view->presenter().onViewInitialized());place(wrapper,empty,48,445,650,60);
        return wrapper;
    }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        super.onViewCreated(view,state);
        getVerticalGridView().setPadding(0,0,0,0);getVerticalGridView().setClipToPadding(false);setAlignment(0);
        getVerticalGridView().addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener(){
            @Override public void onChildViewHolderSelected(RecyclerView parent,RecyclerView.ViewHolder holder,int position,int subposition){
                boolean next=position>0;if(next==collapsed)return;collapsed=next;
                // Official TV collapses the hero on entering shelves; channel search remains fixed.
                view.post(()->{if(wrapper!=null){setAlignment(collapsed?dp(130.5f):0);banner.setAlpha(collapsed?0:1);if(heroHolder!=null)heroHolder.view.setAlpha(collapsed?0:1);}});
            }
        });
        getVerticalGridView().setOnKeyInterceptListener(event->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP&&getSelectedPosition()==1){setPosition(0);if(heroHolder!=null)heroHolder.view.requestFocus();return true;}return false;
        });
        MediaServiceManager.instance().addAccountListener(this);ensureChannel();requestVideoMetadata();renderHero();
    }

    @Override public void clear(){
        super.clear();ensureHero();
        if(wrapper!=null){setAlignment(0);collapsed=false;banner.setAlpha(1);ensureChannel();}
    }

    @Override public void update(VideoGroup group){
        if(group==null)return;
        ensureHero();ensureChannel();
        boolean discoveredLive=false;
        if(group.getAction()!=VideoGroup.ACTION_SYNC&&group.getAction()!=VideoGroup.ACTION_REMOVE){
            for(Video video:group.getVideos()){
                if(video.hasVideo()&&firstVideo==null)firstVideo=video;
                if(video.hasVideo()&&video.isLive&&!video.isUpcoming&&liveVideo==null){liveVideo=video;discoveredLive=true;}
            }
            requestVideoMetadata();
        }
        // The source search/sort positions assume an old search header at index 0.
        int sourcePosition=group.getPosition();
        if(sourcePosition>=0)group.setPosition(sourcePosition+1);
        try{super.update(group);}finally{group.setPosition(sourcePosition);}
        renderHero();renderEmpty();
        if(discoveredLive&&getSelectedPosition()==0&&heroHolder!=null&&heroHolder.subscription.hasFocus())heroHolder.watchLive.requestFocus();
    }

    @Override public void showProgressBar(boolean show){super.showProgressBar(show);loading=show;renderEmpty();}

    private void ensureHero(){
        if(!(getAdapter() instanceof ArrayObjectAdapter))return;
        ArrayObjectAdapter rows=(ArrayObjectAdapter)getAdapter();
        if(rows.indexOf(hero)<0)rows.add(0,hero);
    }

    private void ensureChannel(){
        if(!isAdded()||wrapper==null)return;String channel=presenter().getChannelId();
        if(channel==null||channel.equals(requestedChannel))return;
        generation++;dispose(detailsRequest);dispose(stateRequest);dispose(subscriptionRequest);dispose(uploadsRequest);
        if(requestedChannel!=null){firstVideo=null;liveVideo=null;}
        requestedChannel=channel;details=null;videoMetadata=null;stateRequested=false;isSubscribed=false;
        final int requestGeneration=generation;
        detailsRequest=PersonalChannelMetadata.load(channel).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(result->{if(current(requestGeneration)){details=result;applyVideoMetadata();renderHero();loadBanner();}},error->{ /* Keep available service/seed fields. */ });
        renderHero();loadBanner();
    }

    private void requestVideoMetadata(){
        if(firstVideo==null||stateRequested||wrapper==null)return;stateRequested=true;final int requestGeneration=generation,requestAccount=accountGeneration;
        stateRequest=itemService().getMetadataObserve(firstVideo.mediaItem!=null?firstVideo.mediaItem:SimpleMediaItem.from(firstVideo))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(metadata->{if(current(requestGeneration)&&requestAccount==accountGeneration){videoMetadata=metadata;applyVideoMetadata();renderHero();}},error->{ });
    }

    private void applyVideoMetadata(){
        if(videoMetadata!=null&&channelId()!=null&&channelId().equals(videoMetadata.getChannelId()))isSubscribed=videoMetadata.isSubscribed();
    }

    @Override public void onAccountChanged(Account account){
        if(wrapper==null)return;
        wrapper.post(()->{if(wrapper!=null){
            accountGeneration++;dispose(stateRequest);dispose(subscriptionRequest);stateRequested=false;videoMetadata=null;isSubscribed=false;
            requestVideoMetadata();renderHero();
        }});
    }

    private void loadBanner(){if(banner!=null){if(details!=null&&details.bannerUrl!=null)Glide.with(banner).load(details.bannerUrl).centerCrop().into(banner);else Glide.with(banner).clear(banner);}}

    private void renderHero(){
        if(heroHolder==null)return;HeroHolder holder=heroHolder;Video seed=presenter().getChannel();
        String title=details!=null?details.title:null;
        if(title==null&&seed!=null)title=seed.isChannel()?seed.getTitle():seed.getAuthor();
        if(title==null&&videoMetadata!=null&&channelId()!=null&&channelId().equals(videoMetadata.getChannelId()))title=videoMetadata.getAuthor();
        holder.name.setText(title!=null?title:requestedChannel);
        String handle=details!=null?details.handle:requestedChannel!=null&&requestedChannel.startsWith("@")?requestedChannel:null;
        String subscribers=details!=null&&details.subscribers!=null?getString(R.string.personal_channel_subscribers,formatCount(details.subscribers)):null;
        if(subscribers==null&&videoMetadata!=null&&channelId()!=null&&channelId().equals(videoMetadata.getChannelId()))subscribers=videoMetadata.getSubscriberCount();
        holder.facts.setText(handle==null?subscribers:subscribers==null?handle:handle+" · "+subscribers);
        holder.facts.setVisibility(TextUtils.isEmpty(holder.facts.getText())?View.GONE:View.VISIBLE);
        String avatar=details!=null?details.avatarUrl:null;
        if(avatar==null&&videoMetadata!=null&&channelId()!=null&&channelId().equals(videoMetadata.getChannelId()))avatar=videoMetadata.getAuthorImageUrl();
        if(avatar==null&&seed!=null&&seed.isChannel())avatar=seed.cardImageUrl;
        if(avatar!=null)Glide.with(holder.avatar).load(avatar).circleCrop().into(holder.avatar);else Glide.with(holder.avatar).clear(holder.avatar);
        String description=details==null?null:details.description;
        holder.description.setText(description==null?"":description+" "+getString(R.string.personal_channel_more));
        holder.description.setVisibility(description==null?View.GONE:View.VISIBLE);
        holder.watchLive.setVisibility(liveVideo==null?View.GONE:View.VISIBLE);
        holder.subscription.setText(isSubscribed?R.string.personal_channel_subscribed:R.string.action_subscribe);
        holder.subscription.setEnabled(!active(subscriptionRequest));
    }

    private void renderEmpty(){if(empty!=null)empty.setVisibility(!loading&&getAdapter()!=null&&getAdapter().size()<=1?View.VISIBLE:View.GONE);}

    private void toggleSubscription(){
        if(!YouTubeServiceManager.instance().getSignInService().isSigned()){YTSignInPresenter.instance(requireContext()).start();return;}
        if(channelId()==null||active(subscriptionRequest))return;
        boolean target=!isSubscribed;final int requestGeneration=generation,requestAccount=accountGeneration;
        subscriptionRequest=(target?itemService().subscribeObserve(channelId()):itemService().unsubscribeObserve(channelId()))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(value->{ },error->{
                    if(current(requestGeneration)&&requestAccount==accountGeneration){Toast.makeText(requireContext(),R.string.personal_channel_subscription_error,Toast.LENGTH_SHORT).show();renderHero();}
                },()->{if(current(requestGeneration)&&requestAccount==accountGeneration){isSubscribed=target;renderHero();}});
        renderHero();
    }

    private void submitSearch(){
        if(search==null)return;String query=search.getText().toString().trim();if(query.isEmpty())return;
        hideKeyboard();presenter().onSearchSubmit(query);
    }

    private void showDescription(){
        if(details==null||details.description==null)return;
        AppDialogPresenter dialog=AppDialogPresenter.instance(requireContext());
        dialog.appendLongTextCategory(null,UiOptionItem.from(details.description));dialog.showDialog(details.title);
    }

    private void showOptions(){
        AppDialogPresenter dialog=AppDialogPresenter.instance(requireContext());
        dialog.appendSingleButton(UiOptionItem.from(getString(R.string.personal_channel_home),item->{dialog.closeDialog();presenter().onViewInitialized();}));
        dialog.appendSingleButton(UiOptionItem.from(getString(R.string.personal_channel_uploads),item->{dialog.closeDialog();showUploads();}));
        dialog.appendSingleButton(UiOptionItem.from(getString(R.string.search_sorting),item->{dialog.closeDialog();presenter().onSearchSettingsClicked();}));
        dialog.showDialog(getString(R.string.personal_channel_content));
    }
    private void channelOptionsOnHold(View view){
        view.setOnLongClickListener(v->{showOptions();return true;});
        view.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN)showOptions();return true;}return false;});
    }

    private void showUploads(){
        dispose(uploadsRequest);Video channel=new Video();channel.channelId=channelId();if(channel.channelId==null)return;
        final int requestGeneration=generation;showProgressBar(true);
        uploadsRequest=YouTubeServiceManager.instance().getContentService().getGroupObserve(SimpleMediaItem.from(channel))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{if(current(requestGeneration)){clear();if(group!=null)presenter().updateRows(Collections.singletonList(group));setPosition(1);}},
                        error->{if(current(requestGeneration))showProgressBar(false);},()->{if(current(requestGeneration))showProgressBar(false);});
    }

    @Override protected VideoCardPresenter createVideoCardPresenter(){return new PersonalVideoCardPresenter(){
        @Override protected Pair<Integer,Integer> getCardDimensPx(Context context){return new Pair<>(PersonalChannelFragment.dp(context,264),PersonalChannelFragment.dp(context,148.5f));}
        @Override protected boolean isLargeCard(Video video){return false;}
    };}
    @Override protected ListRowPresenter createListRowPresenter(){return new ChannelRowsPresenter();}
    @Override protected boolean useChildRoundedCorners(){return false;}

    void setListening(boolean listening){if(microphone!=null)microphone.setActivated(listening);}
    void setVoiceText(String text,boolean submit){if(search!=null){search.setText(text);if(submit)submitSearch();}}
    void voiceUnavailable(int message){if(isAdded())Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show();}
    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==PersonalChannelVoiceController.REQUEST_MIC){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)voice.start();else voiceUnavailable(R.string.personal_search_voice_permission);}
    }
    @Override public void onActivityResult(int requestCode,int resultCode,@Nullable Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PersonalChannelVoiceController.REQUEST_SPEECH&&resultCode==Activity.RESULT_OK&&data!=null){ArrayList<String> matches=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(matches!=null&&!matches.isEmpty())setVoiceText(matches.get(0),true);}
    }
    @Override public void onStop(){voice.stop();hideKeyboard();super.onStop();}
    @Override public void onDestroyView(){
        generation++;dispose(detailsRequest);dispose(stateRequest);dispose(subscriptionRequest);dispose(uploadsRequest);voice.stop();
        MediaServiceManager.instance().removeAccountListener(this);if(banner!=null)Glide.with(banner).clear(banner);
        wrapper=null;banner=null;search=null;microphone=null;empty=null;heroHolder=null;requestedChannel=null;super.onDestroyView();
    }
    private void hideKeyboard(){if(search!=null){InputMethodManager manager=(InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(manager!=null)manager.hideSoftInputFromWindow(search.getWindowToken(),0);if(Build.VERSION.SDK_INT>=21)search.setShowSoftInputOnFocus(false);}}
    private boolean current(int token){return token==generation&&wrapper!=null&&isAdded()&&requestedChannel!=null&&requestedChannel.equals(presenter().getChannelId());}
    private String channelId(){return details!=null&&details.channelId!=null?details.channelId:requestedChannel;}
    private ChannelPresenter presenter(){return ChannelPresenter.instance(requireContext());}
    private MediaItemService itemService(){return YouTubeServiceManager.instance().getMediaItemService();}
    private static void dispose(Disposable request){if(request!=null)request.dispose();}
    private static boolean active(Disposable request){return request!=null&&!request.isDisposed();}
    private int dp(float value){return dp(requireContext(),value);}
    private static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static String formatCount(String value){
        try{long number=Long.parseLong(value);if(number>=1000000)return String.format(java.util.Locale.getDefault(),"%.1fM",number/1000000d);if(number>=1000)return String.format(java.util.Locale.getDefault(),"%.1fK",number/1000d);return NumberFormat.getIntegerInstance().format(number);}catch(NumberFormatException error){return value;}
    }
    private TextView text(String value,int size,boolean bold){TextView view=new TextView(requireContext());view.setText(value);view.setTextSize(size);view.setTextColor(0xFFF1F1F1);view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView button(String value,int size){TextView view=text(value,size,true);view.setId(View.generateViewId());view.setFocusable(true);view.setTextColor(focusText());view.setGravity(Gravity.CENTER);view.setPadding(dp(18),0,dp(18),0);view.setBackground(background(18));return view;}
    private StateListDrawable background(float radius){return background(radius,0xAA303030);}
    private StateListDrawable background(float radius,int normal){StateListDrawable drawable=new StateListDrawable();for(boolean focus:new boolean[]{true,false}){GradientDrawable shape=new GradientDrawable();shape.setColor(focus?0xFFF1F1F1:normal);shape.setCornerRadius(dp(radius));drawable.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},shape);}return drawable;}
    private static ColorStateList focusText(){return new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFF0F0F0F,0xFFE5E5E5});}
    private void place(FrameLayout parent,View view,float x,float y,float width,float height){FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width<0?(int)width:dp(width),height<0?(int)height:dp(height));params.leftMargin=dp(x);params.topMargin=dp(y);parent.addView(view,params);}

    private static final class HeroRow extends Row { }
    private final class HeroPresenter extends RowPresenter {
        HeroPresenter(){setHeaderPresenter(null);setSelectEffectEnabled(false);}
        @Override protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent){
            HeroLayout view=new HeroLayout(requireContext());view.setLayoutParams(new ViewGroup.LayoutParams(-1,dp(435)));view.setClipChildren(false);
            HeroHolder holder=new HeroHolder(view);
            holder.avatar=new ImageView(requireContext());place(view,holder.avatar,48,244.5f,36,36);
            holder.name=text("",18,true);holder.name.setSingleLine(true);place(view,holder.name,93,244,700,22);
            holder.facts=text("",12,false);holder.facts.setTextColor(0xFFAAAAAA);holder.facts.setSingleLine(true);place(view,holder.facts,93,267,700,17);
            holder.description=text("",14,false);holder.description.setMaxLines(2);holder.description.setEllipsize(TextUtils.TruncateAt.END);holder.description.setFocusable(true);
            holder.description.setOnClickListener(v->showDescription());holder.description.setTextColor(focusText());holder.description.setBackground(background(6,0x00000000));place(view,holder.description,48,301.5f,445,40);
            LinearLayout actions=new LinearLayout(requireContext());actions.setGravity(Gravity.CENTER_VERTICAL);place(view,actions,48,363,-1,36);
            holder.watchLive=button(getString(R.string.personal_channel_watch_live),12);holder.watchLive.setOnClickListener(v->{if(liveVideo!=null)VideoActionPresenter.instance(requireContext()).apply(liveVideo);});
            Drawable liveIcon=DrawableCompat.wrap(ContextCompat.getDrawable(requireContext(),R.drawable.personal_channel_live)).mutate();DrawableCompat.setTintList(liveIcon,focusText());
            holder.watchLive.setCompoundDrawablesWithIntrinsicBounds(liveIcon,null,null,null);holder.watchLive.setCompoundDrawablePadding(dp(6));holder.watchLive.setPadding(dp(12),0,dp(12),0);
            addAction(actions,holder.watchLive);holder.watchLive.getLayoutParams().width=dp(109);
            holder.subscription=button(getString(R.string.action_subscribe),12);holder.subscription.setOnClickListener(v->toggleSubscription());addAction(actions,holder.subscription);holder.subscription.getLayoutParams().width=dp(90);
            channelOptionsOnHold(holder.watchLive);channelOptionsOnHold(holder.subscription);
            view.primary=holder.watchLive;view.secondary=holder.subscription;
            return holder;
        }
        private void addAction(LinearLayout parent,View action){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-1);params.rightMargin=dp(12);parent.addView(action,params);}
        @Override protected void onBindRowViewHolder(RowPresenter.ViewHolder holder,Object item){super.onBindRowViewHolder(holder,item);heroHolder=(HeroHolder)holder;holder.view.setAlpha(collapsed?0:1);renderHero();}
        @Override protected void onUnbindRowViewHolder(RowPresenter.ViewHolder holder){if(heroHolder==holder)heroHolder=null;Glide.with(((HeroHolder)holder).avatar).clear(((HeroHolder)holder).avatar);super.onUnbindRowViewHolder(holder);}
    }
    private static final class HeroHolder extends RowPresenter.ViewHolder {ImageView avatar;TextView name,facts,description,watchLive,subscription;HeroHolder(View view){super(view);}}
    private static final class HeroLayout extends FrameLayout {
        View primary,secondary;HeroLayout(Context context){super(context);}
        @Override protected boolean onRequestFocusInDescendants(int direction,Rect previous){return primary!=null&&primary.getVisibility()==View.VISIBLE&&primary.requestFocus(direction,previous)||secondary!=null&&secondary.requestFocus(direction,previous)||super.onRequestFocusInDescendants(direction,previous);}
    }
    private static final class ChannelRowsPresenter extends ListRowPresenter {
        ChannelRowsPresenter(){super(FocusHighlight.ZOOM_FACTOR_NONE,false);setSelectEffectEnabled(false);setShadowEnabled(false);enableChildRoundedCorners(true);setHeaderPresenter(new ChannelHeader());}
        @Override protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent){RowPresenter.ViewHolder holder=super.createRowViewHolder(parent);align(holder);return holder;}
        @Override protected void onRowViewExpanded(RowPresenter.ViewHolder holder,boolean expanded){super.onRowViewExpanded(holder,expanded);align(holder);}
        @Override protected void onRowViewSelected(RowPresenter.ViewHolder holder,boolean selected){super.onRowViewSelected(holder,selected);align(holder);}
        private void align(RowPresenter.ViewHolder holder){ListRowPresenter.ViewHolder row=(ListRowPresenter.ViewHolder)holder;row.getGridView().setPadding(dp(holder.view.getContext(),48),0,dp(holder.view.getContext(),48),dp(holder.view.getContext(),24));row.getGridView().setHorizontalSpacing(dp(holder.view.getContext(),18));row.getGridView().setClipToPadding(false);}
    }
    private static final class ChannelHeader extends RowHeaderPresenter {
        @Override public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent){TextView title=new TextView(parent.getContext());title.setTextSize(15);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setTextColor(0xFFB8B8B8);title.setIncludeFontPadding(false);title.setGravity(Gravity.TOP);title.setPadding(dp(parent.getContext(),48),dp(parent.getContext(),10),0,0);title.setLayoutParams(new ViewGroup.LayoutParams(-1,dp(parent.getContext(),40.5f)));return new RowHeaderPresenter.ViewHolder(title);}
        @Override public void onBindViewHolder(Presenter.ViewHolder holder,Object item){HeaderItem header=((Row)item).getHeaderItem();((TextView)holder.view).setText(header==null?"":header.getName());}
        @Override protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder){ }
    }
}
