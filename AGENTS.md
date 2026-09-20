# AGENTS.md — watchdown

Guidance for AI coding agents working in the **watchdown** repository.

**watchdown** is a command-line tool that takes a YouTube URL, downloads the
audio, transcribes it locally with **Whisper**, summarizes it locally with a
model served by **Ollama** (through **Spring AI**), and writes the result as a
folder of Markdown files that follows the **AGENTS.md** convention, so other
coding agents can use the video as context.

```
watchdown https://youtu.be/dQw4w9WgXcQ
  → yt-dlp (audio + metadata) → whisper (transcript) → Ollama (summary) → Markdown
```

Source: https://github.com/boaglio/watchdown

Treat this file as the contract for the project. If you change a rule here,
update the code and tests in the same commit.

---

## 1. Scope

### Goals (v1)

- One or more YouTube video URLs in, one output folder per video out.
- Local audio or video files too, through `--file` (§6.6).
- Everything runs **locally**: no cloud APIs and no API keys.
- Transcripts include timestamps, and every summary point links back to the
  exact moment in the video.
- The transcript comes from the captions the creator uploaded when the video
  has them, and from Whisper otherwise (`--captions`, §6.2).
- Output is plain Markdown that an agent can use without extra tools.
- Scripting-friendly: logs go to **stderr**, and output folder paths go to **stdout**.

### Non-goals (v1)

- Playlists and channels (reject them with a clear message).
- Any GUI, web server, or REST API. The app is **non-web**.
- Speaker diarization.

---

## 2. Tech stack

- **Java 25**
- **Spring Boot 4.1.x** (`spring-boot-starter-parent`), with
  `spring.main.web-application-type=none` and the banner off
- **Spring AI 2.0.x** (`spring-ai-bom`, `spring-ai-starter-model-ollama`),
  called through `ChatClient`
- **picocli** (`picocli-spring-boot-starter`) for argument parsing, help and
  version output
- **Jackson** for the JSON config file and Whisper's JSON output
- **Maven**, always through the wrapper (`./mvnw`)
- **JUnit 5**, **AssertJ**, **Mockito** for tests

External tools. They are not bundled, and the app calls them as processes:

| Tool      | Purpose                              | Check                   |
|-----------|--------------------------------------|-------------------------|
| `yt-dlp`  | metadata + audio download            | `yt-dlp --version`      |
| `ffmpeg`  | audio extraction, and `ffprobe` for the length of a `--file` input | `ffmpeg -version` |
| `whisper` | transcription (openai-whisper CLI); not needed with `--captions only` | `whisper --help` |
| `ollama`  | local LLM server (HTTP, default `http://localhost:11434`) | `ollama list` |

---

## 3. Build, run, test

The repository starts empty. If `mvnw` is missing, create the wrapper
first with `mvn -N wrapper:wrapper`, then use `./mvnw` from then on.

```bash
./mvnw test                      # unit tests only; no network, no external tools
./mvnw verify -Pintegration      # + *IT tests; needs yt-dlp, whisper, ollama running
./mvnw clean package             # → target/watchdown.jar (executable)

java -jar target/watchdown.jar --help
java -jar target/watchdown.jar -v https://www.youtube.com/watch?v=VIDEO_ID
```

Ship a `bin/watchdown` launcher. It resolves its own directory through
symlinks, so it works when linked onto the PATH; builds
`target/watchdown.jar` with `./mvnw` the first time it is needed; and then
`exec`s the jar so arguments and exit codes pass straight through. Build
progress goes to stderr, because stdout is reserved for the output folder
paths. `WATCHDOWN_JAR` overrides the jar, and `JAVA_OPTS` is passed to the
JVM.

Run `./mvnw test` before you finish any change.

---

## 4. Command-line interface

```
Usage: watchdown [OPTIONS] [<url>...]

  <url>...                 Zero or more YouTube video URLs
                           (youtube.com/watch?v=, youtu.be/, youtube.com/shorts/)

  -F, --file <path>        A local audio or video file instead of a URL.
                           Repeatable, and it can be mixed with URLs.

  -o, --output <dir>       Output root directory          (config: outputDir)
  -c, --config <file>      Config file to use             (default: see §5)
  -m, --model <name>       Ollama model for summaries      (config: ollama.model)
  -w, --whisper-model <n>  Whisper model (tiny…large)      (config: whisper.model)
  -l, --language <code>    Spoken language, e.g. en, pt; 'auto' to detect
                                                           (config: whisper.language)
  -s, --summary-language <code>
                           Language of the summary; 'auto' = same as video
                                                           (config: summary.language)
      --captions <mode>    auto: the creator's captions when the video has them,
                           else whisper; never: always whisper; only: captions
                           or nothing                       (config: captions)
  -v, --verbose            Detailed progress, commands, timings (config: verbose)
  -f, --force              Ignore cached audio/transcript, redo every step
      --keep-audio         Keep the downloaded audio file in the output folder
      --check              Verify external tools + Ollama model, then exit
      --init-config        Write a default config file to the default path, then exit
  -h, --help               Show help
  -V, --version            Show version
```

### Precedence

**CLI flag > config file > built-in default.** Resolve all three layers
into one immutable `WatchdownConfig` record **once, right after
parsing**. No other code reads flags or the file directly.

### Verbose mode (`-v`)

Default (quiet) output is one line per step on stderr:

```
[1/4] Downloading   "Video title" (12:34) ............ done  4.1s
[2/4] Transcribing  whisper small, lang=en ........... done  48.2s
[3/4] Summarizing   gemma3:4b, 3 chunks .............. done  22.7s
[4/4] Writing       ./watchdown-out/video-title-dQw4w9WgXcQ
```

With `--verbose`, also show:

- the resolved config (where each value came from: flag, file, or default)
- every external command line, exactly as run, and its exit code
- the external tool's stdout/stderr, streamed live with a `  │ ` prefix
- transcript stats (segments, words, detected language)
- how the summary was split into chunks: count, approximate tokens per chunk,
  and the time taken per Ollama call
- cache hits and misses

Turn it on by raising the `com.boaglio.watchdown` logger to `DEBUG` at
runtime through Spring Boot's `LoggingSystem`. Spring and library logs stay
at `WARN` even in verbose mode. Never use `System.out` for logs. stdout is
reserved for the final output paths, one per line.

### Exit codes

| Code | Meaning                                              |
|------|------------------------------------------------------|
| 0    | All URLs processed                                   |
| 1    | Unexpected error                                     |
| 2    | Usage or config error (bad flag, bad JSON, bad URL)  |
| 3    | Missing dependency (tool not on PATH, Ollama unreachable, model not pulled) |
| 4    | Download failed                                      |
| 5    | Transcription failed                                 |
| 6    | Summarization failed (the transcript is still written) |

If you pass several URLs or files, one failure doesn't stop the others. The
exit code is the highest code any job produced.

Only the dependencies a run actually uses are required: a run of URLs with
`--captions only` needs no whisper and no ffmpeg, and a run of `--file`
inputs needs no yt-dlp. `--check` reports on what the given arguments and
configuration would need.

---

## 5. Configuration file (`config.json`)

### Location, in lookup order

1. `--config <file>`
2. `$XDG_CONFIG_HOME/watchdown/config.json`
   (default `~/.config/watchdown/config.json`)
3. Nothing is found: use built-in defaults, and in verbose mode log a hint
   about `--init-config`.

`--init-config` writes the defaults below to location 2. It won't overwrite
an existing file unless you also pass `--force`.

### Schema and defaults

```json
{
  "outputDir": "./watchdown-out",
  "cacheDir": "~/.cache/watchdown",
  "keepAudio": false,
  "verbose": false,
  "captions": "auto",

  "ytDlp": {
    "path": "yt-dlp",
    "extraArgs": [],
    "timeoutMinutes": 15
  },

  "whisper": {
    "path": "whisper",
    "model": "small",
    "language": "auto",
    "device": "cpu",
    "timeoutMinutes": 120
  },

  "ollama": {
    "baseUrl": "http://localhost:11434",
    "model": "gemma3:4b",
    "temperature": 0.2,
    "numCtx": 8192,
    "timeoutSeconds": 300
  },

  "summary": {
    "language": "auto",
    "chunkTokens": 3000,
    "maxKeyPoints": 10
  }
}
```

### Rules

- Every key is optional. A partial file is merged over the defaults.
- Expand `~` and `$HOME` in path values.
- An unknown key logs a **warning** and processing continues (a typo
  shouldn't break a run). Invalid JSON or a wrong value type is an
  **error**: exit code 2, with a message that includes the file path and
  the JSON path, for example `ollama.temperature: expected number`.
- Model the config as nested Java **records** (`WatchdownConfig`,
  `YtDlpConfig`, `WhisperConfig`, `OllamaConfig`, `SummaryConfig`) with
  defaults in one place (`WatchdownConfig.defaults()`).
- The resolved config is the **single source of truth** for Ollama too.
  Build the Spring AI chat model and options from `OllamaConfig` after the
  CLI is parsed. Don't rely on `spring.ai.ollama.*` properties in
  `application.yaml` for values the user can change.

---

## 6. Pipeline

Each URL moves through four steps. Each step is its own class behind a
small interface, so tests can replace it.

### 6.1 Download (`download/`)

1. Validate and normalize the URL. Extract the 11-character video ID. Reject
   playlist-only URLs (`list=` with no `v=`) and anything that isn't YouTube.
2. Fetch metadata: `yt-dlp --dump-single-json --no-playlist <url>`. Map
   id, title, channel, upload date, duration, description, chapters, and
   webpage URL into a `VideoMetadata` record.
3. Download the audio:
   `yt-dlp --no-playlist -f bestaudio -x --audio-format mp3 -o <cache>/<id>/audio.%(ext)s <url>`.

### 6.2 Transcribe (`transcribe/`)

The transcript comes from one of two sources, chosen by `CaptionPolicy` from
the `captions` mode **before anything is downloaded**, so a video with captions
never downloads audio:

| Mode    | Uses                                                                  |
|---------|-----------------------------------------------------------------------|
| `auto`  | The captions the creator uploaded; Whisper when the video has none      |
| `never` | Whisper, always                                                        |
| `only`  | The creator's captions, else YouTube's automatic ones; never Whisper, and fails with exit code 5 when the video has neither |

`auto` never reaches for YouTube's automatic captions: they are ASR output just
like Whisper's, so falling back to Whisper keeps one known quality bar.

Captions come from
`yt-dlp --skip-download --write-subs|--write-auto-subs --sub-langs <code> --sub-format json3`
and are parsed by `CaptionParser`: one segment per event, joining `segs[].utf8`,
dropping the `aAppend` repeats that automatic captions use for their rolling
window. The language follows `--language`, or the video's own language when that
is `auto`; `en` also matches `en-US` and other regional variants.

Whisper:

- Run `whisper <audio> --model <m> --output_format json --output_dir <cache>/<id>`
  and add `--language <code>` unless the language is `auto`, plus `--device <d>`.
- Parse Whisper's JSON: `language` plus `segments[]` (`start`, `end`, `text`)
  into `Transcript(language, List<Segment>)`.
- Keep the `Transcriber` interface free of any Whisper-specific details, so a
  whisper.cpp implementation can be added later.

### 6.3 Summarize (`summarize/`)

- Use Spring AI `ChatClient` against Ollama with `model`, `temperature`
  and `numCtx` from config.
- **Map-reduce** for long videos:
  - If the video has chapters, make one chunk per chapter. Otherwise split
    on segment boundaries at about `summary.chunkTokens` tokens, estimating
    tokens as `chars / 4`. Never split in the middle of a segment.
  - **Map:** summarize each chunk. The prompt contains the chunk's segments
    with `[mm:ss]` timestamps.
  - **Reduce:** combine the chunk summaries into the final result: a title,
    a TL;DR (2 or 3 sentences), up to `maxKeyPoints` key points (each with
    one timestamp), and one short summary per section.
  - Skip the map step when the whole transcript fits in one chunk.
- Ask for **JSON** output and map it into a `Summary` record with Spring AI
  structured output (`.entity(Summary.class)`). If parsing fails, retry
  **once** with a stricter reminder, then fail with exit code 6.
- Keep prompts in `src/main/resources/prompts/*.st`. Don't build prompts
  inline in Java.
- Summary language: `auto` means the language Whisper detected. Otherwise
  use the given code, and say so in the prompt.
- The model must only summarize what the transcript says. The prompts
  say: no outside facts, and no timestamps that aren't in the transcript.
  After parsing, check the output: any timestamp outside `[0, duration]`
  gets dropped.

### 6.4 Render (`render/`)

Write the files described in §7. Write each file to a temporary file first,
then move it into place atomically. That way a failed run never leaves a
half-written folder that looks complete.

### 6.5 Cache

- Everything intermediate goes to `<cacheDir>/<videoId>/`: `metadata.json`,
  `audio.mp3`, Whisper's `audio.json`, and `captions.<lang>.json3`.
- On a rerun, skip any step whose cached output exists. Transcription is
  the slow step, so this cache matters. `--force` ignores the cache.
- Summaries aren't cached. Changing the model or prompt and rerunning
  should just work.
- Delete the audio after a successful transcription unless `keepAudio` is
  set, in which case copy it into the output folder.

### 6.6 Local files (`--file`)

A `--file` input skips the download step entirely and goes straight to
Whisper; captions do not apply. Whisper reads video containers directly, so
no conversion is needed.

- The id is the first 8 hex characters of the SHA-256 of the absolute path,
  so the same file keeps its cache directory and output folder across runs,
  and two files with the same name in different directories don't collide.
- The title is the file name without its extension; there is no channel, and
  the date is the file's last-modified date.
- The length comes from `ffprobe`; when that isn't available it comes from
  the end of the transcript, so a missing ffprobe never fails a run.
- There is no URL to link to, so every timestamp is rendered as plain
  `[mm:ss]` instead of a link, and `url:` in the front matter is empty.
- **The user's file is never moved, copied, or deleted.** `--keep-audio`
  only ever applies to audio that watchdown downloaded itself.

### 6.7 Partial failure

If summarization fails, still write `transcript.md` and an `AGENTS.md`
that says the summary is missing and why. Then exit with code 6.

---

## 7. Output format

This is the product. Each video becomes one folder:

```
<outputDir>/<slug>-<videoId>/
  AGENTS.md        # entry point for agents: what this is, TL;DR, key points, file map
  summary.md       # section-by-section summary with timestamp links
  transcript.md    # full timestamped transcript
  audio.mp3        # only with --keep-audio
```

`slug` is the title transliterated to ASCII, lower-cased, with non-alphanumeric
characters replaced by `-` and cut to 60 characters. Rerunning the same
video overwrites its folder.

Timestamp links always use the format `[mm:ss](https://www.youtube.com/watch?v=<id>&t=<seconds>s)`,
or `h:mm:ss` for videos an hour or longer. A `--file` input has nothing to link
to, so its timestamps are plain `[mm:ss]`.

### 7.1 `AGENTS.md` (generated)

```markdown
# AGENTS.md — <Video title>

Agent-readable digest of the YouTube video **<title>** by **<channel>**
(<duration>, published <yyyy-mm-dd>): <url>

## How to use this folder

- `summary.md`: section-by-section summary with timestamp links.
- `transcript.md`: full transcript. **This is the source of truth.**
- The summaries were generated by a local LLM and may contain mistakes.
  Check anything important against `transcript.md` before relying on it.
- When citing this video, use timestamp links (`[mm:ss](url&t=Ns)`).

## TL;DR

<2–3 sentences>

## Key points

- <point> ([03:12](…&t=192s))
- …

## Provenance

| Field         | Value                     |
|---------------|---------------------------|
| Video ID      | <id>                      |
| Language      | <detected language>       |
| Transcribed   | `<transcript source>`     |
| Summarized    | ollama `<model>`          |
| Generated     | <ISO-8601 timestamp>      |
| Tool          | watchdown <version>       |
```

### 7.2 `summary.md` and `transcript.md`

Both start with YAML front matter (`title`, `channel`, `url`, `video_id`,
`duration_seconds`, `published`, `language`, `generated_at`), followed by:

- `summary.md`: `# <title>`, then one `## <section title> ([mm:ss](…))`
  per section with its summary paragraph.
- `transcript.md`: `# Transcript — <title>`, then one line per segment:
  `**[mm:ss](…)** text`. If the video has chapters, insert
  `## <chapter title>` headings at the chapter boundaries.

### 7.3 Rendering rules

- The output is deterministic apart from `generated_at`: the same inputs
  always produce the same bytes. Golden-file tests depend on this.
- Escape Markdown-sensitive characters that come from the title, channel,
  and transcript text.
- Use UTF-8 and `\n` line endings, and end each file with a newline.

---

## 8. Repository layout

```
pom.xml
bin/watchdown                          # launcher script
src/main/java/com/boaglio/watchdown/
  WatchdownApplication.java            # @SpringBootApplication, non-web
  cli/          WatchdownCommand (picocli @Command), ExitCode, ConsoleReporter
  config/       WatchdownConfig + nested records, ConfigLoader, ConfigResolver
  process/      ProcessRunner (interface), DefaultProcessRunner, ProcessResult
  download/     YouTubeUrl, VideoMetadata, YtDlpDownloader, LocalMedia, MediaProbe
  transcribe/   Transcriber, WhisperTranscriber, Transcript, Segment
  summarize/    Summarizer, OllamaSummarizer, Chunker, Summary
  render/       MarkdownRenderer, Slugs, Timestamps
  pipeline/     Pipeline, VideoJob, Cache, Doctor (--check), CaptionPolicy
src/main/resources/
  application.yaml                     # non-web, banner off, logging levels
  prompts/chunk-summary.st
  prompts/final-summary.st
src/test/java/com/boaglio/watchdown/…
src/test/resources/
  fixtures/yt-dlp-metadata.json        # recorded yt-dlp output
  fixtures/whisper-output.json         # recorded whisper output
  golden/                              # expected AGENTS.md / summary.md / transcript.md
```

---

## 9. Coding conventions

- Use **constructor injection** only. No field `@Autowired`.
- Use **records** for data (config, metadata, transcript, summary). Keep them
  immutable.
- Every external command goes through `ProcessRunner`:
  - Pass arguments as a `List<String>`. **Never** run a command through a
    shell (`sh -c`), and never build a command by concatenating strings.
    The URL is untrusted input.
  - Every call has a timeout from config. When it's exceeded, kill the
    process tree.
  - Capture stdout and stderr. Stream them to the logger in verbose mode,
    and include the last 20 lines of stderr in the error message when a
    command fails.
- Nothing in `main` catches `Exception` and swallows it. Each step throws its
  own exception (`DownloadException`, `TranscriptionException`, …), and
  `WatchdownCommand` maps it to the exit code and a one-line message.
  Stack traces appear only in verbose mode.
- Before downloading anything, check the tools the run needs: they must be
  on the PATH, Ollama must be reachable, and the model must appear in
  `/api/tags`. Fail fast with exit code 3 and a fix hint, for example
  `ollama pull gemma3:4b`.
- Write code, comments, and messages in English.
- Don't add dependencies beyond §2 without a clear reason noted in the
  commit message.
- Keep `.gitignore` covering `target/`, `watchdown-out/`, and IDE folders.

---

## 10. Testing

- `./mvnw test` must pass **offline**, with no yt-dlp, whisper, or Ollama
  installed.
  - Use a fake `ProcessRunner` that replays the fixtures in
    `src/test/resources/fixtures/`.
  - Use a stub `ChatModel` that returns canned JSON for the summarizer.
- Unit tests are required for:
  - URL validation and ID extraction: watch, youtu.be, shorts, extra
    parameters, playlist rejection
  - config loading: defaults, partial merge, unknown keys, bad types,
    `~` expansion
  - precedence between flags, the config file, and defaults
  - the chunker: chapter splits, token splits, never splitting a segment
  - caption parsing: `aAppend` repeats, word-level `segs`, events with no
    duration, milliseconds to seconds
  - the caption policy: each mode, the fallback to Whisper, language matching
  - timestamp formatting and link building (`mm:ss` and `h:mm:ss`)
  - slug generation: accents, emoji, length cap
  - local files: title and id from the path, rejecting a missing file or a
    directory, and rendering without a URL
  - summary JSON parsing, the single retry, and dropping out-of-range
    timestamps
  - exit-code mapping for each exception type
- **Golden-file tests** for `MarkdownRenderer`, with `generated_at` fixed
  by an injected `Clock`. To update the golden files, run
  `./mvnw test -Dwatchdown.updateGolden=true`, then review the diff.
- Integration tests (`*IT.java`, `integration` Maven profile) run the full
  pipeline against a short video given in `WATCHDOWN_IT_URL`, using the
  `tiny` Whisper model. They are skipped when the variable is unset.

---

## 11. Milestones

1. **Skeleton:** Maven wrapper, Spring Boot non-web app, picocli command,
   `--help`/`--version`, config loading and resolution, `--init-config`,
   `--check`, and verbose logging.
2. **Transcript:** download and transcribe, cache, and `transcript.md`
   written.
3. **Summary:** Ollama summarizer with map-reduce and structured output;
   `summary.md` and `AGENTS.md` written, golden tests passing.
4. **Polish:** multiple URLs, partial-failure handling, `--keep-audio`, the
   launcher script, and a README with install steps for the external tools.

Each milestone is done when `./mvnw test` is green and the README reflects
any user-visible change.

---

## 12. Working agreement for agents

- Read this file before you change anything. If you find a conflict
  between the code and this file, raise it before you change either one.
- Keep changes small and on topic. One milestone item per commit is ideal.
- Don't commit generated output (`watchdown-out/`), audio files, or
  anything from the cache.
- Don't call real YouTube, Whisper, or Ollama from unit tests.
