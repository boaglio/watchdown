package com.boaglio.watchdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.Fixtures;
import com.boaglio.watchdown.config.WhisperConfig;
import com.boaglio.watchdown.config.YtDlpConfig;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.download.YouTubeUrl;
import com.boaglio.watchdown.download.YtDlpDownloader;
import com.boaglio.watchdown.process.FakeProcessRunner;
import com.boaglio.watchdown.summarize.KeyPoint;
import com.boaglio.watchdown.summarize.Section;
import com.boaglio.watchdown.summarize.Summary;
import com.boaglio.watchdown.transcribe.Transcript;
import com.boaglio.watchdown.transcribe.WhisperTranscriber;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-file tests. The output is deterministic apart from {@code generated_at}, which the
 * injected clock pins. Run {@code ./mvnw test -Dwatchdown.updateGolden=true} to rewrite the files
 * under {@code src/test/resources/golden}, then review the diff.
 */
class MarkdownRendererGoldenTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2025-09-18T12:00:00Z"), ZoneOffset.UTC);
    private static final Path GOLDEN_SOURCES = Path.of("src", "test", "resources", "golden");

    @TempDir
    Path output;

    @Test
    void writesTheAgentsFile() throws IOException {
        assertMatchesGolden("AGENTS.md", new MarkdownRenderer(FIXED).agents(request(summary())));
    }

    @Test
    void writesTheSummaryFile() throws IOException {
        assertMatchesGolden("summary.md", new MarkdownRenderer(FIXED).summary(request(summary())));
    }

    @Test
    void writesTheTranscriptFile() throws IOException {
        assertMatchesGolden("transcript.md", new MarkdownRenderer(FIXED).transcript(request(summary())));
    }

    @Test
    void writesTheWholeFolderAndOverwritesIt() {
        MarkdownRenderer renderer = new MarkdownRenderer(FIXED);

        Path folder = renderer.render(request(summary()), output);
        Path again = renderer.render(request(summary()), output);

        assertThat(folder).isEqualTo(again);
        assertThat(folder.getFileName().toString())
                .isEqualTo("building-a-local-transcription-pipeline-dQw4w9WgXcQ");
        assertThat(folder.resolve("AGENTS.md")).exists();
        assertThat(folder.resolve("summary.md")).exists();
        assertThat(folder.resolve("transcript.md")).exists();
        assertThat(folder.resolve("audio.mp3")).doesNotExist();
    }

    @Test
    void producesTheSameBytesEveryTime() {
        MarkdownRenderer renderer = new MarkdownRenderer(FIXED);

        assertThat(renderer.agents(request(summary()))).isEqualTo(renderer.agents(request(summary())));
    }

    @Test
    void saysWhyTheSummaryIsMissingWhenSummarizationFailed() {
        MarkdownRenderer renderer = new MarkdownRenderer(FIXED);

        Path folder = renderer.render(request(null), output);
        String agents = renderer.agents(request(null));

        assertThat(folder.resolve("transcript.md")).exists();
        assertThat(folder.resolve("summary.md")).doesNotExist();
        assertThat(agents)
                .contains("No summary is available")
                .contains("the model was not reachable")
                .contains("| Summarized    | failed");
    }

    @Test
    void escapesMarkdownThatCameFromTheVideo() {
        VideoMetadata metadata = new VideoMetadata("dQw4w9WgXcQ", "Why *stars* and _underscores_ break",
                "Labs [sic]", null, 60, "", List.of(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        RenderRequest request = new RenderRequest(metadata, transcript(), summary(), null,
                "small", "gemma3:4b", "1.0.0");

        String agents = new MarkdownRenderer(FIXED).agents(request);

        assertThat(agents).contains("Why \\*stars\\* and \\_underscores\\_ break");
        assertThat(agents).contains("Labs \\[sic\\]");
    }

    @Test
    void endsEveryFileWithExactlyOneNewline() {
        MarkdownRenderer renderer = new MarkdownRenderer(FIXED);

        assertThat(renderer.transcript(request(summary()))).endsWith("watching.\n");
        assertThat(renderer.summary(request(summary()))).endsWith("local model.\n");
    }

    private void assertMatchesGolden(String name, String actual) throws IOException {
        Path golden = GOLDEN_SOURCES.resolve(name);
        if (Boolean.getBoolean("watchdown.updateGolden")) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, actual);
        }
        assertThat(actual)
                .as("%s differs from the golden file; rerun with -Dwatchdown.updateGolden=true to accept", name)
                .isEqualTo(Fixtures.read("/golden/" + name));
    }

    private static RenderRequest request(Summary summary) {
        return new RenderRequest(metadata(), transcript(), summary,
                summary == null ? "the model was not reachable at http://localhost:11434" : null,
                "small", "gemma3:4b", "1.0.0");
    }

    private static VideoMetadata metadata() {
        return new YtDlpDownloader(new FakeProcessRunner(), Fixtures.mapper(), YtDlpConfig.defaults())
                .parseMetadata(Fixtures.ytDlpMetadata(), YouTubeUrl.parse("https://youtu.be/dQw4w9WgXcQ"));
    }

    private static Transcript transcript() {
        return new WhisperTranscriber(new FakeProcessRunner(), Fixtures.mapper(), WhisperConfig.defaults())
                .parse(Fixtures.whisperOutput());
    }

    private static Summary summary() {
        return new Summary(
                "Building a local transcription pipeline",
                "The video walks through a transcription pipeline that runs entirely on your own "
                        + "machine. It combines yt-dlp, whisper and a local model, and caches every step.",
                List.of(
                        new KeyPoint("Nothing leaves your laptop", 14),
                        new KeyPoint("A cache makes reruns cheap", 250)),
                List.of(
                        new Section("Why local", 0, "The reasons to keep the whole pipeline off the cloud."),
                        new Section("The pipeline", 240,
                                "yt-dlp fetches the audio, whisper transcribes it, and the summary "
                                        + "comes from a local model.")));
    }
}
