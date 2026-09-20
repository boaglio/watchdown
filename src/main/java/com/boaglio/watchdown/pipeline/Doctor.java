package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.process.ProcessRunner;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;

/**
 * Checks the external tools before anything is downloaded, so a missing dependency fails fast with
 * exit code 3 and a hint about how to fix it.
 */
public class Doctor {

    private static final Logger log = LoggerFactory.getLogger(Doctor.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(20);

    /** One checked dependency, as {@code --check} prints it. */
    public record Check(String name, boolean ok, String detail, String hint) {
    }

    private final ProcessRunner runner;
    private final OllamaApi ollamaApi;
    private final WatchdownConfig config;

    public Doctor(ProcessRunner runner, OllamaApi ollamaApi, WatchdownConfig config) {
        this.runner = runner;
        this.ollamaApi = ollamaApi;
        this.config = config;
    }

    /** What a particular run needs, which is not always everything. */
    public record Needs(boolean ytDlp, boolean whisper) {

        /** Everything the current configuration could ask for, which is what {@code --check} reports. */
        public static Needs of(boolean anyUrls, boolean anyFiles, boolean captionsOnly) {
            return new Needs(anyUrls, anyFiles || (anyUrls && !captionsOnly));
        }
    }

    /** Runs every check for what this run needs, without throwing. */
    public List<Check> checkAll(Needs needs) {
        List<Check> checks = new ArrayList<>();
        checks.add(needs.ytDlp()
                ? tool("yt-dlp", List.of(config.ytDlp().path(), "--version"),
                        "install it with: pip install -U yt-dlp")
                : notNeeded("yt-dlp"));
        checks.add(needs.whisper()
                ? tool("ffmpeg", List.of("ffmpeg", "-version"),
                        "install it with your package manager, for example: apt install ffmpeg")
                : notNeeded("ffmpeg"));
        checks.add(needs.whisper()
                ? tool("whisper", List.of(config.whisper().path(), "--help"),
                        "install it with: pip install -U openai-whisper")
                : notNeeded("whisper"));
        checks.add(ollama());
        return List.copyOf(checks);
    }

    private static Check notNeeded(String name) {
        return new Check(name, true, "not needed for this run", "");
    }

    /** Fails with exit code 3 on the first dependency that is not usable. */
    public void require(Needs needs) {
        for (Check check : checkAll(needs)) {
            if (!check.ok()) {
                throw new DependencyException(check.name() + ": " + check.detail() + "\n  " + check.hint());
            }
        }
    }

    private Check tool(String name, List<String> probe, String hint) {
        boolean available = runner.toolAvailable(probe, PROBE_TIMEOUT);
        return new Check(name, available,
                available ? "found" : "not found on the PATH", hint);
    }

    private Check ollama() {
        String wanted = config.ollama().model();
        List<String> models;
        try {
            OllamaApi.ListModelResponse response = ollamaApi.listModels();
            models = response == null || response.models() == null
                    ? List.of()
                    : response.models().stream().map(OllamaApi.Model::model).toList();
        } catch (RuntimeException e) {
            log.debug("the Ollama check failed", e);
            return new Check("ollama", false,
                    "unreachable at " + config.ollama().baseUrl() + " (" + rootCauseOf(e) + ")",
                    "start it with: ollama serve");
        }
        boolean pulled = models.stream().anyMatch(model -> matches(model, wanted));
        return new Check("ollama", pulled,
                pulled ? "model " + wanted + " is available" : "model " + wanted + " is not pulled",
                "pull it with: ollama pull " + wanted);
    }

    /** The innermost message, which is what actually says why the connection failed. */
    private static String rootCauseOf(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /** Ollama reports {@code gemma3:4b} but users often configure {@code gemma3}. */
    private static boolean matches(String available, String wanted) {
        return available.equals(wanted)
                || available.equals(wanted + ":latest")
                || available.startsWith(wanted + ":");
    }
}
