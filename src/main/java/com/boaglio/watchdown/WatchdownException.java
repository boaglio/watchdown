package com.boaglio.watchdown;

/**
 * Base class for every failure watchdown reports to the user. Each subclass carries the
 * process exit code the CLI must return, so the command never has to guess.
 */
public abstract class WatchdownException extends RuntimeException {

    private final int exitCode;

    protected WatchdownException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    protected WatchdownException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
