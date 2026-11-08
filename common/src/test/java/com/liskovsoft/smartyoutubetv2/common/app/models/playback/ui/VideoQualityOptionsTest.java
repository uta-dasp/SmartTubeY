package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class VideoQualityOptionsTest {
    @Test public void codecDuplicatesKeepSourcePreferenceUnlessCurrentTrackIsPresent() {
        FakeFormat preferred = video("preferred-codec", 1080, 30);
        FakeFormat current = video("current-codec", 1080, 30);
        FakeFormat staleSelected = video("stale-selected", 720, 30);
        staleSelected.selected = true;
        List<FormatItem> source = Arrays.asList(preferred, current, staleSelected);
        List<OptionItem> basic = VideoQualityOptions.create(source, current, null);
        assertEquals(2, basic.size());
        assertEquals("1080p", basic.get(0).getTitle());
        assertSame(current, basic.get(0).getData());
        assertTrue(basic.get(0).isSelected());
        assertFalse(basic.get(1).isSelected());
        assertSame(preferred, VideoQualityOptions.create(source, null, null).get(0).getData());
        assertEquals(3, source.size()); // Advanced's source list is unchanged.
    }

    @Test public void distinguishesRealFrameRatesAndCoalescesFractionalCodecVariants() {
        List<OptionItem> basic = VideoQualityOptions.create(Arrays.asList(
                video("30", 1080, 30), video("29.97", 1080, 29.97f),
                video("24", 1080, 24), video("59.94", 1080, 59.94f),
                video("60", 1080, 60), video("4k", 2160, 30)), null, null);
        assertEquals(4, basic.size());
        assertEquals("2160p", basic.get(0).getTitle());
        assertEquals("1080p60", basic.get(1).getTitle());
        assertEquals("1080p30", basic.get(2).getTitle());
        assertEquals("1080p24", basic.get(3).getTitle());
    }

    @Test public void excludesDisabledUnknownResolutionAndNonVideoWithoutInventingAuto() {
        FakeFormat disabled = video("disabled", 0, 0); disabled.disabled = true;
        FakeFormat audio = video("audio", 1080, 30); audio.type = FormatItem.TYPE_AUDIO;
        FakeFormat unknown = video("unknown-size", -1, 30);
        FakeFormat noFps = video("unknown-fps", 720, Float.NaN);
        List<OptionItem> basic = VideoQualityOptions.create(Arrays.asList(disabled, audio, unknown, null, noFps), disabled, null);
        assertEquals(1, basic.size());
        assertEquals("720p", basic.get(0).getTitle());
        assertSame(noFps, basic.get(0).getData());
        assertFalse(basic.get(0).isSelected());
        assertTrue(VideoQualityOptions.create(Arrays.asList(disabled, unknown), disabled, null).isEmpty());
    }

    @Test public void selectionDelegatesTheOriginalFormatOptionToTheExistingCallback() {
        FakeFormat preferred = video("preferred", 1080, 30);
        FakeFormat current = video("current", 1080, 30);
        List<OptionItem> callbacks = new ArrayList<>();
        List<OptionItem> basic = VideoQualityOptions.create(Arrays.asList(preferred, current), current, callbacks::add);
        basic.get(0).onSelect(true);
        assertEquals(1, callbacks.size());
        assertSame(current, UiOptionItem.toFormat(callbacks.get(0)));
        assertTrue(callbacks.get(0).isSelected());
        assertNotSame(basic.get(0), callbacks.get(0));
    }

    private static FakeFormat video(String name, int height, float fps) { return new FakeFormat(name, height, fps); }
    private static final class FakeFormat implements FormatItem {
        final String name; final int height; final float fps;
        int type = FormatItem.TYPE_VIDEO;
        boolean selected, disabled;
        FakeFormat(String name, int height, float fps) { this.name = name; this.height = height; this.fps = fps; }
        @Override public int getId() { return name.hashCode(); }
        @Override public String getFormatId() { return name; }
        @Override public CharSequence getTitle() { return name; }
        @Override public boolean isDefault() { return disabled; }
        @Override public boolean isSelected() { return selected; }
        @Override public boolean isPreset() { return false; }
        @Override public float getFrameRate() { return fps; }
        @Override public String getLanguage() { return null; }
        @Override public int getWidth() { return height > 0 ? height * 16 / 9 : -1; }
        @Override public int getHeight() { return height; }
        @Override public int getType() { return type; }
        @Override public MediaTrack getTrack() { return null; }
    }
}
