package com.boaglio.watchdown.pipeline;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.cli.ExitCode;

/** An external tool is missing, Ollama is unreachable, or the model is not pulled. */
public class DependencyException extends WatchdownException {

    public DependencyException(String message) {
        super(ExitCode.MISSING_DEPENDENCY, message);
    }

    public DependencyException(String message, Throwable cause) {
        super(ExitCode.MISSING_DEPENDENCY, message, cause);
    }
}
