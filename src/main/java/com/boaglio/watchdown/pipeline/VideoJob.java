package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.download.YouTubeUrl;
import java.nio.file.Path;

/** The outcome of one URL: where the folder was written, and the exit code it produced. */
public record VideoJob(YouTubeUrl url, Path outputFolder, int exitCode, String failure) {

    public static VideoJob succeeded(YouTubeUrl url, Path folder) {
        return new VideoJob(url, folder, 0, null);
    }

    public static VideoJob failed(YouTubeUrl url, Path folder, int exitCode, String failure) {
        return new VideoJob(url, folder, exitCode, failure);
    }

    public boolean wroteSomething() {
        return outputFolder != null;
    }
}
