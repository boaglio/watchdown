package com.boaglio.watchdown.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boaglio.watchdown.cli.UsageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaTest {

    @TempDir
    Path directory;

    @Test
    void takesTheTitleFromTheFileName() throws IOException {
        LocalMedia media = LocalMedia.of(file("Team standup 2025-09-18.m4a"));

        assertThat(media.title()).isEqualTo("Team standup 2025-09-18");
    }

    @Test
    void keepsAFileNameThatHasNoExtension() throws IOException {
        assertThat(LocalMedia.of(file("recording")).title()).isEqualTo("recording");
    }

    @Test
    void givesTheSameFileTheSameIdEveryTime() throws IOException {
        Path file = file("talk.mp3");

        assertThat(LocalMedia.of(file).id()).isEqualTo(LocalMedia.of(file).id());
    }

    @Test
    void givesTwoFilesOfTheSameNameDifferentIds() throws IOException {
        Path first = file("one/talk.mp3");
        Path second = file("two/talk.mp3");

        assertThat(LocalMedia.of(first).id()).isNotEqualTo(LocalMedia.of(second).id());
    }

    @Test
    void resolvesTheFileToAnAbsolutePath() throws IOException {
        LocalMedia media = LocalMedia.of(file("talk.mp3"));

        assertThat(media.path().isAbsolute()).isTrue();
    }

    @Test
    void buildsMetadataWithoutAChannelOrAUrl() throws IOException {
        VideoMetadata metadata = LocalMedia.of(file("talk.mp3")).asMetadata(754);

        assertThat(metadata.title()).isEqualTo("talk");
        assertThat(metadata.channel()).isEmpty();
        assertThat(metadata.webpageUrl()).isNull();
        assertThat(metadata.durationSeconds()).isEqualTo(754);
        assertThat(metadata.chapters()).isEmpty();
        assertThat(metadata.captionLanguages()).isEmpty();
        assertThat(metadata.uploadDate()).isNotNull();
    }

    @Test
    void rejectsAFileThatIsNotThere() {
        assertThatThrownBy(() -> LocalMedia.of(directory.resolve("missing.mp3")))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("file not found");
    }

    @Test
    void rejectsADirectory() {
        assertThatThrownBy(() -> LocalMedia.of(directory))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("not a readable file");
    }

    private Path file(String name) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "audio");
        return file;
    }
}
