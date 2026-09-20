package com.boaglio.watchdown.summarize;

import tools.jackson.databind.annotation.JsonDeserialize;

/** One section of the video, with the moment it starts and a short summary of it. */
public record Section(String title, @JsonDeserialize(using = Seconds.class) double start, String summary) {
}
