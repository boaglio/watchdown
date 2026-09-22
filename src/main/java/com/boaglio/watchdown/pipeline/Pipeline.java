package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.cli.ConsoleReporter;
import com.boaglio.watchdown.cli.ExitCode;
import com.boaglio.watchdown.config.WatchdownConfig;
import com.boaglio.watchdown.download.Downloader;
import com.boaglio.watchdown.download.LocalMedia;
import com.boaglio.watchdown.download.MediaProbe;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.download.YouTubeUrl;
import com.boaglio.watchdown.render.MarkdownRenderer;
import com.boaglio.watchdown.render.RenderRequest;
import com.boaglio.watchdown.render.Timestamps;
import com.boaglio.watchdown.summarize.Chunker;
import com.boaglio.watchdown.summarize.Summarizer;
import com.boaglio.watchdown.summarize.Summary;
import com.boaglio.watchdown.transcribe.CaptionParser;
import com.boaglio.watchdown.transcribe.Transcript;
import com.boaglio.watchdown.transcribe.Transcriber;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * The four steps of AGENTS.md section 6, run for one URL at a time.
 *
 * <p>The transcript comes either from YouTube's captions or from Whisper; {@link CaptionPolicy}
 * decides which before anything is downloaded, so a video with captions never downloads audio.
 *
 * <p>When summarization fails the transcript is still written, along with an AGENTS.md that says
 * the summary is missing and why; the job then reports exit code 6.
 */
public class Pipeline {

    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);
    private static final int STEPS = 4;

    private final Downloader downloader;
    private final Transcriber transcriber;
    private final CaptionParser captionParser;
    private final MediaProbe probe;
    private final Summarizer summarizer;
    private final MarkdownRenderer renderer;
    private final Cache cache;
    private final WatchdownConfig config;
    private final ConsoleReporter reporter;
    private final JsonMapper mapper;
    private final String version;

    public Pipeline(Downloader downloader, Transcriber transcriber, CaptionParser captionParser,
            MediaProbe probe, Summarizer summarizer, MarkdownRenderer renderer, Cache cache,
            WatchdownConfig config, ConsoleReporter reporter, JsonMapper mapper, String version) {
        this.downloader = downloader;
        this.transcriber = transcriber;
        this.captionParser = captionParser;
        this.probe = probe;
        this.summarizer = summarizer;
        this.renderer = renderer;
        this.cache = cache;
        this.config = config;
        this.reporter = reporter;
        this.mapper = mapper;
        this.version = version;
    }

    public VideoJob run(YouTubeUrl url) {
        reporter.stepStart(1, STEPS, "Downloading");
        VideoMetadata metadata = metadata(url);

        TranscriptSource source = new CaptionPolicy(config.captions(), config.whisper().model())
                .choose(metadata, config.whisper().language());
        log.debug("transcript source: {}", source.display());

        Path material = materialFor(url, source);
        reporter.stepDone();

        Transcript transcript = transcribe(url.videoId(), source, material, metadata.durationSeconds());
        return summarizeAndWrite(url.canonicalUrl(), metadata, transcript, source);
    }

    /**
     * The same four steps for a file that is already on disk. Nothing is downloaded, captions do
     * not apply, and the user's file is never moved, copied or deleted.
     */
    public VideoJob run(LocalMedia media) {
        reporter.stepStart(1, STEPS, "Reading");
        reporter.doing("measuring the file");
        int duration = probe.durationOf(media.path());
        VideoMetadata metadata = media.asMetadata(duration);
        reporter.detail("\"%s\" (%s)".formatted(metadata.title(),
                duration > 0 ? Timestamps.duration(duration) : "unknown length"));
        reporter.stepDone();

        TranscriptSource source = new TranscriptSource(TranscriptSource.Kind.WHISPER,
                config.whisper().language(),
                "whisper " + config.whisper().model() + ", lang=" + config.whisper().language());
        Transcript transcript = transcribe(media.id(), source, media.path(), duration);

        if (duration <= 0) {
            // No ffprobe: the transcript is the only thing that knows how long the recording is.
            metadata = media.asMetadata((int) Math.ceil(transcript.endsAt()));
        }
        return summarizeAndWrite(media.display(), metadata, transcript, source);
    }

    private VideoJob summarizeAndWrite(String jobSource, VideoMetadata metadata, Transcript transcript,
            TranscriptSource source) {
        Summary summary = null;
        String failure = null;

        int chunks = new Chunker(config.summary().chunkTokens()).split(transcript, metadata.chapters()).size();
        reporter.stepStart(3, STEPS, "Summarizing", "%s, %d chunk%s"
                .formatted(config.ollama().model(), chunks, chunks == 1 ? "" : "s"));
        try {
            summary = summarizer.summarize(metadata, transcript, reporter.progress());
            reporter.stepDone();
        } catch (WatchdownException e) {
            failure = e.getMessage();
            reporter.stepFailed(e.getMessage());
            log.debug("summarization failed", e);
        }

        Path folder = write(metadata, transcript, source, summary, failure);
        return summary == null
                ? VideoJob.failed(jobSource, folder, ExitCode.SUMMARIZATION_FAILED, failure)
                : VideoJob.succeeded(jobSource, folder);
    }

    private VideoMetadata metadata(YouTubeUrl url) {
        Path metadataFile = cache.metadataFile(url.videoId());
        VideoMetadata metadata;
        if (cache.isHit(metadataFile)) {
            log.debug("cache hit: {}", metadataFile);
            metadata = readMetadata(metadataFile);
        } else {
            log.debug("cache miss: {}", metadataFile);
            // The first thing every run does, and the one that most often takes a while.
            reporter.doing("asking yt-dlp about the video");
            metadata = downloader.fetchMetadata(url);
            writeMetadata(metadataFile, metadata);
        }
        reporter.detail("\"%s\" (%s)".formatted(metadata.title(),
                Timestamps.duration(metadata.durationSeconds())));
        return metadata;
    }

    /**
     * Fetches whatever the chosen source needs: a caption track, or the audio. Nothing is fetched
     * when the transcript is already cached, unless the user asked to keep the audio.
     */
    private Path materialFor(YouTubeUrl url, TranscriptSource source) {
        return source.fromCaptions() ? captionsIfNeeded(url, source) : audioIfNeeded(url);
    }

    private Path captionsIfNeeded(YouTubeUrl url, TranscriptSource source) {
        Path captions = cache.captionFile(url.videoId(), source.language());
        if (cache.isHit(captions)) {
            log.debug("cache hit: {}", captions);
            return captions;
        }
        log.debug("cache miss: {}", captions);
        reporter.doing("captions");
        return downloader.downloadCaptions(url, cache.directoryFor(url.videoId()),
                source.language(), source.automatic());
    }

    private Path audioIfNeeded(YouTubeUrl url) {
        Path audio = cache.audioFile(url.videoId());
        if (cache.isHit(audio)) {
            log.debug("cache hit: {}", audio);
            return audio;
        }
        if (cache.isHit(cache.transcriptFile(url.videoId())) && !config.keepAudio()) {
            log.debug("skipping the audio download, the transcript is already cached");
            return null;
        }
        log.debug("cache miss: {}", audio);
        reporter.doing("audio");
        return downloader.downloadAudio(url, cache.directoryFor(url.videoId()), reporter.progress());
    }

    private Transcript transcribe(String id, TranscriptSource source, Path material, int durationSeconds) {
        if (source.fromCaptions()) {
            reporter.stepStart(2, STEPS, "Transcribing", source.display());
            Transcript transcript = captionParser.read(material, source.language());
            reporter.stepDone();
            return logged(transcript);
        }

        Path transcriptFile = cache.transcriptFile(id);
        if (cache.isHit(transcriptFile)) {
            log.debug("cache hit: {}", transcriptFile);
            reporter.stepStart(2, STEPS, "Transcribing", "cached transcript");
            Transcript cached = transcriber.readCached(transcriptFile);
            reporter.stepDone();
            return logged(cached);
        }

        log.debug("cache miss: {}", transcriptFile);
        reporter.stepStart(2, STEPS, "Transcribing", source.display());
        Transcript transcript = transcriber.transcribe(material, cache.directoryFor(id),
                config.whisper().language(), durationSeconds, reporter.progress());
        reporter.stepDone();
        return logged(transcript);
    }

    private static Transcript logged(Transcript transcript) {
        log.debug("transcript: {} segments, {} words, language={}",
                transcript.segments().size(), transcript.wordCount(), transcript.language());
        return transcript;
    }

    private Path write(VideoMetadata metadata, Transcript transcript, TranscriptSource source,
            Summary summary, String failure) {
        RenderRequest request = new RenderRequest(metadata, transcript, summary, failure,
                source.display(), config.ollama().model(), version);
        Path folder = renderer.render(request, config.outputDir());
        handleAudio(metadata.id(), folder);
        reporter.finalStep(4, STEPS, "Writing", folder);
        return folder;
    }

    /**
     * The downloaded audio is deleted after a successful transcription unless the user asked to
     * keep it. This only ever touches the cache, so a {@code --file} input is left alone.
     */
    private void handleAudio(String videoId, Path folder) {
        Path audio = cache.audioFile(videoId);
        if (!Files.isRegularFile(audio)) {
            return;
        }
        try {
            if (config.keepAudio()) {
                Files.copy(audio, folder.resolve("audio.mp3"), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.delete(audio);
                log.debug("deleted the cached audio {}", audio);
            }
        } catch (IOException e) {
            log.warn("cannot handle the audio file {}: {}", audio, e.getMessage());
        }
    }

    private VideoMetadata readMetadata(Path file) {
        try {
            return mapper.readValue(Files.readString(file), VideoMetadata.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private void writeMetadata(Path file, VideoMetadata metadata) {
        try {
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
        } catch (IOException e) {
            log.warn("cannot cache the metadata in {}: {}", file, e.getMessage());
        }
    }
}
