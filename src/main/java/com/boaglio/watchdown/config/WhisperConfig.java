package com.boaglio.watchdown.config;

import java.time.Duration;

public record WhisperConfig(String path, String model, String language, String device, int timeoutMinutes) {

    public static WhisperConfig defaults() {
        return new WhisperConfig("whisper", "small", "auto", "cpu", 120);
    }

    public boolean autoDetectLanguage() {
        return "auto".equalsIgnoreCase(language);
    }

    public Duration timeout() {
        return Duration.ofMinutes(timeoutMinutes);
    }
}
