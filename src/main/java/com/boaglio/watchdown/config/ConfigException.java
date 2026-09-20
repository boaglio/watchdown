package com.boaglio.watchdown.config;

import com.boaglio.watchdown.cli.UsageException;

/** Invalid JSON or a value of the wrong type in the config file. */
public class ConfigException extends UsageException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
