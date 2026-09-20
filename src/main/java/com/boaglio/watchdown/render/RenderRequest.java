package com.boaglio.watchdown.render;

import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.summarize.Summary;
import com.boaglio.watchdown.transcribe.Transcript;

/**
 * Everything the renderer needs for one video. {@code summary} is null when summarization failed,
 * in which case {@code summaryFailure} says why and the folder is still written.
 *
 * <p>{@code transcriptSource} is how the transcript was produced, for example
 * {@code whisper small, lang=auto} or {@code captions en}.
 */
public record RenderRequest(
        VideoMetadata metadata,
        Transcript transcript,
        Summary summary,
        String summaryFailure,
        String transcriptSource,
        String ollamaModel,
        String toolVersion) {

    public boolean hasSummary() {
        return summary != null;
    }
}
