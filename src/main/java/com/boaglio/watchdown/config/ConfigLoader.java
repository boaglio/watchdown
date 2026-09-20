package com.boaglio.watchdown.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads {@code config.json} and merges it over {@link WatchdownConfig#defaults()}.
 *
 * <p>Every key is optional. An unknown key is a warning, because a typo should not break a run.
 * Invalid JSON or a value of the wrong type is an error that carries the file path and the JSON
 * path of the offending value.
 */
@Component
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private final JsonMapper mapper;

    public ConfigLoader(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** The XDG location watchdown uses when no {@code --config} is given. */
    public static Path defaultConfigPath() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdg == null || xdg.isBlank()) ? UserPaths.expand("~/.config") : Path.of(xdg);
        return base.resolve("watchdown").resolve("config.json");
    }

    /** Loads the file if it exists; returns the built-in defaults when it does not. */
    public WatchdownConfig load(Path file) {
        if (file == null || !Files.exists(file)) {
            return WatchdownConfig.defaults();
        }
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(file));
        } catch (JacksonException e) {
            throw new ConfigException(file + ": invalid JSON (" + e.getOriginalMessage() + ")", e);
        } catch (IOException e) {
            throw new ConfigException(file + ": cannot be read (" + e.getMessage() + ")", e);
        }
        if (root == null || root.isNull()) {
            return WatchdownConfig.defaults();
        }
        if (!root.isObject()) {
            throw new ConfigException(file + ": expected a JSON object at the top level");
        }
        return merge(root, file);
    }

    private WatchdownConfig merge(JsonNode root, Path file) {
        Reader reader = new Reader(file);
        WatchdownConfig defaults = WatchdownConfig.defaults();

        reader.warnUnknownKeys(root, "", Set.of("outputDir", "cacheDir", "keepAudio", "verbose",
                "captions", "ytDlp", "whisper", "ollama", "summary"));

        JsonNode ytDlp = reader.object(root, "ytDlp");
        reader.warnUnknownKeys(ytDlp, "ytDlp", Set.of("path", "extraArgs", "timeoutMinutes"));
        YtDlpConfig ytDlpDefaults = defaults.ytDlp();
        YtDlpConfig ytDlpConfig = new YtDlpConfig(
                reader.string(ytDlp, "ytDlp.path", ytDlpDefaults.path()),
                reader.stringList(ytDlp, "ytDlp.extraArgs", ytDlpDefaults.extraArgs()),
                reader.integer(ytDlp, "ytDlp.timeoutMinutes", ytDlpDefaults.timeoutMinutes()));

        JsonNode whisper = reader.object(root, "whisper");
        reader.warnUnknownKeys(whisper, "whisper", Set.of("path", "model", "language", "device", "timeoutMinutes"));
        WhisperConfig whisperDefaults = defaults.whisper();
        WhisperConfig whisperConfig = new WhisperConfig(
                reader.string(whisper, "whisper.path", whisperDefaults.path()),
                reader.string(whisper, "whisper.model", whisperDefaults.model()),
                reader.string(whisper, "whisper.language", whisperDefaults.language()),
                reader.string(whisper, "whisper.device", whisperDefaults.device()),
                reader.integer(whisper, "whisper.timeoutMinutes", whisperDefaults.timeoutMinutes()));

        JsonNode ollama = reader.object(root, "ollama");
        reader.warnUnknownKeys(ollama, "ollama", Set.of("baseUrl", "model", "temperature", "numCtx", "timeoutSeconds"));
        OllamaConfig ollamaDefaults = defaults.ollama();
        OllamaConfig ollamaConfig = new OllamaConfig(
                reader.string(ollama, "ollama.baseUrl", ollamaDefaults.baseUrl()),
                reader.string(ollama, "ollama.model", ollamaDefaults.model()),
                reader.number(ollama, "ollama.temperature", ollamaDefaults.temperature()),
                reader.integer(ollama, "ollama.numCtx", ollamaDefaults.numCtx()),
                reader.integer(ollama, "ollama.timeoutSeconds", ollamaDefaults.timeoutSeconds()));

        JsonNode summary = reader.object(root, "summary");
        reader.warnUnknownKeys(summary, "summary", Set.of("language", "chunkTokens", "maxKeyPoints"));
        SummaryConfig summaryDefaults = defaults.summary();
        SummaryConfig summaryConfig = new SummaryConfig(
                reader.string(summary, "summary.language", summaryDefaults.language()),
                reader.integer(summary, "summary.chunkTokens", summaryDefaults.chunkTokens()),
                reader.integer(summary, "summary.maxKeyPoints", summaryDefaults.maxKeyPoints()));

        return new WatchdownConfig(
                reader.path(root, "outputDir", defaults.outputDir()),
                reader.path(root, "cacheDir", defaults.cacheDir()),
                reader.bool(root, "keepAudio", defaults.keepAudio()),
                reader.bool(root, "verbose", defaults.verbose()),
                reader.captionMode(root, "captions", defaults.captions()),
                ytDlpConfig,
                whisperConfig,
                ollamaConfig,
                summaryConfig);
    }

    /** Which top-level keys the file actually set, so {@code --verbose} can show where a value came from. */
    public Set<String> keysPresentIn(Path file) {
        if (file == null || !Files.exists(file)) {
            return Set.of();
        }
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(file));
        } catch (JacksonException | IOException e) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(root, "", keys);
        return keys;
    }

    private static void collectKeys(JsonNode node, String prefix, Set<String> keys) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            keys.add(path);
            collectKeys(entry.getValue(), path, keys);
        });
    }

    /** Reads single values out of the tree, reporting type errors with their JSON path. */
    private record Reader(Path file) {

        JsonNode object(JsonNode parent, String name) {
            JsonNode node = parent.get(name);
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isObject()) {
                throw error(name, "object", node);
            }
            return node;
        }

        String string(JsonNode parent, String jsonPath, String fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isString()) {
                throw error(jsonPath, "string", node);
            }
            return node.asString();
        }

        java.nio.file.Path path(JsonNode parent, String jsonPath, java.nio.file.Path fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isString()) {
                throw error(jsonPath, "string", node);
            }
            return UserPaths.expand(node.asString());
        }

        CaptionMode captionMode(JsonNode parent, String jsonPath, CaptionMode fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isString()) {
                throw error(jsonPath, "string, one of " + CaptionMode.names(), node);
            }
            try {
                return CaptionMode.parse(node.asString());
            } catch (IllegalArgumentException e) {
                throw new ConfigException(file + ": " + jsonPath + ": " + e.getMessage());
            }
        }

        boolean bool(JsonNode parent, String jsonPath, boolean fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isBoolean()) {
                throw error(jsonPath, "boolean", node);
            }
            return node.asBoolean();
        }

        int integer(JsonNode parent, String jsonPath, int fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isIntegralNumber()) {
                throw error(jsonPath, "integer", node);
            }
            return node.asInt();
        }

        double number(JsonNode parent, String jsonPath, double fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isNumber()) {
                throw error(jsonPath, "number", node);
            }
            return node.asDouble();
        }

        List<String> stringList(JsonNode parent, String jsonPath, List<String> fallback) {
            JsonNode node = valueAt(parent, jsonPath);
            if (node == null) {
                return fallback;
            }
            if (!node.isArray()) {
                throw error(jsonPath, "array of strings", node);
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isString()) {
                    throw error(jsonPath, "array of strings", item);
                }
                values.add(item.asString());
            }
            return List.copyOf(values);
        }

        private JsonNode valueAt(JsonNode parent, String jsonPath) {
            if (parent == null) {
                return null;
            }
            String name = jsonPath.substring(jsonPath.lastIndexOf('.') + 1);
            JsonNode node = parent.get(name);
            return (node == null || node.isNull()) ? null : node;
        }

        void warnUnknownKeys(JsonNode node, String prefix, Set<String> known) {
            if (node == null) {
                return;
            }
            for (String name : node.propertyNames()) {
                if (!known.contains(name)) {
                    String path = prefix.isEmpty() ? name : prefix + "." + name;
                    log.warn("{}: unknown key '{}' ignored", file, path);
                }
            }
        }

        private ConfigException error(String jsonPath, String expected, JsonNode actual) {
            return new ConfigException(file + ": " + jsonPath + ": expected " + expected
                    + ", found " + describe(actual));
        }

        private static String describe(JsonNode node) {
            return switch (node.getNodeType()) {
                case ARRAY -> "array";
                case BINARY -> "binary";
                case BOOLEAN -> "boolean";
                case MISSING, NULL -> "null";
                case NUMBER -> "number";
                case OBJECT, POJO -> "object";
                case STRING -> "string";
            };
        }
    }
}
