package com.boaglio.watchdown.download;

import com.boaglio.watchdown.cli.UsageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/**
 * A local audio or video file to transcribe, as the alternative to a YouTube URL.
 *
 * <p>The id is a short digest of the absolute path, so the same file keeps the same cache
 * directory and the same output folder across runs, and two files with the same name in different
 * directories do not collide.
 */
public record LocalMedia(Path path, String id, String title) {

    private static final int ID_LENGTH = 8;

    public static LocalMedia of(Path file) {
        if (file == null) {
            throw new UsageException("no file given");
        }
        Path absolute = file.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new UsageException("file not found: " + file);
        }
        if (!Files.isRegularFile(absolute) || !Files.isReadable(absolute)) {
            throw new UsageException("not a readable file: " + file);
        }
        return new LocalMedia(absolute, idOf(absolute), titleOf(absolute));
    }

    /** What the progress line and the error messages call this job. */
    public String display() {
        return path.getFileName().toString();
    }

    /**
     * Metadata for a local file. There is no channel, no publication date and no URL to link to,
     * so those stay empty and the renderer writes plain timestamps instead of links.
     *
     * @param durationSeconds the probed duration, or 0 when it could not be determined
     */
    public VideoMetadata asMetadata(int durationSeconds) {
        LocalDate modified = null;
        try {
            modified = Files.getLastModifiedTime(path).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException e) {
            // Leave the date out; it is decoration, not something worth failing a run over.
        }
        return new VideoMetadata(id, title, "", modified, durationSeconds, "", List.of(),
                null, null, List.of(), List.of());
    }

    private static String titleOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        return stem.isBlank() ? name : stem;
    }

    private static String idOf(Path file) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, ID_LENGTH / 2);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
