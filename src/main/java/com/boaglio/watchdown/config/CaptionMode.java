package com.boaglio.watchdown.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How much watchdown is allowed to use YouTube's own captions instead of transcribing. */
public enum CaptionMode {

    /** Use the captions the creator uploaded when the video has them; otherwise run Whisper. */
    AUTO,
    /** Always run Whisper, even when the video has captions. */
    NEVER,
    /** Use captions only: the creator's if they exist, otherwise the automatic ones. Never Whisper. */
    ONLY;

    public static CaptionMode parse(String value) {
        if (value == null) {
            return AUTO;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "never" -> NEVER;
            case "only" -> ONLY;
            default -> throw new IllegalArgumentException(
                    "expected one of " + names() + ", found '" + value + "'");
        };
    }

    public static String names() {
        return Arrays.stream(values())
                .map(mode -> mode.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean allowsCaptions() {
        return this != NEVER;
    }

    public boolean allowsWhisper() {
        return this != ONLY;
    }

    /** Automatic captions are a fallback only when the user asked for captions and nothing else. */
    public boolean allowsAutomaticCaptions() {
        return this == ONLY;
    }
}
