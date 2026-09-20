package com.boaglio.watchdown.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.config.CaptionMode;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.transcribe.TranscriptionException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptionPolicyTest {

    @Test
    void autoPrefersTheCaptionsTheCreatorUploaded() {
        TranscriptSource source = policy(CaptionMode.AUTO).choose(video(List.of("en", "pt"), List.of("es")), "auto");

        assertThat(source.kind()).isEqualTo(TranscriptSource.Kind.MANUAL_CAPTIONS);
        assertThat(source.language()).isEqualTo("en");
        assertThat(source.display()).isEqualTo("captions en");
    }

    @Test
    void autoFallsBackToWhisperWhenThereAreNoManualCaptions() {
        TranscriptSource source = policy(CaptionMode.AUTO).choose(video(List.of(), List.of("en")), "auto");

        assertThat(source.kind()).isEqualTo(TranscriptSource.Kind.WHISPER);
        assertThat(source.display()).isEqualTo("whisper small, lang=auto");
    }

    @Test
    void autoNeverReachesForTheAutomaticCaptions() {
        TranscriptSource source = policy(CaptionMode.AUTO).choose(video(List.of(), List.of("en", "pt")), "en");

        assertThat(source.fromCaptions()).isFalse();
    }

    @Test
    void neverAlwaysRunsWhisper() {
        TranscriptSource source = policy(CaptionMode.NEVER).choose(video(List.of("en"), List.of("en")), "auto");

        assertThat(source.kind()).isEqualTo(TranscriptSource.Kind.WHISPER);
    }

    @Test
    void onlyFallsBackToTheAutomaticCaptions() {
        TranscriptSource source = policy(CaptionMode.ONLY).choose(video(List.of(), List.of("en")), "auto");

        assertThat(source.kind()).isEqualTo(TranscriptSource.Kind.AUTOMATIC_CAPTIONS);
        assertThat(source.automatic()).isTrue();
        assertThat(source.display()).isEqualTo("captions en, automatic");
    }

    @Test
    void onlyFailsWhenTheVideoHasNoCaptionsAtAll() {
        assertThatThrownBy(() -> policy(CaptionMode.ONLY).choose(video(List.of(), List.of()), "auto"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("no captions")
                .hasMessageContaining("Drop --captions only");
    }

    @Test
    void onlyNamesTheLanguageItCouldNotFind() {
        assertThatThrownBy(() -> policy(CaptionMode.ONLY).choose(video(List.of("pt"), List.of("pt")), "ja"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("no captions in ja");
    }

    @Test
    void picksTheLanguageTheUserAskedFor() {
        TranscriptSource source = policy(CaptionMode.AUTO).choose(video(List.of("en", "pt"), List.of()), "pt");

        assertThat(source.language()).isEqualTo("pt");
    }

    @Test
    void matchesARegionalVariantOfTheWantedLanguage() {
        TranscriptSource source = policy(CaptionMode.AUTO).choose(video(List.of("en-US"), List.of()), "en");

        assertThat(source.language()).isEqualTo("en-US");
    }

    @Test
    void autoLanguageFollowsTheVideosOwnLanguage() {
        VideoMetadata portuguese = new VideoMetadata("id", "t", "c", null, 60, "", List.of(), "url",
                "pt", List.of("en", "pt"), List.of());

        assertThat(policy(CaptionMode.AUTO).choose(portuguese, "auto").language()).isEqualTo("pt");
    }

    @Test
    void autoLanguageFallsBackToTheFirstTrackWhenTheVideoDoesNotSay() {
        VideoMetadata unknown = new VideoMetadata("id", "t", "c", null, 60, "", List.of(), "url",
                null, List.of("fr", "en"), List.of());

        assertThat(policy(CaptionMode.AUTO).choose(unknown, "auto").language()).isEqualTo("fr");
    }

    @Test
    void whisperKeepsTheLanguageTheUserAskedFor() {
        TranscriptSource source = policy(CaptionMode.NEVER).choose(video(List.of("en"), List.of()), "pt");

        assertThat(source.language()).isEqualTo("pt");
        assertThat(source.display()).isEqualTo("whisper small, lang=pt");
    }

    private static CaptionPolicy policy(CaptionMode mode) {
        return new CaptionPolicy(mode, "small");
    }

    private static VideoMetadata video(List<String> manual, List<String> automatic) {
        return new VideoMetadata("dQw4w9WgXcQ", "A title", "A channel", null, 754, "", List.of(),
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "en", manual, automatic);
    }
}
