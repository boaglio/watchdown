package com.boaglio.watchdown.download;

import com.boaglio.watchdown.process.ProcessException;
import com.boaglio.watchdown.process.ProcessResult;
import com.boaglio.watchdown.process.ProcessRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks ffprobe how long a local file is.
 *
 * <p>Best effort: when ffprobe is missing or cannot read the file the duration comes from the
 * transcript instead, so a working run never depends on it.
 */
public class MediaProbe {

    private static final Logger log = LoggerFactory.getLogger(MediaProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ProcessRunner runner;

    public MediaProbe(ProcessRunner runner) {
        this.runner = runner;
    }

    /** The duration in whole seconds, or 0 when it could not be determined. */
    public int durationOf(Path file) {
        List<String> command = List.of("ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.toString());
        try {
            ProcessResult result = runner.run(command, TIMEOUT);
            if (!result.successful()) {
                log.debug("ffprobe could not read {} (exit {})", file, result.exitCode());
                return 0;
            }
            return (int) Math.round(Double.parseDouble(result.stdout().strip()));
        } catch (ProcessException | NumberFormatException e) {
            log.debug("no duration for {}: {}", file, e.getMessage());
            return 0;
        }
    }
}
