package com.liskovsoft.smartyoutubetv2.tv.ui.personal.search;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Honors SmartTube's three recognizer choices and owns their search-screen lifecycle. */
final class PersonalSearchVoiceController implements RecognitionListener {
    static final int REQUEST_SPEECH = 0x501;
    static final int REQUEST_MIC = 0x502;
    private final PersonalSearchFragment fragment;
    private SpeechRecognizer recognizer;
    private boolean gotevListening;

    PersonalSearchVoiceController(PersonalSearchFragment fragment) { this.fragment = fragment; }

    void start() {
        if (!fragment.isAdded()) return;
        int type = SearchData.instance(fragment.requireContext()).getSpeechRecognizerType();
        if (type == SearchData.SPEECH_RECOGNIZER_INTENT) { startIntent(); return; }
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(fragment.requireContext(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fragment.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        stop();
        fragment.setListening(true);
        try {
            if (type == SearchData.SPEECH_RECOGNIZER_GOTEV) {
                Speech.init(fragment.requireContext());
                gotevListening = true;
                Speech.getInstance().startListening(new SpeechDelegate() {
                    @Override public void onStartOfSpeech() { fragment.setListening(true); }
                    @Override public void onSpeechRmsChanged(float value) { }
                    @Override public void onSpeechPartialResults(List<String> results) {
                        if (fragment.isAdded() && results != null && !results.isEmpty()) fragment.setVoiceText(results.get(0), false);
                    }
                    @Override public void onSpeechResult(String result) { complete(result); }
                });
            } else if (SpeechRecognizer.isRecognitionAvailable(fragment.requireContext())) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(fragment.requireContext());
                recognizer.setRecognitionListener(this);
                recognizer.startListening(intent());
            } else { startIntent(); }
        } catch (SpeechRecognitionNotAvailable | GoogleVoiceTypingDisabledException | RuntimeException error) {
            stop(); fragment.voiceUnavailable(R.string.personal_search_voice_unavailable);
        }
    }

    @SuppressWarnings("deprecation") private void startIntent() {
        fragment.setListening(false);
        try { fragment.startActivityForResult(intent(), REQUEST_SPEECH); }
        catch (ActivityNotFoundException | SecurityException error) { fragment.voiceUnavailable(R.string.personal_search_voice_unavailable); }
    }

    private Intent intent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Build.VERSION.SDK_INT >= 21 ? Locale.getDefault().toLanguageTag() : Locale.getDefault().toString())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, fragment.getString(R.string.personal_search_voice));
    }

    void stop() {
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        if (gotevListening) {
            try { Speech.getInstance().stopListening(); } catch (RuntimeException | NoSuchMethodError ignored) { }
            gotevListening = false;
        }
        fragment.setListening(false);
    }

    private void complete(String text) {
        fragment.setListening(false);
        if (fragment.isAdded() && text != null && !text.trim().isEmpty()) fragment.setVoiceText(text, true);
    }
    @Override public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        complete(matches == null || matches.isEmpty() ? null : matches.get(0));
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (fragment.isAdded() && matches != null && !matches.isEmpty()) fragment.setVoiceText(matches.get(0), false);
    }
    @Override public void onError(int error) {
        fragment.setListening(false);
        if (error != SpeechRecognizer.ERROR_CLIENT && fragment.isAdded()) fragment.voiceUnavailable(R.string.personal_search_voice_unavailable);
    }
    @Override public void onReadyForSpeech(Bundle params) { fragment.setListening(true); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
