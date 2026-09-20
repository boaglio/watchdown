package com.boaglio.watchdown.process;

/** An external command could not be started, timed out, or was interrupted. */
public class ProcessException extends RuntimeException {

    public ProcessException(String message) {
        super(message);
    }

    public ProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
