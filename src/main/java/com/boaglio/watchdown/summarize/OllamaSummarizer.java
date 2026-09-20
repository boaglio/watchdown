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
 * <p>A small model will often answer with a title, a TL;DR and key points and no sections at all,
 * which leaves a summary with nothing to read. Rather than accept that, the sections are chased:
 * up to {@code summary.sectionAttempts} tries, each asking a smaller question than the last —
 * first the whole summary again with the omission named, then the sections on their own, then the
 * same thing with the number asked for coming down. The TL;DR and key points from the first good
 * answer are kept throughout, so a later attempt can only add sections, never take anything away.
 * If every attempt comes back empty the summary is still returned: no sections beats no summary.
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
        if (!summary.sections().isEmpty() || config.sectionAttempts() <= 1) {
            return summary;
        }
        return chaseSections(summary, metadata, body, language, prompt, progress);
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
                return new Summary(best.title(), best.tldr(), best.keyPoints(), sections);
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

        String title = summary.title() == null || summary.title().isBlank() ? metadata.title() : summary.title();
        return new Summary(title, summary.tldr().strip(), keyPoints, sections);
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
