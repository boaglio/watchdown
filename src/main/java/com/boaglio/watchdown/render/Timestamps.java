package com.boaglio.watchdown.render;

import com.boaglio.watchdown.download.YouTubeUrl;

/**
 * Timestamp formatting and link building. Every summary point links back to the exact moment in
 * the video, always as {@code [mm:ss](url&t=Ns)}, or {@code h:mm:ss} for videos an hour or longer.
 */
public final class Timestamps {

    private static final int ONE_HOUR = 3600;

    private Timestamps() {
    }

    /** Formats a position, choosing the layout from the position itself. */
    public static String format(double seconds) {
        return format(seconds, seconds);
    }

    /**
     * Formats a position, choosing {@code h:mm:ss} when the video is an hour or longer so that
     * every timestamp in one file has the same shape.
     */
    public static String format(double seconds, double videoDuration) {
        long total = Math.max(0, Math.round(seconds));
        long hours = total / ONE_HOUR;
        long minutes = (total % ONE_HOUR) / 60;
        long secs = total % 60;
        boolean longVideo = videoDuration >= ONE_HOUR || total >= ONE_HOUR;
        return longVideo
                ? "%d:%02d:%02d".formatted(hours, minutes, secs)
                : "%02d:%02d".formatted(minutes, secs);
    }

    /** The full Markdown link, for example {@code [03:12](https://…&t=192s)}. */
    public static String link(String videoId, double seconds, double videoDuration) {
        return "[" + format(seconds, videoDuration) + "](" + url(videoId, seconds) + ")";
    }

    public static String url(String videoId, double seconds) {
        return YouTubeUrl.watchUrl(videoId) + "&t=" + Math.max(0, Math.round(seconds)) + "s";
    }

    /** {@code 12:34} style duration, used in prose such as "(12:34, published …)". */
    public static String duration(int seconds) {
        return format(seconds, seconds);
    }
}
