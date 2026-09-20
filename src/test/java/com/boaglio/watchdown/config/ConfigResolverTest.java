package com.boaglio.watchdown.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigResolverTest {

    private final ConfigResolver resolver = new ConfigResolver();

    @Test
    void aFlagWinsOverTheFile() {
        WatchdownConfig fromFile = withOllamaModel("llama3.2");

        ConfigResolver.Resolution resolution = resolver.resolve(
                new CliOptions(null, "qwen3:4b", null, null, null, null, null),
                fromFile,
                Set.of("ollama.model"));

        assertThat(resolution.config().ollama().model()).isEqualTo("qwen3:4b");
        assertThat(resolution.sources()).containsEntry("ollama.model", ConfigResolver.Source.FLAG);
    }

    @Test
    void theFileWinsOverTheDefault() {
        ConfigResolver.Resolution resolution = resolver.resolve(
                CliOptions.none(), withOllamaModel("llama3.2"), Set.of("ollama.model"));

        assertThat(resolution.config().ollama().model()).isEqualTo("llama3.2");
        assertThat(resolution.sources()).containsEntry("ollama.model", ConfigResolver.Source.FILE);
    }

    @Test
    void theDefaultIsUsedWhenNobodySaysAnything() {
        ConfigResolver.Resolution resolution = resolver.resolve(
                CliOptions.none(), WatchdownConfig.defaults(), Set.of());

        assertThat(resolution.config()).isEqualTo(WatchdownConfig.defaults());
        assertThat(resolution.sources().values()).containsOnly(ConfigResolver.Source.DEFAULT);
    }

    @Test
    void resolvesEveryFlagTheCliOffers() {
        ConfigResolver.Resolution resolution = resolver.resolve(
                new CliOptions(Path.of("/tmp/out"), "qwen3:4b", "tiny", "pt", "en", true, true),
                WatchdownConfig.defaults(),
                Set.of());

        WatchdownConfig config = resolution.config();
        assertThat(config.outputDir()).isEqualTo(Path.of("/tmp/out"));
        assertThat(config.ollama().model()).isEqualTo("qwen3:4b");
        assertThat(config.whisper().model()).isEqualTo("tiny");
        assertThat(config.whisper().language()).isEqualTo("pt");
        assertThat(config.summary().language()).isEqualTo("en");
        assertThat(config.verbose()).isTrue();
        assertThat(config.keepAudio()).isTrue();
    }

    @Test
    void leavesUntouchedPartsOfASectionAlone() {
        ConfigResolver.Resolution resolution = resolver.resolve(
                new CliOptions(null, null, "tiny", null, null, null, null),
                WatchdownConfig.defaults(),
                Set.of());

        assertThat(resolution.config().whisper().language()).isEqualTo("auto");
        assertThat(resolution.config().whisper().device()).isEqualTo("cpu");
        assertThat(resolution.config().whisper().timeoutMinutes()).isEqualTo(120);
    }

    private static WatchdownConfig withOllamaModel(String model) {
        WatchdownConfig defaults = WatchdownConfig.defaults();
        OllamaConfig ollama = defaults.ollama();
        return new WatchdownConfig(defaults.outputDir(), defaults.cacheDir(), defaults.keepAudio(),
                defaults.verbose(), defaults.ytDlp(), defaults.whisper(),
                new OllamaConfig(ollama.baseUrl(), model, ollama.temperature(), ollama.numCtx(),
                        ollama.timeoutSeconds()),
                defaults.summary());
    }
}
