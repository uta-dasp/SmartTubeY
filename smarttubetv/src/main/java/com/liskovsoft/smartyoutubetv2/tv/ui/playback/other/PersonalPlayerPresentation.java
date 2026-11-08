package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Resource-selected presentation; playback/controller behavior stays shared with upstream. */
public final class PersonalPlayerPresentation {
    private PersonalPlayerPresentation() {}

    public static boolean isEnabled(Context context) {
        return context != null && context.getResources().getBoolean(R.bool.personal_player_presentation);
    }
}
