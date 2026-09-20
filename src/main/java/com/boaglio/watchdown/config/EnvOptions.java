package com.boaglio.watchdown.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * Configuration taken from the environment, which sits between the command line and the config
 * file: a flag is what you meant this time, the environment is how this shell is set up, and the
 * file is your standing preference.
 *
 * <p>{@code WATCHDOWN_ROOT} is where the output folders are written. {@code ~} and {@code $HOME}
 * are expanded in it, as they are in the config file.
 */
public record EnvOptions(Path outputDir) {

    public static final String OUTPUT_DIR = "WATCHDOWN_ROOT";

    public static EnvOptions none() {
        return new EnvOptions(null);
    }

    public static EnvOptions from(Map<String, String> environment) {
        String root = environment.get(OUTPUT_DIR);
        return new EnvOptions(root == null || root.isBlank() ? null : UserPaths.expand(root.strip()));
    }

    public static EnvOptions fromSystem() {
        return from(System.getenv());
    }
}
