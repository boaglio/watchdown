package com.boaglio.watchdown.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The intermediate files of one run, under {@code <cacheDir>/<videoId>/}.
 *
 * <p>Transcription is the slow step, so a rerun reuses whatever is already there. {@code --force}
 * makes every lookup a miss. Summaries are never cached: changing the model or the prompt and
 * rerunning should just work.
 */
public class Cache {

    private final Path root;
    private final boolean force;

    public Cache(Path root, boolean force) {
        this.root = root;
        this.force = force;
    }

    public Path directoryFor(String videoId) {
        Path directory = root.resolve(videoId);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create the cache directory " + directory, e);
        }
        return directory;
    }

    public Path metadataFile(String videoId) {
        return directoryFor(videoId).resolve("metadata.json");
    }

    public Path audioFile(String videoId) {
        return directoryFor(videoId).resolve("audio.mp3");
    }

    public Path transcriptFile(String videoId) {
        return directoryFor(videoId).resolve("audio.json");
    }

    /** True when the file can be reused, which is never the case under {@code --force}. */
    public boolean isHit(Path file) {
        return !force && Files.isRegularFile(file);
    }

    public boolean forced() {
        return force;
    }
}
