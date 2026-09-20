package com.boaglio.watchdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void lowercasesAndJoinsWordsWithDashes() {
        assertThat(Slugs.slugify("Building a Local Pipeline")).isEqualTo("building-a-local-pipeline");
    }

    @Test
    void transliteratesAccentsToAscii() {
        assertThat(Slugs.slugify("Programação Java em São Paulo"))
                .isEqualTo("programacao-java-em-sao-paulo");
    }

    @Test
    void dropsEmojiAndPunctuation() {
        assertThat(Slugs.slugify("🚀 Ship it! (part 2/3)")).isEqualTo("ship-it-part-2-3");
    }

    @Test
    void capsTheLengthAtSixtyCharacters() {
        String slug = Slugs.slugify("a".repeat(80));

        assertThat(slug).hasSize(60);
    }

    @Test
    void neverEndsOrStartsWithADash() {
        assertThat(Slugs.slugify("  --- hello --- ")).isEqualTo("hello");
    }

    @Test
    void fallsBackWhenNothingSurvives() {
        assertThat(Slugs.slugify("🎬🎬🎬")).isEqualTo("video");
        assertThat(Slugs.slugify("")).isEqualTo("video");
        assertThat(Slugs.slugify(null)).isEqualTo("video");
    }
}
