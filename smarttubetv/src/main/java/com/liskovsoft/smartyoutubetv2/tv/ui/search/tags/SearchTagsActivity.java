package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.os.Bundle;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SearchTagsActivity extends LeanbackActivity {
    private SearchScreen mFragment;
    private boolean mDownPressed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_search_tags);
        mFragment = (SearchScreen) getSupportFragmentManager()
                .findFragmentById(R.id.search_tags_fragment);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mFragment != null && mFragment.onSearchKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
