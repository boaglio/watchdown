package com.boaglio.watchdown.transcribe;

import com.boaglio.watchdown.config.WhisperConfig;
import com.boaglio.watchdown.process.ProcessException;
import com.boaglio.watchdown.process.ProcessResult;
import com.boaglio.watchdown.process.ProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link Transcriber} backed by the openai-whisper command line tool. */
public class WhisperTranscriber implements Transcriber {

    private final ProcessRunner runner;
    private final JsonMapper mapper;
    private final WhisperConfig config;

    public WhisperTranscriber(ProcessRunner runner, JsonMapper mapper, WhisperConfig config) {
        this.runner = runner;
        this.mapper = mapper;
        this.config = config;
    }

    /** Where whisper writes its JSON for a given audio file. */
    public static Path jsonOutputFor(Path audio, Path workDirectory) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return workDirectory.resolve((dot < 0 ? name : name.substring(0, dot)) + ".json");
    }

    @Override
    public Transcript transcribe(Path audio, Path workDirectory, String language) {
        try {
            Files.createDirectories(workDirectory);
        } catch (IOException e) {
            throw new TranscriptionException("cannot create " + workDirectory + ": " + e.getMessage(), e);
        }

        List<String> command = new ArrayList<>(List.of(
                config.path(),
                audio.toString(),
                "--model", config.model(),
                "--output_format", "json",
                "--output_dir", workDirectory.toString(),
                "--device", config.device()));
        if (language != null && !"auto".equalsIgnoreCase(language)) {
            command.add("--language");
            command.add(language);
        }

        ProcessResult result;
        try {
            result = runner.run(command, config.timeout());
        } catch (ProcessException e) {
            throw new TranscriptionException(e.getMessage(), e);
        }
        if (!result.successful()) {
            throw new TranscriptionException("whisper failed (exit " + result.exitCode() + "):\n"
                    + result.tailOfStderr(20));
        }

        Path json = jsonOutputFor(audio, workDirectory);
        if (!Files.exists(json)) {
            throw new TranscriptionException("whisper finished but " + json + " is missing");
        }
        return readCached(json);
    }

    @Override
    public Transcript readCached(Path cachedOutput) {
        String json;
        try {
            json = Files.readString(cachedOutput);
        } catch (IOException e) {
            throw new TranscriptionException("cannot read " + cachedOutput + ": " + e.getMessage(), e);
        }
        return parse(json);
    }

    /** Parses Whisper's JSON: the detected language plus the segments. */
    public Transcript parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new TranscriptionException("whisper output is not JSON: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new TranscriptionException("whisper output is not a JSON object");
        }

        JsonNode languageNode = root.path("language");
        String language = languageNode.isString() ? languageNode.asString() : "unknown";

        List<Segment> segments = new ArrayList<>();
        JsonNode segmentNodes = root.path("segments");
        if (segmentNodes.isArray()) {
            for (JsonNode segment : segmentNodes) {
                String text = segment.path("text").isString() ? segment.path("text").asString() : "";
                if (text.isBlank()) {
                    continue;
                }
                segments.add(new Segment(
                        segment.path("start").asDouble(0),
                        segment.path("end").asDouble(0),
                        text));
            }
        }
        if (segments.isEmpty()) {
            throw new TranscriptionException("whisper produced no speech segments");
        }
        return new Transcript(language, segments);
    }
}
