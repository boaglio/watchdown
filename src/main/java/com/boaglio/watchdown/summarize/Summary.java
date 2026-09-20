package com.boaglio.watchdown.summarize;

import java.util.List;

/** The final summary, as the model returns it and as the renderer consumes it. */
public record Summary(String title, String tldr, List<KeyPoint> keyPoints, List<Section> sections) {

    public Summary {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
