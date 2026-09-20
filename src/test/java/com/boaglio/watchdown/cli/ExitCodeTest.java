package com.boaglio.watchdown.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boaglio.watchdown.config.ConfigException;
import com.boaglio.watchdown.download.DownloadException;
import com.boaglio.watchdown.pipeline.DependencyException;
import com.boaglio.watchdown.summarize.SummarizationException;
import com.boaglio.watchdown.transcribe.TranscriptionException;
import org.junit.jupiter.api.Test;

class ExitCodeTest {

    @Test
    void mapsEveryExceptionTypeToItsCode() {
        assertThat(ExitCode.of(new UsageException("bad flag"))).isEqualTo(2);
        assertThat(ExitCode.of(new ConfigException("bad json"))).isEqualTo(2);
        assertThat(ExitCode.of(new DependencyException("no whisper"))).isEqualTo(3);
        assertThat(ExitCode.of(new DownloadException("yt-dlp failed"))).isEqualTo(4);
        assertThat(ExitCode.of(new TranscriptionException("whisper failed"))).isEqualTo(5);
        assertThat(ExitCode.of(new SummarizationException("ollama failed"))).isEqualTo(6);
    }

    @Test
    void anythingElseIsAnUnexpectedError() {
        assertThat(ExitCode.of(new IllegalStateException("boom"))).isEqualTo(1);
    }

    @Test
    void theRunReportsTheHighestCodeAnyUrlProduced() {
        assertThat(ExitCode.worst(ExitCode.worst(0, 4), 3)).isEqualTo(4);
        assertThat(ExitCode.worst(0, 0)).isEqualTo(0);
    }
}
