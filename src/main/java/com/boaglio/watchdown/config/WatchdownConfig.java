package com.boaglio.watchdown.config;

import java.nio.file.Path;

/**
 * The resolved configuration: CLI flags merged over the config file merged over these defaults.
 * It is built once, right after parsing, and is the single source of truth from then on.
 */
public record WatchdownConfig(
        Path outputDir,
        Path cacheDir,
        boolean keepAudio,
        boolean verbose,
        CaptionMode captions,
        YtDlpConfig ytDlp,
        WhisperConfig whisper,
        OllamaConfig ollama,
        SummaryConfig summary) {

    public static WatchdownConfig defaults() {
        return new WatchdownConfig(
                Path.of("./watchdown-out"),
                UserPaths.expand("~/.cache/watchdown"),
                false,
                false,
                CaptionMode.AUTO,
                YtDlpConfig.defaults(),
                WhisperConfig.defaults(),
                OllamaConfig.defaults(),
                SummaryConfig.defaults());
    }
}
