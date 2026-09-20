package com.boaglio.watchdown.download;

import java.time.LocalDate;
import java.util.List;

/** The subset of yt-dlp's metadata that the output files need. */
public record VideoMetadata(
        String id,
        String title,
        String channel,
        LocalDate uploadDate,
        int durationSeconds,
        String description,
        List<Chapter> chapters,
        String webpageUrl) {

    public VideoMetadata {
        chapters = List.copyOf(chapters);
    }

    public boolean hasChapters() {
        return !chapters.isEmpty();
    }
}
