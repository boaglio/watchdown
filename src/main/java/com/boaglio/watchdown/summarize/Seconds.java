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
 */
public class Seconds extends ValueDeserializer<Double> {

    private static final Pattern CLOCK = Pattern.compile(
            "^\\[?\\s*(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{1,2})(?:\\.\\d+)?\\s*]?$");

    @Override
    public Double deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        String text = parser.getString();
        return text == null ? 0 : parse(text);
    }

    /** Parses {@code 125}, {@code "125"}, {@code "2:05"}, {@code "1:02:05"} or {@code "[02:05]"}. */
    public static double parse(String value) {
        String text = value.strip();
        if (text.isEmpty()) {
            return 0;
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
            return 0;
        }
    }
}
