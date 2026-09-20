package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.config.CaptionMode;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.transcribe.TranscriptionException;

/**
 * Decides whether a video is transcribed from YouTube's captions or by Whisper.
 *
 * <p>{@code auto} prefers the captions the creator uploaded, because they are written by a human
 * and cost nothing to fetch, and falls back to Whisper otherwise. {@code only} also accepts
 * YouTube's automatic captions, and never runs Whisper. {@code never} always runs Whisper.
 */
public class CaptionPolicy {

    private final CaptionMode mode;
    private final String whisperModel;

    public CaptionPolicy(CaptionMode mode, String whisperModel) {
        this.mode = mode;
        this.whisperModel = whisperModel;
    }

    public TranscriptSource choose(VideoMetadata metadata, String wantedLanguage) {
        if (mode.allowsCaptions()) {
            String manual = metadata.captionLanguageFor(wantedLanguage, false);
            if (manual != null) {
                return new TranscriptSource(TranscriptSource.Kind.MANUAL_CAPTIONS, manual,
                        "captions " + manual);
            }
            if (mode.allowsAutomaticCaptions()) {
                String automatic = metadata.captionLanguageFor(wantedLanguage, true);
                if (automatic != null) {
                    return new TranscriptSource(TranscriptSource.Kind.AUTOMATIC_CAPTIONS, automatic,
                            "captions " + automatic + ", automatic");
                }
            }
        }
        if (!mode.allowsWhisper()) {
            throw new TranscriptionException(
                    "--captions only was given but this video has no captions"
                            + (isSpecific(wantedLanguage) ? " in " + wantedLanguage : "")
                            + ". Drop --captions only to transcribe it with whisper.");
        }
        return new TranscriptSource(TranscriptSource.Kind.WHISPER, wantedLanguage,
                "whisper " + whisperModel + ", lang=" + wantedLanguage);
    }

    private static boolean isSpecific(String language) {
        return language != null && !"auto".equalsIgnoreCase(language);
    }
}
