package com.boaglio.watchdown.summarize;

import java.util.List;

/**
 * The answer to the sections-only question (AGENTS.md section 6.3).
 *
 * <p>When a model returns a summary with no sections in it, watchdown asks again for the sections
 * alone. A smaller question is a much easier one for a 4B model than the whole summary object, and
 * a wrapper record keeps the answer a JSON object, which is what the structured-output converter
 * and the models themselves are happiest with.
 */
public record Sections(List<Section> sections) {

    public Sections {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
