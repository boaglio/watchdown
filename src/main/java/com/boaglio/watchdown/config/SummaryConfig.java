package com.boaglio.watchdown.config;

/**
 * How the summary is produced.
 *
 * @param sectionAttempts how many times to ask for the sections before giving up on them. Small
 *                        local models often answer with a TL;DR and key points and no sections at
 *                        all; each further attempt asks a smaller question than the last. 1 means
 *                        ask once and accept whatever comes back.
 */
public record SummaryConfig(String language, int chunkTokens, int maxKeyPoints, int sectionAttempts) {

    public static SummaryConfig defaults() {
        return new SummaryConfig("auto", 3000, 10, 5);
    }

    public boolean followsVideoLanguage() {
        return "auto".equalsIgnoreCase(language);
    }
}
