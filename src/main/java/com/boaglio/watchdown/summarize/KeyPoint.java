package com.boaglio.watchdown.summarize;

import tools.jackson.databind.annotation.JsonDeserialize;

/** One takeaway, with the moment in the video it came from. */
public record KeyPoint(String text, @JsonDeserialize(using = Seconds.class) double timestamp) {
}
