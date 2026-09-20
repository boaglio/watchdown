package com.boaglio.watchdown.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.cli.ConsoleReporter;
import com.boaglio.watchdown.config.CaptionMode;
import com.boaglio.watchdown.config.OllamaConfig;
import com.boaglio.watchdown.config.SummaryConfig;
import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.config.WhisperConfig;
import com.boaglio.watchdown.config.YtDlpConfig;
import com.boaglio.watchdown.download.YouTubeUrl;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The full pipeline against a real short video, with the {@code tiny} Whisper model.
 *
 * <p>It needs yt-dlp, whisper and a running Ollama, so it only runs when {@code WATCHDOWN_IT_URL}
 * points at a short video: {@code ./mvnw verify -Pintegration}.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "WATCHDOWN_IT_URL", matches = ".+")
class WatchdownPipelineIT {

    @Autowired
    private PipelineFactory factory;

    @TempDir
    Path workspace;

    @Test
    void turnsARealVideoIntoAFolderOfMarkdown() {
        YouTubeUrl url = YouTubeUrl.parse(System.getenv("WATCHDOWN_IT_URL"));
        WatchdownConfig config = new WatchdownConfig(
                workspace.resolve("out"),
                workspace.resolve("cache"),
                false,
                true,
                CaptionMode.NEVER,
                YtDlpConfig.defaults(),
                new WhisperConfig("whisper", "tiny", "auto", "cpu", 120),
                OllamaConfig.defaults(),
                SummaryConfig.defaults());
        ConsoleReporter reporter = new ConsoleReporter(System.out, System.err, true);

        factory.doctor(config).require(Doctor.Needs.of(true, false, false));
        VideoJob job = factory.create(config, reporter, true).run(url);

        assertThat(job.exitCode()).isZero();
        assertThat(job.outputFolder().resolve("AGENTS.md")).isNotEmptyFile();
        assertThat(job.outputFolder().resolve("summary.md")).isNotEmptyFile();
        assertThat(job.outputFolder().resolve("transcript.md")).isNotEmptyFile();
    }
}
