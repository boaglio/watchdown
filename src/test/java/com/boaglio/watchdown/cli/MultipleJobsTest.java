package com.boaglio.watchdown.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.download.YouTubeUrl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AGENTS.md section 4: one failing job does not stop the others, and the run reports the highest
 * exit code any job produced. A URL that cannot even be parsed is that job's failure, not the run's.
 */
class MultipleJobsTest {

    @Test
    void aBadUrlDoesNotStopTheGoodOnes() {
        List<String> processed = new ArrayList<>();
        List<String> urls = List.of(
                "https://www.youtube.com/playlist?list=PLbad",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://vimeo.com/1");

        int worst = ExitCode.OK;
        for (String url : urls) {
            worst = ExitCode.worst(worst, run(() -> processed.add(YouTubeUrl.parse(url).videoId())));
        }

        assertThat(processed).containsExactly("dQw4w9WgXcQ");
        assertThat(worst).isEqualTo(ExitCode.USAGE_ERROR);
    }

    @Test
    void theRunReportsTheHighestCodeAnyJobProduced() {
        int worst = ExitCode.OK;
        worst = ExitCode.worst(worst, ExitCode.OK);
        worst = ExitCode.worst(worst, ExitCode.SUMMARIZATION_FAILED);
        worst = ExitCode.worst(worst, ExitCode.USAGE_ERROR);

        assertThat(worst).isEqualTo(ExitCode.SUMMARIZATION_FAILED);
    }

    @Test
    void parseErrorsDoNotRepeatTheUrlTheCallerAlreadyPrints() {
        int code = run(() -> YouTubeUrl.parse("https://www.youtube.com/playlist?list=PLbad"));

        assertThat(code).isEqualTo(ExitCode.USAGE_ERROR);
        assertThat(lastMessage).isEqualTo("playlists are not supported, pass a single video URL");
    }

    private String lastMessage;

    private int run(Runnable job) {
        try {
            job.run();
            return ExitCode.OK;
        } catch (WatchdownException e) {
            lastMessage = e.getMessage();
            return e.exitCode();
        }
    }
}
