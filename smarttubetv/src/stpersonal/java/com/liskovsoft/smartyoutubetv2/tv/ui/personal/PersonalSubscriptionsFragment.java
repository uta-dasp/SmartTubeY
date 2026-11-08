package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.FocusHighlightHelper;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BrowseProcessorManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** All subscriptions and per-channel uploads in independent, recycled TV panes. */
public class PersonalSubscriptionsFragment extends Fragment implements VideoSection, MediaServiceManager.AccountChangeListener {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<VideoGroup> pendingUpdates=new ArrayList<>();
    private final List<Video> recentChannels=new ArrayList<>();
    private final Map<String,Video> alphabeticalChannels=new LinkedHashMap<>();
    // Video only weakly owns its group. Keep these alive for channel menu/restore contracts.
    private VideoGroup recentChannelGroup;
    private final List<VideoGroup> alphabeticalChannelGroups=new ArrayList<>();
    private BrowsePresenter presenter;
    private ContentService service;
    private BrowseProcessorManager channelProcessor;
    private VideoGroupObjectAdapter allVideos,channelVideos;
    private ArrayObjectAdapter channelChoices;
    private ItemBridgeAdapter channelBridge,videoBridge;
    private VerticalGridView channelGrid,videoGrid;
    private FrameLayout root;
    private TextView heading,status,channelStatus;
    private ProgressBar progress;
    private Video selectedChannel;
    private MediaGroup alphabeticalGroup;
    private VideoGroup channelGroup;
    private Disposable recentRequest,namesRequest,namesMoreRequest,channelRequest,channelMoreRequest;
    private Runnable pendingChannelLoad;
    private int channelGeneration,channelRequests;
    private boolean allLoading,channelLoading,channelExhausted,pendingVideoFocus;
    private boolean rebuildingChannels,viewDestroyed,namesExhausted;
    private ErrorFragmentData allError;
    private Video restoreItem;
    private String restoreChannelKey;
    private boolean restoreRequested,restoreRight;
    private int restorePosition=-1;
    private final ViewTreeObserver.OnGlobalFocusChangeListener pendingFocusListener=(oldFocus,newFocus)->{
        if(rebuildingChannels||!pendingVideoFocus||newFocus==null)return;
        if(newFocus==status)return;
        if(isInside(newFocus,channelGrid)){
            RecyclerView.ViewHolder holder=channelGrid.findContainingViewHolder(newFocus);
            if(holder instanceof ItemBridgeAdapter.ViewHolder){
                Object item=((ItemBridgeAdapter.ViewHolder)holder).getItem();
                if(item instanceof PersonalSubscriptionChannelPresenter.Entry&&
                        ((PersonalSubscriptionChannelPresenter.Entry)item).key.equals(channelKey(selectedChannel)))return;
            }
        }
        // Moving to another channel, the rail, or the video pane supersedes the pending click.
        pendingVideoFocus=false;
    };

    @Override public void onCreate(@Nullable Bundle state){
        super.onCreate(null);
        presenter=BrowsePresenter.instance(requireContext());
        service=YouTubeServiceManager.instance().getContentService();
        channelProcessor=new BrowseProcessorManager(requireContext(),video->{
            VideoGroup group=VideoGroup.from(video);group.setAction(VideoGroup.ACTION_SYNC);update(group);
        });
        PersonalVideoCardPresenter cards=new PersonalVideoCardPresenter(){
            @Override protected Pair<Integer,Integer> getCardDimensPx(Context context){
                return new Pair<>(PersonalVideoCardPresenter.dp(context,264),PersonalVideoCardPresenter.dp(context,148.5f));
            }
            @Override protected boolean isLargeCard(Video video){return false;}
        };
        cards.setOnItemViewLongPressedListener((holder,item)->{if(item instanceof Video)presenter.onVideoItemLongClicked((Video)item);});
        allVideos=new VideoGroupObjectAdapter(cards);channelVideos=new VideoGroupObjectAdapter(cards);
        PersonalSubscriptionChannelPresenter channels=new PersonalSubscriptionChannelPresenter();
        channels.setOnItemViewLongPressedListener((holder,item)->{
            PersonalSubscriptionChannelPresenter.Entry entry=(PersonalSubscriptionChannelPresenter.Entry)item;
            if(entry.video!=null)presenter.onVideoItemLongClicked(entry.video);
            else presenter.onSectionLongPressed(MediaGroup.TYPE_SUBSCRIPTIONS);
        });
        Presenter alphabeticHeader=new AlphabeticHeaderPresenter();
        channelChoices=new ArrayObjectAdapter(new PresenterSelector(){
            @Override public Presenter getPresenter(Object item){return item instanceof String?alphabeticHeader:channels;}
            @Override public Presenter[] getPresenters(){return new Presenter[]{alphabeticHeader,channels};}
        });
        channelBridge=new ItemBridgeAdapter(channelChoices);
        channelBridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener(){
            @Override public void onCreate(ItemBridgeAdapter.ViewHolder holder){
                holder.getViewHolder().view.setOnClickListener(view->{
                    if(holder.getItem() instanceof PersonalSubscriptionChannelPresenter.Entry){
                        cancelRestore();chooseChannel((PersonalSubscriptionChannelPresenter.Entry)holder.getItem(),true);
                    }
                });
            }
        });
        videoBridge=new ItemBridgeAdapter(allVideos);
        FocusHighlightHelper.setupBrowseItemFocusHighlight(videoBridge,FocusHighlight.ZOOM_FACTOR_NONE,false);
        videoBridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener(){
            @Override public void onCreate(ItemBridgeAdapter.ViewHolder holder){
                holder.getViewHolder().view.setOnClickListener(view->{if(holder.getItem() instanceof Video)presenter.onVideoItemClicked((Video)holder.getItem());});
            }
        });
        rebuildChannels();
        List<VideoGroup> pending=new ArrayList<>(pendingUpdates);pendingUpdates.clear();
        for(VideoGroup group:pending)applyUpdate(group);
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){
        root=new FrameLayout(requireContext());root.setClipChildren(false);
        viewDestroyed=false;
        TextView title=text(getString(R.string.header_subscriptions),23,true);
        place(title,7.5f,33,500,36);
        channelGrid=new VerticalGridView(requireContext());channelGrid.setId(R.id.personal_subscriptions_channels);
        channelGrid.setNumColumns(1);channelGrid.setColumnWidth(dp(210));channelGrid.setAdapter(channelBridge);
        channelGrid.setPadding(0,0,0,dp(20));channelGrid.setVerticalSpacing(0);channelGrid.setClipToPadding(false);
        channelGrid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
        channelGrid.setWindowAlignmentOffsetPercent(50);
        place(channelGrid,-5,96,210,-1);
        channelGrid.setOnKeyInterceptListener(event->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(activeVideos().size()>0)videoGrid.requestFocus();else status.requestFocus();return true;
            }
            return false;
        });
        channelGrid.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener(){
            @Override public void onChildViewHolderSelected(RecyclerView parent,RecyclerView.ViewHolder holder,int position,int subposition){
                if(rebuildingChannels||holder==null||!holder.itemView.hasFocus()||position<0||position>=channelChoices.size())return;
                Object value=channelChoices.get(position);
                if(value instanceof PersonalSubscriptionChannelPresenter.Entry){
                    PersonalSubscriptionChannelPresenter.Entry entry=(PersonalSubscriptionChannelPresenter.Entry)value;
                    if(restoreRequested&&entry.video==null&&restoreChannelKey!=null)return;
                    if(entry.video!=null)presenter.onVideoItemSelected(entry.video);
                    if(MainUIData.instance(requireContext()).isUploadsAutoLoadEnabled())chooseChannel(entry,false);
                    if(position>channelChoices.size()-6)loadMoreChannels();
                }
            }
        });
        View divider=new View(requireContext());divider.setBackgroundColor(0xFF272727);place(divider,216,96,1.5f,-1);
        heading=text(selectedChannel==null?getString(R.string.personal_subscriptions_most_relevant):selectedChannel.getTitle(),15,false);heading.setTextColor(0xFFB8B8B8);
        place(heading,232.5f,90,570,27);
        HorizontalScrollView horizontal=new HorizontalScrollView(requireContext());
        horizontal.setHorizontalScrollBarEnabled(false);horizontal.setFillViewport(false);horizontal.setClipToPadding(false);
        videoGrid=new VerticalGridView(requireContext());videoGrid.setId(R.id.personal_subscriptions_videos);
        videoGrid.setNumColumns(3);videoGrid.setColumnWidth(dp(264));videoGrid.setHorizontalSpacing(dp(18));videoGrid.setVerticalSpacing(dp(57));
        videoGrid.setPadding(0,0,0,dp(35));videoGrid.setClipToPadding(false);videoGrid.setClipChildren(false);
        videoGrid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);videoGrid.setWindowAlignmentOffset(0);
        videoGrid.setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        videoGrid.setItemAlignmentOffset(0);
        videoGrid.setItemAlignmentOffsetPercent(BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
        videoGrid.setAdapter(videoBridge);
        horizontal.addView(videoGrid,new HorizontalScrollView.LayoutParams(dp(828),-1));
        place(horizontal,232.5f,127.5f,-1,-1);
        videoGrid.setOnKeyInterceptListener(event->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT&&videoGrid.getSelectedPosition()%3==0){channelGrid.requestFocus();return true;}
            return false;
        });
        videoGrid.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener(){
            @Override public void onChildViewHolderSelected(RecyclerView parent,RecyclerView.ViewHolder holder,int position,int subposition){
                VideoGroupObjectAdapter videos=activeVideos();
                if(holder==null||position<0||position>=videos.size())return;
                Video video=(Video)videos.get(position);
                if(holder.itemView.hasFocus())presenter.onVideoItemSelected(video);
                if(holder.itemView.hasFocus()&&position>=videos.size()-5){
                    if(selectedChannel==null)presenter.onScrollEnd((Video)videos.get(videos.size()-1));else loadMoreChannelVideos();
                }
                focusPendingVideos();
            }
        });
        status=text("",15,false);status.setFocusable(true);status.setPadding(dp(12),dp(12),dp(12),dp(12));
        status.setBackground(focusBackground());status.setTextColor(focusText());status.setVisibility(View.GONE);
        status.setOnClickListener(view->{
            if(selectedChannel==null&&allError!=null)allError.onAction();
            else if(selectedChannel==null)presenter.onSectionFocused(MediaGroup.TYPE_SUBSCRIPTIONS);
            else chooseChannel(new PersonalSubscriptionChannelPresenter.Entry(selectedChannel,selectedChannel.getTitle(),channelKey(selectedChannel)),false);
        });place(status,232.5f,130,580,90);
        channelStatus=text("",13,false);channelStatus.setPadding(dp(10),dp(8),dp(8),dp(8));
        channelStatus.setBackground(focusBackground());channelStatus.setTextColor(focusText());channelStatus.setVisibility(View.GONE);
        channelStatus.setOnClickListener(view->{
            if(MediaServiceManager.instance().getSelectedAccount()==null)AccountSelectionPresenter.instance(requireContext()).show(true);else loadChannels();
        });place(channelStatus,-5,145,210,100);
        progress=new ProgressBar(requireContext());place(progress,815,92,18,18);progress.setVisibility(View.GONE);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(pendingFocusListener);
        return root;
    }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        super.onViewCreated(view,state);MediaServiceManager.instance().addAccountListener(this);loadChannels();renderStatus();tryRestore();
    }

    @Override public void onAccountChanged(Account account){
        safely(()->{
            cancelRestore();cancelChannelLoad();selectedChannel=null;channelGroup=null;channelVideos.clear();allVideos.clear();allError=null;
            recentChannels.clear();alphabeticalChannels.clear();recentChannelGroup=null;alphabeticalChannelGroups.clear();alphabeticalGroup=null;rebuildChannels();
            videoBridge.setAdapter(allVideos);heading.setText(R.string.personal_subscriptions_most_relevant);loadChannels();renderStatus();
        });
    }

    @Override public void update(VideoGroup group){
        if(group==null)return;
        if(allVideos==null){pendingUpdates.add(group);return;}
        safely(()->applyUpdate(group));
    }

    private void applyUpdate(VideoGroup group){
        int action=group.getAction();
        if(action==VideoGroup.ACTION_SYNC||action==VideoGroup.ACTION_REMOVE||action==VideoGroup.ACTION_REMOVE_AUTHOR){
            modify(allVideos,group);modify(channelVideos,group);
            if(action==VideoGroup.ACTION_REMOVE){
                for(Video item:group.getVideos())if(!item.hasVideo())removeChannelChoice(item);
            }
            if(action==VideoGroup.ACTION_REMOVE_AUTHOR&&root!=null)loadChannels();
        }else{
            if(action==VideoGroup.ACTION_REPLACE){allVideos.clear();allError=null;}
            if(!group.isEmpty())allVideos.add(group);
        }
        renderStatus();tryRestore();
        if(selectedChannel==null)focusPendingVideos();
    }

    private void modify(VideoGroupObjectAdapter adapter,VideoGroup group){
        if(group.getAction()==VideoGroup.ACTION_SYNC)adapter.sync(group);
        else if(group.getAction()==VideoGroup.ACTION_REMOVE)adapter.remove(group);
        else if(!group.isEmpty())adapter.removeAuthor(group);
    }

    private void removeChannelChoice(Video removed){
        String key=channelKey(removed);
        for(Iterator<Video> iterator=recentChannels.iterator();iterator.hasNext();){
            Video video=iterator.next();
            if(video==removed||channelKey(video).equals(key))iterator.remove();
        }
        alphabeticalChannels.remove(key);
        if(selectedChannel!=null&&channelKey(selectedChannel).equals(key)){
            chooseChannel((PersonalSubscriptionChannelPresenter.Entry)channelChoices.get(0),false);
        }
        rebuildChannels();
    }

    @Override public void clear(){
        if(allVideos==null){pendingUpdates.clear();return;}
        safely(()->{allVideos.clear();allError=null;renderStatus();});
    }
    @Override public boolean isEmpty(){return allVideos==null?pendingUpdates.isEmpty():allVideos.isEmpty()&&channelVideos.isEmpty()&&recentChannels.isEmpty()&&alphabeticalChannels.isEmpty();}
    @Override public int getPosition(){return videoGrid==null?restorePosition:videoGrid.getSelectedPosition();}
    @Override public void setPosition(int index){restorePosition=index;if(videoGrid!=null&&index>=0&&index<activeVideos().size())videoGrid.setSelectedPosition(index);}
    @Override public void selectItem(Video item){
        if(item==null)return;restoreItem=item;restoreRequested=true;restoreRight=item.hasVideo();
        boolean all=item.getGroup()!=null&&item.getGroup().getType()==MediaGroup.TYPE_SUBSCRIPTIONS;
        MediaGroup media=item.getGroup()!=null?item.getGroup().getMediaGroup():null;
        restoreChannelKey=all?null:selectedChannel!=null&&channelVideos.indexOfAlt(item)>=0?channelKey(selectedChannel):
                media!=null&&media.getChannelId()!=null?"channel:"+media.getChannelId():channelKey(item);
        tryRestore();
    }

    private void loadChannels(){
        dispose(recentRequest);dispose(namesRequest);dispose(namesMoreRequest);channelRequests=2;namesExhausted=false;
        renderChannelStatus();
        recentRequest=service.getSubscribedChannelsByNewContentObserve().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{
                    if(root==null)return;recentChannels.clear();
                    recentChannelGroup=VideoGroup.from(group);
                    List<Video> videos=recentChannelGroup.getVideos();recentChannels.addAll(videos.subList(0,Math.min(7,videos.size())));
                    rebuildChannels();
                },error->channelRequestFinished(),this::channelRequestFinished);
        namesRequest=service.getSubscribedChannelsByNameObserve().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{
                    if(root==null)return;alphabeticalChannels.clear();alphabeticalChannelGroups.clear();appendAlphabetical(group);rebuildChannels();
                },error->channelRequestFinished(),this::channelRequestFinished);
    }

    private void channelRequestFinished(){if(root==null)return;channelRequests=Math.max(0,channelRequests-1);renderChannelStatus();tryRestore();}
    private void appendAlphabetical(MediaGroup group){
        alphabeticalGroup=group;if(group==null)return;
        VideoGroup videos=VideoGroup.from(group);alphabeticalChannelGroups.add(videos);
        for(Video video:videos.getVideos())alphabeticalChannels.put(channelKey(video),video);
    }
    private void loadMoreChannels(){
        if(namesExhausted||alphabeticalGroup==null||alphabeticalGroup.getNextPageKey()==null||active(namesMoreRequest))return;
        int previous=alphabeticalChannels.size();
        namesMoreRequest=service.continueGroupObserve(alphabeticalGroup).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{if(root!=null){appendAlphabetical(group);namesExhausted=alphabeticalChannels.size()<=previous;rebuildChannels();}},
                        error->{namesExhausted=true;handler.post(this::tryRestore);},()->handler.post(this::tryRestore));
    }

    private void rebuildChannels(){
        if(channelChoices==null)return;
        safely(()->{
            String focused=null;boolean hadFocus=channelGrid!=null&&channelGrid.hasFocus();
            if(hadFocus&&channelGrid.getSelectedPosition()>=0&&channelGrid.getSelectedPosition()<channelChoices.size()){
                Object old=channelChoices.get(channelGrid.getSelectedPosition());
                if(old instanceof PersonalSubscriptionChannelPresenter.Entry)focused=((PersonalSubscriptionChannelPresenter.Entry)old).key;
            }
            rebuildingChannels=true;
            channelChoices.clear();
            PersonalSubscriptionChannelPresenter.Entry all=new PersonalSubscriptionChannelPresenter.Entry(null,getString(R.string.personal_subscriptions_all),"all");
            all.selected=selectedChannel==null;channelChoices.add(all);
            for(Video video:recentChannels)channelChoices.add(choice(video));
            if(!alphabeticalChannels.isEmpty()){
                channelChoices.add(getString(R.string.personal_subscriptions_alphabetic));
                List<Video> sorted=new ArrayList<>(alphabeticalChannels.values());Collator collator=Collator.getInstance();
                Collections.sort(sorted,(left,right)->collator.compare(String.valueOf(left.getTitle()),String.valueOf(right.getTitle())));
                for(Video video:sorted)channelChoices.add(choice(video));
            }
            rebuildingChannels=false;
            if(focused!=null&&channelGrid!=null){int position=findChannelPosition(focused);if(position>=0)channelGrid.setSelectedPosition(position);}
            renderChannelStatus();tryRestore();
        });
    }

    private PersonalSubscriptionChannelPresenter.Entry choice(Video video){
        PersonalSubscriptionChannelPresenter.Entry entry=new PersonalSubscriptionChannelPresenter.Entry(video,video.getTitle(),channelKey(video));
        entry.selected=selectedChannel!=null&&entry.key.equals(channelKey(selectedChannel));return entry;
    }
    private int findChannelPosition(String key){
        for(int i=0;i<channelChoices.size();i++)if(channelChoices.get(i) instanceof PersonalSubscriptionChannelPresenter.Entry&&
                key.equals(((PersonalSubscriptionChannelPresenter.Entry)channelChoices.get(i)).key))return i;
        return -1;
    }

    private void chooseChannel(PersonalSubscriptionChannelPresenter.Entry entry,boolean focusVideos){
        safely(()->chooseChannelNow(entry,focusVideos));
    }

    private void chooseChannelNow(PersonalSubscriptionChannelPresenter.Entry entry,boolean focusVideos){
        if(root==null)return;
        boolean same=entry.video==null?selectedChannel==null:selectedChannel!=null&&entry.key.equals(channelKey(selectedChannel));
        if(same&&(selectedChannel==null||!activeVideos().isEmpty()||channelLoading)){
            pendingVideoFocus|=focusVideos;
            if(focusVideos&&!activeVideos().isEmpty())focusPendingVideos();
            else if(focusVideos&&status.getVisibility()==View.VISIBLE)status.requestFocus();
            return;
        }
        cancelChannelLoad();
        selectedChannel=entry.video;channelGroup=null;channelExhausted=false;pendingVideoFocus=focusVideos;
        for(int i=0;i<channelChoices.size();i++)if(channelChoices.get(i) instanceof PersonalSubscriptionChannelPresenter.Entry){
            PersonalSubscriptionChannelPresenter.Entry item=(PersonalSubscriptionChannelPresenter.Entry)channelChoices.get(i);
            item.selected=item.key.equals(entry.key);
        }
        channelChoices.notifyItemRangeChanged(0,channelChoices.size());
        videoBridge.setAdapter(activeVideos());
        heading.setText(selectedChannel==null?getString(R.string.personal_subscriptions_most_relevant):selectedChannel.getTitle());
        if(selectedChannel==null){channelLoading=false;renderStatus();if(focusVideos)focusPendingVideos();tryRestore();return;}
        channelVideos.clear();channelLoading=true;renderStatus();
        final int generation=channelGeneration;final Video channel=selectedChannel;
        pendingChannelLoad=()->{
            channelRequest=service.getGroupObserve(channel.mediaItem!=null?channel.mediaItem:SimpleMediaItem.from(channel))
                    .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(group->{
                        if(root==null||generation!=channelGeneration)return;
                        channelGroup=VideoGroup.from(group);supplyChannelIds(channelGroup,channel);channelVideos.add(channelGroup);channelLoading=false;channelProcessor.process(channelGroup);
                        renderStatus();tryRestore();focusPendingVideos();
                    },error->{if(root!=null&&generation==channelGeneration){channelLoading=false;renderStatus();}},
                            ()->{if(root!=null&&generation==channelGeneration){channelLoading=false;renderStatus();handler.post(this::tryRestore);}});
        };
        handler.postDelayed(pendingChannelLoad,focusVideos?0:180);
    }

    private void loadMoreChannelVideos(){
        if(channelGroup==null||channelGroup.getMediaGroup()==null||channelGroup.getNextPageKey()==null||channelExhausted||active(channelRequest)||active(channelMoreRequest)||channelLoading)return;
        final int generation=channelGeneration;final int previous=channelVideos.size();
        channelMoreRequest=service.continueGroupObserve(channelGroup.getMediaGroup()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{
                    if(root==null||generation!=channelGeneration)return;
                    VideoGroup update=VideoGroup.from(channelGroup,group);supplyChannelIds(update,selectedChannel);channelVideos.add(update);channelProcessor.process(update);
                    channelExhausted=channelVideos.size()<=previous;tryRestore();
                },error->{ /* Keep the existing page; reaching its end again retries the continuation. */ },()->handler.post(this::tryRestore));
    }

    void setAllFeedLoading(boolean loading){allLoading=loading;renderStatus();}
    void showSubscriptionsError(ErrorFragmentData data){allError=data;allLoading=false;renderStatus();}
    private void renderStatus(){
        if(root==null||status==null)return;
        progress.setVisibility(channelLoading?View.VISIBLE:View.GONE);
        boolean empty=activeVideos().isEmpty()&&!(selectedChannel==null?allLoading:channelLoading);
        status.setText(selectedChannel==null&&allError!=null?allError.getMessage()+"\n\n"+allError.getActionText():getString(R.string.personal_subscriptions_videos_retry));
        status.setVisibility(empty?View.VISIBLE:View.GONE);
    }
    private void renderChannelStatus(){
        if(root==null||channelStatus==null)return;
        boolean empty=recentChannels.isEmpty()&&alphabeticalChannels.isEmpty();
        channelStatus.setText(channelRequests>0?R.string.personal_subscriptions_loading_channels:R.string.personal_subscriptions_channels_retry);
        channelStatus.setFocusable(channelRequests==0);channelStatus.setVisibility(empty?View.VISIBLE:View.GONE);
    }
    private VideoGroupObjectAdapter activeVideos(){return selectedChannel==null?allVideos:channelVideos;}
    private void focusPendingVideos(){
        if(!pendingVideoFocus||root==null||videoGrid==null||activeVideos().isEmpty())return;
        FrameLayout owner=root;String channel=channelKey(selectedChannel);
        // Adapter arrival can precede RecyclerView's first child layout. Retain intent until
        // focus succeeds; its selection callback retries once children become available.
        videoGrid.post(()->{
            if(root==owner&&pendingVideoFocus&&videoGrid!=null&&channel.equals(channelKey(selectedChannel))&&
                    !activeVideos().isEmpty()&&videoGrid.requestFocus())pendingVideoFocus=false;
        });
    }
    private static void supplyChannelIds(VideoGroup group,Video channel){
        if(channel!=null&&channel.channelId!=null)for(Video video:group.getVideos())if(video.channelId==null)video.channelId=channel.channelId;
    }

    private void tryRestore(){
        if(!restoreRequested||root==null||channelChoices==null)return;
        if(restoreChannelKey!=null&&(selectedChannel==null||!restoreChannelKey.equals(channelKey(selectedChannel)))){
            int position=findChannelPosition(restoreChannelKey);
            if(position>=0){channelGrid.setSelectedPosition(position);chooseChannel((PersonalSubscriptionChannelPresenter.Entry)channelChoices.get(position),false);return;}
            if(channelRequests>0)return;
            if(!namesExhausted&&alphabeticalGroup!=null&&alphabeticalGroup.getNextPageKey()!=null){loadMoreChannels();return;}
            restoreChannelKey=null;
        }
        if(restoreChannelKey==null&&selectedChannel!=null){chooseChannel((PersonalSubscriptionChannelPresenter.Entry)channelChoices.get(0),false);return;}
        if(!restoreRight){
            int position=findChannelPosition(selectedChannel==null?"all":channelKey(selectedChannel));
            if(position>=0)channelGrid.setSelectedPosition(position);channelGrid.requestFocus();cancelRestore();return;
        }
        VideoGroupObjectAdapter videos=activeVideos();int index=restoreItem==null?-1:videos.indexOfAlt(restoreItem);
        if(index>=0){videoGrid.setSelectedPosition(index);videoGrid.requestFocus();cancelRestore();}
        else if(videos.size()>0){
            VideoGroup lastGroup=((Video)videos.get(videos.size()-1)).getGroup();
            MediaGroup group=lastGroup==null?null:lastGroup.getMediaGroup();
            // A no-growth channel page can retain a continuation token. Treat exhaustion as
            // terminal so a missing remembered item still receives the bounded focus fallback.
            if(group!=null&&group.getNextPageKey()!=null&&(selectedChannel==null||!channelExhausted)){
                if(selectedChannel==null)presenter.onScrollEnd((Video)videos.get(videos.size()-1));else loadMoreChannelVideos();
            }else{videoGrid.setSelectedPosition(Math.max(0,Math.min(restorePosition,videos.size()-1)));videoGrid.requestFocus();cancelRestore();}
        }
    }
    private void cancelRestore(){restoreRequested=false;restoreItem=null;restoreChannelKey=null;}
    private void cancelChannelLoad(){
        pendingVideoFocus=false;channelGeneration++;dispose(channelRequest);dispose(channelMoreRequest);channelProcessor.dispose();
        if(pendingChannelLoad!=null)handler.removeCallbacks(pendingChannelLoad);
    }
    private static String channelKey(Video video){
        if(video==null)return "all";
        return video.channelId!=null?"channel:"+video.channelId:video.getReloadPageKey()!=null?"reload:"+video.getReloadPageKey():"title:"+video.getTitle();
    }

    private void safely(Runnable action){
        if(allVideos==null||viewDestroyed)return;
        if(Looper.myLooper()!=Looper.getMainLooper()||(channelGrid!=null&&channelGrid.isComputingLayout())||(videoGrid!=null&&videoGrid.isComputingLayout())){
            handler.postDelayed(()->safely(action),16);
        }else action.run();
    }

    @Override public void onDestroyView(){
        viewDestroyed=true;
        pendingVideoFocus=false;
        if(root!=null)root.getViewTreeObserver().removeOnGlobalFocusChangeListener(pendingFocusListener);
        MediaServiceManager.instance().removeAccountListener(this);
        channelGeneration++;handler.removeCallbacksAndMessages(null);
        dispose(recentRequest);dispose(namesRequest);dispose(namesMoreRequest);dispose(channelRequest);dispose(channelMoreRequest);channelProcessor.dispose();
        root=null;channelGrid=null;videoGrid=null;status=null;channelStatus=null;progress=null;heading=null;
        super.onDestroyView();
    }
    @Override public void onPause(){pendingVideoFocus=false;super.onPause();}
    private static boolean isInside(View view,View ancestor){
        if(ancestor==null)return false;
        while(view!=null){if(view==ancestor)return true;view=view.getParent() instanceof View?(View)view.getParent():null;}
        return false;
    }
    private static void dispose(Disposable disposable){if(disposable!=null)disposable.dispose();}
    private static boolean active(Disposable disposable){return disposable!=null&&!disposable.isDisposed();}
    private TextView text(String value,int size,boolean bold){
        TextView view=new TextView(requireContext());view.setText(value);view.setTextSize(size);view.setTextColor(0xFFF1F1F1);
        view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;
    }
    private StateListDrawable focusBackground(){
        StateListDrawable value=new StateListDrawable();
        for(boolean focus:new boolean[]{true,false}){GradientDrawable shape=new GradientDrawable();shape.setColor(focus?0xFFF1F1F1:0xFF272727);shape.setCornerRadius(dp(9));value.addState(focus?new int[]{android.R.attr.state_focused}:new int[]{},shape);}return value;
    }
    private static ColorStateList focusText(){return new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFF0F0F0F,0xFFF1F1F1});}
    private void place(View view,float x,float y,float width,float height){
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width<0?(int)width:dp(width),height<0?(int)height:dp(height));params.leftMargin=dp(x);params.topMargin=dp(y);root.addView(view,params);
    }
    private int dp(float value){return PersonalVideoCardPresenter.dp(requireContext(),value);}

    private static class AlphabeticHeaderPresenter extends Presenter{
        @Override public ViewHolder onCreateViewHolder(ViewGroup parent){
            TextView view=new TextView(parent.getContext());view.setTextSize(12);view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            view.setTextColor(0xFFAAAAAA);view.setIncludeFontPadding(false);view.setGravity(Gravity.BOTTOM);
            view.setPadding(PersonalVideoCardPresenter.dp(parent.getContext(),9.5f),0,0,PersonalVideoCardPresenter.dp(parent.getContext(),6));
            view.setLayoutParams(new ViewGroup.LayoutParams(PersonalVideoCardPresenter.dp(parent.getContext(),210),PersonalVideoCardPresenter.dp(parent.getContext(),31.5f)));return new ViewHolder(view);
        }
        @Override public void onBindViewHolder(ViewHolder holder,Object item){((TextView)holder.view).setText((String)item);}
        @Override public void onUnbindViewHolder(ViewHolder holder){ }
    }
}
