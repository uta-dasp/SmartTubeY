package com.liskovsoft.smartyoutubetv2.tv.ui.personal;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;

import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
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
        presenter.setHeaderPresenter(new RowHeaderPresenter() {
            @Override
            public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
                Presenter.ViewHolder holder = super.onCreateViewHolder(parent);
                TextView label = holder.view instanceof TextView ? (TextView) holder.view :
                        holder.view.findViewById(R.id.row_header);
                if (label != null) {
                    label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    label.setTextSize(15);
                    label.setTextColor(0xFFAAAAAA);
                    label.setIncludeFontPadding(false);
                }
                return holder;
            }

            @Override
            protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) {
                holder.view.setAlpha(1);
                TextView label = holder.view instanceof TextView ? (TextView) holder.view :
                        holder.view.findViewById(R.id.row_header);
                if (label != null) label.setTextColor(holder.getSelectLevel() > 0.5f ? 0xFFF1F1F1 : 0xFFAAAAAA);
            }
        });
        return presenter;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getVerticalGridView() != null) {
            getVerticalGridView().setPadding(0, 0, 0, 0);
            getVerticalGridView().setClipToPadding(false);
            getVerticalGridView().setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
            setAlignment(PersonalVideoCardPresenter.dp(requireContext(), 17.5f));
        }
    }
}
