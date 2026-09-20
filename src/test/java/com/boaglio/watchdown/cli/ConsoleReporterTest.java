package com.boaglio.watchdown.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.Progress;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConsoleReporterTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void writesOnePlainLinePerStepWhenTheOutputIsNotATerminal() {
        ConsoleReporter reporter = reporter(false, false);

        reporter.stepStart(1, 4, "Downloading", "\"A video\" (12:34)");
        reporter.progress().fraction(0.5);
        reporter.stepDone();

        String printed = stderr();
        assertThat(printed.lines()).hasSize(1);
        assertThat(printed).startsWith("[1/4] Downloading  \"A video\" (12:34) ");
        assertThat(printed).contains("done");
        // No bar and no carriage returns, so a log file or a pipe stays readable.
        assertThat(printed).doesNotContain("\r").doesNotContain("█").doesNotContain("░");
    }

    @Test
    void drawsABarOnATerminalOnceTheStepKnowsHowFarAlongItIs() {
        ConsoleReporter reporter = reporter(false, true);

        reporter.stepStart(2, 4, "Transcribing", "whisper small");
        reporter.progress().fraction(0.5);

        String printed = stderr();
        assertThat(printed).contains("\r");
        assertThat(printed).contains("██████████░░░░░░░░░░");
        assertThat(printed).contains("50%");
    }

    @Test
    void showsASpinnerWhileTheFractionIsUnknown() {
        ConsoleReporter reporter = reporter(false, true);

        reporter.stepStart(3, 4, "Summarizing", "gemma3:4b");
        reporter.progress().note("3/12");

        assertThat(stderr()).contains("3/12");
        assertThat(stderr()).containsAnyOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏");
    }

    @Test
    void leavesOneCleanLineInTheScrollbackWhenTheStepEnds() {
        ConsoleReporter reporter = reporter(false, true);

        reporter.stepStart(2, 4, "Transcribing", "whisper small");
        reporter.progress().fraction(0.5);
        reporter.stepDone();

        String lastLine = stderr().substring(stderr().lastIndexOf('\r') + 1);
        assertThat(lastLine).startsWith("[2/4] Transcribing whisper small ");
        assertThat(lastLine).contains("done").doesNotContain("█");
    }

    @Test
    void verboseModeKeepsTheLogReadableInsteadOfDrawingABar() {
        ConsoleReporter reporter = reporter(true, true);

        reporter.stepStart(3, 4, "Summarizing", "gemma3:4b");
        reporter.progress().fraction(0.5);
        reporter.stepDone();

        assertThat(stderr()).doesNotContain("\r").doesNotContain("█");
        assertThat(stderr().lines()).hasSize(2);
    }

    @Test
    void reportsAFailedStepWithoutLosingTheReason() {
        ConsoleReporter reporter = reporter(false, false);

        reporter.stepStart(3, 4, "Summarizing", "gemma3:4b");
        reporter.stepFailed("the model is unreachable\nsecond line is dropped");

        assertThat(stderr()).contains("FAILED  the model is unreachable");
        assertThat(stderr()).doesNotContain("second line");
    }

    @Test
    void sendsOnlyTheOutputFolderToStdout() {
        ConsoleReporter reporter = reporter(false, true);

        reporter.stepStart(1, 4, "Downloading", "a video");
        reporter.progress().fraction(0.3);
        reporter.output(Path.of("/tmp/out/a-video-abc"));

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("/tmp/out/a-video-abc%n".formatted());
    }

    @Test
    void aStepThatReportsNothingStillFinishes() {
        ConsoleReporter reporter = reporter(false, false);

        reporter.stepStart(4, 4, "Writing");
        reporter.stepDone();

        assertThat(stderr()).contains("[4/4] Writing").contains("done");
    }

    @Test
    void theDoNothingProgressIsSafeToCall() {
        Progress.NONE.fraction(0.5);
        Progress.NONE.note("anything");
    }

    private ConsoleReporter reporter(boolean verbose, boolean live) {
        return new ConsoleReporter(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), verbose, live);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
