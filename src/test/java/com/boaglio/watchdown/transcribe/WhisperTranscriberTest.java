package com.boaglio.watchdown.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.Fixtures;
import com.boaglio.watchdown.config.WhisperConfig;
import com.boaglio.watchdown.process.FakeProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhisperTranscriberTest {

    @TempDir
    Path directory;

    @Test
    void parsesTheRecordedWhisperOutput() {
        Transcript transcript = transcriber(new FakeProcessRunner()).parse(Fixtures.whisperOutput());

        assertThat(transcript.language()).isEqualTo("en");
        assertThat(transcript.segments()).hasSize(6);
        assertThat(transcript.segments().getFirst().text()).isEqualTo("Welcome back to the channel.");
        assertThat(transcript.segments().getFirst().start()).isEqualTo(0.0);
        assertThat(transcript.endsAt()).isEqualTo(750.0);
    }

    @Test
    void dropsSegmentsThatContainNoSpeech() {
        Transcript transcript = transcriber(new FakeProcessRunner()).parse(Fixtures.whisperOutput());

        assertThat(transcript.segments()).noneMatch(segment -> segment.text().isBlank());
    }

    @Test
    void buildsTheWhisperCommandFromTheConfiguration() throws IOException {
        Path audio = directory.resolve("audio.mp3");
        Files.writeString(audio, "audio");
        FakeProcessRunner runner = new FakeProcessRunner()
                .doingOnRun(() -> writeWhisperOutput(directory.resolve("audio.json")));

        transcriber(runner).transcribe(audio, directory, "pt");

        assertThat(runner.lastCommand()).containsExactly(
                "whisper", audio.toString(),
                "--model", "small",
                "--output_format", "json",
                "--output_dir", directory.toString(),
                "--device", "cpu",
                "--language", "pt");
    }

    @Test
    void leavesTheLanguageOutWhenItShouldBeDetected() throws IOException {
        Path audio = directory.resolve("audio.mp3");
        Files.writeString(audio, "audio");
        FakeProcessRunner runner = new FakeProcessRunner()
                .doingOnRun(() -> writeWhisperOutput(directory.resolve("audio.json")));

        transcriber(runner).transcribe(audio, directory, "auto");

        assertThat(runner.lastCommand()).doesNotContain("--language");
    }

    @Test
    void readsATranscriptBackFromTheCache() throws IOException {
        Path json = directory.resolve("audio.json");
        Files.writeString(json, Fixtures.whisperOutput());

        assertThat(transcriber(new FakeProcessRunner()).readCached(json).segments()).hasSize(6);
    }

    @Test
    void reportsWhatWhisperPrintedWhenItFails() throws IOException {
        Path audio = directory.resolve("audio.mp3");
        Files.writeString(audio, "audio");
        FakeProcessRunner runner = new FakeProcessRunner().answering(2, "", "RuntimeError: model not found");

        assertThatThrownBy(() -> transcriber(runner).transcribe(audio, directory, "auto"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("exit 2")
                .hasMessageContaining("model not found");
    }

    @Test
    void failsWhenWhisperWritesNothing() throws IOException {
        Path audio = directory.resolve("audio.mp3");
        Files.writeString(audio, "audio");

        assertThatThrownBy(() -> transcriber(new FakeProcessRunner()).transcribe(audio, directory, "auto"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("is missing");
    }

    @Test
    void failsOnOutputThatIsNotJson() {
        assertThatThrownBy(() -> transcriber(new FakeProcessRunner()).parse("not json"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not JSON");
    }

    @Test
    void failsWhenThereIsNoSpeechAtAll() {
        assertThatThrownBy(() -> transcriber(new FakeProcessRunner())
                .parse("{\"language\":\"en\",\"segments\":[]}"))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("no speech");
    }

    private WhisperTranscriber transcriber(FakeProcessRunner runner) {
        return new WhisperTranscriber(runner, Fixtures.mapper(), WhisperConfig.defaults());
    }

    private static void writeWhisperOutput(Path path) {
        try {
            Files.writeString(path, Fixtures.whisperOutput());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
