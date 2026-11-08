package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ListRowPresenter;

import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoRowsFragment;

public class PersonalVideoRowsFragment extends VideoRowsFragment {
    @Override
    protected VideoCardPresenter createVideoCardPresenter() {
        return new PersonalVideoCardPresenter();
    }

    @Override
    protected ListRowPresenter createListRowPresenter() {
        ListRowPresenter presenter = new ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false);
        presenter.setSelectEffectEnabled(false);
        presenter.setShadowEnabled(false);
        presenter.enableChildRoundedCorners(true);
        return presenter;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getVerticalGridView() != null) {
            getVerticalGridView().setPadding(0, 0, 0, 0);
            getVerticalGridView().setClipToPadding(false);
            getVerticalGridView().setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
            getVerticalGridView().setWindowAlignmentOffset(0);
            getVerticalGridView().setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        }
    }
}
