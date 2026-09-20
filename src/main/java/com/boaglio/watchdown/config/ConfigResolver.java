package com.boaglio.watchdown.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the precedence rule from AGENTS.md section 4: CLI flag &gt; config file &gt; built-in
 * default. The result is produced once, right after parsing, and nothing else reads flags or the
 * file again.
 */
@Component
public class ConfigResolver {

    /** Where a resolved value came from, so {@code --verbose} can explain the configuration. */
    public enum Source {
        FLAG, FILE, DEFAULT;

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
        Map<String, Source> sources = new LinkedHashMap<>();

        var outputDir = cli.outputDir() != null ? cli.outputDir() : fromFile.outputDir();
        sources.put("outputDir", source(cli.outputDir() != null, fileKeys.contains("outputDir")));

        sources.put("cacheDir", source(false, fileKeys.contains("cacheDir")));

        boolean verbose = cli.verbose() != null ? cli.verbose() : fromFile.verbose();
        sources.put("verbose", source(cli.verbose() != null, fileKeys.contains("verbose")));

        boolean keepAudio = cli.keepAudio() != null ? cli.keepAudio() : fromFile.keepAudio();
        sources.put("keepAudio", source(cli.keepAudio() != null, fileKeys.contains("keepAudio")));

        CaptionMode captions = cli.captions() != null ? cli.captions() : fromFile.captions();
        sources.put("captions", source(cli.captions() != null, fileKeys.contains("captions")));

        WhisperConfig whisper = fromFile.whisper();
        if (cli.whisperModel() != null || cli.whisperLanguage() != null) {
            whisper = new WhisperConfig(
                    whisper.path(),
                    cli.whisperModel() != null ? cli.whisperModel() : whisper.model(),
                    cli.whisperLanguage() != null ? cli.whisperLanguage() : whisper.language(),
                    whisper.device(),
                    whisper.timeoutMinutes());
        }
        sources.put("whisper.model", source(cli.whisperModel() != null, fileKeys.contains("whisper.model")));
        sources.put("whisper.language", source(cli.whisperLanguage() != null, fileKeys.contains("whisper.language")));

        OllamaConfig ollama = fromFile.ollama();
        if (cli.ollamaModel() != null) {
            ollama = new OllamaConfig(ollama.baseUrl(), cli.ollamaModel(), ollama.temperature(),
                    ollama.numCtx(), ollama.timeoutSeconds());
        }
        sources.put("ollama.model", source(cli.ollamaModel() != null, fileKeys.contains("ollama.model")));
        sources.put("ollama.baseUrl", source(false, fileKeys.contains("ollama.baseUrl")));

        SummaryConfig summary = fromFile.summary();
        if (cli.summaryLanguage() != null) {
            summary = new SummaryConfig(cli.summaryLanguage(), summary.chunkTokens(), summary.maxKeyPoints());
        }
        sources.put("summary.language", source(cli.summaryLanguage() != null, fileKeys.contains("summary.language")));

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

    private static Source source(boolean fromFlag, boolean fromFile) {
        if (fromFlag) {
            return Source.FLAG;
        }
        return fromFile ? Source.FILE : Source.DEFAULT;
    }
}
