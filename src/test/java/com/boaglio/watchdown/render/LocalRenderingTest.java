package com.boaglio.watchdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.summarize.KeyPoint;
import com.boaglio.watchdown.summarize.Section;
import com.boaglio.watchdown.summarize.Summary;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A local recording has no URL, so its timestamps are plain text instead of links. */
class LocalRenderingTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2025-09-18T12:00:00Z"), ZoneOffset.UTC);

    private final MarkdownRenderer renderer = new MarkdownRenderer(FIXED);

    @Test
    void writesPlainTimestampsInTheTranscript() {
        String transcript = renderer.transcript(request());

        assertThat(transcript).contains("**[00:00]** Hello there.");
        assertThat(transcript).doesNotContain("youtube.com");
    }

    @Test
    void writesPlainTimestampsInTheSummaryAndTheDigest() {
        assertThat(renderer.summary(request())).contains("## The middle ([02:00])");
        assertThat(renderer.agents(request())).contains("- A point ([00:30])");
    }

    @Test
    void repeatsTheKeyPointsWithPlainTimestampsToo() {
        assertThat(renderer.summary(request()))
                .contains("## Key points")
                .contains("- A point ([00:30])")
                .doesNotContain("youtube.com");
    }

    @Test
    void callsItARecordingAndNamesNoChannel() {
        String agents = renderer.agents(request());

        assertThat(agents)
                .contains("Agent-readable digest of the recording **standup**")
                .contains(", recorded 2025-09-18).")
                .doesNotContain(" by **");
    }

    @Test
    void labelsTheIdAsASourceId() {
        assertThat(renderer.agents(request())).containsPattern("\\| Source ID +\\| a1b2c3d4");
    }

    @Test
    void leavesTheUrlEmptyInTheFrontMatter() {
        assertThat(renderer.transcript(request())).contains("url: \"\"");
    }

    @Test
    void tellsAgentsToCiteTheRecordingWithoutALink() {
        assertThat(renderer.agents(request()))
                .contains("use its timestamps (`[mm:ss]`)");
    }

    private static RenderRequest request() {
        VideoMetadata metadata = new VideoMetadata("a1b2c3d4", "standup", "", LocalDate.of(2025, 9, 18),
                180, "", List.of(), null, null, List.of(), List.of());
        Transcript transcript = new Transcript("en", List.of(
                new Segment(0, 10, "Hello there."),
                new Segment(120, 130, "And that is the plan.")));
        Summary summary = new Summary("standup", "A short standup.",
                List.of(new KeyPoint("A point", 30)),
                List.of(new Section("The middle", 120, "What was said.")));
        return new RenderRequest(metadata, transcript, summary, null,
                "whisper small, lang=auto", "gemma3:4b", "1.0.0");
    }
}
