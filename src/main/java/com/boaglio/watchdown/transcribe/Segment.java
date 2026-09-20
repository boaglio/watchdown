package com.boaglio.watchdown.transcribe;

/** One timed piece of speech, exactly as Whisper produced it. */
public record Segment(double start, double end, String text) {

    public Segment {
        text = text == null ? "" : text.strip();
    }

    public int approximateTokens() {
        return Math.max(1, text.length() / 4);
    }
}
