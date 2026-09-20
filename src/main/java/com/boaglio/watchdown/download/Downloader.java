package com.boaglio.watchdown.download;

import java.nio.file.Path;

/** Fetches the metadata and the audio of a single video. */
public interface Downloader {

    VideoMetadata fetchMetadata(YouTubeUrl url);

    /** Downloads the audio into {@code targetDirectory} and returns the audio file. */
    Path downloadAudio(YouTubeUrl url, Path targetDirectory);
}
