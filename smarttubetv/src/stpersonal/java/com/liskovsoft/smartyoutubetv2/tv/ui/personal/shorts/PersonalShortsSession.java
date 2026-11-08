package com.liskovsoft.smartyoutubetv2.tv.ui.personal.shorts;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

/** Retains the source group (Video only weakly owns it), never another player or playback queue. */
final class PersonalShortsSession {
    private static VideoGroup group;
    private static String entryVideo;
    private static Video lastVideo;
    static void enter(VideoGroup source,Video video){group=source;entryVideo=video.videoId;lastVideo=video;}
    static boolean consumeRailEntry(Video video){boolean result=video!=null&&video.videoId!=null&&video.videoId.equals(entryVideo);entryVideo=null;return result;}
    static void retain(Video video){lastVideo=video;if(video!=null&&video.getGroup()!=null)group=video.getGroup();}
    static Video lastVideo(){return lastVideo;}
    static void clear(){group=null;entryVideo=null;lastVideo=null;}
}
