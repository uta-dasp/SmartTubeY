package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.ArrayList;

/** Cold grid entry belongs to the original transport; utilities remain an explicit Up route. */
public class PersonalPlayerRowView extends FrameLayout {
    public PersonalPlayerRowView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected boolean onRequestFocusInDescendants(int direction, Rect previous) {
        View focused = findFocus();
        if (focused != null && focused != this && focused.requestFocus(direction, previous)) return true;
        PlaybackTransportRowView transport = findViewById(R.id.transport_row);
        return transport != null && transport.requestFocus(direction, previous)
                || super.onRequestFocusInDescendants(direction, previous);
    }

    @Override public void addFocusables(ArrayList<View> views, int direction, int mode) {
        // Pinned GridLayoutManager can enumerate a row directly instead of requesting focus on it.
        if (!hasFocus()) {
            PlaybackTransportRowView transport = findViewById(R.id.transport_row);
            if (transport != null && transport.addPersonalDefaultFocusables(views, direction, mode)) return;
        }
        super.addFocusables(views, direction, mode);
    }
}
