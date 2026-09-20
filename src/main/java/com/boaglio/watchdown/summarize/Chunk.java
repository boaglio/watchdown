package com.boaglio.watchdown.summarize;

import com.boaglio.watchdown.transcribe.Segment;
import java.util.List;

/**
 * A slice of the transcript that is summarized in one model call. {@code title} is the chapter
 * title when the video has chapters, and null when the chunk came from a plain token split.
 */
public record Chunk(String title, List<Segment> segments) {

    public Chunk {
        segments = List.copyOf(segments);
    }

    public double start() {
        return segments.isEmpty() ? 0 : segments.getFirst().start();
    }

    public double end() {
        return segments.isEmpty() ? 0 : segments.getLast().end();
    }

    public int approximateTokens() {
        return segments.stream().mapToInt(Segment::approximateTokens).sum();
    }
}
