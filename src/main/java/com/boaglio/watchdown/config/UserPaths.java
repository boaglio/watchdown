package com.boaglio.watchdown.config;

import java.nio.file.Path;

/** Path values in the config file may start with {@code ~} or {@code $HOME}. */
public final class UserPaths {

    private UserPaths() {
    }

    public static Path expand(String value) {
        String home = System.getProperty("user.home");
        String expanded = value;
        if (expanded.equals("~") || expanded.startsWith("~/")) {
            expanded = home + expanded.substring(1);
        } else if (expanded.equals("$HOME") || expanded.startsWith("$HOME/")) {
            expanded = home + expanded.substring("$HOME".length());
        } else if (expanded.startsWith("${HOME}")) {
            expanded = home + expanded.substring("${HOME}".length());
        }
        return Path.of(expanded);
    }
}
