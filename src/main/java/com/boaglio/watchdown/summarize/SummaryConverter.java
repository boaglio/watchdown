package com.boaglio.watchdown.summarize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.CompositeResponseTextCleaner;
import org.springframework.ai.converter.ResponseTextCleaner;
import org.springframework.ai.converter.WhitespaceCleaner;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the structured-output converter for {@link Summary}, tuned for small local models.
 *
 * <p>A 4B model asked for JSON gets the shape right and the punctuation wrong. Rather than lose a
 * whole summary to that, watchdown reads the answer leniently: single quotes, unquoted keys,
 * trailing commas and leading zeros are all accepted, and a clock-style timestamp that would not
 * even tokenize as JSON is rewritten before parsing. What the model *says* is still entirely its
 * own; only the syntax is forgiven.
 */
public final class SummaryConverter {

    /**
     * Pulls the JSON object out of an answer that also contains prose.
     *
     * <p>Small models like to introduce themselves ("Here is the summary:") and to wrap the answer
     * in a Markdown fence, sometimes both. The stock cleaner only handles an answer that is
     * nothing but a fence, so this takes the fenced block when there is one and otherwise the
     * outermost braces.
     */
    static final class JsonBodyCleaner implements ResponseTextCleaner {

        private static final Pattern FENCE = Pattern.compile(
                "```(?:json|JSON)?\\s*\\R(.*?)\\R?```", Pattern.DOTALL);

        @Override
        public String clean(String text) {
            if (text == null || text.isBlank()) {
                return text;
            }
            Matcher fence = FENCE.matcher(text);
            String body = fence.find() ? fence.group(1) : text;

            int start = body.indexOf('{');
            int end = body.lastIndexOf('}');
            return start >= 0 && end > start ? body.substring(start, end + 1) : body;
        }
    }

    /**
     * {@code "timestamp": 02:05} is not JSON at all: the parser stops at the colon. The model means
     * two minutes and five seconds, so rewrite it as the number of seconds before parsing.
     */
    static final class ClockTimestampCleaner implements ResponseTextCleaner {

        private static final Pattern UNQUOTED_CLOCK = Pattern.compile(
                "(\"(?:timestamp|start|end)\"\\s*:\\s*)(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?");

        @Override
        public String clean(String text) {
            if (text == null) {
                return null;
            }
            Matcher matcher = UNQUOTED_CLOCK.matcher(text);
            StringBuilder repaired = new StringBuilder();
            while (matcher.find()) {
                long first = Long.parseLong(matcher.group(2));
                long second = Long.parseLong(matcher.group(3));
                long third = matcher.group(4) == null ? -1 : Long.parseLong(matcher.group(4));
                long seconds = third < 0
                        ? first * 60 + second
                        : first * 3600 + second * 60 + third;
                matcher.appendReplacement(repaired, Matcher.quoteReplacement(matcher.group(1) + seconds));
            }
            matcher.appendTail(repaired);
            return repaired.toString();
        }
    }

    private SummaryConverter() {
    }

    public static BeanOutputConverter<Summary> create() {
        return new BeanOutputConverter<>(Summary.class, lenientMapper(), cleaner());
    }

    /** The same leniency for the sections-only retry (AGENTS.md section 6.3). */
    public static BeanOutputConverter<Sections> sections() {
        return new BeanOutputConverter<>(Sections.class, lenientMapper(), cleaner());
    }

    static JsonMapper lenientMapper() {
        return JsonMapper.builder()
                // A model that adds a field of its own ("title", "duration", "notes") should not
                // cost us the whole summary; we read the fields we asked for and ignore the rest.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    static ResponseTextCleaner cleaner() {
        return CompositeResponseTextCleaner.builder()
                .addCleaner(new JsonBodyCleaner())
                .addCleaner(new ClockTimestampCleaner())
                .addCleaner(new WhitespaceCleaner())
                .build();
    }
}
