package com.boaglio.watchdown.cli;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.config.CliOptions;
import com.boaglio.watchdown.config.ConfigLoader;
import com.boaglio.watchdown.config.ConfigResolver;
import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.download.YouTubeUrl;
import com.boaglio.watchdown.pipeline.Doctor;
import com.boaglio.watchdown.pipeline.Pipeline;
import com.boaglio.watchdown.pipeline.PipelineFactory;
import com.boaglio.watchdown.pipeline.VideoJob;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The watchdown command line. It resolves the configuration once, then runs the pipeline for each
 * URL. One failing URL does not stop the others, and the exit code is the highest any URL produced.
 */
@Component
@Command(
        name = "watchdown",
        mixinStandardHelpOptions = true,
        versionProvider = Version.class,
        sortOptions = false,
        description = "Turns a YouTube video into a folder of Markdown an agent can read.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
public class WatchdownCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(WatchdownCommand.class);
    private static final String APPLICATION_LOGGER = "com.boaglio.watchdown";

    @Parameters(arity = "0..*", paramLabel = "<url>",
            description = "One or more YouTube video URLs.")
    private List<String> urls = new ArrayList<>();

    @Option(names = {"-o", "--output"}, paramLabel = "<dir>",
            description = "Output root directory (config: outputDir).")
    private Path outputDir;

    @Option(names = {"-c", "--config"}, paramLabel = "<file>",
            description = "Config file to use.")
    private Path configFile;

    @Option(names = {"-m", "--model"}, paramLabel = "<name>",
            description = "Ollama model for summaries (config: ollama.model).")
    private String ollamaModel;

    @Option(names = {"-w", "--whisper-model"}, paramLabel = "<n>",
            description = "Whisper model, tiny to large (config: whisper.model).")
    private String whisperModel;

    @Option(names = {"-l", "--language"}, paramLabel = "<code>",
            description = "Spoken language, e.g. en, pt; 'auto' to detect (config: whisper.language).")
    private String language;

    @Option(names = {"-s", "--summary-language"}, paramLabel = "<code>",
            description = "Language of the summary; 'auto' = same as video (config: summary.language).")
    private String summaryLanguage;

    @Option(names = {"-v", "--verbose"},
            description = "Detailed progress, commands, timings (config: verbose).")
    private Boolean verbose;

    @Option(names = {"-f", "--force"},
            description = "Ignore cached audio and transcript, redo every step.")
    private boolean force;

    @Option(names = "--keep-audio",
            description = "Keep the downloaded audio file in the output folder.")
    private Boolean keepAudio;

    @Option(names = "--check",
            description = "Verify external tools and the Ollama model, then exit.")
    private boolean check;

    @Option(names = "--init-config",
            description = "Write a default config file to the default path, then exit.")
    private boolean initConfig;

    private final ConfigLoader configLoader;
    private final ConfigResolver configResolver;
    private final PipelineFactory pipelineFactory;
    private final LoggingSystem loggingSystem;

    public WatchdownCommand(ConfigLoader configLoader, ConfigResolver configResolver,
            PipelineFactory pipelineFactory, LoggingSystem loggingSystem) {
        this.configLoader = configLoader;
        this.configResolver = configResolver;
        this.pipelineFactory = pipelineFactory;
        this.loggingSystem = loggingSystem;
    }

    @Override
    public Integer call() {
        ConsoleReporter reporter = new ConsoleReporter(System.out, System.err, Boolean.TRUE.equals(verbose));
        try {
            return run(reporter);
        } catch (WatchdownException e) {
            reporter.error(e.getMessage());
            log.debug("failed", e);
            return e.exitCode();
        } catch (RuntimeException e) {
            reporter.error(e.getMessage() == null ? e.toString() : e.getMessage());
            log.debug("unexpected failure", e);
            return ExitCode.UNEXPECTED_ERROR;
        }
    }

    private int run(ConsoleReporter reporter) {
        if (initConfig) {
            return writeDefaultConfig(reporter);
        }

        Path file = configFile != null ? configFile : ConfigLoader.defaultConfigPath();
        if (configFile != null && !Files.exists(configFile)) {
            throw new UsageException("config file not found: " + configFile);
        }
        WatchdownConfig fromFile = configLoader.load(file);
        ConfigResolver.Resolution resolution = configResolver.resolve(
                new CliOptions(outputDir, ollamaModel, whisperModel, language, summaryLanguage, verbose, keepAudio),
                fromFile,
                configLoader.keysPresentIn(file));
        WatchdownConfig config = resolution.config();

        if (config.verbose()) {
            loggingSystem.setLogLevel(APPLICATION_LOGGER, LogLevel.DEBUG);
            reporter = new ConsoleReporter(System.out, System.err, true);
            describe(resolution, file);
        }

        if (check) {
            return report(pipelineFactory.doctor(config), reporter);
        }
        if (urls.isEmpty()) {
            throw new UsageException("no URL given. Try 'watchdown --help'.");
        }

        List<YouTubeUrl> parsed = urls.stream().map(YouTubeUrl::parse).toList();
        pipelineFactory.doctor(config).requireAll();

        Pipeline pipeline = pipelineFactory.create(config, reporter, force);
        int worst = ExitCode.OK;
        for (YouTubeUrl url : parsed) {
            worst = ExitCode.worst(worst, runOne(pipeline, url, reporter));
        }
        return worst;
    }

    private int runOne(Pipeline pipeline, YouTubeUrl url, ConsoleReporter reporter) {
        try {
            VideoJob job = pipeline.run(url);
            if (job.wroteSomething()) {
                reporter.output(job.outputFolder());
            }
            return job.exitCode();
        } catch (WatchdownException e) {
            reporter.error(url.canonicalUrl() + ": " + e.getMessage());
            log.debug("{} failed", url.canonicalUrl(), e);
            return e.exitCode();
        }
    }

    private int report(Doctor doctor, ConsoleReporter reporter) {
        int exitCode = ExitCode.OK;
        for (Doctor.Check result : doctor.checkAll()) {
            reporter.info("%-8s %-4s %s".formatted(result.name(), result.ok() ? "ok" : "FAIL", result.detail()));
            if (!result.ok()) {
                reporter.info("         " + result.hint());
                exitCode = ExitCode.MISSING_DEPENDENCY;
            }
        }
        return exitCode;
    }

    private int writeDefaultConfig(ConsoleReporter reporter) {
        Path target = configFile != null ? configFile : ConfigLoader.defaultConfigPath();
        if (Files.exists(target) && !force) {
            throw new UsageException(target + " already exists, pass --force to overwrite it");
        }
        try (InputStream defaults = getClass().getResourceAsStream("/default-config.json")) {
            if (defaults == null) {
                throw new IllegalStateException("default-config.json is missing from the jar");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, new String(defaults.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UsageException("cannot write " + target + ": " + e.getMessage(), e);
        }
        reporter.info("wrote " + target);
        return ExitCode.OK;
    }

    private void describe(ConfigResolver.Resolution resolution, Path file) {
        WatchdownConfig config = resolution.config();
        log.debug("config file: {}", Files.exists(file) ? file : file + " (not found, using defaults)");
        if (!Files.exists(file)) {
            log.debug("run 'watchdown --init-config' to write one");
        }
        log.debug("resolved configuration:");
        log.debug("{}", resolution.describe("outputDir", config.outputDir()));
        log.debug("{}", resolution.describe("cacheDir", config.cacheDir()));
        log.debug("{}", resolution.describe("keepAudio", config.keepAudio()));
        log.debug("{}", resolution.describe("verbose", config.verbose()));
        log.debug("{}", resolution.describe("whisper.model", config.whisper().model()));
        log.debug("{}", resolution.describe("whisper.language", config.whisper().language()));
        log.debug("{}", resolution.describe("ollama.baseUrl", config.ollama().baseUrl()));
        log.debug("{}", resolution.describe("ollama.model", config.ollama().model()));
        log.debug("{}", resolution.describe("summary.language", config.summary().language()));
    }
}
