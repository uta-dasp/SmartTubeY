package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Library navigation around the existing authenticated History service and action dispatch. */
public class PersonalLibraryFragment extends PersonalVideoRowsFragment {
    private FrameLayout wrapper;
    private TextView error;
    private Disposable watchLaterLookup;

    @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state) {
        View rows=super.onCreateView(inflater,parent,state);
        wrapper=new FrameLayout(requireContext());
        wrapper.setClipChildren(false);wrapper.setClipToPadding(false);
        HorizontalScrollView tiles=new HorizontalScrollView(requireContext());
        tiles.setHorizontalScrollBarEnabled(false);tiles.setClipToPadding(false);tiles.setClipChildren(false);
        LinearLayout items=new LinearLayout(requireContext());
        items.setOrientation(LinearLayout.HORIZONTAL);items.setClipChildren(false);items.setClipToPadding(false);
        tiles.addView(items,new HorizontalScrollView.LayoutParams(-2,-1));
        addTile(items,getString(R.string.header_history),0xFF3F00A0,0xFF7C35D8,()->{
            if(getVerticalGridView()!=null)getVerticalGridView().requestFocus();
        });
        addTile(items,getString(R.string.personal_library_watch_later),0xFF005F79,0xFF0096BA,this::openWatchLater);
        addTile(items,getString(R.string.header_playlists),0xFF006354,0xFF00876F,()->openSection(MediaGroup.TYPE_USER_PLAYLISTS));
        addTile(items,getString(R.string.header_music),0xFF873000,0xFFDE4800,()->openSection(MediaGroup.TYPE_MUSIC));
        addTile(items,getString(R.string.personal_library_find_movies),0xFF735700,0xFFC49B00,
                ()->SearchPresenter.instance(requireContext()).startSearch("movies & TV"));
        addTile(items,getString(R.string.my_videos),0xFF5D005E,0xFF97008B,()->openSection(MediaGroup.TYPE_MY_VIDEOS));
        FrameLayout.LayoutParams tileParams=new FrameLayout.LayoutParams(-1,dp(92));
        tileParams.leftMargin=dp(-4);tileParams.topMargin=dp(41);
        wrapper.addView(tiles,tileParams);
        FrameLayout.LayoutParams rowsParams=new FrameLayout.LayoutParams(-1,-1);
        rowsParams.topMargin=dp(194);wrapper.addView(rows,rowsParams);
        error=new TextView(requireContext());
        error.setTextSize(15);
        error.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xFF0F0F0F,0xFFF1F1F1}));
        error.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        error.setGravity(Gravity.CENTER_VERTICAL);error.setPadding(dp(12),dp(12),dp(12),dp(12));
        error.setFocusable(true);error.setBackground(tileBackground());error.setVisibility(View.GONE);
        FrameLayout.LayoutParams errorParams=new FrameLayout.LayoutParams(dp(650),dp(100));
        errorParams.topMargin=dp(175);wrapper.addView(error,errorParams);
        return wrapper;
    }

    @Override protected VideoCardPresenter createVideoCardPresenter() {
        return new PersonalVideoCardPresenter(){
            @Override protected Pair<Integer,Integer> getCardDimensPx(Context context){
                return new Pair<>(PersonalVideoCardPresenter.dp(context,264),PersonalVideoCardPresenter.dp(context,148.5f));
            }
            @Override protected boolean isLargeCard(Video video){return false;}
        };
    }

    @Override protected ListRowPresenter createListRowPresenter() {return new LibraryRowPresenter();}

    @Override public void update(VideoGroup group) {
        if(group==null)return;
        // Guest authCheck emits the complete local History with a fresh group ID and APPEND
        // on every refresh. Its original grid has one surface; a rows view needs a snapshot
        // replacement, otherwise focusing Library then selecting it creates duplicate shelves.
        boolean localHistorySnapshot=group.getType()==MediaGroup.TYPE_HISTORY&&group.getMediaGroup()==null&&
                group.getSection()!=null&&(group.getAction()==VideoGroup.ACTION_APPEND||group.getAction()==VideoGroup.ACTION_REPLACE);
        if(localHistorySnapshot){
            group.setAction(VideoGroup.ACTION_REPLACE);
            if(getVerticalGridView()!=null&&getVerticalGridView().isComputingLayout()){
                View view=getView();
                if(view!=null)view.post(()->{if(isAdded()&&wrapper!=null)update(group);});
                return;
            }
        }
        if(error!=null)error.setVisibility(View.GONE);
        super.update(group);
    }

    void showLibraryError(ErrorFragmentData data) {
        if(error==null)return;
        error.setText(data.getMessage()+"\n\n"+data.getActionText());
        error.setOnClickListener(view->data.onAction());error.setVisibility(View.VISIBLE);
    }

    private void addTile(LinearLayout parent,String title,int start,int end,Runnable action) {
        FrameLayout tile=new FrameLayout(requireContext());
        tile.setFocusable(true);tile.setContentDescription(title);tile.setPadding(dp(4),dp(4),dp(4),dp(4));
        tile.setBackground(tileBackground());tile.setOnClickListener(view->action.run());
        TextView label=new TextView(requireContext());
        label.setText(title);label.setTextSize(18);label.setTextColor(0xFFF1F1F1);label.setIncludeFontPadding(false);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        label.setGravity(Gravity.BOTTOM);label.setMaxLines(2);label.setPadding(dp(12),0,dp(12),dp(10.5f));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{start,end});
        gradient.setCornerRadius(dp(9));label.setBackground(gradient);
        tile.addView(label,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(158),dp(92));
        params.rightMargin=dp(10);parent.addView(tile,params);
    }

    private void openSection(int sectionId) {
        if(getParentFragment() instanceof PersonalBrowseFragment)((PersonalBrowseFragment)getParentFragment()).openLibraryDestination(sectionId);
    }

    private void openWatchLater() {
        if(watchLaterLookup!=null&&!watchLaterLookup.isDisposed())return;
        watchLaterLookup=YouTubeServiceManager.instance().getContentService().getPlaylistsObserve()
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{
                    if(!isAdded()||wrapper==null)return;
                    if(group!=null&&group.getMediaItems()!=null)for(MediaItem item:group.getMediaItems()){
                        if("WL".equals(item.getPlaylistId())||"VLWL".equals(item.getChannelId())){
                            VideoActionPresenter.instance(requireContext()).apply(Video.from(item));return;
                        }
                    }
                    watchLaterFallback();
                },failure->{if(isAdded()&&wrapper!=null)watchLaterFallback();});
    }

    private void watchLaterFallback() {
        Toast.makeText(requireContext(),R.string.personal_library_watch_later_fallback,Toast.LENGTH_LONG).show();
        openSection(MediaGroup.TYPE_USER_PLAYLISTS);
    }

    @Override public void onDestroyView() {
        if(watchLaterLookup!=null)watchLaterLookup.dispose();
        wrapper=null;error=null;super.onDestroyView();
    }

    private StateListDrawable tileBackground() {
        StateListDrawable states=new StateListDrawable();
        GradientDrawable focus=new GradientDrawable();focus.setColor(0xFFF1F1F1);focus.setCornerRadius(dp(13));
        GradientDrawable normal=new GradientDrawable();normal.setColor(Color.TRANSPARENT);
        states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},normal);return states;
    }

    private static class LibraryRowPresenter extends ListRowPresenter {
        LibraryRowPresenter(){
            super(FocusHighlight.ZOOM_FACTOR_NONE,false);setShadowEnabled(false);setSelectEffectEnabled(false);
            setHeaderPresenter(new LibraryHeaderPresenter());
        }
        @Override protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent){
            RowPresenter.ViewHolder holder=super.createRowViewHolder(parent);align(holder);return holder;
        }
        @Override protected void onRowViewExpanded(RowPresenter.ViewHolder holder,boolean expanded){super.onRowViewExpanded(holder,expanded);align(holder);}
        @Override protected void onRowViewSelected(RowPresenter.ViewHolder holder,boolean selected){super.onRowViewSelected(holder,selected);align(holder);}
        private void align(RowPresenter.ViewHolder holder){
            ListRowPresenter.ViewHolder row=(ListRowPresenter.ViewHolder)holder;
            float density=holder.view.getResources().getDisplayMetrics().density;
            row.getGridView().setPadding(0,0,0,Math.round(24*density));
            row.getGridView().setHorizontalSpacing(Math.round(18*density));
            row.getGridView().setClipToPadding(false);
        }
    }

    private static class LibraryHeaderPresenter extends RowHeaderPresenter {
        @Override public ViewHolder onCreateViewHolder(ViewGroup parent){
            TextView title=new TextView(parent.getContext());title.setTextSize(15);title.setTextColor(0xFFB8B8B8);
            title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setIncludeFontPadding(false);
            float density=parent.getResources().getDisplayMetrics().density;
            title.setPadding(0,Math.round(8*density),0,Math.round(16*density));return new ViewHolder(title);
        }
        @Override public void onBindViewHolder(Presenter.ViewHolder holder,Object item){
            HeaderItem header=((Row)item).getHeaderItem();((TextView)holder.view).setText(header==null?"":header.getName());
        }
        @Override protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder){ }
    }

    private int dp(float value){return PersonalVideoCardPresenter.dp(requireContext(),value);}
}
