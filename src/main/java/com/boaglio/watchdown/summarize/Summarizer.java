package com.boaglio.watchdown.summarize;

import com.boaglio.watchdown.Progress;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.transcribe.Transcript;

/** Turns a transcript into a {@link Summary}. */
public interface Summarizer {

    Summary summarize(VideoMetadata metadata, Transcript transcript, Progress progress);

    default Summary summarize(VideoMetadata metadata, Transcript transcript) {
        return summarize(metadata, transcript, Progress.NONE);
    }
}
