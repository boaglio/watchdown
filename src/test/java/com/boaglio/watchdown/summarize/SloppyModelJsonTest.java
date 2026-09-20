package com.boaglio.watchdown.summarize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The JSON a small local model actually produces.
 *
 * <p>Every case here is a shape that a 4B model has been seen to emit, or a near neighbour of one.
 * The model's wording is its own; only the syntax is forgiven.
 */
class SloppyModelJsonTest {

    @Test
    void readsATimestampWrittenAsAClockWithALeadingZero() {
        // The exact failure from a real gemma3:4b run: "Invalid numeric value: Leading zeroes
        // not allowed", because 02:05 does not even tokenize as JSON.
        Summary summary = convert("""
                {
                  "title": "Carne moida",
                  "tldr": "Por que nao comprar patinho.",
                  "keyPoints": [{ "text": "O patinho e magro demais", "timestamp": 02:05 }],
                  "sections": [{ "title": "Inicio", "start": 00:14, "summary": "A abertura." }]
                }""");

        assertThat(summary.keyPoints().getFirst().timestamp()).isEqualTo(125);
        assertThat(summary.sections().getFirst().start()).isEqualTo(14);
    }

    @Test
    void readsATimestampQuotedAsAClock() {
        Summary summary = convert("""
                {
                  "title": "T", "tldr": "D",
                  "keyPoints": [{ "text": "a", "timestamp": "2:05" },
                                { "text": "b", "timestamp": "1:02:05" },
                                { "text": "c", "timestamp": "[00:14]" }],
                  "sections": []
                }""");

        assertThat(summary.keyPoints()).extracting(KeyPoint::timestamp)
                .containsExactly(125.0, 3725.0, 14.0);
    }

    @Test
    void readsANumberWithALeadingZero() {
        Summary summary = convert("""
                { "title": "T", "tldr": "D",
                  "keyPoints": [{ "text": "a", "timestamp": 0125 }], "sections": [] }""");

        assertThat(summary.keyPoints().getFirst().timestamp()).isEqualTo(125);
    }

    @Test
    void readsAnAnswerWrappedInAMarkdownFence() {
        Summary summary = convert("""
                Here is the summary:

                ```json
                { "title": "T", "tldr": "D", "keyPoints": [], "sections": [] }
                ```""");

        assertThat(summary.title()).isEqualTo("T");
    }

    @Test
    void readsSingleQuotesUnquotedKeysAndATrailingComma() {
        Summary summary = convert("""
                { title: 'T', 'tldr': 'D',
                  keyPoints: [{ text: 'a', timestamp: 10 },],
                  sections: [] }""");

        assertThat(summary.title()).isEqualTo("T");
        assertThat(summary.keyPoints()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "125, 125",
            "'125', 125",
            "'2:05', 125",
            "'02:05', 125",
            "'1:02:05', 3725",
            "'[02:05]', 125",
            "'00:00', 0",
            "'2:05.500', 125",
            "'not a time', 0",
            "'', 0"})
    void parsesEveryShapeOfSecondsItHasBeenGiven(String written, double expected) {
        assertThat(Seconds.parse(written)).isEqualTo(expected);
    }

    @Test
    void leavesTextThatMerelyLooksLikeATimestampAlone() {
        String cleaned = new SummaryConverter.ClockTimestampCleaner()
                .clean("{\"tldr\": \"He says 02:05 is the best part\", \"timestamp\": 02:05}");

        assertThat(cleaned).contains("He says 02:05 is the best part");
        assertThat(cleaned).contains("\"timestamp\": 125");
    }

    private static Summary convert(String answer) {
        return SummaryConverter.create().convert(answer);
    }
}
