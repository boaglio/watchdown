package com.boaglio.watchdown.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.Fixtures;
import com.boaglio.watchdown.config.YtDlpConfig;
import com.boaglio.watchdown.process.FakeProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpDownloaderTest {

    private static final YouTubeUrl URL = YouTubeUrl.parse("https://youtu.be/dQw4w9WgXcQ");

    @TempDir
    Path directory;

    @Test
    void mapsTheRecordedMetadataIntoTheRecord() {
        FakeProcessRunner runner = new FakeProcessRunner().answering(Fixtures.ytDlpMetadata());

        VideoMetadata metadata = downloader(runner).fetchMetadata(URL);

        assertThat(metadata.id()).isEqualTo("dQw4w9WgXcQ");
        assertThat(metadata.title()).isEqualTo("Building a Local Transcription Pipeline");
        assertThat(metadata.channel()).isEqualTo("Boaglio Labs");
        assertThat(metadata.uploadDate()).isEqualTo(LocalDate.of(2025, 9, 17));
        assertThat(metadata.durationSeconds()).isEqualTo(754);
        assertThat(metadata.webpageUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(metadata.chapters()).extracting(Chapter::title).containsExactly("Why local", "The pipeline");
    }

    @Test
    void asksYtDlpForASingleVideo() {
        FakeProcessRunner runner = new FakeProcessRunner().answering(Fixtures.ytDlpMetadata());

        downloader(runner).fetchMetadata(URL);

        assertThat(runner.lastCommand())
                .containsExactly("yt-dlp", "--dump-single-json", "--no-playlist",
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void passesTheConfiguredExtraArguments() {
        FakeProcessRunner runner = new FakeProcessRunner().answering(Fixtures.ytDlpMetadata());
        YtDlpConfig config = new YtDlpConfig("yt-dlp", List.of("--cookies-from-browser", "firefox"), 15);

        new YtDlpDownloader(runner, Fixtures.mapper(), config).fetchMetadata(URL);

        assertThat(runner.lastCommand()).containsSubsequence("--cookies-from-browser", "firefox");
    }

    @Test
    void reportsWhatYtDlpPrintedWhenItFails() {
        FakeProcessRunner runner = new FakeProcessRunner()
                .answering(1, "", "ERROR: Video unavailable");

        assertThatThrownBy(() -> downloader(runner).fetchMetadata(URL))
                .isInstanceOf(DownloadException.class)
                .hasMessageContaining("exit 1")
                .hasMessageContaining("Video unavailable");
    }

    @Test
    void rejectsAPlaylistThatSlippedThrough() {
        FakeProcessRunner runner = new FakeProcessRunner().answering("{\"entries\": []}");

        assertThatThrownBy(() -> downloader(runner).fetchMetadata(URL))
                .isInstanceOf(DownloadException.class)
                .hasMessageContaining("playlist");
    }

    @Test
    void writesTheAudioWhereTheCacheExpectsIt() throws IOException {
        Path target = directory.resolve("dQw4w9WgXcQ");
        FakeProcessRunner runner = new FakeProcessRunner()
                .doingOnRun(() -> createFile(target.resolve("audio.mp3")));

        Path audio = downloader(runner).downloadAudio(URL, target);

        assertThat(audio).isEqualTo(target.resolve("audio.mp3"));
        assertThat(Files.exists(audio)).isTrue();
        assertThat(runner.lastCommand()).containsSubsequence("-x", "--audio-format", "mp3");
        assertThat(runner.lastCommand()).contains(target.resolve("audio.%(ext)s").toString());
    }

    @Test
    void failsWhenYtDlpLeavesNoAudioBehind() {
        FakeProcessRunner runner = new FakeProcessRunner();

        assertThatThrownBy(() -> downloader(runner).downloadAudio(URL, directory.resolve("empty")))
                .isInstanceOf(DownloadException.class)
                .hasMessageContaining("is missing");
    }

    private YtDlpDownloader downloader(FakeProcessRunner runner) {
        return new YtDlpDownloader(runner, Fixtures.mapper(), YtDlpConfig.defaults());
    }

    private static void createFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "audio");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
