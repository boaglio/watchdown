package com.boaglio.watchdown.transcribe;

import java.util.List;

/** A full transcript: the language Whisper detected, and every segment in order. */
public record Transcript(String language, List<Segment> segments) {

    public Transcript {
        segments = List.copyOf(segments);
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public int wordCount() {
        return segments.stream()
                .mapToInt(segment -> segment.text().isBlank() ? 0 : segment.text().split("\\s+").length)
                .sum();
    }

    public double endsAt() {
        return segments.isEmpty() ? 0 : segments.getLast().end();
    }
}
