package com.boaglio.watchdown.summarize;

/** One section of the video, with the moment it starts and a short summary of it. */
public record Section(String title, double start, String summary) {
}
