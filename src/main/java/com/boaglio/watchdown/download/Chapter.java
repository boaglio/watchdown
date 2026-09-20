package com.boaglio.watchdown.download;

/** A chapter as yt-dlp reports it; seconds are relative to the start of the video. */
public record Chapter(String title, double start, double end) {
}
