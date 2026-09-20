package com.boaglio.watchdown.cli;

import com.boaglio.watchdown.WatchdownException;

/** A bad flag, a bad URL, or a bad configuration file. */
public class UsageException extends WatchdownException {

    public UsageException(String message) {
        super(ExitCode.USAGE_ERROR, message);
    }

    public UsageException(String message, Throwable cause) {
        super(ExitCode.USAGE_ERROR, message, cause);
    }
}
