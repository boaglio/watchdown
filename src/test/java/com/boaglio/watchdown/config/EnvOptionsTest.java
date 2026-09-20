package com.boaglio.watchdown.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvOptionsTest {

    @Test
    void readsTheOutputRootFromTheEnvironment() {
        EnvOptions options = EnvOptions.from(Map.of("WATCHDOWN_ROOT", "/srv/watchdown"));

        assertThat(options.outputDir()).isEqualTo(Path.of("/srv/watchdown"));
    }

    @Test
    void expandsTildeAndHomeJustLikeTheConfigFile() {
        String home = System.getProperty("user.home");

        assertThat(EnvOptions.from(Map.of("WATCHDOWN_ROOT", "~/videos")).outputDir())
                .isEqualTo(Path.of(home, "videos"));
        assertThat(EnvOptions.from(Map.of("WATCHDOWN_ROOT", "$HOME/videos")).outputDir())
                .isEqualTo(Path.of(home, "videos"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(EnvOptions.from(Map.of("WATCHDOWN_ROOT", "  /srv/out  ")).outputDir())
                .isEqualTo(Path.of("/srv/out"));
    }

    @Test
    void treatsAnUnsetOrEmptyValueAsNotSaidAtAll() {
        assertThat(EnvOptions.from(Map.of()).outputDir()).isNull();
        assertThat(EnvOptions.from(Map.of("WATCHDOWN_ROOT", "")).outputDir()).isNull();
        assertThat(EnvOptions.from(Map.of("WATCHDOWN_ROOT", "   ")).outputDir()).isNull();
        assertThat(EnvOptions.none().outputDir()).isNull();
    }
}
