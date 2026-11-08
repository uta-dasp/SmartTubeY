package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import androidx.preference.ListPreference;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppPreferenceManager;
import java.util.List;

/** Only lists with explicit submenu rows need dynamic labels and a non-radio route. */
public final class OptionListPreference extends ListPreference {
    private final List<OptionItem> mOptions;
    private final AppPreferenceManager mManager;

    public OptionListPreference(Context context, List<OptionItem> options, AppPreferenceManager manager) {
        super(context);
        mOptions = options;
        mManager = manager;
    }

    public boolean isSubmenu(int index) {
        return index >= 0 && index < mOptions.size() && mOptions.get(index) instanceof UiOptionItem.Submenu;
    }

    public void refreshOptions() {
        AppPreferenceManager.ListPreferenceData data = mManager.createListPreferenceData(mOptions);
        setEntries(data.entries);
        setEntryValues(data.values);
        setValue(data.defaultValue);
    }
}
