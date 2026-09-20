package com.boaglio.watchdown.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the precedence rule from AGENTS.md section 4: CLI flag &gt; environment &gt; config file
 * &gt; built-in default. The result is produced once, right after parsing, and nothing else reads
 * flags, the environment or the file again.
 */
@Component
public class ConfigResolver {

    /** Where a resolved value came from, so {@code --verbose} can explain the configuration. */
    public enum Source {
        FLAG, ENV, FILE, DEFAULT;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** The resolved config plus the provenance of the values the user can influence. */
    public record Resolution(WatchdownConfig config, Map<String, Source> sources) {

        public Resolution {
            sources = Map.copyOf(sources);
        }

        public String describe(String key, Object value) {
            return "  %-20s %s (%s)".formatted(key, value, sources.getOrDefault(key, Source.DEFAULT));
        }
    }

    public Resolution resolve(CliOptions cli, WatchdownConfig fromFile, Set<String> fileKeys) {
        return resolve(cli, EnvOptions.none(), fromFile, fileKeys);
    }

    public Resolution resolve(CliOptions cli, EnvOptions env, WatchdownConfig fromFile, Set<String> fileKeys) {
        Map<String, Source> sources = new LinkedHashMap<>();

        Path outputDir = firstOf(cli.outputDir(), env.outputDir(), fromFile.outputDir());
        sources.put("outputDir", source(cli.outputDir() != null, env.outputDir() != null,
                fileKeys.contains("outputDir")));

        sources.put("cacheDir", source(false, false, fileKeys.contains("cacheDir")));

        boolean verbose = cli.verbose() != null ? cli.verbose() : fromFile.verbose();
        sources.put("verbose", source(cli.verbose() != null, false, fileKeys.contains("verbose")));

        boolean keepAudio = cli.keepAudio() != null ? cli.keepAudio() : fromFile.keepAudio();
        sources.put("keepAudio", source(cli.keepAudio() != null, false, fileKeys.contains("keepAudio")));

        CaptionMode captions = cli.captions() != null ? cli.captions() : fromFile.captions();
        sources.put("captions", source(cli.captions() != null, false, fileKeys.contains("captions")));

        WhisperConfig whisper = fromFile.whisper();
        if (cli.whisperModel() != null || cli.whisperLanguage() != null) {
            whisper = new WhisperConfig(
                    whisper.path(),
                    cli.whisperModel() != null ? cli.whisperModel() : whisper.model(),
                    cli.whisperLanguage() != null ? cli.whisperLanguage() : whisper.language(),
                    whisper.device(),
                    whisper.timeoutMinutes());
        }
        sources.put("whisper.model", source(cli.whisperModel() != null, false,
                fileKeys.contains("whisper.model")));
        sources.put("whisper.language", source(cli.whisperLanguage() != null, false,
                fileKeys.contains("whisper.language")));

        OllamaConfig ollama = fromFile.ollama();
        if (cli.ollamaModel() != null) {
            ollama = new OllamaConfig(ollama.baseUrl(), cli.ollamaModel(), ollama.temperature(),
                    ollama.numCtx(), ollama.timeoutSeconds());
        }
        sources.put("ollama.model", source(cli.ollamaModel() != null, false, fileKeys.contains("ollama.model")));
        sources.put("ollama.baseUrl", source(false, false, fileKeys.contains("ollama.baseUrl")));

        SummaryConfig summary = fromFile.summary();
        if (cli.summaryLanguage() != null) {
            summary = new SummaryConfig(cli.summaryLanguage(), summary.chunkTokens(), summary.maxKeyPoints(),
                    summary.sectionAttempts());
        }
        sources.put("summary.language", source(cli.summaryLanguage() != null, false,
                fileKeys.contains("summary.language")));

        WatchdownConfig resolved = new WatchdownConfig(
                outputDir,
                fromFile.cacheDir(),
                keepAudio,
                verbose,
                captions,
                fromFile.ytDlp(),
                whisper,
                ollama,
                summary);

        return new Resolution(resolved, sources);
    }

    private static Source source(boolean fromFlag, boolean fromEnv, boolean fromFile) {
        if (fromFlag) {
            return Source.FLAG;
        }
        if (fromEnv) {
            return Source.ENV;
        }
        return fromFile ? Source.FILE : Source.DEFAULT;
    }

    @SafeVarargs
    private static <T> T firstOf(T... candidates) {
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
