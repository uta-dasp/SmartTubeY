package com.liskovsoft.smartyoutubetv2.tv.ui.personal.search;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

/** Recycled result shelves; navigation and service ownership remain in the search screen. */
public class PersonalSearchRowsFragment extends RowsSupportFragment {
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        PersonalSearchFragment screen = (PersonalSearchFragment) getParentFragment();
        setAdapter(screen.resultsAdapter());
        setOnItemViewClickedListener((holder,item,rowHolder,row) -> {
            if (item instanceof Video) screen.onResultClicked((Video) item);
        });
        setOnItemViewSelectedListener((holder,item,rowHolder,row) -> {
            if (item instanceof Video) screen.onResultSelected((Video) item);
        });
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        setAlignment(dp(36.5f));
        if (getVerticalGridView() != null) {
            getVerticalGridView().setPadding(0, 0, 0, dp(36));
            getVerticalGridView().setClipToPadding(false);
            getVerticalGridView().setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
            getVerticalGridView().setWindowAlignmentOffsetPercent(BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        }
    }

    static class SearchRowPresenter extends ListRowPresenter {
        SearchRowPresenter() {
            super(0, false);
            setSelectEffectEnabled(false);
            setShadowEnabled(false);
            setHeaderPresenter(new SearchHeaderPresenter());
        }
        @Override protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
            RowPresenter.ViewHolder holder = super.createRowViewHolder(parent);
            ListRowPresenter.ViewHolder rowHolder = (ListRowPresenter.ViewHolder) holder;
            int space = Math.round(18 * parent.getResources().getDisplayMetrics().density);
            rowHolder.getGridView().setHorizontalSpacing(space);
            rowHolder.getGridView().setPadding(0, space / 3, 0, space / 2);
            rowHolder.getGridView().setClipToPadding(false);
            return holder;
        }
    }

    private static class SearchHeaderPresenter extends RowHeaderPresenter {
        @Override public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(0xFFF1F1F1);
            title.setTextSize(15);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setIncludeFontPadding(false);
            title.setGravity(Gravity.CENTER_VERTICAL);
            float density = parent.getResources().getDisplayMetrics().density;
            title.setPadding(0, Math.round(4 * density), 0, Math.round(12 * density));
            return new ViewHolder(title);
        }
        @Override public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
            HeaderItem header = ((Row) item).getHeaderItem();
            ((TextView) holder.view).setText(header == null ? "" : header.getName());
        }
        @Override protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) { }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
