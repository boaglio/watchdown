package com.boaglio.watchdown.cli;

import com.boaglio.watchdown.WatchdownException;

/** Exit codes defined by AGENTS.md section 4. */
public final class ExitCode {

    public static final int OK = 0;
    public static final int UNEXPECTED_ERROR = 1;
    public static final int USAGE_ERROR = 2;
    public static final int MISSING_DEPENDENCY = 3;
    public static final int DOWNLOAD_FAILED = 4;
    public static final int TRANSCRIPTION_FAILED = 5;
    public static final int SUMMARIZATION_FAILED = 6;

    private ExitCode() {
    }

    /** Maps an exception to its exit code; anything unknown is an unexpected error. */
    public static int of(Throwable throwable) {
        return throwable instanceof WatchdownException watchdown ? watchdown.exitCode() : UNEXPECTED_ERROR;
    }

    /** The exit code of a whole run is the highest code any URL produced. */
    public static int worst(int first, int second) {
        return Math.max(first, second);
    }
}
