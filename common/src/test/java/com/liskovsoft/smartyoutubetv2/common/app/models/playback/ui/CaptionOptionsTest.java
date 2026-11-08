package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptionOptionsTest {
    @Test public void realSourceAsrIsPrimaryAndActualTranslationsKeepOriginalIdentity() {
        Set<String> names = CaptionOptions.sourceLanguages(Arrays.asList(
                subtitle("English", "https://example.invalid/caption?lang=en", "manual"),
                subtitle("English (auto-generated)*", "https://example.invalid/caption?lang=en&kind=asr", "asr"),
                subtitle("English*", "https://example.invalid/caption?lang=en&note=tlang%3Dfr", "asr"),
                subtitle("French*", "https://example.invalid/caption?lang=en&tlang=fr", "asr")));
        assertEquals(3, names.size());
        assertFalse(names.contains("French*"));
        FakeFormat off = format("off", null); off.disabled = true;
        FakeFormat manual = format("1", "English");
        FakeFormat asr = format("2", "English (auto-generated)*");
        FakeFormat sameLanguage = format("3", "English*");
        FakeFormat translated = format("4", "French*");
        List<FormatItem> source = Arrays.asList(off, manual, asr, sameLanguage, translated);
        CaptionOptions.Partition result = CaptionOptions.partition(source, names);
        assertEquals(Arrays.asList(off, manual, asr, sameLanguage), result.primary);
        assertEquals(1, result.translated.size());
        assertSame(translated, result.translated.get(0));
        assertEquals("English (auto-generated)", CaptionOptions.label(asr));
        assertEquals("French", CaptionOptions.label(translated));
        assertEquals(5, source.size());
    }

    @Test public void unavailableOrMalformedMetadataKeepsTheCompleteRealPicker() {
        assertNull(CaptionOptions.sourceLanguages(null));
        assertNull(CaptionOptions.sourceLanguages(Arrays.asList(subtitle("English", "not a URL", "asr"))));
        assertNull(CaptionOptions.sourceLanguages(Arrays.asList(subtitle("English", null, "asr"))));
        FakeFormat source = format("1", "English (auto-generated)*");
        FakeFormat translated = format("2", "French*");
        CaptionOptions.Partition result = CaptionOptions.partition(Arrays.asList(source, translated), null);
        assertEquals(Arrays.asList(source, translated), result.primary);
        assertTrue(result.translated.isEmpty());
    }

    @Test public void checkedTrackUsesActualFormatIdRatherThanTrimmedLanguageEquality() {
        FakeFormat sourceEquivalent = format("1", "English*");
        FakeFormat current = format("2", "English*");
        assertFalse(CaptionOptions.isCurrent(sourceEquivalent, current));
        assertTrue(CaptionOptions.isCurrent(current, format("2", "English*")));
        FakeFormat off = format("off", null); off.disabled = true;
        assertFalse(CaptionOptions.isCurrent(off, current));
        assertTrue(CaptionOptions.isCurrent(off, off));
    }

    @Test public void childChoiceSurvivesBackBeforeEngineUpdateAndFreshDialogResetsToEngine() {
        FakeFormat engineFormat = format("1", "English*");
        FakeFormat chosen = format("2", "Abkhazian*");
        CaptionOptions.DialogSelection selection = new CaptionOptions.DialogSelection();
        assertSame(engineFormat, selection.current(engineFormat));
        int[] calls = {0};
        OptionItem original = UiOptionItem.from(chosen, option -> {
            calls[0]++;
            assertSame(chosen, UiOptionItem.toFormat(option));
            assertTrue(option.isSelected());
            assertSame(chosen, selection.current(engineFormat));
        });
        selection.select(chosen, original, true);
        assertEquals(1, calls[0]);
        assertSame(chosen, selection.current(engineFormat)); // Engine still reports the old track on Back.
        assertTrue(CaptionOptions.isCurrent(chosen, selection.current(engineFormat)));
        assertFalse(CaptionOptions.isCurrent(engineFormat, selection.current(engineFormat)));
        assertEquals("Abkhazian", CaptionOptions.label(selection.current(engineFormat)));
        assertEquals("English", CaptionOptions.label(engineFormat)); // No actual-stream state is changed here.
        assertSame(engineFormat, new CaptionOptions.DialogSelection().current(engineFormat));
    }

    @Test public void reusedFormatIdsAcrossLanguagesCheckOnlyTheActualLanguageAndRoute() {
        FakeFormat english = format("0", "English*");
        FakeFormat abkhazian = format("0", "Abkhazian*");
        FakeFormat zulu = format("0", "Zulu*");
        FakeFormat current = format("0", "Abkhazian*");
        int selectedCount = 0;
        CharSequence routeLabel = null;
        for (FormatItem option : Arrays.asList(english, abkhazian, zulu)) {
            if (CaptionOptions.isCurrent(option, current)) {
                selectedCount++;
                assertSame(abkhazian, option);
                routeLabel = CaptionOptions.label(option);
            }
        }
        assertEquals(1, selectedCount);
        assertEquals("Abkhazian", routeLabel);
        assertFalse(CaptionOptions.isCurrent(english, current));
        assertFalse(CaptionOptions.isCurrent(zulu, current));
    }

    private static MediaSubtitle subtitle(String name, String url, String type) {
        return new MediaSubtitle() {
            @Override public String getBaseUrl() { return url; }
            @Override public boolean isTranslatable() { return true; }
            @Override public String getLanguageCode() { return "en"; }
            @Override public String getVssId() { return "source-id"; }
            @Override public String getName() { return name; }
            @Override public String getMimeType() { return "text/vtt"; }
            @Override public String getCodecs() { return "wvtt"; }
            @Override public String getType() { return type; }
        };
    }

    private static FakeFormat format(String id, String language) { return new FakeFormat(id, language); }
    private static final class FakeFormat implements FormatItem {
        final String id, language;
        boolean disabled;
        FakeFormat(String id, String language) { this.id = id; this.language = language; }
        @Override public int getId() { return id.hashCode(); }
        @Override public String getFormatId() { return id; }
        @Override public CharSequence getTitle() { return language; }
        @Override public boolean isDefault() { return disabled; }
        @Override public boolean isSelected() { return false; }
        @Override public boolean isPreset() { return false; }
        @Override public float getFrameRate() { return 0; }
        @Override public String getLanguage() { return language; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return 0; }
        @Override public int getType() { return FormatItem.TYPE_SUBTITLE; }
        @Override public MediaTrack getTrack() { return null; }
    }
}
