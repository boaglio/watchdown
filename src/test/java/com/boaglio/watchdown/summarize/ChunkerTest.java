package com.boaglio.watchdown.summarize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.download.Chapter;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void makesOneChunkPerChapterWhenTheVideoHasThem() {
        Transcript transcript = new Transcript("en", List.of(
                new Segment(0, 10, "intro one"),
                new Segment(10, 20, "intro two"),
                new Segment(100, 110, "body one"),
                new Segment(110, 120, "body two")));

        List<Chunk> chunks = new Chunker(3000).split(transcript, List.of(
                new Chapter("Intro", 0, 100),
                new Chapter("Body", 100, 200)));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).title()).isEqualTo("Intro");
        assertThat(chunks.get(0).segments()).hasSize(2);
        assertThat(chunks.get(1).title()).isEqualTo("Body");
        assertThat(chunks.get(1).start()).isEqualTo(100);
    }

    @Test
    void keepsSpeechThatFallsOutsideEveryChapter() {
        Transcript transcript = new Transcript("en", List.of(
                new Segment(0, 5, "before the first chapter"),
                new Segment(100, 110, "inside the chapter"),
                new Segment(300, 310, "after the last chapter")));

        List<Chunk> chunks = new Chunker(3000).split(transcript, List.of(new Chapter("Only", 100, 200)));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.stream().mapToInt(chunk -> chunk.segments().size()).sum()).isEqualTo(3);
        assertThat(chunks.get(0).start()).isEqualTo(0);
        assertThat(chunks.get(2).start()).isEqualTo(300);
    }

    @Test
    void splitsOnTokenCountWhenThereAreNoChapters() {
        // Every segment is 40 characters, so about 10 tokens each.
        Transcript transcript = new Transcript("en", segments(10, 40));

        List<Chunk> chunks = new Chunker(25).split(transcript, List.of());

        assertThat(chunks).hasSize(5);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.approximateTokens()).isLessThanOrEqualTo(25));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.title()).isNull());
    }

    @Test
    void neverSplitsASegmentInTheMiddle() {
        Transcript transcript = new Transcript("en", segments(6, 400));

        List<Chunk> chunks = new Chunker(10).split(transcript, List.of());

        assertThat(chunks).hasSize(6);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.segments()).hasSize(1));
        assertThat(chunks.stream().flatMap(chunk -> chunk.segments().stream()).toList())
                .isEqualTo(transcript.segments());
    }

    @Test
    void returnsOneChunkWhenEverythingFits() {
        Transcript transcript = new Transcript("en", segments(5, 40));

        assertThat(new Chunker(3000).split(transcript, List.of())).hasSize(1);
    }

    @Test
    void returnsNothingForAnEmptyTranscript() {
        assertThat(new Chunker(3000).split(new Transcript("en", List.of()), List.of())).isEmpty();
    }

    @Test
    void rejectsANonPositiveChunkSize() {
        assertThatThrownBy(() -> new Chunker(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Segment> segments(int count, int length) {
        List<Segment> segments = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            segments.add(new Segment(index * 10, index * 10 + 10, "x".repeat(length)));
        }
        return segments;
    }
}
