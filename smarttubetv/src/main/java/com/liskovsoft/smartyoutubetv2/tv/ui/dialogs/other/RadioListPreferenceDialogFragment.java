package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.ListPreference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference.LeanbackListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class RadioListPreferenceDialogFragment extends LeanbackListPreferenceDialogFragment {
    private boolean mIsTransparent;
    private int mSubmenuReturnPosition = -1;

    @Override
    public Adapter<ViewHolder> onCreateAdapter() {
        return new AdapterRadio(mEntries, mEntryValues, mInitialSelection);
    }

    public static RadioListPreferenceDialogFragment newInstanceSingle(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final RadioListPreferenceDialogFragment
                fragment = new RadioListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * MOD: Set focus on selected radio item
     */
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (getPreference() instanceof OptionListPreference) {
            OptionListPreference preference = (OptionListPreference) getPreference();
            preference.refreshOptions();
            mEntries = preference.getEntries();
            mEntryValues = preference.getEntryValues();
            mInitialSelection = preference.getValue();
        }
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (view != null) {
            if (mIsTransparent) {
                ViewUtil.enableTransparentDialog(getActivity(), view);
            }

            VerticalGridView verticalGridView = view.findViewById(android.R.id.list);
            if (verticalGridView != null) {
                verticalGridView.scrollToPosition(mSubmenuReturnPosition >= 0
                        ? mSubmenuReturnPosition : findSelectedPosition());
            }
        }

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    private int findSelectedPosition() {
        if (mEntryValues == null) {
            return 0;
        }

        for (int i = 0; i < mEntryValues.length; i++) {
            if (mEntryValues[i].equals(mInitialSelection)) {
                return i;
            }
        }

        return 0;
    }

    /**
     * MOD: Don't exit from dialog after selection has been set
     */
    public class AdapterRadio extends AdapterSingle {
        public AdapterRadio(CharSequence[] entries, CharSequence[] entryValues, CharSequence selectedValue) {
            super(entries, entryValues, selectedValue);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            boolean submenu = getPreference() instanceof OptionListPreference &&
                    ((OptionListPreference) getPreference()).isSubmenu(position);
            if (holder.getWidgetView() instanceof View) {
                ((View) holder.getWidgetView()).setVisibility(submenu ? View.INVISIBLE : View.VISIBLE);
            }
        }

        @Override
        public void onItemClick(ViewHolder viewHolder) {
            final int index = viewHolder.getAdapterPosition();
            if (index == RecyclerView.NO_POSITION) {
                return;
            }
            final CharSequence entry = mEntryValues[index];
            final ListPreference preference = (ListPreference) getPreference();
            if (index >= 0 && preference != null) {
                if (preference instanceof OptionListPreference && ((OptionListPreference) preference).isSubmenu(index)) {
                    mSubmenuReturnPosition = index;
                }
                String value = mEntryValues[index].toString();
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                    mSelectedValue = entry;
                }
            }

            notifyDataSetChanged();
        }
    }
}
