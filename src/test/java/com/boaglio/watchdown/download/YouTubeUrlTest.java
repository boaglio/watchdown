package com.boaglio.watchdown.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.cli.UsageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ"})
    void extractsTheVideoId(String url) {
        assertThat(YouTubeUrl.parse(url).videoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void ignoresExtraParameters() {
        YouTubeUrl url = YouTubeUrl.parse(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&feature=share&ab_channel=Labs");

        assertThat(url.videoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(url.canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    void keepsTheVideoOfAWatchUrlThatAlsoNamesAPlaylist() {
        assertThat(YouTubeUrl.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890").videoId())
                .isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void rejectsAPlaylistOnlyUrl() {
        assertThatThrownBy(() -> YouTubeUrl.parse("https://www.youtube.com/playlist?list=PL1234567890"))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("playlists are not supported");
    }

    @Test
    void rejectsAChannelUrl() {
        assertThatThrownBy(() -> YouTubeUrl.parse("https://www.youtube.com/@boagliolabs"))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("channels are not supported");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://vimeo.com/123456",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ"})
    void rejectsAnythingThatIsNotYouTube(String url) {
        assertThatThrownBy(() -> YouTubeUrl.parse(url))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("not a YouTube URL");
    }

    @Test
    void rejectsAnIdOfTheWrongLength() {
        assertThatThrownBy(() -> YouTubeUrl.parse("https://youtu.be/tooshort"))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("is not a valid video id");
    }

    @Test
    void rejectsAnEmptyUrl() {
        assertThatThrownBy(() -> YouTubeUrl.parse("  "))
                .isInstanceOf(UsageException.class);
    }
}
