package com.boaglio.watchdown.transcribe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads YouTube's {@code json3} caption format into a {@link Transcript}.
 *
 * <p>An event carries a start in milliseconds, a duration, and the text split across
 * {@code segs}. Automatic captions repeat each line in a rolling window: those repeats are marked
 * {@code aAppend} and are dropped, which leaves one clean segment per spoken line.
 */
public class CaptionParser {

    private final JsonMapper mapper;

    public CaptionParser(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public Transcript read(Path file, String language) {
        try {
            return parse(Files.readString(file), language);
        } catch (IOException e) {
            throw new TranscriptionException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public Transcript parse(String json, String language) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new TranscriptionException("the caption file is not JSON: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject() || !root.path("events").isArray()) {
            throw new TranscriptionException("the caption file has no events");
        }

        List<Segment> segments = new ArrayList<>();
        for (JsonNode event : root.path("events")) {
            if (event.path("aAppend").asInt(0) == 1 || !event.path("segs").isArray()) {
                continue;
            }
            String text = textOf(event.path("segs"));
            if (text.isBlank()) {
                continue;
            }
            // One division each, so that 4400ms + 4159ms lands on 8.559 and not 8.559000000000001.
            double startMs = event.path("tStartMs").asDouble(0);
            double start = startMs / 1000.0;
            double end = (startMs + event.path("dDurationMs").asDouble(0)) / 1000.0;
            segments.add(new Segment(start, end, text));
        }

        if (segments.isEmpty()) {
            throw new TranscriptionException("the caption file contains no text");
        }
        return new Transcript(language, closeGaps(segments));
    }

    private static String textOf(JsonNode segs) {
        StringBuilder text = new StringBuilder();
        for (JsonNode seg : segs) {
            text.append(seg.path("utf8").asString(""));
        }
        return text.toString().strip();
    }

    /** An event without a duration ends where the next one starts, so no segment has zero length. */
    private static List<Segment> closeGaps(List<Segment> segments) {
        List<Segment> closed = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            if (segment.end() > segment.start()) {
                closed.add(segment);
                continue;
            }
            double end = index + 1 < segments.size() ? segments.get(index + 1).start() : segment.start();
            closed.add(new Segment(segment.start(), Math.max(end, segment.start()), segment.text()));
        }
        return closed;
    }
}
