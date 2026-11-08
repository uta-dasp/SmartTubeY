package com.liskovsoft.smartyoutubetv2.tv.ui.personal.shorts;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;

/** The normal BrowsePresenter remains the owner of the authenticated Shorts feed and continuation. */
public class PersonalShortsEntryFragment extends Fragment implements VideoSection {
    private VideoGroup group;
    private Video selected;
    private int position;
    private boolean autoStarted;
    private TextView entry;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){
        entry=new TextView(requireContext());entry.setTextSize(18);entry.setTextColor(0xFFF1F1F1);
        entry.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));entry.setGravity(Gravity.CENTER);
        entry.setFocusable(true);entry.setText(R.string.personal_shorts_loading);
        entry.setOnClickListener(view->{if(isEmpty())BrowsePresenter.instance(requireContext()).onSectionFocused(MediaGroup.TYPE_SHORTS);else launch();});
        return entry;
    }
    @Override public void onResume(){super.onResume();Video last=PersonalShortsSession.lastVideo();if(autoStarted&&last!=null)selectItem(last);tryAutoStart();}
    @Override public void update(VideoGroup update){
        if(update==null)return;
        if(update.getAction()==VideoGroup.ACTION_REPLACE)group=null;
        if(update.getAction()==VideoGroup.ACTION_SYNC||update.getAction()==VideoGroup.ACTION_REMOVE||update.getAction()==VideoGroup.ACTION_REMOVE_AUTHOR)return;
        if(!update.isEmpty())group=update;
        if(entry!=null)entry.setText(isEmpty()?R.string.personal_shorts_retry:R.string.personal_shorts_play);
        tryAutoStart();
    }
    private void tryAutoStart(){
        if(!autoStarted&&isResumed()&&!isEmpty()&&ViewManager.instance(requireContext()).getTopView()==BrowseView.class){
            BrowsePresenter presenter=BrowsePresenter.instance(requireContext());
            if(presenter.getCurrentSection()!=null&&presenter.getCurrentSection().getId()==MediaGroup.TYPE_SHORTS)launch();
        }
    }
    private void launch(){
        if(group==null)return;
        Video video=selected!=null&&group.indexOf(selected)>=0?selected:null;
        if(video==null)for(int index=Math.min(Math.max(0,position),group.getSize()-1);index<group.getSize();index++)if(group.get(index).hasVideo()){video=group.get(index);position=index;break;}
        if(video==null)return;
        autoStarted=true;selected=video;video.isShorts=true;
        BrowsePresenter.instance(requireContext()).onVideoItemSelected(video);
        PersonalShortsSession.enter(group,video);
        PlaybackPresenter.instance(requireContext()).openVideo(video);
    }
    @Override public void clear(){group=null;if(entry!=null)entry.setText(R.string.personal_shorts_loading);}
    @Override public boolean isEmpty(){return group==null||group.isEmpty();}
    @Override public int getPosition(){return position;}
    @Override public void setPosition(int index){position=Math.max(0,index);}
    @Override public void selectItem(Video item){selected=item;if(group!=null){int index=group.indexOf(item);if(index>=0)position=index;}}
    @Override public void onDestroyView(){entry=null;super.onDestroyView();}
}
