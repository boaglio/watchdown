package com.boaglio.watchdown.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptionParserTest {

    private final CaptionParser parser = new CaptionParser(Fixtures.mapper());

    @TempDir
    Path directory;

    @Test
    void readsTheRecordedCaptionTrack() {
        Transcript transcript = parser.parse(Fixtures.youtubeCaptions(), "en");

        assertThat(transcript.language()).isEqualTo("en");
        assertThat(transcript.segments()).extracting(Segment::text).containsExactly(
                "[Music]",
                "Welcome back to the channel.",
                "Today we build a transcription pipeline that never leaves your laptop.",
                "So here is the pipeline: yt-dlp, whisper, then a local model.",
                "That is the whole tool. Thanks for watching.");
    }

    @Test
    void turnsMillisecondsIntoSeconds() {
        Transcript transcript = parser.parse(Fixtures.youtubeCaptions(), "en");

        Segment welcome = transcript.segments().get(1);
        assertThat(welcome.start()).isEqualTo(4.4);
        assertThat(welcome.end()).isEqualTo(8.559);
    }

    @Test
    void joinsTheWordLevelPiecesOfOneLine() {
        Transcript transcript = parser.parse(Fixtures.youtubeCaptions(), "en");

        assertThat(transcript.segments().get(1).text()).isEqualTo("Welcome back to the channel.");
    }

    @Test
    void dropsTheRollingWindowRepeatsOfAutomaticCaptions() {
        Transcript transcript = parser.parse(Fixtures.youtubeCaptions(), "en");

        assertThat(transcript.segments()).noneMatch(segment -> segment.text().isBlank());
        assertThat(transcript.segments()).hasSize(5);
    }

    @Test
    void givesAnEventWithoutADurationTheNextEventsStart() {
        Transcript transcript = parser.parse(Fixtures.youtubeCaptions(), "en");

        Segment pipeline = transcript.segments().get(3);
        assertThat(pipeline.start()).isEqualTo(240.5);
        assertThat(pipeline.end()).isEqualTo(740.0);
    }

    @Test
    void readsFromAFile() throws IOException {
        Path file = directory.resolve("captions.en.json3");
        Files.writeString(file, Fixtures.youtubeCaptions());

        assertThat(parser.read(file, "en").segments()).hasSize(5);
    }

    @Test
    void failsOnAFileThatIsNotJson() {
        assertThatThrownBy(() -> parser.parse("not json", "en"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not JSON");
    }

    @Test
    void failsWhenThereAreNoEvents() {
        assertThatThrownBy(() -> parser.parse("{\"wireMagic\":\"pb3\"}", "en"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("no events");
    }

    @Test
    void failsWhenEveryEventIsEmpty() {
        assertThatThrownBy(() -> parser.parse("{\"events\":[{\"tStartMs\":0,\"segs\":[{\"utf8\":\" \"}]}]}", "en"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("no text");
    }
}
