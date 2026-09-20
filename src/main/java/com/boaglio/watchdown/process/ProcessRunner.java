package com.boaglio.watchdown.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Every external command goes through this interface, which is also what tests replace so that
 * {@code ./mvnw test} never touches yt-dlp, whisper, or Ollama.
 */
public interface ProcessRunner {

    /**
     * Runs a command and waits for it. Arguments are passed as a list and never through a shell,
     * because the URL is untrusted input.
     *
     * @throws ProcessException if the command cannot be started or exceeds the timeout
     */
    ProcessResult run(List<String> command, Duration timeout, Path workingDirectory);

    default ProcessResult run(List<String> command, Duration timeout) {
        return run(command, timeout, null);
    }

    /** True when the tool answers at all, which is how {@code --check} looks for it on the PATH. */
    default boolean toolAvailable(List<String> versionCommand, Duration timeout) {
        try {
            return run(versionCommand, timeout).successful();
        } catch (ProcessException e) {
            return false;
        }
    }
}
