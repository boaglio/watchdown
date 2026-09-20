package com.boaglio.watchdown.config;

import java.time.Duration;

public record OllamaConfig(String baseUrl, String model, double temperature, int numCtx, int timeoutSeconds) {

    public static OllamaConfig defaults() {
        return new OllamaConfig("http://localhost:11434", "gemma3:4b", 0.2, 8192, 300);
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }
}
