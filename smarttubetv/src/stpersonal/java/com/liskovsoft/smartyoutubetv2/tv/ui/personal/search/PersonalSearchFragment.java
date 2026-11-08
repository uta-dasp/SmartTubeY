package com.liskovsoft.smartyoutubetv2.tv.ui.personal.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.tv.ui.personal.PersonalAccountChooserFragment;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchScreen;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Native keyboard, suggestions and recycled shelves over the unchanged SearchPresenter contract. */
public class PersonalSearchFragment extends Fragment implements SearchView, SearchScreen,
        PersonalAccountChooserFragment.Listener {
    private static final int BACKGROUND=0xFF0F0F0F, TEXT=0xFFF1F1F1, MUTED=0xFFAAAAAA, FILL=0xFF272727;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer,VideoGroupObjectAdapter> groups = new LinkedHashMap<>();
    private final List<Tag> tags = new ArrayList<>();
    private final List<View> tagViews = new ArrayList<>();
    private final View[][] keys = new View[4][8];
    private final View[] actions = new View[3];
    private SearchPresenter presenter;
    private SearchData preferences;
    private CrashRestorer restorer;
    private MediaServiceSearchTagProvider tagsProvider;
    private PersonalSearchVoiceController voice;
    private PersonalSearchCardSelector cardSelector;
    private ArrayObjectAdapter resultRows;
    private PersonalSearchRowsFragment rowsFragment;
    private FrameLayout root, keyboard, microphone, resultsContainer;
    private LinearLayout suggestions;
    private ScrollView suggestionsScroll;
    private EditText input;
    private ImageView accountAvatar;
    private String accountIdentity;
    private TextView status;
    private ProgressBar progress;
    private Disposable recommendations;
    private Runnable pendingTags;
    private boolean editing=true, numeric, changingInput, loading, autofocus, firstResume=true, listening;
    private String submittedQuery, restoredQuery, restoreVideoKey;
    private int tagsGeneration;
    private int pendingDeadAccent;
    private Video pausedVideo;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(null);
        preferences = SearchData.instance(requireContext());
        presenter = SearchPresenter.instance(requireContext());
        presenter.setView(this);
        restorer = new CrashRestorer(requireContext(),state);
        voice = new PersonalSearchVoiceController(this);
        cardSelector = new PersonalSearchCardSelector((holder,item) -> {
            if(item instanceof Video) presenter.onVideoItemLongClicked((Video)item);
        });
        resultRows = new ArrayObjectAdapter(new PersonalSearchRowsFragment.SearchRowPresenter());
        if(state != null) {
            restoredQuery = state.getString("personal.search.query");
            restoreVideoKey = state.getString("personal.search.video");
        }
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state) {
        root = new FrameLayout(requireContext());
        root.setBackgroundColor(BACKGROUND);
        root.setClipChildren(false);
        buildRail();
        buildHeader();
        suggestionsScroll = new ScrollView(requireContext());
        suggestionsScroll.setId(R.id.personal_search_suggestions);
        suggestionsScroll.setContentDescription(getString(R.string.personal_search_suggestions));
        suggestionsScroll.setVerticalScrollBarEnabled(false);
        suggestions = new LinearLayout(requireContext());
        suggestions.setOrientation(LinearLayout.VERTICAL);
        suggestionsScroll.addView(suggestions,new ScrollView.LayoutParams(-1,-2));
        place(root,suggestionsScroll,78,84,271,335);
        keyboard = new FrameLayout(requireContext());
        keyboard.setId(R.id.personal_search_keyboard);
        place(root,keyboard,360,87,279,160);
        buildKeyboard();
        resultsContainer = new FrameLayout(requireContext());
        resultsContainer.setId(R.id.personal_search_results);
        place(root,resultsContainer,78,271,-1,-1);
        progress = new ProgressBar(requireContext());
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        place(root,progress,644,40,22,22);
        status = label(getString(R.string.personal_search_empty_retry),15,false);
        status.setId(R.id.personal_search_status);
        status.setFocusable(true);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setBackground(focusBackground(6,FILL));
        status.setPadding(dp(12),0,dp(12),0);
        status.setVisibility(View.GONE);
        status.setOnClickListener(view -> submitQuery());
        place(root,status,78,140,500,42);
        return root;
    }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);
        rowsFragment=(PersonalSearchRowsFragment)getChildFragmentManager().findFragmentById(R.id.personal_search_results);
        if(rowsFragment==null) {
            rowsFragment=new PersonalSearchRowsFragment();
            getChildFragmentManager().beginTransaction().replace(R.id.personal_search_results,rowsFragment).commitNow();
        }
        updateMode();
    }

    @Override public void onActivityCreated(@Nullable Bundle state) {
        super.onActivityCreated(state);
        presenter.onViewInitialized();
        restorer.restorePlayback();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("personal.search.query",getSearchText());
        state.putString("personal.search.video",videoKey(presenter.getCurrentVideo()));
        restorer.persistVideo(state,presenter.getCurrentVideo());
    }

    @Override public void onPause() {
        pausedVideo=presenter.getCurrentVideo();
        presenter.onViewPaused();
        hideSystemKeyboard();
        super.onPause();
    }

    @Override public void onResume() {
        super.onResume();
        if(!firstResume) { presenter.onViewResumed(); restoreResultFocus(pausedVideo); }
        firstResume=false;
    }

    @Override public void onStop() { voice.stop(); super.onStop(); }

    @Override public void onDestroyView() {
        tagsGeneration++;
        handler.removeCallbacksAndMessages(null);
        cancelRecommendations();
        voice.stop();
        root=null; input=null; accountAvatar=null;
        super.onDestroyView();
    }

    @Override public void onDestroy() { presenter.onViewDestroyed(); super.onDestroy(); }

    private void buildHeader() {
        microphone=iconButton("mic",getString(R.string.personal_search_voice),18,() -> startVoiceRecognition());
        microphone.setId(R.id.personal_search_microphone);
        microphone.setBackground(focusBackground(18,FILL));
        place(root,microphone,78,33,36,36);
        input=new EditText(requireContext());
        input.setId(R.id.personal_search_input);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        input.setTextColor(TEXT); input.setHintTextColor(0xFFBFBFBF);
        input.setHint(R.string.personal_search_hint);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(16),0,dp(16),0);
        input.setBackground(shape(18,FILL));
        input.setInputType(InputType.TYPE_CLASS_TEXT | (preferences.isTypingCorrectionDisabled()?InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS:0));
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        if(Build.VERSION.SDK_INT>=21) input.setShowSoftInputOnFocus(false);
        input.setOnClickListener(view -> enterEditing());
        input.setOnLongClickListener(view -> { presenter.onSearchSettingsClicked(); return true; });
        input.setOnKeyListener((view,key,event) -> {
            if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT) { enterEditing(); keys[0][0].requestFocus(); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN) { focusSuggestionsOrKeys(); return true; }
            return false;
        });
        input.setOnEditorActionListener((view,action,event) -> {
            if(action==EditorInfo.IME_ACTION_SEARCH || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_DOWN)) {
                submitQuery(); return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence value,int start,int before,int count) {
                if(!changingInput) {
                    enterEditing(); queueTags();
                    if(TextUtils.isEmpty(value)) loadRecommendations(); else cancelRecommendations();
                }
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        place(root,input,123,33,516,36);
        microphone.setNextFocusRightId(input.getId());
        microphone.setOnFocusChangeListener((view,focused) -> {
            ((PersonalSearchIcon)microphone.getChildAt(0)).setColor(focused?BACKGROUND:TEXT);
            if(!listening) input.setHint(focused?R.string.personal_search_voice:R.string.personal_search_hint);
        });
        microphone.setOnKeyListener((view,key,event) -> {
            if(event.getAction()==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_DOWN)) {
                enterEditing(); if(key==KeyEvent.KEYCODE_DPAD_RIGHT) keys[0][0].requestFocus();else focusSuggestionsOrKeys();return true;
            }
            return false;
        });
        TextView brand=label(getString(R.string.app_name),25,true);
        FrameLayout.LayoutParams brandParams=new FrameLayout.LayoutParams(dp(170),dp(36),Gravity.TOP|Gravity.RIGHT);
        brandParams.topMargin=dp(33);brandParams.rightMargin=dp(54);brand.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        root.addView(brand,brandParams);
    }

    private void buildRail() {
        ImageView avatar=new ImageView(requireContext());
        accountAvatar=avatar;
        avatar.setBackground(shape(20,FILL));
        avatar.setFocusable(true);avatar.setContentDescription(getString(R.string.personal_search_account));
        avatar.setOnClickListener(view -> PersonalAccountChooserFragment.show(this));
        refreshAccountBadge();
        place(root,avatar,25.5f,37.5f,27,27);
        String[] icons={"search","home","shorts","subscriptions","library","music","live","gaming","movies"};
        int[] destinations={-1,MediaGroup.TYPE_HOME,MediaGroup.TYPE_SHORTS,MediaGroup.TYPE_SUBSCRIPTIONS,MediaGroup.TYPE_USER_PLAYLISTS,
                MediaGroup.TYPE_MUSIC,MediaGroup.TYPE_LIVE,MediaGroup.TYPE_GAMING,MediaGroup.TYPE_MOVIES};
        int[] titles={R.string.personal_search_hint,R.string.personal_search_home,R.string.header_shorts,R.string.header_subscriptions,
                R.string.personal_search_library,R.string.header_music,R.string.personal_search_live,R.string.header_gaming,R.string.personal_search_movies};
        for(int i=0;i<icons.length;i++) {
            final int destination=destinations[i];
            FrameLayout button=iconButton(icons[i],getString(titles[i]),18,() -> {
                if(destination<0) focusOnSearchField();
                else BrowsePresenter.instance(requireContext()).selectSection(destination);
            });
            if(i==0) {
                PersonalSearchIcon searchIcon=(PersonalSearchIcon)button.getChildAt(0);
                searchIcon.setColor(TEXT);
                button.setOnFocusChangeListener((view,focused)->searchIcon.setColor(focused?BACKGROUND:TEXT));
            }
            place(root,button,21,99+i*39,36,36);
        }
        FrameLayout settings=iconButton("settings",getString(R.string.personal_search_settings),18,() -> presenter.onSearchSettingsClicked());
        place(root,settings,21,474,36,36);
    }

    private void refreshAccountBadge() {
        if(accountAvatar==null)return;
        Account account=MediaServiceManager.instance().getSelectedAccount();
        accountIdentity=account==null?null:account.getEmail()+"|"+account.getName()+"|"+account.getAvatarImageUrl();
        Glide.with(this).clear(accountAvatar);
        if(account!=null&&!TextUtils.isEmpty(account.getAvatarImageUrl()))
            Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(accountAvatar);
        else accountAvatar.setImageResource(R.drawable.personal_account_placeholder);
    }

    @Override public void onPersonalAccountChooserClosed() {
        if(root==null)return;
        String previous=accountIdentity;
        refreshAccountBadge();
        if(!TextUtils.equals(previous,accountIdentity)) {
            if(!AccountsData.instance(requireContext()).isPasswordAccepted()){finishReally();return;}
            String query=getSearchText();
            clearSearch();startSearch(query);
        }
        if(accountAvatar!=null)accountAvatar.requestFocus();
    }

    private void buildKeyboard() {
        keyboard.removeAllViews();
        String[] rows=numeric?new String[]{"1234567","890@#$%","&*()_+=",".,!?/:;"}:new String[]{"ABCDEFG","HIJKLMN","OPQRSTU","VWXYZ-'"};
        for(int row=0;row<4;row++) for(int col=0;col<7;col++) {
            final String character=String.valueOf(rows[row].charAt(col));
            TextView key=key(character,18,() -> insert(character.toLowerCase(java.util.Locale.ROOT)));
            keys[row][col]=key;
            place(keyboard,key,col*33,row*33,27,27);
            wireKey(key,row,col);
        }
        FrameLayout delete=iconButton("delete",getString(R.string.personal_search_delete),18,this::deleteCharacter);
        delete.setBackground(focusBackground(6,0x00000000));
        delete.setOnLongClickListener(view -> {setInput("");return true;});
        keys[0][7]=delete;place(keyboard,delete,241.5f,0,27,27);wireKey(delete,0,7);
        TextView toggle=key(numeric?"ABC":"&123",12,() -> {numeric=!numeric;buildKeyboard();keys[1][7].requestFocus();});
        keys[1][7]=toggle;place(keyboard,toggle,241.5f,33,27,27);wireKey(toggle,1,7);
        FrameLayout language=iconButton("language",getString(R.string.personal_search_language),18,this::showSystemKeyboard);
        language.setBackground(focusBackground(6,0x00000000));
        keys[2][7]=language;keys[3][7]=null;place(keyboard,language,241.5f,66,27,27);wireKey(language,2,7);
        int[] labels={R.string.personal_search_space,R.string.personal_search_clear,R.string.personal_search_submit};
        Runnable[] handlers={()->insert(" "),()->setInput(""),this::submitQuery};
        for(int i=0;i<3;i++) {
            final int column=i;
            TextView action=key(getString(labels[i]),12,handlers[i]);
            action.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            action.setBackground(focusBackground(6,FILL));
            actions[i]=action;place(keyboard,action,7.5f+i*72,132,66,27);
            action.setOnKeyListener((view,key,event) -> {
                if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                if(key==KeyEvent.KEYCODE_DPAD_UP){keys[3][column*3].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT&&column>0){actions[column-1].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&column<2){actions[column+1].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&hasResults()){focusResults();return true;}
                return false;
            });
        }
    }

    private void wireKey(View view,int row,int col) {
        view.setOnKeyListener((v,key,event) -> {
            if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
            switch(key) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if(col==0)focusSuggestionsOrMic();else keys[row][col-1].requestFocus();return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if(col<7&&keys[row][col+1]!=null)keys[row][col+1].requestFocus();return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if(row==0)input.requestFocus();else keys[row-1][col].requestFocus();return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if(row<3&&keys[row+1][col]!=null)keys[row+1][col].requestFocus();else actions[Math.min(2,col/3)].requestFocus();return true;
                default:return false;
            }
        });
    }

    private void insert(String value) {
        int start=input.getSelectionStart(),end=input.getSelectionEnd();
        if(start<0||end<0){start=input.length();end=start;}
        input.getText().replace(Math.min(start,end),Math.max(start,end),value);
    }

    private void deleteCharacter() {
        int start=input.getSelectionStart(),end=input.getSelectionEnd();
        if(start<0||end<0){start=input.length();end=start;}
        if(start!=end)input.getText().delete(Math.min(start,end),Math.max(start,end));
        else if(start>0)input.getText().delete(input.getText().toString().offsetByCodePoints(start,-1),start);
    }

    private void setInput(String value) {
        input.setText(value);input.setSelection(input.length());
    }

    private void enterEditing() {
        if(input==null)return;
        if(!editing) {presenter.disposeActions();editing=true;autofocus=false;}
        updateMode();
    }

    private void updateMode() {
        if(root==null)return;
        keyboard.setVisibility(editing?View.VISIBLE:View.GONE);
        suggestionsScroll.setVisibility(editing?View.VISIBLE:View.GONE);
        FrameLayout.LayoutParams field=(FrameLayout.LayoutParams)input.getLayoutParams();
        field.width=dp(editing?516:315);input.setLayoutParams(field);
        FrameLayout.LayoutParams shelves=(FrameLayout.LayoutParams)resultsContainer.getLayoutParams();
        shelves.topMargin=dp(editing?271:103);resultsContainer.setLayoutParams(shelves);
        resultsContainer.setVisibility(!editing||TextUtils.isEmpty(getSearchText())?View.VISIBLE:View.GONE);
        status.setVisibility(!editing&&!loading&&!hasResults()?View.VISIBLE:View.GONE);
    }

    private void submitQuery() {
        String query=getSearchText().trim();
        if(query.isEmpty()){focusOnSearchField();return;}
        cancelRecommendations();hideSystemKeyboard();voice.stop();
        submittedQuery=query;editing=false;autofocus=preferences.isFocusOnResultsEnabled();
        updateMode();presenter.onSearch(query);
    }

    private void queueTags() {
        if(pendingTags!=null)handler.removeCallbacks(pendingTags);
        final int generation=++tagsGeneration;
        final String query=getSearchText();
        final WeakReference<PersonalSearchFragment> reference=new WeakReference<>(this);
        pendingTags=()->{
            if(tagsProvider==null)return;
            tagsProvider.search(query,values->{
                PersonalSearchFragment screen=reference.get();
                if(screen!=null) screen.handler.post(()->{
                    if(screen.root!=null&&screen.isAdded()&&screen.tagsGeneration==generation)screen.showTags(values);
                });
            });
        };
        handler.postDelayed(pendingTags,180);
    }

    private void showTags(List<Tag> values) {
        View focused=suggestions.findFocus();int oldIndex=tagViews.indexOf(focused);
        tags.clear();tagViews.clear();suggestions.removeAllViews();
        if(values!=null)tags.addAll(values);
        for(int i=0;i<tags.size();i++) {
            Tag tag=tags.get(i);
            FrameLayout row=new FrameLayout(requireContext());
            row.setId(View.generateViewId());row.setFocusable(true);row.setContentDescription(tag.tag);
            row.setBackground(focusBackground(6,0x00000000));
            PersonalSearchIcon icon=new PersonalSearchIcon(requireContext(),TextUtils.isEmpty(getSearchText())?"history":"search");
            place(row,icon,10,7.5f,18,18);
            TextView text=label(tag.tag,15,false);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.END);
            String query=getSearchText();
            int boldStart=!query.isEmpty()&&tag.tag.regionMatches(true,0,query,0,query.length())?query.length():0;
            SpannableString completion=new SpannableString(tag.tag);
            if(boldStart<completion.length())completion.setSpan(new StyleSpan(Typeface.BOLD),boldStart,completion.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(completion);
            place(row,text,37.5f,0,227,33);
            row.setOnFocusChangeListener((view,hasFocus)->{text.setTextColor(hasFocus?BACKGROUND:TEXT);icon.setColor(hasFocus?BACKGROUND:TEXT);});
            row.setOnClickListener(view->{setInput(tag.tag);submitQuery();});
            row.setOnLongClickListener(view->{
                if(GeneralData.instance(requireContext()).isOkButtonLongPressDisabled())return false;
                presenter.onTagLongClicked(tag);return true;
            });
            row.setNextFocusRightId(keys[0][0].getId());row.setNextFocusUpId(input.getId());
            row.setOnKeyListener((view,key,event)->{
                if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_RIGHT){keys[0][0].requestFocus();return true;}
                if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_MENU){presenter.onTagLongClicked(tag);return true;}
                return false;
            });
            suggestions.addView(row,new LinearLayout.LayoutParams(-1,dp(33)));tagViews.add(row);
        }
        for(int i=1;i<tagViews.size();i++)tagViews.get(i).setNextFocusUpId(tagViews.get(i-1).getId());
        for(int i=0;i<tagViews.size()-1;i++)tagViews.get(i).setNextFocusDownId(tagViews.get(i+1).getId());
        if(oldIndex>=0&&!tagViews.isEmpty())tagViews.get(Math.min(oldIndex,tagViews.size()-1)).requestFocus();
    }

    private void loadRecommendations() {
        cancelRecommendations();
        if(!isAdded()||!editing||!TextUtils.isEmpty(getSearchText()))return;
        recommendations=YouTubeServiceManager.instance().getContentService().getRecommendedObserve()
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(group->{
                    if(root!=null&&editing&&TextUtils.isEmpty(getSearchText())){
                        runWhenRowsReady(()->{
                            resultRows.clear();groups.clear();applyGroup(VideoGroup.from(group));
                        });
                    }
                },error->{ /* Recommendations are optional; the query keyboard remains available. */ });
    }

    private void cancelRecommendations() { if(recommendations!=null){recommendations.dispose();recommendations=null;} }

    @Override public void setTagsProvider(MediaServiceSearchTagProvider provider) {tagsProvider=provider;queueTags();}
    @Override public void clearSearchTags() {
        tagsGeneration++;if(pendingTags!=null)handler.removeCallbacks(pendingTags);if(root!=null)showTags(null);
    }
    @Override public void removeSearchTag(Tag tag) {
        tagsGeneration++;if(pendingTags!=null)handler.removeCallbacks(pendingTags);
        if(root==null)return;List<Tag> remaining=new ArrayList<>(tags);remaining.remove(tag);showTags(remaining);
    }

    @Override public void startSearch(String query) {
        if(input==null)return;
        if(query==null&&restoredQuery!=null){query=restoredQuery;restoredQuery=null;}
        changingInput=true;input.setText(query==null?"":query);input.setSelection(input.length());changingInput=false;
        queueTags();
        if(query!=null&&!query.trim().isEmpty())submitQuery();
        else {
            editing=true;updateMode();loadRecommendations();microphone.requestFocus();
            if(preferences.isKeyboardAutoShowEnabled())handler.post(this::showSystemKeyboard);
        }
    }
    @Override public String getSearchText() {return input==null?(submittedQuery==null?"":submittedQuery):input.getText().toString();}
    @Override public void startVoiceRecognition() {enterEditing();voice.start();}

    @Override public void updateSearch(VideoGroup group) {
        if(group==null)return;
        runWhenRowsReady(()->applyGroup(group));
    }

    private void applyGroup(VideoGroup group) {
        if(root==null)return;
        int action=group.getAction();
        if(action==VideoGroup.ACTION_REPLACE){resultRows.clear();groups.clear();}
        VideoGroupObjectAdapter adapter=groups.get(group.getId());
        if(action==VideoGroup.ACTION_SYNC){if(adapter!=null)adapter.sync(group);return;}
        if(action==VideoGroup.ACTION_REMOVE){if(adapter!=null)adapter.remove(group);return;}
        if(action==VideoGroup.ACTION_REMOVE_AUTHOR){if(adapter!=null)adapter.removeAuthor(group);return;}
        if(group.isEmpty())return;
        if(adapter==null) {
            adapter=new VideoGroupObjectAdapter(group,cardSelector);groups.put(group.getId(),adapter);
            String title=editing?getString(R.string.personal_search_recommended):
                    resultRows.size()==0?getString(R.string.personal_search_results,submittedQuery):group.getTitle();
            resultRows.add(new ListRow(new HeaderItem(group.getId(),title),adapter));
        } else adapter.add(group);
        updateMode();
        if(restoreVideoKey!=null){restoreResultFocus(null);}
        else if(autofocus&&!editing){autofocus=false;handler.postDelayed(()->{if(!editing)focusResults();},80);}
    }

    @Override public void clearSearch() {
        runWhenRowsReady(()->{resultRows.clear();groups.clear();updateMode();});
    }

    @Override public void showProgressBar(boolean show) {
        if(root==null)return;
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->showProgressBar(show));return;}
        loading=show;progress.setVisibility(show?View.VISIBLE:View.GONE);updateMode();
    }

    private void runWhenRowsReady(Runnable action) {
        if(root==null)return;
        if(Looper.myLooper()!=Looper.getMainLooper()||(rowsFragment!=null&&rowsFragment.getVerticalGridView()!=null&&rowsFragment.getVerticalGridView().isComputingLayout())){
            handler.postDelayed(()->runWhenRowsReady(action),16);
        }else action.run();
    }

    ArrayObjectAdapter resultsAdapter() {return resultRows;}
    void onResultClicked(Video video) {presenter.onVideoItemClicked(video);}
    void onResultSelected(Video video) {
        presenter.onVideoItemSelected(video);
        for(VideoGroupObjectAdapter adapter:groups.values()){
            int index=adapter.indexOf(video);
            if(index>=0&&index>=adapter.size()-5){presenter.onScrollEnd((Video)adapter.get(adapter.size()-1));break;}
        }
    }

    private void restoreResultFocus(Video video) {
        if(editing||rowsFragment==null)return;
        String key=restoreVideoKey!=null?restoreVideoKey:videoKey(video);if(key==null)return;
        int row=0;
        for(VideoGroupObjectAdapter adapter:groups.values()){
            for(int item=0;item<adapter.size();item++)if(key.equals(videoKey((Video)adapter.get(item)))){
                restoreVideoKey=null;
                rowsFragment.setSelectedPosition(row,false,new ListRowPresenter.SelectItemViewHolderTask(item));
                focusResults();return;
            }
            row++;
        }
    }
    private static String videoKey(Video video) {
        if(video==null)return null;
        return video.videoId!=null?"video:"+video.videoId:video.playlistId!=null?"playlist:"+video.playlistId:video.channelId!=null?"channel:"+video.channelId:null;
    }

    private void focusResults() {if(root!=null&&hasResults()&&resultsContainer.getVisibility()==View.VISIBLE)resultsContainer.requestFocus();}
    private void focusSuggestionsOrKeys() {if(!tagViews.isEmpty())tagViews.get(0).requestFocus();else keys[0][0].requestFocus();}
    private void focusSuggestionsOrMic() {if(!tagViews.isEmpty())tagViews.get(0).requestFocus();else microphone.requestFocus();}
    @Override public void focusOnSearchField() {enterEditing();microphone.requestFocus();}
    @Override public boolean hasResults() {return resultRows!=null&&resultRows.size()>0;}
    @Override public boolean onSearchKeyDown(int keyCode,KeyEvent event) {
        // Focus stays on the TV keyboard while Bluetooth/ADB keyboards send ordinary text.
        // An EditText that owns focus performs its own editing before the Activity fallback.
        if(input!=null&&!input.hasFocus()&&!event.isCtrlPressed()&&!event.isMetaPressed()) {
            if(keyCode==KeyEvent.KEYCODE_DEL) {
                enterEditing();
                if(pendingDeadAccent!=0)pendingDeadAccent=0;else deleteCharacter();
                return true;
            }
            int character=event.getUnicodeChar();
            if((character&KeyCharacterMap.COMBINING_ACCENT)!=0) {
                pendingDeadAccent=character&KeyCharacterMap.COMBINING_ACCENT_MASK;
                return true;
            }
            if(character!=0&&Character.isValidCodePoint(character)&&!Character.isISOControl(character)) {
                enterEditing();
                if(pendingDeadAccent!=0) {
                    int combined=KeyCharacterMap.getDeadChar(pendingDeadAccent,character);
                    if(combined!=0)character=combined;else insert(new String(Character.toChars(pendingDeadAccent)));
                    pendingDeadAccent=0;
                }
                insert(new String(Character.toChars(character)));
                return true;
            }
        }
        if(keyCode==KeyEvent.KEYCODE_MENU){
            // Card presenters already dispatch MENU to their contextual action handler.
            if(!resultsContainer.hasFocus())presenter.onSearchSettingsClicked();return true;
        }
        if(keyCode==KeyEvent.KEYCODE_BACK&&!editing){presenter.disposeActions();enterEditing();queueTags();microphone.requestFocus();return true;}
        if(keyCode==KeyEvent.KEYCODE_SEARCH){startVoiceRecognition();return true;}
        return false;
    }
    @Override public void onFinish() {presenter.disposeActions();cancelRecommendations();voice.stop();presenter.onFinish();}
    @Override public void finishReally() {if(getActivity() instanceof LeanbackActivity)((LeanbackActivity)getActivity()).finishReally();}

    void setListening(boolean value) {
        listening=value;
        if(input!=null)input.setHint(value?R.string.personal_search_listening:R.string.personal_search_hint);
    }
    void setVoiceText(String text,boolean submit) {
        if(input==null)return;setInput(text);if(submit)submitQuery();
    }
    void voiceUnavailable(int message) {
        if(!isAdded())return;Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show();focusOnSearchField();
    }
    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==PersonalSearchVoiceController.REQUEST_MIC){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)voice.start();
            else voiceUnavailable(R.string.personal_search_voice_permission);
        }
    }
    @Override public void onActivityResult(int requestCode,int resultCode,@Nullable Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PersonalSearchVoiceController.REQUEST_SPEECH&&resultCode==Activity.RESULT_OK&&data!=null){
            ArrayList<String> matches=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(matches!=null&&!matches.isEmpty())setVoiceText(matches.get(0),true);
        }
    }

    private void showSystemKeyboard() {
        enterEditing();input.requestFocus();if(Build.VERSION.SDK_INT>=21)input.setShowSoftInputOnFocus(true);
        InputMethodManager manager=(InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(manager!=null)manager.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
    }
    private void hideSystemKeyboard() {
        if(input==null)return;
        InputMethodManager manager=(InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(manager!=null)manager.hideSoftInputFromWindow(input.getWindowToken(),0);
        if(Build.VERSION.SDK_INT>=21)input.setShowSoftInputOnFocus(false);
    }

    private TextView label(String value,int size,boolean bold) {
        TextView view=new TextView(requireContext());view.setText(value);view.setTextSize(size);view.setTextColor(TEXT);
        view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
    }
    private TextView key(String value,int size,Runnable click) {
        TextView view=label(value,size,false);view.setId(View.generateViewId());view.setFocusable(true);
        view.setGravity(Gravity.CENTER);view.setTextColor(focusText());view.setBackground(focusBackground(6,0x00000000));
        view.setContentDescription(value);view.setOnClickListener(v->click.run());return view;
    }
    private FrameLayout iconButton(String symbol,String description,float iconSize,Runnable click) {
        FrameLayout view=new FrameLayout(requireContext());view.setId(View.generateViewId());view.setFocusable(true);
        view.setContentDescription(description);view.setBackground(focusBackground(18,0x00000000));
        PersonalSearchIcon icon=new PersonalSearchIcon(requireContext(),symbol);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(iconSize),dp(iconSize),Gravity.CENTER);view.addView(icon,params);
        view.setOnClickListener(v->click.run());view.setOnFocusChangeListener((v,focused)->icon.setColor(focused?BACKGROUND:MUTED));return view;
    }
    private ColorStateList focusText() {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{BACKGROUND,MUTED});
    }
    private StateListDrawable focusBackground(float radius,int normal) {
        StateListDrawable drawable=new StateListDrawable();drawable.addState(new int[]{android.R.attr.state_focused},shape(radius,TEXT));
        drawable.addState(new int[]{},shape(radius,normal));return drawable;
    }
    private GradientDrawable shape(float radius,int color) {GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private void place(FrameLayout parent,View view,float left,float top,float width,float height) {
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width<0?(int)width:dp(width),height<0?(int)height:dp(height));
        params.leftMargin=dp(left);params.topMargin=dp(top);parent.addView(view,params);
    }
    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
}
