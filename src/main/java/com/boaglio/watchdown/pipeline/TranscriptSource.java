package com.boaglio.watchdown.pipeline;

/**
 * Where a transcript comes from, decided once per video before anything is downloaded.
 *
 * @param language the caption language to fetch, or the language to pass to Whisper
 * @param display  what the step line and the provenance table show
 */
public record TranscriptSource(Kind kind, String language, String display) {

    public enum Kind { MANUAL_CAPTIONS, AUTOMATIC_CAPTIONS, WHISPER }

    public boolean fromCaptions() {
        return kind != Kind.WHISPER;
    }

    public boolean automatic() {
        return kind == Kind.AUTOMATIC_CAPTIONS;
    }
}
