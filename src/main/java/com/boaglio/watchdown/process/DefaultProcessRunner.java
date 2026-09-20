package com.boaglio.watchdown.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs commands with {@link ProcessBuilder}. Output is captured and, in verbose mode, streamed to
 * the log with a {@code  │ } prefix. When the timeout is exceeded the whole process tree is killed.
 */
@Component
public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessRunner.class);
    private static final String STREAM_PREFIX = "  │ ";

    @Override
    public ProcessResult run(List<String> command, Duration timeout, Path workingDirectory) {
        if (command.isEmpty()) {
            throw new ProcessException("empty command");
        }
        log.debug("$ {}", String.join(" ", command));
        long startedAt = System.nanoTime();

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ProcessException(command.getFirst() + ": cannot be started (" + e.getMessage() + ")", e);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outReader = drain(process.getInputStream(), stdout);
        Thread errReader = drain(process.getErrorStream(), stderr);

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(process);
            throw new ProcessException(command.getFirst() + ": interrupted", e);
        }

        if (!finished) {
            killTree(process);
            throw new ProcessException(command.getFirst() + ": timed out after " + timeout.toSeconds() + "s");
        }

        join(outReader);
        join(errReader);

        Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
        int exitCode = process.exitValue();
        log.debug("→ exit {} in {}ms", exitCode, duration.toMillis());
        return new ProcessResult(command, exitCode, stdout.toString(), stderr.toString(), duration);
    }

    private Thread drain(InputStream stream, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append('\n');
                    log.debug("{}{}", STREAM_PREFIX, line);
                }
            } catch (IOException e) {
                log.debug("{}<stream closed: {}>", STREAM_PREFIX, e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread thread) {
        try {
            thread.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void killTree(Process process) {
        List<ProcessHandle> descendants = new ArrayList<>(process.descendants().toList());
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
