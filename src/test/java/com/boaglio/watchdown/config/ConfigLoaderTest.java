package com.boaglio.watchdown.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader(Fixtures.mapper());

    @TempDir
    Path directory;

    @Test
    void usesTheBuiltInDefaultsWhenThereIsNoFile() {
        WatchdownConfig config = loader.load(directory.resolve("missing.json"));

        assertThat(config).isEqualTo(WatchdownConfig.defaults());
        assertThat(config.ollama().model()).isEqualTo("gemma3:4b");
        assertThat(config.whisper().model()).isEqualTo("small");
        assertThat(config.summary().chunkTokens()).isEqualTo(3000);
    }

    @Test
    void mergesAPartialFileOverTheDefaults() throws IOException {
        Path file = write("""
                {
                  "outputDir": "/tmp/out",
                  "ollama": { "model": "llama3.2" }
                }""");

        WatchdownConfig config = loader.load(file);

        assertThat(config.outputDir()).isEqualTo(Path.of("/tmp/out"));
        assertThat(config.ollama().model()).isEqualTo("llama3.2");
        assertThat(config.ollama().baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(config.ollama().temperature()).isEqualTo(0.2);
        assertThat(config.whisper()).isEqualTo(WhisperConfig.defaults());
    }

    @Test
    void readsEverySectionOfAFullFile() throws IOException {
        Path file = write(Fixtures.read("/default-config.json"));

        assertThat(loader.load(file)).isEqualTo(WatchdownConfig.defaults());
    }

    @Test
    void expandsTildeAndHomeInPaths() throws IOException {
        Path file = write("""
                {
                  "outputDir": "~/videos",
                  "cacheDir": "$HOME/cache/watchdown"
                }""");

        WatchdownConfig config = loader.load(file);

        String home = System.getProperty("user.home");
        assertThat(config.outputDir()).isEqualTo(Path.of(home, "videos"));
        assertThat(config.cacheDir()).isEqualTo(Path.of(home, "cache", "watchdown"));
    }

    @Test
    void ignoresAnUnknownKeyInsteadOfBreakingTheRun() throws IOException {
        Path file = write("""
                {
                  "outputDirr": "./typo",
                  "ollama": { "modell": "llama3.2" }
                }""");

        assertThat(loader.load(file)).isEqualTo(WatchdownConfig.defaults());
    }

    @Test
    void rejectsAValueOfTheWrongType() throws IOException {
        Path file = write("""
                { "ollama": { "temperature": "warm" } }""");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("ollama.temperature: expected number");
    }

    @Test
    void rejectsASectionThatIsNotAnObject() throws IOException {
        Path file = write("""
                { "whisper": "small" }""");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("whisper: expected object");
    }

    @Test
    void rejectsInvalidJson() throws IOException {
        Path file = write("{ not json");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("invalid JSON");
    }

    @Test
    void acceptsAnIntegerTemperature() throws IOException {
        Path file = write("""
                { "ollama": { "temperature": 1 } }""");

        assertThat(loader.load(file).ollama().temperature()).isEqualTo(1.0);
    }

    @Test
    void readsExtraArgsAsAListOfStrings() throws IOException {
        Path file = write("""
                { "ytDlp": { "extraArgs": ["--cookies-from-browser", "firefox"] } }""");

        assertThat(loader.load(file).ytDlp().extraArgs())
                .containsExactly("--cookies-from-browser", "firefox");
    }

    @Test
    void rejectsExtraArgsThatAreNotStrings() throws IOException {
        Path file = write("""
                { "ytDlp": { "extraArgs": [1, 2] } }""");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("ytDlp.extraArgs: expected array of strings");
    }

    @Test
    void listsTheKeysTheFileActuallySet() throws IOException {
        Path file = write("""
                { "outputDir": "./out", "ollama": { "model": "llama3.2" } }""");

        assertThat(loader.keysPresentIn(file))
                .contains("outputDir", "ollama", "ollama.model")
                .doesNotContain("whisper.model");
    }

    private Path write(String json) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, json);
        return file;
    }
}
