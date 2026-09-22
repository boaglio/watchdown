package com.boaglio.watchdown.summarize;

import com.boaglio.watchdown.Progress;
import com.boaglio.watchdown.config.SummaryConfig;
import com.boaglio.watchdown.download.VideoMetadata;
import com.boaglio.watchdown.render.Timestamps;
import com.boaglio.watchdown.transcribe.Segment;
import com.boaglio.watchdown.transcribe.Transcript;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;

/**
 * Map-reduce summarizer on top of a local Ollama model.
 *
 * <p>Long videos are split into chunks (one per chapter, or by token count), each chunk is
 * summarized on its own, and the chunk summaries are combined into the final result. A transcript
 * that fits in one chunk skips the map step. The model only ever sees the transcript: the prompts
 * forbid outside facts and timestamps that are not in the text, and anything outside
 * {@code [0, duration]} is dropped afterwards.
 *
 * <p>A small model will often answer with a TL;DR and key points and no sections at all, which
 * leaves a summary with nothing to read. Rather than accept that, the sections are chased: up to
 * {@code summary.sectionAttempts} tries, each asking a smaller question than the last — first the
 * whole summary again with the omission named, then the sections on their own, then the same thing
 * with the number asked for coming down. The TL;DR and key points from the first good answer are
 * kept throughout, so a later attempt can only add sections, never take anything away.
 *
 * <p>When the model still will not write any, watchdown writes them itself from the parts of the
 * video it already summarized in the map step, which is what a section is anyway. Only a video
 * short enough to have skipped that step pays for anything extra. A summary with no sections at
 * all is now close to impossible, but if it does happen the summary is still returned: no sections
 * beats no summary.
 */
public class OllamaSummarizer implements Summarizer {

    private static final Logger log = LoggerFactory.getLogger(OllamaSummarizer.class);
    private static final String STRICTER_REMINDER = """
            Your previous answer could not be parsed. Answer again with the requested JSON object \
            only: no prose before or after it, no Markdown code fence, and no comments. Every \
            timestamp must be a plain whole number of seconds, so the moment 02:05 is written as \
            125, not as 02:05 and not as "2:05".""";
    private static final String SECTIONS_REMINDER = """
            Your previous answer had no usable "sections" array. Answer again with the same JSON \
            object, and this time fill in "sections": the video in order, one entry per part, each \
            with a title, a "start" that is a plain whole number of seconds taken from the \
            material, and a one-sentence summary. Every video has parts, however short it is.""";
    /** How many sections attempt 3 asks for; each attempt after it asks for one fewer, down to one. */
    private static final int FIRST_MINIMUM_SECTIONS = 3;
    /** How many parts a one-chunk transcript is cut into when the sections have to be built here. */
    private static final int FALLBACK_PARTS = 3;

    private final ChatClient chatClient;
    private final Resource chunkPrompt;
    private final Resource finalPrompt;
    private final Resource sectionsPrompt;
    private final SummaryConfig config;

    public OllamaSummarizer(ChatClient chatClient, Resource chunkPrompt, Resource finalPrompt,
            Resource sectionsPrompt, SummaryConfig config) {
        this.chatClient = chatClient;
        this.chunkPrompt = chunkPrompt;
        this.finalPrompt = finalPrompt;
        this.sectionsPrompt = sectionsPrompt;
        this.config = config;
    }

    @Override
    public Summary summarize(VideoMetadata metadata, Transcript transcript, Progress progress) {
        if (transcript.isEmpty()) {
            throw new SummarizationException("the transcript is empty, there is nothing to summarize");
        }
        String language = config.followsVideoLanguage() ? transcript.language() : config.language();
        List<Chunk> chunks = new Chunker(config.chunkTokens()).split(transcript, metadata.chapters());
        log.debug("summarizing {} chunk(s) in {}", chunks.size(), language);

        // The reduce call is roughly one chunk's worth of work, so count it as one more step.
        int steps = chunks.size() > 1 ? chunks.size() + 1 : 1;
        progress.fraction(0);

        List<String> chunkSummaries = new ArrayList<>();
        if (chunks.size() > 1) {
            for (int index = 0; index < chunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                progress.note("%d/%d".formatted(index + 1, chunks.size()));
                log.debug("chunk {}/{}: {} segments, ~{} tokens, {}–{}",
                        index + 1, chunks.size(), chunk.segments().size(), chunk.approximateTokens(),
                        Timestamps.format(chunk.start()), Timestamps.format(chunk.end()));
                long startedAt = System.nanoTime();
                chunkSummaries.add(summarizeChunk(chunk, metadata, language));
                log.debug("chunk {}/{} done in {}ms", index + 1, chunks.size(),
                        (System.nanoTime() - startedAt) / 1_000_000);
                progress.fraction((double) (index + 1) / steps);
            }
            progress.note("combining");
        }

        Summary summary = reduce(metadata, transcript, chunks, chunkSummaries, language, progress);
        progress.fraction(1);
        progress.note("");
        return summary;
    }

    private String summarizeChunk(Chunk chunk, VideoMetadata metadata, String language) {
        String prompt = render(chunkPrompt, Map.of(
                "title", metadata.title(),
                "chapter", chunk.title() == null ? "" : chunk.title(),
                "language", language,
                "transcript", asTimestampedText(chunk.segments(), metadata.durationSeconds())));
        try {
            String answer = chatClient.prompt().user(prompt).call().content();
            return answer == null ? "" : answer.strip();
        } catch (RuntimeException e) {
            throw new SummarizationException("the model failed while summarizing a chunk: " + e.getMessage(), e);
        }
    }

    /** The final answer, validated, with the sections chased if the first attempt came back without. */
    private Summary reduce(VideoMetadata metadata, Transcript transcript, List<Chunk> chunks,
            List<String> chunkSummaries, String language, Progress progress) {
        String body = chunkSummaries.isEmpty()
                ? asTimestampedText(transcript.segments(), metadata.durationSeconds())
                : joinChunkSummaries(chunks, chunkSummaries, metadata.durationSeconds());

        String prompt = render(finalPrompt, Map.of(
                "title", metadata.title(),
                "channel", metadata.channel(),
                "duration", Timestamps.duration(metadata.durationSeconds()),
                "language", language,
                "maxKeyPoints", String.valueOf(config.maxKeyPoints()),
                "material", body));

        Summary summary = validate(askWithParseRetry(prompt), metadata);
        if (!summary.sections().isEmpty()) {
            return summary;
        }
        if (config.sectionAttempts() > 1) {
            summary = chaseSections(summary, metadata, body, language, prompt, progress);
            if (!summary.sections().isEmpty()) {
                return summary;
            }
        }
        List<Section> parts = sectionsFromParts(metadata, transcript, chunks, chunkSummaries, language, progress);
        return parts.isEmpty() ? summary : new Summary(summary.tldr(), summary.keyPoints(), parts);
    }

    /**
     * The sections we can build ourselves when the model will not write any.
     *
     * <p>The map step already summarized the video part by part, and a part is exactly what a
     * section is: a title, the moment it starts, and what it says. Those summaries are free — they
     * were paid for on the way here — and their timestamps are ours, so nothing in them can be
     * invented. A transcript short enough to skip the map step has no such summaries, so its parts
     * are summarized now; that costs a few calls, but only for a video that fits in one chunk.
     */
    private List<Section> sectionsFromParts(VideoMetadata metadata, Transcript transcript, List<Chunk> chunks,
            List<String> chunkSummaries, String language, Progress progress) {
        if (!chunkSummaries.isEmpty()) {
            log.debug("building the sections from the {} chunk summaries", chunkSummaries.size());
            return zip(chunks, chunkSummaries, metadata);
        }
        if (config.sectionAttempts() <= 1) {
            // "Ask once and take what comes back" covers the summary itself; it also means we do
            // not go off and spend more calls on sections the user did not insist on.
            return List.of();
        }

        List<Chunk> parts = new Chunker(partTokensFor(transcript)).split(transcript, List.of());
        log.debug("no sections from the model; summarizing the video in {} part(s) instead", parts.size());
        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            progress.note("sections %d/%d".formatted(index + 1, parts.size()));
            try {
                summaries.add(summarizeChunk(parts.get(index), metadata, language));
            } catch (RuntimeException e) {
                log.debug("summarizing part {} for the sections failed: {}", index + 1, summarize(e));
                return List.of();
            }
        }
        progress.note("");
        return zip(parts, summaries, metadata);
    }

    /** Splits what fits in one chunk into {@link #FALLBACK_PARTS} parts of roughly equal length. */
    private static int partTokensFor(Transcript transcript) {
        int tokens = transcript.segments().stream().mapToInt(Segment::approximateTokens).sum();
        return Math.max(1, (int) Math.ceil((double) tokens / FALLBACK_PARTS));
    }

    /** One section per part: the chapter's name or "Part N", the part's own start, and its summary. */
    private static List<Section> zip(List<Chunk> parts, List<String> summaries, VideoMetadata metadata) {
        List<Section> sections = new ArrayList<>();
        for (int index = 0; index < summaries.size() && index < parts.size(); index++) {
            Chunk part = parts.get(index);
            String prose = leadingProse(summaries.get(index));
            if (!hasText(prose)) {
                // A heading with nothing under it is worse than no section at all.
                continue;
            }
            String title = hasText(part.title()) ? part.title() : "Part " + (index + 1);
            sections.add(new Section(title, part.start(), prose));
        }
        return usableSections(sections, metadata);
    }

    /**
     * The prose a chunk summary opens with, which is what a section wants. The bullet list that
     * follows it belongs to the key points, and its bracketed timestamps would only be escaped
     * into noise in a section paragraph.
     */
    static String leadingProse(String chunkSummary) {
        if (chunkSummary == null) {
            return "";
        }
        StringBuilder prose = new StringBuilder();
        for (String line : chunkSummary.strip().lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("#")) {
                break;
            }
            if (!trimmed.isEmpty()) {
                prose.append(prose.isEmpty() ? "" : " ").append(trimmed);
            }
        }
        return prose.toString();
    }

    /** The first ask, and the one parse-failure retry of AGENTS.md section 6.3. */
    private Summary askWithParseRetry(String prompt) {
        try {
            return ask(prompt);
        } catch (RuntimeException first) {
            log.debug("the model's answer could not be parsed ({}), retrying once with a stricter reminder",
                    summarize(first));
            try {
                // Telling the model what was wrong with the last answer beats a generic scolding.
                return ask(prompt + "\n\n" + STRICTER_REMINDER
                        + "\n\nThe error from the last answer was: " + summarize(first));
            } catch (RuntimeException second) {
                throw new SummarizationException(
                        "the model did not return a usable summary after one retry: " + summarize(second), second);
            }
        }
    }

    /**
     * Asks again for the sections the model left out, each attempt a smaller question than the
     * last: the whole summary once more with the omission named, then the sections on their own,
     * then the same with the number asked for coming down to one.
     *
     * <p>Only the sections are taken from these answers. The title, TL;DR and key points stay as
     * the first good answer wrote them, so a later attempt can add and never subtract, and a
     * failure at any point simply leaves {@code best} as it was.
     */
    private Summary chaseSections(Summary best, VideoMetadata metadata, String material, String language,
            String finalAsk, Progress progress) {
        int attempts = config.sectionAttempts();
        for (int attempt = 2; attempt <= attempts; attempt++) {
            progress.note("sections %d/%d".formatted(attempt, attempts));
            long startedAt = System.nanoTime();
            List<Section> sections = attempt == 2
                    ? sectionsFromWholeSummary(finalAsk, metadata)
                    : sectionsOnTheirOwn(best, metadata, material, language, minimumSectionsFor(attempt));
            log.debug("sections attempt {}/{}: {} usable in {}ms", attempt, attempts, sections.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            if (!sections.isEmpty()) {
                return new Summary(best.tldr(), best.keyPoints(), sections);
            }
        }
        log.debug("no sections after {} attempt(s); keeping the summary without them", attempts);
        progress.note("");
        return best;
    }

    /** Attempt 2: the same question with the omission named. A model often just forgot. */
    private List<Section> sectionsFromWholeSummary(String finalAsk, VideoMetadata metadata) {
        try {
            return usableSections(ask(finalAsk + "\n\n" + SECTIONS_REMINDER).sections(), metadata);
        } catch (RuntimeException e) {
            log.debug("asking for the whole summary again failed: {}", summarize(e));
            return List.of();
        }
    }

    /** Attempt 3 and after: sections and nothing else, which is a much smaller thing to generate. */
    private List<Section> sectionsOnTheirOwn(Summary best, VideoMetadata metadata, String material,
            String language, int minimum) {
        String prompt = render(sectionsPrompt, Map.of(
                "title", metadata.title(),
                "duration", Timestamps.duration(metadata.durationSeconds()),
                "tldr", best.tldr(),
                "minSections", String.valueOf(minimum),
                "language", language,
                "material", material));
        try {
            Sections answer = chatClient.prompt().user(prompt).call().entity(SummaryConverter.sections());
            return answer == null ? List.of() : usableSections(answer.sections(), metadata);
        } catch (RuntimeException e) {
            log.debug("asking for the sections alone failed: {}", summarize(e));
            return List.of();
        }
    }

    /** Three on the first sections-only attempt, then one fewer each time, never below one. */
    private static int minimumSectionsFor(int attempt) {
        return Math.max(1, FIRST_MINIMUM_SECTIONS - (attempt - 3));
    }

    private Summary ask(String prompt) {
        Summary summary = chatClient.prompt().user(prompt).call().entity(SummaryConverter.create());
        if (summary == null || summary.tldr() == null || summary.tldr().isBlank()) {
            throw new IllegalStateException("the answer has no TL;DR");
        }
        return summary;
    }

    /** Drops anything the model invented outside the video, as required by AGENTS.md section 6.3. */
    private Summary validate(Summary summary, VideoMetadata metadata) {
        double duration = metadata.durationSeconds();
        List<KeyPoint> keyPoints = summary.keyPoints().stream()
                .filter(point -> hasText(point.text()))
                .filter(point -> inRange(point.timestamp(), duration))
                .limit(config.maxKeyPoints())
                .toList();
        List<Section> sections = usableSections(summary.sections(), metadata);

        int droppedPoints = summary.keyPoints().size() - keyPoints.size();
        int droppedSections = summary.sections().size() - sections.size();
        if (droppedPoints > 0 || droppedSections > 0) {
            log.debug("dropped {} key point(s) and {} section(s): no text, or a moment that is "
                    + "missing or outside the video", droppedPoints, droppedSections);
        }

        return new Summary(summary.tldr().strip(), keyPoints, sections);
    }

    /** The sections worth keeping: some text, a moment inside the video, and in order. */
    private static List<Section> usableSections(List<Section> sections, VideoMetadata metadata) {
        double duration = metadata.durationSeconds();
        return sections.stream()
                .filter(section -> hasText(section.title()) || hasText(section.summary()))
                .filter(section -> inRange(section.start(), duration))
                .sorted((left, right) -> Double.compare(left.start(), right.start()))
                .toList();
    }

    /** NaN fails this, which is how a moment the model never gave us gets dropped. */
    private static boolean inRange(double seconds, double duration) {
        return seconds >= 0 && (duration <= 0 || seconds <= duration);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String joinChunkSummaries(List<Chunk> chunks, List<String> summaries, int duration) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < summaries.size(); index++) {
            Chunk chunk = chunks.get(index);
            joined.append("## Part ").append(index + 1)
                    .append(" (").append(Timestamps.format(chunk.start(), duration))
                    .append(" - ").append(Timestamps.format(chunk.end(), duration)).append(')');
            if (chunk.title() != null && !chunk.title().isBlank()) {
                joined.append(" - ").append(chunk.title());
            }
            joined.append('\n').append(summaries.get(index)).append("\n\n");
        }
        return joined.toString().strip();
    }

    static String asTimestampedText(List<Segment> segments, int duration) {
        StringBuilder text = new StringBuilder();
        for (Segment segment : segments) {
            text.append('[').append(Timestamps.format(segment.start(), duration)).append("] ")
                    .append(segment.text()).append('\n');
        }
        return text.toString().strip();
    }

    /** The first line of a parser error, which is the part that says what to fix. */
    private static String summarize(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return (newline < 0 ? message : message.substring(0, newline)).strip();
    }

    private static String render(Resource template, Map<String, Object> variables) {
        return PromptTemplate.builder().resource(template).build().render(variables);
    }
}
