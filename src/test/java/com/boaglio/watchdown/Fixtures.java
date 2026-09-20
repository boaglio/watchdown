package com.boaglio.watchdown;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** Recorded yt-dlp and whisper output, so the unit tests never touch the real tools. */
public final class Fixtures {

    private Fixtures() {
    }

    public static JsonMapper mapper() {
        return JsonMapper.builder().build();
    }

    public static String ytDlpMetadata() {
        return read("/fixtures/yt-dlp-metadata.json");
    }

    public static String whisperOutput() {
        return read("/fixtures/whisper-output.json");
    }

    public static String read(String resource) {
        try (InputStream stream = Fixtures.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
