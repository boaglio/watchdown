package com.boaglio.watchdown.summarize;

/** One takeaway, with the moment in the video it came from. */
public record KeyPoint(String text, double timestamp) {
}
