package com.boaglio.watchdown.config;

import java.nio.file.Path;

/**
 * The configuration values the user gave on the command line. Every field is nullable: a null
 * means "the user did not say", which is what lets {@link ConfigResolver} apply the precedence
 * rule flag &gt; file &gt; default.
 */
public record CliOptions(
        Path outputDir,
        String ollamaModel,
        String whisperModel,
        String whisperLanguage,
        String summaryLanguage,
        Boolean verbose,
        Boolean keepAudio) {

    public static CliOptions none() {
        return new CliOptions(null, null, null, null, null, null, null);
    }
}
