package com.boaglio.watchdown.transcribe;

import com.boaglio.watchdown.WatchdownException;
import com.boaglio.watchdown.cli.ExitCode;

/** Whisper failed, or its output could not be read. */
public class TranscriptionException extends WatchdownException {

    public TranscriptionException(String message) {
        super(ExitCode.TRANSCRIPTION_FAILED, message);
    }

    public TranscriptionException(String message, Throwable cause) {
        super(ExitCode.TRANSCRIPTION_FAILED, message, cause);
    }
}
