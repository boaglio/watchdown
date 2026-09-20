package com.boaglio.watchdown.config;

import java.time.Duration;
import java.util.List;

public record YtDlpConfig(String path, List<String> extraArgs, int timeoutMinutes) {

    public YtDlpConfig {
        extraArgs = List.copyOf(extraArgs);
    }

    public static YtDlpConfig defaults() {
        return new YtDlpConfig("yt-dlp", List.of(), 15);
    }

    public Duration timeout() {
        return Duration.ofMinutes(timeoutMinutes);
    }
}
