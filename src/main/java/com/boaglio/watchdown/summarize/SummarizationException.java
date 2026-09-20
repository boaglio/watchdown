package com.boaglio.watchdown.summarize;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.cli.ExitCode;

/** The model failed or returned something that is not a usable summary. */
public class SummarizationException extends WatchdownException {

    public SummarizationException(String message) {
        super(ExitCode.SUMMARIZATION_FAILED, message);
    }

    public SummarizationException(String message, Throwable cause) {
        super(ExitCode.SUMMARIZATION_FAILED, message, cause);
    }
}
