package com.boaglio.watchdown.transcribe;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.Progress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The transcription progress watchdown reads out of the timestamps whisper prints as it goes. */
class WhisperProgressTest {

    private final List<Double> reported = new ArrayList<>();

    private final Progress progress = new Progress() {
        @Override
        public void fraction(double fraction) {
            reported.add(fraction);
        }

        @Override
        public void note(String note) {
        }
    };

    @Test
    void turnsWhispersPositionIntoAFractionOfTheRecording() {
        WhisperTranscriber.reportProgress("[00:00.000 --> 00:30.000]  Welcome back.", 300, progress);
        WhisperTranscriber.reportProgress("[02:30.000 --> 02:45.000]  Halfway.", 300, progress);

        assertThat(reported).containsExactly(0.1, 0.55);
    }

    @Test
    void readsAnHourLongPosition() {
        WhisperTranscriber.reportProgress("[01:00:00.000 --> 01:30:00.000]  Late.", 7200, progress);

        assertThat(reported).containsExactly(0.75);
    }

    @Test
    void neverReportsMoreThanFinished() {
        WhisperTranscriber.reportProgress("[00:00.000 --> 09:59.000]  Past the end.", 60, progress);

        assertThat(reported).containsExactly(1.0);
    }

    @Test
    void staysQuietWhenTheLengthIsUnknown() {
        WhisperTranscriber.reportProgress("[00:00.000 --> 00:30.000]  Welcome back.", 0, progress);

        assertThat(reported).isEmpty();
    }

    @Test
    void ignoresWhisperLinesThatAreNotSegments() {
        WhisperTranscriber.reportProgress("Detected language: Portuguese", 300, progress);

        assertThat(reported).isEmpty();
    }
}
