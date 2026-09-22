package com.boaglio.watchdown.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.Fixtures;
import com.boaglio.watchdown.config.CaptionMode;
import com.boaglio.watchdown.config.OllamaConfig;
import com.boaglio.watchdown.config.SummaryConfig;
import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.config.WhisperConfig;
import com.boaglio.watchdown.config.YtDlpConfig;
import com.boaglio.watchdown.cli.ConsoleReporter;
import com.boaglio.watchdown.download.Downloader;
import com.boaglio.watchdown.download.LocalMedia;
import com.boaglio.watchdown.download.MediaProbe;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.download.YouTubeUrl;
import com.boaglio.watchdown.render.MarkdownRenderer;
import com.boaglio.watchdown.summarize.KeyPoint;
import com.boaglio.watchdown.summarize.Section;
import com.boaglio.watchdown.summarize.SummarizationException;
import com.boaglio.watchdown.summarize.Summarizer;
import com.boaglio.watchdown.summarize.Summary;
import com.boaglio.watchdown.process.FakeProcessRunner;
import com.boaglio.watchdown.transcribe.CaptionParser;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import com.boaglio.watchdown.transcribe.Transcriber;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineTest {

    private static final YouTubeUrl URL = YouTubeUrl.parse("https://youtu.be/dQw4w9WgXcQ");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2025-09-18T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path workspace;

    @Test
    void writesTheWholeFolderAndReportsSuccess() {
        FakeDownloader downloader = new FakeDownloader();
        FakeTranscriber transcriber = new FakeTranscriber();

        VideoJob job = pipeline(downloader, transcriber, summarizer(), config(false), false).run(URL);

        assertThat(job.exitCode()).isZero();
        assertThat(job.outputFolder().resolve("AGENTS.md")).exists();
        assertThat(job.outputFolder().resolve("summary.md")).exists();
        assertThat(job.outputFolder().resolve("transcript.md")).exists();
        assertThat(downloader.metadataCalls).isEqualTo(1);
        assertThat(transcriber.transcribeCalls).isEqualTo(1);
    }

    @Test
    void skipsTheSlowStepsOnARerun() {
        FakeDownloader downloader = new FakeDownloader();
        FakeTranscriber transcriber = new FakeTranscriber();

        pipeline(downloader, transcriber, summarizer(), config(false), false).run(URL);
        pipeline(downloader, transcriber, summarizer(), config(false), false).run(URL);

        assertThat(downloader.metadataCalls).isEqualTo(1);
        assertThat(downloader.audioCalls).isEqualTo(1);
        assertThat(transcriber.transcribeCalls).isEqualTo(1);
        assertThat(transcriber.cachedCalls).isEqualTo(1);
    }

    @Test
    void forceRedoesEveryStep() {
        FakeDownloader downloader = new FakeDownloader();
        FakeTranscriber transcriber = new FakeTranscriber();

        pipeline(downloader, transcriber, summarizer(), config(false), false).run(URL);
        pipeline(downloader, transcriber, summarizer(), config(false), true).run(URL);

        assertThat(downloader.metadataCalls).isEqualTo(2);
        assertThat(downloader.audioCalls).isEqualTo(2);
        assertThat(transcriber.transcribeCalls).isEqualTo(2);
    }

    @Test
    void stillWritesTheTranscriptWhenSummarizationFails() throws IOException {
        Summarizer failing = (metadata, transcript, progress) -> {
            throw new SummarizationException("ollama is not running");
        };

        VideoJob job = pipeline(new FakeDownloader(), new FakeTranscriber(), failing, config(false), false).run(URL);

        assertThat(job.exitCode()).isEqualTo(6);
        assertThat(job.outputFolder().resolve("transcript.md")).exists();
        assertThat(job.outputFolder().resolve("summary.md")).doesNotExist();
        assertThat(Files.readString(job.outputFolder().resolve("AGENTS.md")))
                .contains("No summary is available")
                .contains("ollama is not running");
    }

    @Test
    void deletesTheAudioAfterASuccessfulTranscription() {
        VideoJob job = pipeline(new FakeDownloader(), new FakeTranscriber(), summarizer(), config(false), false)
                .run(URL);

        assertThat(workspace.resolve("cache/dQw4w9WgXcQ/audio.mp3")).doesNotExist();
        assertThat(job.outputFolder().resolve("audio.mp3")).doesNotExist();
    }

    @Test
    void keepsTheAudioInTheOutputFolderWhenAsked() {
        VideoJob job = pipeline(new FakeDownloader(), new FakeTranscriber(), summarizer(), config(true), false)
                .run(URL);

        assertThat(job.outputFolder().resolve("audio.mp3")).exists();
    }

    @Test
    void usesTheCaptionsInsteadOfDownloadingAudio() {
        FakeDownloader downloader = new FakeDownloader(List.of("en"));
        FakeTranscriber transcriber = new FakeTranscriber();

        VideoJob job = pipeline(downloader, transcriber, summarizer(), config(false, CaptionMode.AUTO), false)
                .run(URL);

        assertThat(job.exitCode()).isZero();
        assertThat(downloader.captionCalls).isEqualTo(1);
        assertThat(downloader.audioCalls).isZero();
        assertThat(transcriber.transcribeCalls).isZero();
        assertThat(job.outputFolder().resolve("transcript.md")).exists();
    }

    @Test
    void recordsTheCaptionSourceInTheProvenance() throws IOException {
        VideoJob job = pipeline(new FakeDownloader(List.of("en")), new FakeTranscriber(), summarizer(),
                config(false, CaptionMode.AUTO), false).run(URL);

        assertThat(Files.readString(job.outputFolder().resolve("AGENTS.md")))
                .containsPattern("\\| Transcribed +\\| `captions en`");
    }

    @Test
    void reusesACachedCaptionTrack() {
        FakeDownloader downloader = new FakeDownloader(List.of("en"));

        pipeline(downloader, new FakeTranscriber(), summarizer(), config(false, CaptionMode.AUTO), false).run(URL);
        pipeline(downloader, new FakeTranscriber(), summarizer(), config(false, CaptionMode.AUTO), false).run(URL);

        assertThat(downloader.captionCalls).isEqualTo(1);
    }

    @Test
    void fallsBackToWhisperWhenTheVideoHasNoCaptions() {
        FakeDownloader downloader = new FakeDownloader(List.of());
        FakeTranscriber transcriber = new FakeTranscriber();

        pipeline(downloader, transcriber, summarizer(), config(false, CaptionMode.AUTO), false).run(URL);

        assertThat(downloader.captionCalls).isZero();
        assertThat(downloader.audioCalls).isEqualTo(1);
        assertThat(transcriber.transcribeCalls).isEqualTo(1);
    }

    @Test
    void transcribesALocalFileWithoutDownloadingAnything() throws IOException {
        FakeDownloader downloader = new FakeDownloader();
        FakeTranscriber transcriber = new FakeTranscriber();
        LocalMedia recording = LocalMedia.of(recordingFile());

        VideoJob job = pipeline(downloader, transcriber, summarizer(), config(false), false).run(recording);

        assertThat(job.exitCode()).isZero();
        assertThat(job.source()).isEqualTo("standup.m4a");
        assertThat(downloader.metadataCalls).isZero();
        assertThat(downloader.audioCalls).isZero();
        assertThat(transcriber.transcribeCalls).isEqualTo(1);
        assertThat(job.outputFolder().getFileName().toString()).startsWith("standup-");
        assertThat(Files.readString(job.outputFolder().resolve("transcript.md")))
                .doesNotContain("youtube.com");
    }

    @Test
    void neverTouchesTheUsersOwnFile() {
        Path file = recordingFile();
        LocalMedia recording = LocalMedia.of(file);

        pipeline(new FakeDownloader(), new FakeTranscriber(), summarizer(), config(true), false)
                .run(recording);

        assertThat(file).exists();
    }

    @Test
    void takesTheDurationFromTheTranscriptWhenNothingCanProbeIt() throws IOException {
        Pipeline pipeline = new Pipeline(new FakeDownloader(), new FakeTranscriber(),
                new CaptionParser(Fixtures.mapper()),
                new MediaProbe(new FakeProcessRunner().answering(1, "", "ffprobe: not found")),
                summarizer(), new MarkdownRenderer(FIXED), new Cache(config(false).cacheDir(), false),
                config(false), quietReporter(), Fixtures.mapper(), "1.0.0");

        VideoJob job = pipeline.run(LocalMedia.of(recordingFile()));

        assertThat(Files.readString(job.outputFolder().resolve("transcript.md")))
                .contains("duration_seconds: 750");
    }

    @Test
    void printsOneLinePerStepOnStderr() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter reporter = new ConsoleReporter(new PrintStream(out), new PrintStream(err), false);

        pipeline(new FakeDownloader(), new FakeTranscriber(), summarizer(), config(false), false, reporter)
                .run(URL);

        assertThat(err.toString().lines().toList())
                .hasSize(4)
                .satisfies(lines -> {
                    assertThat(lines.get(0)).startsWith("[1/4] Downloading").contains("(12:34)").endsWith("s");
                    assertThat(lines.get(1)).startsWith("[2/4] Transcribing").contains("whisper small");
                    assertThat(lines.get(2)).startsWith("[3/4] Summarizing").contains("1 chunk");
                    assertThat(lines.get(3)).startsWith("[4/4] Writing").contains("dQw4w9WgXcQ");
                });
        assertThat(out.toString()).isEmpty();
    }

    private Pipeline pipeline(Downloader downloader, Transcriber transcriber, Summarizer summarizer,
            WatchdownConfig config, boolean force) {
        return pipeline(downloader, transcriber, summarizer, config, force, quietReporter());
    }

    private static ConsoleReporter quietReporter() {
        return new ConsoleReporter(new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()), false);
    }

    private Path recordingFile() {
        Path file = workspace.resolve("standup.m4a");
        try {
            Files.writeString(file, "audio");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private Pipeline pipeline(Downloader downloader, Transcriber transcriber, Summarizer summarizer,
            WatchdownConfig config, boolean force, ConsoleReporter reporter) {
        return new Pipeline(downloader, transcriber, new CaptionParser(Fixtures.mapper()),
                new MediaProbe(new FakeProcessRunner().answering("180.5\n")), summarizer,
                new MarkdownRenderer(FIXED), new Cache(config.cacheDir(), force), config, reporter,
                Fixtures.mapper(), "1.0.0");
    }

    private WatchdownConfig config(boolean keepAudio) {
        return config(keepAudio, CaptionMode.NEVER);
    }

    private WatchdownConfig config(boolean keepAudio, CaptionMode captions) {
        return new WatchdownConfig(
                workspace.resolve("out"),
                workspace.resolve("cache"),
                keepAudio,
                false,
                captions,
                YtDlpConfig.defaults(),
                WhisperConfig.defaults(),
                OllamaConfig.defaults(),
                SummaryConfig.defaults());
    }

    private static Summarizer summarizer() {
        return (metadata, transcript, progress) -> new Summary("A TL;DR.",
                List.of(new KeyPoint("A point", 10)),
                List.of(new Section("A section", 0, "What happens here.")));
    }

    private static final class FakeDownloader implements Downloader {

        private final List<String> captionLanguages;

        private int metadataCalls;
        private int audioCalls;
        private int captionCalls;

        private FakeDownloader() {
            this(List.of());
        }

        private FakeDownloader(List<String> captionLanguages) {
            this.captionLanguages = captionLanguages;
        }

        @Override
        public VideoMetadata fetchMetadata(YouTubeUrl url) {
            metadataCalls++;
            return new VideoMetadata(url.videoId(), "Building a Local Transcription Pipeline", "Boaglio Labs",
                    LocalDate.of(2025, 9, 17), 754, "", List.of(), url.canonicalUrl(), "en",
                    captionLanguages, List.of());
        }

        @Override
        public Path downloadCaptions(YouTubeUrl url, Path targetDirectory, String language, boolean automatic) {
            captionCalls++;
            Path captions = targetDirectory.resolve("captions." + language + ".json3");
            try {
                Files.createDirectories(targetDirectory);
                Files.writeString(captions, Fixtures.youtubeCaptions());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return captions;
        }

        @Override
        public Path downloadAudio(YouTubeUrl url, Path targetDirectory,
                com.boaglio.watchdown.Progress progress) {
            audioCalls++;
            Path audio = targetDirectory.resolve("audio.mp3");
            try {
                Files.createDirectories(targetDirectory);
                Files.writeString(audio, "audio");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return audio;
        }
    }

    private static final class FakeTranscriber implements Transcriber {

        private int transcribeCalls;
        private int cachedCalls;

        @Override
        public Transcript transcribe(Path audio, Path workDirectory, String language,
                int durationSeconds, com.boaglio.watchdown.Progress progress) {
            transcribeCalls++;
            try {
                Files.writeString(workDirectory.resolve("audio.json"), Fixtures.whisperOutput());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return transcript();
        }

        @Override
        public Transcript readCached(Path cachedOutput) {
            cachedCalls++;
            return transcript();
        }

        private static Transcript transcript() {
            return new Transcript("en", List.of(
                    new Segment(0, 6.5, "Welcome back to the channel."),
                    new Segment(740, 750, "That is the whole tool.")));
        }
    }
}
