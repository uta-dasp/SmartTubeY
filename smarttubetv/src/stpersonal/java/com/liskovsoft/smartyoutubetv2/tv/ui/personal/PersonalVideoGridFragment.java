package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.VideoGridFragment;

public class PersonalVideoGridFragment extends VideoGridFragment {
    @Override
    protected VideoCardPresenter createVideoCardPresenter() {
        return new PersonalVideoCardPresenter() {
            @Override
            protected Pair<Integer, Integer> getCardDimensPx(Context context) {
                return new Pair<>(dp(context, 264), dp(context, 148.5f));
            }

            @Override
            protected boolean isLargeCard(Video video) { return false; }
        };
    }

    @Override
    protected VerticalGridPresenter createVerticalGridPresenter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false);
        presenter.setShadowEnabled(false);
        presenter.enableChildRoundedCorners(false);
        return presenter;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (getBrowseGrid() != null) {
            getBrowseGrid().setPadding(0, 0, 0, 0);
            getBrowseGrid().setClipToPadding(false);
            getBrowseGrid().setHorizontalSpacing(PersonalVideoCardPresenter.dp(requireContext(), 18));
            getBrowseGrid().setVerticalSpacing(PersonalVideoCardPresenter.dp(requireContext(), 30));
            getBrowseGrid().setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
            getBrowseGrid().setWindowAlignmentOffset(0);
            getBrowseGrid().setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        }
    }
}
