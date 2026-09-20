package com.boaglio.watchdown.download;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.Progress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The download progress watchdown reads out of what yt-dlp prints while it works. */
class YtDlpProgressTest {

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
    void readsThePercentageYtDlpPrints() {
        YtDlpDownloader.reportProgress("[download]   4.6% of    3.40MiB at    1.20MiB/s ETA 00:02", progress);
        YtDlpDownloader.reportProgress("[download] 100% of    3.40MiB in 00:00:02 at 1.51MiB/s", progress);

        assertThat(reported).containsExactly(0.046, 1.0);
    }

    @Test
    void ignoresTheOtherLinesYtDlpPrints() {
        YtDlpDownloader.reportProgress("[info] aircAruvnKk: Downloading 1 format(s): 251", progress);
        YtDlpDownloader.reportProgress("[ExtractAudio] Destination: audio.mp3", progress);

        assertThat(reported).isEmpty();
    }
}
