package com.boaglio.watchdown.summarize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.config.SummaryConfig;
import com.boaglio.watchdown.download.Chapter;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class OllamaSummarizerTest {

    private static final Resource CHUNK_PROMPT = new ClassPathResource("prompts/chunk-summary.st");
    private static final Resource FINAL_PROMPT = new ClassPathResource("prompts/final-summary.st");
    private static final Resource SECTIONS_PROMPT = new ClassPathResource("prompts/sections-retry.st");

    private static final String GOOD_ANSWER = """
            {
              "title": "Local transcription",
              "tldr": "The video shows how to transcribe locally. It never uses a cloud API.",
              "keyPoints": [
                { "text": "Everything runs on your laptop", "timestamp": 14 },
                { "text": "The cache makes reruns cheap", "timestamp": 250 }
              ],
              "sections": [
                { "title": "Why local", "start": 0, "summary": "The reasons to avoid the cloud." },
                { "title": "The pipeline", "start": 240, "summary": "yt-dlp, whisper, then a local model." }
              ]
            }""";

    @Test
    void parsesTheModelsJsonIntoASummary() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.title()).isEqualTo("Local transcription");
        assertThat(summary.tldr()).startsWith("The video shows");
        assertThat(summary.keyPoints()).hasSize(2);
        assertThat(summary.keyPoints().getFirst().timestamp()).isEqualTo(14);
        assertThat(summary.sections()).hasSize(2);
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void skipsTheMapStepWhenEverythingFitsInOneChunk() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(model.callCount()).isEqualTo(1);
        assertThat(model.prompts().getFirst()).contains("Welcome back");
    }

    @Test
    void summarizesEachChunkBeforeCombiningThem() {
        StubChatModel model = new StubChatModel()
                .answering("First part summary.", "Second part summary.", GOOD_ANSWER);
        SummaryConfig config = new SummaryConfig("auto", 10, 10, 1);

        Summary summary = summarizer(model, config).summarize(withChapters(), transcript());

        assertThat(model.callCount()).isEqualTo(3);
        assertThat(model.prompts().getLast())
                .contains("First part summary.")
                .contains("Second part summary.");
        assertThat(summary.sections()).hasSize(2);
    }

    @Test
    void retriesOnceWithAStricterReminder() {
        StubChatModel model = new StubChatModel().answering("Sorry, I cannot do that.", GOOD_ANSWER);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.title()).isEqualTo("Local transcription");
        assertThat(model.callCount()).isEqualTo(2);
        assertThat(model.prompts().getLast()).contains("could not be parsed");
    }

    @Test
    void failsAfterTheSecondUnusableAnswer() {
        StubChatModel model = new StubChatModel().answering("nope", "still nope");

        assertThatThrownBy(() -> summarizer(model, SummaryConfig.defaults())
                .summarize(metadata(), transcript()))
                .isInstanceOf(SummarizationException.class)
                .hasMessageContaining("after one retry");
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void dropsTimestampsOutsideTheVideo() {
        StubChatModel model = new StubChatModel().answering("""
                {
                  "title": "Local transcription",
                  "tldr": "A summary.",
                  "keyPoints": [
                    { "text": "inside", "timestamp": 100 },
                    { "text": "after the end", "timestamp": 9999 },
                    { "text": "before the start", "timestamp": -5 }
                  ],
                  "sections": [
                    { "title": "Real", "start": 0, "summary": "in range" },
                    { "title": "Invented", "start": 8000, "summary": "out of range" }
                  ]
                }""");

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.keyPoints()).extracting(KeyPoint::text).containsExactly("inside");
        assertThat(summary.sections()).extracting(Section::title).containsExactly("Real");
    }

    @Test
    void dropsEntriesWhoseMomentTheModelNeverGave() {
        StubChatModel model = new StubChatModel().answering("""
                {
                  "title": "Local transcription",
                  "tldr": "A summary.",
                  "keyPoints": [{ "text": "real", "timestamp": 100 },
                                { "text": "no moment", "timestamp": null }],
                  "sections": [{ "title": "Real", "start": 0, "summary": "in range" },
                               { "title": "No moment", "summary": "the model forgot the start" }]
                }""");

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.keyPoints()).extracting(KeyPoint::text).containsExactly("real");
        assertThat(summary.sections()).extracting(Section::title).containsExactly("Real");
    }

    @Test
    void stillSucceedsWhenEverySectionLostItsMoment() {
        // Losing the sections beats losing the whole summary and exiting 6.
        StubChatModel model = new StubChatModel().answering("""
                {
                  "title": "Local transcription",
                  "tldr": "A summary that survived.",
                  "keyPoints": [{ "text": "real", "timestamp": 100 }],
                  "sections": [{ "title": "No moment", "summary": "s" }]
                }""");

        Summary summary = summarizer(model, oneAttempt()).summarize(metadata(), transcript());

        assertThat(summary.tldr()).isEqualTo("A summary that survived.");
        assertThat(summary.keyPoints()).hasSize(1);
        assertThat(summary.sections()).isEmpty();
    }

    @Test
    void dropsEntriesWithNoTextAtAll() {
        StubChatModel model = new StubChatModel().answering("""
                {
                  "title": "T", "tldr": "D",
                  "keyPoints": [{ "text": "  ", "timestamp": 10 },
                                { "text": "real", "timestamp": 20 }],
                  "sections": [{ "title": "", "start": 0, "summary": "  " }]
                }""");

        Summary summary = summarizer(model, oneAttempt()).summarize(metadata(), transcript());

        assertThat(summary.keyPoints()).extracting(KeyPoint::text).containsExactly("real");
        assertThat(summary.sections()).isEmpty();
    }

    @Test
    void keepsAtMostTheConfiguredNumberOfKeyPoints() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        Summary summary = summarizer(model, new SummaryConfig("auto", 3000, 1, 1))
                .summarize(metadata(), transcript());

        assertThat(summary.keyPoints()).hasSize(1);
    }

    @Test
    void tellsThePromptWhichLanguageToWriteIn() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        summarizer(model, new SummaryConfig("pt", 3000, 10, 1)).summarize(metadata(), transcript());

        assertThat(model.prompts().getFirst()).contains("Write every piece of text in pt");
    }

    @Test
    void refusesAnEmptyTranscript() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        assertThatThrownBy(() -> summarizer(model, SummaryConfig.defaults())
                .summarize(metadata(), new Transcript("en", List.of())))
                .isInstanceOf(SummarizationException.class)
                .hasMessageContaining("empty");
    }

    // ---------------------------------------------------------------- chasing the sections

    private static final String NO_SECTIONS = """
            {
              "title": "Local transcription",
              "tldr": "The video shows how to transcribe locally.",
              "keyPoints": [{ "text": "Everything runs on your laptop", "timestamp": 14 }],
              "sections": []
            }""";
    private static final String SECTIONS_ONLY = """
            {
              "sections": [
                { "title": "Why local", "start": 0, "summary": "The reasons to avoid the cloud." },
                { "title": "The pipeline", "start": 240, "summary": "yt-dlp, whisper, a local model." }
              ]
            }""";

    @Test
    void asksAgainWhenTheFirstAnswerHasNoSections() {
        StubChatModel model = new StubChatModel().answering(NO_SECTIONS, GOOD_ANSWER);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.sections()).extracting(Section::title).containsExactly("Why local", "The pipeline");
        assertThat(model.callCount()).isEqualTo(2);
        assertThat(model.prompts().getLast()).contains("no usable \"sections\" array");
    }

    @Test
    void asksForTheSectionsAloneOnceAskingAgainHasNotWorked() {
        StubChatModel model = new StubChatModel().answering(NO_SECTIONS, NO_SECTIONS, SECTIONS_ONLY);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.sections()).extracting(Section::title).containsExactly("Why local", "The pipeline");
        assertThat(model.callCount()).isEqualTo(3);
        assertThat(model.prompts().getLast())
                .contains("the sections are the only thing missing")
                .contains("3 or more entries");
    }

    @Test
    void aLaterAttemptOnlyEverAddsSections() {
        // The title, TL;DR and key points stay as the first good answer wrote them.
        StubChatModel model = new StubChatModel().answering(NO_SECTIONS, NO_SECTIONS, SECTIONS_ONLY);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.title()).isEqualTo("Local transcription");
        assertThat(summary.tldr()).isEqualTo("The video shows how to transcribe locally.");
        assertThat(summary.keyPoints()).extracting(KeyPoint::text)
                .containsExactly("Everything runs on your laptop");
    }

    @Test
    void asksForFewerSectionsEachTimeItFails() {
        StubChatModel model = new StubChatModel()
                .answering(NO_SECTIONS, NO_SECTIONS, NO_SECTIONS, NO_SECTIONS, SECTIONS_ONLY);

        summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(model.callCount()).isEqualTo(5);
        assertThat(model.prompts().get(2)).contains("3 or more entries");
        assertThat(model.prompts().get(3)).contains("2 or more entries");
        assertThat(model.prompts().get(4)).contains("1 or more entries");
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttemptsAndKeepsTheSummary() {
        StubChatModel model = new StubChatModel()
                .answering(NO_SECTIONS, NO_SECTIONS, NO_SECTIONS, NO_SECTIONS, NO_SECTIONS);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(model.callCount()).isEqualTo(5);
        assertThat(summary.sections()).isEmpty();
        assertThat(summary.tldr()).isEqualTo("The video shows how to transcribe locally.");
        assertThat(summary.keyPoints()).hasSize(1);
    }

    @Test
    void anAnswerThatCannotBeParsedDuringTheChaseIsJustAnotherFailedAttempt() {
        StubChatModel model = new StubChatModel()
                .answering(NO_SECTIONS, "I am sorry, I cannot do that.", SECTIONS_ONLY);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.sections()).hasSize(2);
        assertThat(model.callCount()).isEqualTo(3);
    }

    @Test
    void sectionsWithNoUsableMomentDoNotCountAsFound() {
        String withoutMoments = """
                { "sections": [{ "title": "No moment", "summary": "the model forgot the start" }] }""";
        StubChatModel model = new StubChatModel()
                .answering(NO_SECTIONS, NO_SECTIONS, withoutMoments, SECTIONS_ONLY);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.sections()).extracting(Section::title).containsExactly("Why local", "The pipeline");
        assertThat(model.callCount()).isEqualTo(4);
    }

    @Test
    void doesNotAskAgainWhenTheFirstAnswerAlreadyHasSections() {
        StubChatModel model = new StubChatModel().answering(GOOD_ANSWER);

        Summary summary = summarizer(model, SummaryConfig.defaults()).summarize(metadata(), transcript());

        assertThat(summary.sections()).hasSize(2);
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void oneAttemptMeansTakeWhateverComesBack() {
        StubChatModel model = new StubChatModel().answering(NO_SECTIONS, GOOD_ANSWER);

        Summary summary = summarizer(model, oneAttempt()).summarize(metadata(), transcript());

        assertThat(summary.sections()).isEmpty();
        assertThat(model.callCount()).isEqualTo(1);
    }

    private static OllamaSummarizer summarizer(StubChatModel model, SummaryConfig config) {
        return new OllamaSummarizer(ChatClient.create(model), CHUNK_PROMPT, FINAL_PROMPT, SECTIONS_PROMPT, config);
    }

    /** The shipped defaults, but asking once: for the tests that are about validation, not retrying. */
    private static SummaryConfig oneAttempt() {
        SummaryConfig defaults = SummaryConfig.defaults();
        return new SummaryConfig(defaults.language(), defaults.chunkTokens(), defaults.maxKeyPoints(), 1);
    }

    private static Transcript transcript() {
        return new Transcript("en", List.of(
                new Segment(0, 6.5, "Welcome back to the channel."),
                new Segment(240.5, 249.8, "So here is the pipeline: yt-dlp, whisper, then a local model."),
                new Segment(740, 750, "That is the whole tool.")));
    }

    private static VideoMetadata metadata() {
        return new VideoMetadata("dQw4w9WgXcQ", "Building a Local Transcription Pipeline", "Boaglio Labs",
                LocalDate.of(2025, 9, 17), 754, "", List.of(),
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "en", List.of(), List.of());
    }

    private static VideoMetadata withChapters() {
        VideoMetadata base = metadata();
        return new VideoMetadata(base.id(), base.title(), base.channel(), base.uploadDate(),
                base.durationSeconds(), base.description(),
                List.of(new Chapter("Why local", 0, 240), new Chapter("The pipeline", 240, 754)),
                base.webpageUrl(), base.language(), base.captionLanguages(),
                base.automaticCaptionLanguages());
    }
}
