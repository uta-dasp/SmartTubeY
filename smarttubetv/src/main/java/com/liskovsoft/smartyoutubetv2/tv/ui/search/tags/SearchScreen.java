package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.view.KeyEvent;

/** Activity-facing navigation shared by the legacy and personal search presentations. */
public interface SearchScreen {
    boolean hasResults();
    void focusOnSearchField();
    void onFinish();
    boolean onSearchKeyDown(int keyCode, KeyEvent event);
}
