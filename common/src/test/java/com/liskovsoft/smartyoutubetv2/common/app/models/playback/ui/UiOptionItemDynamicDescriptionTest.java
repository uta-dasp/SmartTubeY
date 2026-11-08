package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiOptionItemDynamicDescriptionTest {
    @Test public void detachedSubscriberStopsReceivingUpdatesAndReattachReadsCurrentState() {
        String[] state = {"1080/30/AVC"};
        UiOptionItem.DynamicDescription option = dynamic(state);
        List<CharSequence> rendered = new ArrayList<>();
        Runnable subscriber = () -> rendered.add(option.getDescription());
        option.addDescriptionListener(subscriber);
        option.addDescriptionListener(subscriber);
        state[0] = "720/30/VP9";
        option.notifyDescriptionChanged();
        assertEquals(Arrays.asList("720/30/VP9"), rendered);

        option.removeDescriptionListener(subscriber);
        state[0] = "480/30/AVC";
        option.notifyDescriptionChanged();
        assertEquals(1, rendered.size());
        assertEquals("480/30/AVC", option.getDescription());

        option.addDescriptionListener(subscriber);
        option.notifyDescriptionChanged();
        assertEquals(Arrays.asList("720/30/VP9", "480/30/AVC"), rendered);
    }

    @Test public void subscriberMayDetachDuringNotification() {
        UiOptionItem.DynamicDescription option = dynamic(new String[]{"Off"});
        int[] calls = {0};
        Runnable[] subscriber = {null};
        subscriber[0] = () -> {
            calls[0]++;
            option.removeDescriptionListener(subscriber[0]);
        };
        option.addDescriptionListener(subscriber[0]);
        option.notifyDescriptionChanged();
        option.notifyDescriptionChanged();
        assertEquals(1, calls[0]);
    }

    private static UiOptionItem.DynamicDescription dynamic(String[] state) {
        return new UiOptionItem.DynamicDescription() {
            @Override public CharSequence getDescription() { return state[0]; }
        };
    }
}
