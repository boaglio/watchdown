# watchdown

Turn a YouTube video into a folder of Markdown that a coding agent can read.

```
watchdown https://youtu.be/dQw4w9WgXcQ
  → yt-dlp (audio + metadata) → whisper (transcript) → Ollama (summary) → Markdown
```

Everything runs **locally**: no cloud APIs and no API keys. The transcript keeps its timestamps,
and every summary point links back to the exact moment in the video.

## What you get

One folder per video:

```
watchdown-out/building-a-local-transcription-pipeline-dQw4w9WgXcQ/
  AGENTS.md        # entry point for agents: what this is, TL;DR, key points, file map
  summary.md       # section-by-section summary with timestamp links
  transcript.md    # full timestamped transcript
  audio.mp3        # only with --keep-audio
```

## Requirements

- Java 25
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — `pip install -U yt-dlp`
- [ffmpeg](https://ffmpeg.org/) — `apt install ffmpeg`, `brew install ffmpeg`
- [openai-whisper](https://github.com/openai/whisper) — `pip install -U openai-whisper`
- [Ollama](https://ollama.com/) with a model pulled — `ollama pull gemma3:4b`

Check everything at once:

```bash
watchdown --check
```

## Install

```bash
./mvnw clean package
java -jar target/watchdown.jar --help
```

`bin/watchdown` is a thin launcher; put it on your PATH to type `watchdown` instead.

## Usage

```
Usage: watchdown [OPTIONS] <url>...

  <url>...                 One or more YouTube video URLs
                           (youtube.com/watch?v=, youtu.be/, youtube.com/shorts/)

  -o, --output <dir>       Output root directory          (config: outputDir)
  -c, --config <file>      Config file to use             (default: see below)
  -m, --model <name>       Ollama model for summaries     (config: ollama.model)
  -w, --whisper-model <n>  Whisper model (tiny…large)     (config: whisper.model)
  -l, --language <code>    Spoken language, e.g. en, pt; 'auto' to detect
                                                          (config: whisper.language)
  -s, --summary-language <code>
                           Language of the summary; 'auto' = same as video
                                                          (config: summary.language)
  -v, --verbose            Detailed progress, commands, timings (config: verbose)
  -f, --force              Ignore cached audio/transcript, redo every step
      --keep-audio         Keep the downloaded audio file in the output folder
      --check              Verify external tools + Ollama model, then exit
      --init-config        Write a default config file to the default path, then exit
  -h, --help               Show help
  -V, --version            Show version
```

Progress goes to **stderr**, one line per step; the output folder paths go to **stdout**, one per
line, so watchdown fits into a pipeline:

```bash
watchdown https://youtu.be/dQw4w9WgXcQ | xargs -I{} ls {}
```

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

With several URLs, one failure does not stop the others, and the exit code is the highest any URL
produced.

## Configuration

`watchdown --init-config` writes the defaults to `$XDG_CONFIG_HOME/watchdown/config.json`
(`~/.config/watchdown/config.json`). Every key is optional and a partial file is merged over the
defaults. `~` and `$HOME` are expanded in path values.

```json
{
  "outputDir": "./watchdown-out",
  "cacheDir": "~/.cache/watchdown",
  "keepAudio": false,
  "verbose": false,

  "ytDlp":   { "path": "yt-dlp",  "extraArgs": [], "timeoutMinutes": 15 },
  "whisper": { "path": "whisper", "model": "small", "language": "auto",
               "device": "cpu", "timeoutMinutes": 120 },
  "ollama":  { "baseUrl": "http://localhost:11434", "model": "gemma3:4b",
               "temperature": 0.2, "numCtx": 8192, "timeoutSeconds": 300 },
  "summary": { "language": "auto", "chunkTokens": 3000, "maxKeyPoints": 10 }
}
```

A CLI flag beats the config file, which beats the built-in default. An unknown key is a warning; a
value of the wrong type is an error that names the file and the JSON path.

## Cache

Intermediate files live in `<cacheDir>/<videoId>/`: `metadata.json`, `audio.mp3` and Whisper's
`audio.json`. A rerun skips any step whose output is already there — transcription is the slow one,
so this matters. `--force` ignores the cache. Summaries are never cached, so changing the model or
the prompt and rerunning just works. The audio is deleted after a successful transcription unless
you pass `--keep-audio`.

## Development

```bash
./mvnw test                      # unit tests only; no network, no external tools
./mvnw verify -Pintegration      # + *IT tests; needs yt-dlp, whisper, ollama running
./mvnw clean package             # → target/watchdown.jar
```

The integration test runs only when `WATCHDOWN_IT_URL` points at a short video:

```bash
WATCHDOWN_IT_URL=https://youtu.be/SHORT_VIDEO ./mvnw verify -Pintegration
```

Golden-file tests pin the rendered Markdown. To accept a deliberate change:

```bash
./mvnw test -Dwatchdown.updateGolden=true   # then review the diff
```

`AGENTS.md` at the repository root is the contract for this project; read it before changing
anything.

## Caveats

The summaries come from a local LLM and may contain mistakes. `transcript.md` is the source of
truth — check anything important against it.
