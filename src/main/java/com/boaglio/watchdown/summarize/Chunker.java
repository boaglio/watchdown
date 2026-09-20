package com.boaglio.watchdown.summarize;

import com.boaglio.watchdown.download.Chapter;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a transcript into the chunks the map step summarizes.
 *
 * <p>Chapters win when the video has them; otherwise segments are grouped until they reach about
 * {@code chunkTokens} tokens, estimated as {@code chars / 4}. A segment is never split in half.
 */
public class Chunker {

    private final int chunkTokens;

    public Chunker(int chunkTokens) {
        if (chunkTokens <= 0) {
            throw new IllegalArgumentException("chunkTokens must be positive, got " + chunkTokens);
        }
        this.chunkTokens = chunkTokens;
    }

    public List<Chunk> split(Transcript transcript, List<Chapter> chapters) {
        if (transcript.isEmpty()) {
            return List.of();
        }
        return chapters == null || chapters.isEmpty()
                ? splitByTokens(transcript.segments())
                : splitByChapters(transcript.segments(), chapters);
    }

    private List<Chunk> splitByChapters(List<Segment> segments, List<Chapter> chapters) {
        List<Chunk> chunks = new ArrayList<>();
        List<Segment> leftovers = new ArrayList<>();

        for (Segment segment : segments) {
            Chapter chapter = chapterOf(segment, chapters);
            if (chapter == null) {
                leftovers.add(segment);
                continue;
            }
            // Speech that belongs to no chapter is kept as its own chunk, in the order it was said.
            if (!leftovers.isEmpty()) {
                chunks.addAll(splitByTokens(List.copyOf(leftovers)));
                leftovers.clear();
            }
            if (!chunks.isEmpty() && chapter.title().equals(chunks.getLast().title())) {
                List<Segment> merged = new ArrayList<>(chunks.getLast().segments());
                merged.add(segment);
                chunks.set(chunks.size() - 1, new Chunk(chapter.title(), merged));
            } else {
                chunks.add(new Chunk(chapter.title(), List.of(segment)));
            }
        }
        if (!leftovers.isEmpty()) {
            chunks.addAll(splitByTokens(List.copyOf(leftovers)));
        }
        return List.copyOf(chunks);
    }

    private static Chapter chapterOf(Segment segment, List<Chapter> chapters) {
        return chapters.stream()
                .filter(chapter -> segment.start() >= chapter.start() && segment.start() < chapter.end())
                .findFirst()
                .orElse(null);
    }

    private List<Chunk> splitByTokens(List<Segment> segments) {
        List<Chunk> chunks = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        int tokens = 0;
        for (Segment segment : segments) {
            int segmentTokens = segment.approximateTokens();
            if (!current.isEmpty() && tokens + segmentTokens > chunkTokens) {
                chunks.add(new Chunk(null, current));
                current = new ArrayList<>();
                tokens = 0;
            }
            current.add(segment);
            tokens += segmentTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(new Chunk(null, current));
        }
        return List.copyOf(chunks);
    }
}
