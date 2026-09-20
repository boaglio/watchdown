package com.boaglio.watchdown.download;

import com.boaglio.watchdown.Progress;
import com.boaglio.watchdown.config.YtDlpConfig;
import com.boaglio.watchdown.process.ProcessException;
import com.boaglio.watchdown.process.ProcessResult;
import com.boaglio.watchdown.process.ProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link Downloader} backed by the {@code yt-dlp} command line tool. */
public class YtDlpDownloader implements Downloader {

    private static final Logger log = LoggerFactory.getLogger(YtDlpDownloader.class);
    private static final DateTimeFormatter UPLOAD_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String AUDIO_FORMAT = "mp3";
    private static final String CAPTION_FORMAT = "json3";
    private static final Pattern DOWNLOAD_PERCENT = Pattern.compile(
            "\\[download]\\s+(\\d+(?:\\.\\d+)?)%");

    private final ProcessRunner runner;
    private final JsonMapper mapper;
    private final YtDlpConfig config;

    public YtDlpDownloader(ProcessRunner runner, JsonMapper mapper, YtDlpConfig config) {
        this.runner = runner;
        this.mapper = mapper;
        this.config = config;
    }

    @Override
    public VideoMetadata fetchMetadata(YouTubeUrl url) {
        List<String> command = new ArrayList<>(List.of(
                config.path(), "--dump-single-json", "--no-playlist"));
        command.addAll(config.extraArgs());
        command.add(url.canonicalUrl());

        ProcessResult result = run(command);
        if (!result.successful()) {
            throw new DownloadException("yt-dlp could not read the video metadata (exit "
                    + result.exitCode() + "):\n" + result.tailOfStderr(20));
        }
        return parseMetadata(result.stdout(), url);
    }

    /** Parses one {@code --dump-single-json} document. Package-private so tests can use fixtures. */
    public VideoMetadata parseMetadata(String json, YouTubeUrl url) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new DownloadException("yt-dlp returned metadata that is not JSON: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new DownloadException("yt-dlp returned no metadata for " + url.canonicalUrl());
        }
        if (root.has("entries")) {
            throw new DownloadException("this URL points at a playlist, pass a single video URL");
        }

        String id = text(root, "id", url.videoId());
        String title = text(root, "title", "Untitled");
        String channel = text(root, "channel", text(root, "uploader", "Unknown channel"));
        String description = text(root, "description", "");
        String webpageUrl = text(root, "webpage_url", url.canonicalUrl());
        int duration = root.path("duration").isNumber() ? (int) Math.round(root.path("duration").asDouble()) : 0;

        LocalDate uploadDate = null;
        String rawDate = text(root, "upload_date", "");
        if (!rawDate.isBlank()) {
            try {
                uploadDate = LocalDate.parse(rawDate, UPLOAD_DATE);
            } catch (DateTimeParseException e) {
                log.debug("unreadable upload_date '{}', leaving it out", rawDate);
            }
        }

        List<Chapter> chapters = new ArrayList<>();
        JsonNode chapterNodes = root.path("chapters");
        if (chapterNodes.isArray()) {
            for (JsonNode chapter : chapterNodes) {
                chapters.add(new Chapter(
                        text(chapter, "title", "Chapter"),
                        chapter.path("start_time").asDouble(0),
                        chapter.path("end_time").asDouble(duration)));
            }
        }

        return new VideoMetadata(id, title, channel, uploadDate, duration, description, chapters,
                webpageUrl, text(root, "language", null),
                languagesOf(root.path("subtitles")),
                languagesOf(root.path("automatic_captions")));
    }

    /** The caption languages yt-dlp listed, keeping only the ones it can hand us as json3. */
    private static List<String> languagesOf(JsonNode tracks) {
        if (!tracks.isObject()) {
            return List.of();
        }
        List<String> languages = new ArrayList<>();
        for (String language : tracks.propertyNames()) {
            JsonNode formats = tracks.path(language);
            for (JsonNode format : formats) {
                if (CAPTION_FORMAT.equals(format.path("ext").asString(""))) {
                    languages.add(language);
                    break;
                }
            }
        }
        return List.copyOf(languages);
    }

    @Override
    public Path downloadCaptions(YouTubeUrl url, Path targetDirectory, String language, boolean automatic) {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new DownloadException("cannot create " + targetDirectory + ": " + e.getMessage(), e);
        }

        List<String> command = new ArrayList<>(List.of(
                config.path(),
                "--no-playlist",
                "--skip-download",
                automatic ? "--write-auto-subs" : "--write-subs",
                "--sub-langs", language,
                "--sub-format", CAPTION_FORMAT,
                "-o", targetDirectory.resolve("captions.%(ext)s").toString()));
        command.addAll(config.extraArgs());
        command.add(url.canonicalUrl());

        ProcessResult result = run(command);
        if (!result.successful()) {
            throw new DownloadException("yt-dlp could not download the captions (exit "
                    + result.exitCode() + "):\n" + result.tailOfStderr(20));
        }

        Path captions = captionFile(targetDirectory, language);
        if (!Files.exists(captions)) {
            throw new DownloadException("yt-dlp finished but " + captions + " is missing");
        }
        return captions;
    }

    /** Where yt-dlp leaves the caption track for a language, given our output template. */
    public static Path captionFile(Path directory, String language) {
        return directory.resolve("captions." + language + "." + CAPTION_FORMAT);
    }

    @Override
    public Path downloadAudio(YouTubeUrl url, Path targetDirectory, Progress progress) {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new DownloadException("cannot create " + targetDirectory + ": " + e.getMessage(), e);
        }

        List<String> command = new ArrayList<>(List.of(
                config.path(),
                "--no-playlist",
                "-f", "bestaudio",
                "-x", "--audio-format", AUDIO_FORMAT,
                "-o", targetDirectory.resolve("audio.%(ext)s").toString()));
        command.addAll(config.extraArgs());
        command.add(url.canonicalUrl());

        ProcessResult result = run(command, line -> reportProgress(line, progress));
        if (!result.successful()) {
            throw new DownloadException("yt-dlp could not download the audio (exit "
                    + result.exitCode() + "):\n" + result.tailOfStderr(20));
        }

        Path audio = targetDirectory.resolve("audio." + AUDIO_FORMAT);
        if (!Files.exists(audio)) {
            throw new DownloadException("yt-dlp finished but " + audio + " is missing");
        }
        return audio;
    }

    private ProcessResult run(List<String> command) {
        return run(command, line -> { });
    }

    private ProcessResult run(List<String> command, java.util.function.Consumer<String> onLine) {
        try {
            return runner.run(command, config.timeout(), onLine);
        } catch (ProcessException e) {
            throw new DownloadException(e.getMessage(), e);
        }
    }

    /** yt-dlp rewrites a line like {@code [download]  45.2% of 3.40MiB at 1.2MiB/s}. */
    static void reportProgress(String line, Progress progress) {
        Matcher percent = DOWNLOAD_PERCENT.matcher(line);
        if (percent.find()) {
            progress.fraction(Double.parseDouble(percent.group(1)) / 100.0);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : fallback;
    }
}
