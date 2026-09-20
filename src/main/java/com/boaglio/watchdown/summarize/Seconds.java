package com.boaglio.watchdown.summarize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a position in the video as a number of seconds, however the model chose to write it.
 *
 * <p>The prompt asks for a plain integer, but a small model that has just read a transcript full of
 * {@code [mm:ss]} marks will often answer {@code "02:05"}, {@code "1:02:05"} or {@code "[02:05]"}.
 * Those all mean something unambiguous, so watchdown accepts them rather than throwing away a
 * whole summary over the format of one field.
 *
 * <p>A moment the model left out, sent as {@code null}, or wrote as something unreadable becomes
 * {@link Double#NaN}: there is no moment in the video to point at, so the entry is dropped later
 * rather than silently pinned to the start of the video. Jackson asks for the null and absent
 * values separately, and a record's primitive {@code double} cannot take a null, so both are
 * answered here.
 */
public class Seconds extends ValueDeserializer<Double> {

    private static final Pattern CLOCK = Pattern.compile(
            "^\\[?\\s*(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{1,2})(?:\\.\\d+)?\\s*]?$");

    /** What the model left out or sent as null: no moment at all, never the start of the video. */
    public static final double UNKNOWN = Double.NaN;

    @Override
    public Double deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        if (token != JsonToken.VALUE_STRING) {
            return UNKNOWN;
        }
        String text = parser.getString();
        return text == null ? UNKNOWN : parse(text);
    }

    @Override
    public Double getNullValue(DeserializationContext context) {
        return UNKNOWN;
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return UNKNOWN;
    }

    /** Parses {@code 125}, {@code "125"}, {@code "2:05"}, {@code "1:02:05"} or {@code "[02:05]"}. */
    public static double parse(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String text = value.strip();
        if (text.isEmpty()) {
            return UNKNOWN;
        }
        Matcher clock = CLOCK.matcher(text);
        if (clock.matches()) {
            long hours = clock.group(1) == null ? 0 : Long.parseLong(clock.group(1));
            long minutes = Long.parseLong(clock.group(2));
            long seconds = Long.parseLong(clock.group(3));
            return hours * 3600 + minutes * 60 + seconds;
        }
        try {
            return Double.parseDouble(text.replace("[", "").replace("]", "").strip());
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }
}
