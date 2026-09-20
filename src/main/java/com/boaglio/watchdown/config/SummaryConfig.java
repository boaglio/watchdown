package com.boaglio.watchdown.config;

public record SummaryConfig(String language, int chunkTokens, int maxKeyPoints) {

    public static SummaryConfig defaults() {
        return new SummaryConfig("auto", 3000, 10);
    }

    public boolean followsVideoLanguage() {
        return "auto".equalsIgnoreCase(language);
    }
}
