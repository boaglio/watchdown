package com.boaglio.watchdown.download;

import com.boaglio.watchdown.cli.UsageException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A validated YouTube video URL, reduced to its 11-character video id and a canonical watch URL.
 *
 * <p>Playlists and channels are out of scope for v1 and are rejected with a clear message.
 */
public record YouTubeUrl(String videoId, String canonicalUrl) {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static final java.util.Set<String> HOSTS = java.util.Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtu.be", "www.youtu.be");

    public static YouTubeUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UsageException("empty URL");
        }
        String trimmed = raw.strip();
        URI uri;
        try {
            uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
        } catch (URISyntaxException e) {
            throw new UsageException("not a valid URL");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UsageException("not a YouTube URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOSTS.contains(host)) {
            throw new UsageException("not a YouTube URL");
        }

        Map<String, String> query = parseQuery(uri.getRawQuery());
        String path = uri.getPath() == null ? "" : uri.getPath();

        String id = null;
        if (host.endsWith("youtu.be")) {
            id = firstPathSegment(path);
        } else if (path.equals("/watch")) {
            id = query.get("v");
        } else if (path.startsWith("/shorts/")) {
            id = firstPathSegment(path.substring("/shorts".length()));
        } else if (path.startsWith("/live/")) {
            id = firstPathSegment(path.substring("/live".length()));
        } else if (path.startsWith("/embed/")) {
            id = firstPathSegment(path.substring("/embed".length()));
        }

        if (id == null || id.isBlank()) {
            if (query.containsKey("list") || path.startsWith("/playlist")) {
                throw new UsageException("playlists are not supported, pass a single video URL");
            }
            if (path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/")
                    || path.startsWith("/user/")) {
                throw new UsageException("channels are not supported, pass a single video URL");
            }
            throw new UsageException("no video id found in the URL");
        }
        if (!VIDEO_ID.matcher(id).matches()) {
            throw new UsageException("'" + id + "' is not a valid video id");
        }
        return new YouTubeUrl(id, watchUrl(id));
    }

    public static String watchUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static String firstPathSegment(String path) {
        String withoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
        int slash = withoutLeadingSlash.indexOf('/');
        return slash < 0 ? withoutLeadingSlash : withoutLeadingSlash.substring(0, slash);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.putIfAbsent(
                        java.net.URLDecoder.decode(pair.substring(0, equals), java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(equals + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return values;
    }
}
