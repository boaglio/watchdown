package com.boaglio.watchdown.download;

import java.nio.file.Path;

/** Fetches the metadata, the captions and the audio of a single video. */
public interface Downloader {

    VideoMetadata fetchMetadata(YouTubeUrl url);

    /** Downloads the audio into {@code targetDirectory} and returns the audio file. */
    Path downloadAudio(YouTubeUrl url, Path targetDirectory);

    /**
     * Downloads one caption track into {@code targetDirectory} and returns the file.
     *
     * @param language the exact caption language code, as the metadata listed it
     * @param automatic whether the track is one of YouTube's automatic captions
     */
    Path downloadCaptions(YouTubeUrl url, Path targetDirectory, String language, boolean automatic);
}
