package com.boaglio.watchdown.pipeline;

import java.nio.file.Path;

/**
 * The outcome of one job: where the folder was written, and the exit code it produced.
 *
 * @param source the URL or the file name, for messages
 */
public record VideoJob(String source, Path outputFolder, int exitCode, String failure) {

    public static VideoJob succeeded(String source, Path folder) {
        return new VideoJob(source, folder, 0, null);
    }

    public static VideoJob failed(String source, Path folder, int exitCode, String failure) {
        return new VideoJob(source, folder, exitCode, failure);
    }

    public boolean wroteSomething() {
        return outputFolder != null;
    }
}
