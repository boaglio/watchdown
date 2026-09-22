package com.boaglio.watchdown.config;

/**
 * How the summary is produced.
 *
 * @param sectionAttempts how many times to ask the model for the sections before watchdown builds
 *                        them from the parts of the video itself. Small local models often answer
 *                        with a TL;DR and key points and no sections at all; each further attempt
 *                        asks a smaller question than the last. 1 means ask once and take what
 *                        comes back, and it also stops watchdown spending calls of its own.
 */
public record SummaryConfig(String language, int chunkTokens, int maxKeyPoints, int sectionAttempts) {

    public static SummaryConfig defaults() {
        return new SummaryConfig("auto", 3000, 10, 3);
    }

    public boolean followsVideoLanguage() {
        return "auto".equalsIgnoreCase(language);
    }
}
