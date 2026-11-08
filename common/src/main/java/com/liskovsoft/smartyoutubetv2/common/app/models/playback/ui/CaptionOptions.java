package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Presentation over actual subtitle metadata; no URL or token is retained. */
public final class CaptionOptions {
    private CaptionOptions() { }

    /** Null means metadata is incomplete: the caller must keep the complete flat picker. */
    public static Set<String> sourceLanguages(List<MediaSubtitle> subtitles) {
        if (subtitles == null || subtitles.isEmpty()) return null;
        Set<String> names = new HashSet<>();
        for (MediaSubtitle subtitle : subtitles) {
            if (subtitle == null || subtitle.getBaseUrl() == null) return null;
            String name = subtitle.getName() != null ? subtitle.getName() : subtitle.getLanguageCode();
            if (name == null) return null;
            try {
                URI uri = new URI(subtitle.getBaseUrl());
                if (!uri.isAbsolute() || uri.isOpaque()) return null;
                boolean translated = false;
                String query = uri.getRawQuery();
                if (query != null) for (String parameter : query.split("&")) {
                    String key = parameter.split("=", 2)[0];
                    if ("tlang".equals(key)) { translated = true; break; }
                }
                if (!translated) names.add(name);
            } catch (URISyntaxException error) {
                return null;
            }
        }
        return Collections.unmodifiableSet(names);
    }

    public static Partition partition(List<FormatItem> formats, Set<String> sourceLanguages) {
        Partition result = new Partition();
        if (formats != null) for (FormatItem format : formats) {
            if (format == null || format.getType() != FormatItem.TYPE_SUBTITLE) continue;
            if (sourceLanguages == null || format.isDefault() ||
                    !SubtitleTrack.isAuto(format.getLanguage()) || sourceLanguages.contains(format.getLanguage())) {
                result.primary.add(format);
            } else {
                result.translated.add(format);
            }
        }
        return result;
    }

    public static boolean isCurrent(FormatItem format, FormatItem current) {
        if (current == null) return format.isSelected();
        if (format.isDefault() || current.isDefault()) return format.isDefault() && current.isDefault();
        if (format.getFormatId() != null && current.getFormatId() != null) {
            return format.getFormatId().equals(current.getFormatId());
        }
        return format.getLanguage() != null && format.getLanguage().equals(current.getLanguage());
    }

    /** Keep the actual source name (including supplied ASR text), removing only its transport marker. */
    public static CharSequence label(FormatItem format) {
        String language = format.getLanguage();
        return SubtitleTrack.isAuto(language) ? language.substring(0, language.length() - 1) : format.getTitle();
    }

    public static final class Partition {
        public final List<FormatItem> primary = new ArrayList<>();
        public final List<FormatItem> translated = new ArrayList<>();
    }
}
