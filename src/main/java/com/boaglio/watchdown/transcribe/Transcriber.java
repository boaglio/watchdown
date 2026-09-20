package com.boaglio.watchdown.transcribe;

import com.boaglio.watchdown.Progress;
import java.nio.file.Path;

/**
 * Turns an audio file into a {@link Transcript}. Nothing here mentions Whisper, so a whisper.cpp
 * implementation can be added later without touching the pipeline.
 */
public interface Transcriber {

    /**
     * @param audio the audio file to transcribe
     * @param workDirectory where the transcriber may leave its own output
     * @param language a language code, or {@code auto} to let the transcriber detect it
     * @param durationSeconds how long the recording is, so progress can be a fraction; 0 if unknown
     * @param progress where to report how far along the transcription is
     */
    Transcript transcribe(Path audio, Path workDirectory, String language, int durationSeconds,
            Progress progress);

    default Transcript transcribe(Path audio, Path workDirectory, String language) {
        return transcribe(audio, workDirectory, language, 0, Progress.NONE);
    }

    /** Reads a transcript the transcriber produced earlier, so a rerun can skip the slow step. */
    Transcript readCached(Path cachedOutput);
}
