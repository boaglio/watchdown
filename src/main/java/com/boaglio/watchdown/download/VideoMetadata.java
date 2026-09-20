package com.boaglio.watchdown.download;

import java.time.LocalDate;
import java.util.List;

/** The subset of yt-dlp's metadata that the output files and the caption path need. */
public record VideoMetadata(
        String id,
        String title,
        String channel,
        LocalDate uploadDate,
        int durationSeconds,
        String description,
        List<Chapter> chapters,
        String webpageUrl,
        String language,
        List<String> captionLanguages,
        List<String> automaticCaptionLanguages) {

    public VideoMetadata {
        // Nulls arrive from metadata.json files cached by an older version of watchdown.
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
        captionLanguages = captionLanguages == null ? List.of() : List.copyOf(captionLanguages);
        automaticCaptionLanguages = automaticCaptionLanguages == null
                ? List.of() : List.copyOf(automaticCaptionLanguages);
    }

    public boolean hasChapters() {
        return !chapters.isEmpty();
    }

    /**
     * The caption language that best matches what the user asked for, or null when the video has
     * none. {@code auto} follows the video's own language, then whatever comes first.
     */
    public String captionLanguageFor(String wanted, boolean includeAutomatic) {
        String match = bestMatch(captionLanguages, wanted);
        if (match == null && includeAutomatic) {
            match = bestMatch(automaticCaptionLanguages, wanted);
        }
        return match;
    }

    private String bestMatch(List<String> available, String wanted) {
        if (available.isEmpty()) {
            return null;
        }
        if (wanted == null || "auto".equalsIgnoreCase(wanted)) {
            String fromVideo = language == null ? null : exactOrRegional(available, language);
            return fromVideo != null ? fromVideo : available.getFirst();
        }
        return exactOrRegional(available, wanted);
    }

    /** {@code en} also matches {@code en-US} and {@code en-orig}, which YouTube hands out. */
    private static String exactOrRegional(List<String> available, String wanted) {
        return available.stream()
                .filter(code -> code.equalsIgnoreCase(wanted))
                .findFirst()
                .or(() -> available.stream()
                        .filter(code -> code.regionMatches(true, 0, wanted + "-", 0, wanted.length() + 1))
                        .findFirst())
                .orElse(null);
    }
}
