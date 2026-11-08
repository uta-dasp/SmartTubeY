package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Basic quality labels over original, source-ordered track choices. No capabilities are added. */
public final class VideoQualityOptions {
    private VideoQualityOptions() { }

    public static List<OptionItem> create(List<FormatItem> formats, FormatItem current, OptionCallback callback) {
        Map<String, Choice> groups = new LinkedHashMap<>();
        boolean currentAvailable = false;
        if (formats != null && current != null) {
            for (FormatItem format : formats) {
                if (eligible(format) && current.equals(format)) { currentAvailable = true; break; }
            }
        }
        if (formats != null) for (FormatItem format : formats) {
            if (!eligible(format)) continue;
            int height = height(format);
            if (height <= 0) continue; // Unknown dimensions retain their full HQ entry.
            int fps = frameRate(format);
            boolean selected = currentAvailable ? current.equals(format) : format.isSelected();
            String key = height + ":" + fps;
            Choice previous = groups.get(key);
            // TrackSelectorManager already supplies preferred codec/bitrate order.
            // Keep its first candidate unless the actual current track belongs to this group.
            if (previous == null || (selected && !previous.selected)) {
                groups.put(key, new Choice(format, height, fps, selected));
            }
        }
        List<Choice> choices = new ArrayList<>(groups.values());
        Collections.sort(choices, (left, right) -> left.height == right.height
                ? right.fps - left.fps : right.height - left.height);
        List<OptionItem> result = new ArrayList<>();
        for (Choice choice : choices) {
            boolean multipleFrameRates = false;
            for (Choice other : choices) if (other.height == choice.height && other.fps != choice.fps) {
                multipleFrameRates = true; break;
            }
            String title = choice.height + "p";
            if (choice.fps > 30 || (multipleFrameRates && choice.fps > 0)) title += choice.fps;
            OptionItem original = UiOptionItem.from(choice.format, callback);
            // Delegate through the original option: UiOptionItem.toFormat(callbackItem) must
            // still return the exact engine FormatItem, not a reconstructed display preset.
            result.add(UiOptionItem.from(title, option -> original.onSelect(option.isSelected()),
                    choice.selected, choice.format));
        }
        return result;
    }

    private static boolean eligible(FormatItem format) {
        return format != null && format.getType() == FormatItem.TYPE_VIDEO && !format.isDefault();
    }

    private static int height(FormatItem format) {
        MediaTrack track = format.getTrack();
        return track != null && track.format != null
                ? TrackSelectorUtil.getRealHeight(track.format) : format.getHeight();
    }

    private static int frameRate(FormatItem format) {
        MediaTrack track = format.getTrack();
        // ExoFormatItem substitutes 30 for unknown live fps. Read the raw track when available.
        float fps = track != null && track.format != null ? track.format.frameRate : format.getFrameRate();
        return fps > 0 && !Float.isNaN(fps) && !Float.isInfinite(fps) ? Math.round(fps) : 0;
    }

    private static final class Choice {
        final FormatItem format;
        final int height;
        final int fps;
        final boolean selected;
        Choice(FormatItem format, int height, int fps, boolean selected) {
            this.format = format; this.height = height; this.fps = fps; this.selected = selected;
        }
    }
}
