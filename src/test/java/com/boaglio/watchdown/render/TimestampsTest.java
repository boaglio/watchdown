package com.boaglio.watchdown.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimestampsTest {

    @Test
    void formatsShortVideosAsMinutesAndSeconds() {
        assertThat(Timestamps.format(0)).isEqualTo("00:00");
        assertThat(Timestamps.format(9)).isEqualTo("00:09");
        assertThat(Timestamps.format(192)).isEqualTo("03:12");
        assertThat(Timestamps.format(3599)).isEqualTo("59:59");
    }

    @Test
    void formatsLongVideosAsHoursMinutesAndSeconds() {
        assertThat(Timestamps.format(3600)).isEqualTo("1:00:00");
        assertThat(Timestamps.format(3725)).isEqualTo("1:02:05");
    }

    @Test
    void usesTheVideoDurationSoOneFileIsConsistent() {
        assertThat(Timestamps.format(65, 4000)).isEqualTo("0:01:05");
        assertThat(Timestamps.format(65, 400)).isEqualTo("01:05");
    }

    @Test
    void roundsToTheNearestSecond() {
        assertThat(Timestamps.format(6.5)).isEqualTo("00:07");
        assertThat(Timestamps.format(6.4)).isEqualTo("00:06");
    }

    @Test
    void buildsALinkToTheExactMoment() {
        assertThat(Timestamps.link("dQw4w9WgXcQ", 192, 754))
                .isEqualTo("[03:12](https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=192s)");
    }

    @Test
    void neverLinksToANegativePosition() {
        assertThat(Timestamps.url("dQw4w9WgXcQ", -5)).endsWith("&t=0s");
    }
}
