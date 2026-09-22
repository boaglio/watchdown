package com.boaglio.watchdown.summarize;

import java.util.List;

/**
 * The final summary, as the model returns it and as the renderer consumes it.
 *
 * <p>There is no title here on purpose. The video already has one, from the video itself, and a
 * small model asked to write another one will sometimes write a wrong one: a video about beef
 * came back titled "chicken". The renderer uses the real title, and the model is only asked for
 * what the transcript alone can answer.
 */
public record Summary(String tldr, List<KeyPoint> keyPoints, List<Section> sections) {

    public Summary {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
