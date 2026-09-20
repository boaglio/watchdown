# watchdown

Turn a YouTube video into a folder of Markdown that a coding agent — or you — can actually read.

```
watchdown https://youtu.be/VIDEO_ID
  → yt-dlp → captions or whisper → Ollama → Markdown
```

Everything runs **locally**. No cloud APIs, no API keys, nothing leaves your machine.

## One example, end to end

```console
$ watchdown --captions only https://www.youtube.com/watch?v=aircAruvnKk
[1/4] Downloading  "But what is a neural network? | Deep learning chapter 1" (18:40) . done  5,2s
[2/4] Transcribing captions en .......................... done  0,0s
[3/4] Summarizing  gemma3:4b, 12 chunks ................. done  74,3s
[4/4] Writing      ./watchdown-out/but-what-is-a-neural-network-deep-learning-chapter-1-aircAruvnKk
./watchdown-out/but-what-is-a-neural-network-deep-learning-chapter-1-aircAruvnKk
```

Progress goes to **stderr**; the folder path goes to **stdout**, so watchdown pipes cleanly.
On a terminal the slow steps show a live bar (`███████░░░░░░░ 37% 4/12 0:52`) so you can tell
working from stuck.

You get one folder per video:

```
but-what-is-a-neural-network-deep-learning-chapter-1-aircAruvnKk/
  AGENTS.md      # the digest: TL;DR, key points, how to use the folder
  summary.md     # TL;DR, key points, then a section per part
  transcript.md  # every word, timestamped and linked
```

**`transcript.md`** — 286 timestamped lines, one per caption, with the video's own chapters
as headings.
Every timestamp links to that exact second:

```markdown
## Introduction example

**[00:04](https://www.youtube.com/watch?v=aircAruvnKk&t=4s)** This is a 3.
**[00:06](https://www.youtube.com/watch?v=aircAruvnKk&t=6s)** It's sloppily written and rendered at an extremely low resolution of 28x28 pixels,
**[00:11](https://www.youtube.com/watch?v=aircAruvnKk&t=11s)** but your brain has no trouble recognizing it as a 3.
```

**`AGENTS.md`** — what an agent reads first. It says what the video is, gives the takeaways with
links, tells the agent the transcript is the source of truth, and records how it was made:

```markdown
## Key points

- A brain recognises a sloppy 3 effortlessly ([00:04](https://www.youtube.com/watch?v=aircAruvnKk&t=4s))
- The network takes 784 input neurons, one per pixel ([03:00](https://www.youtube.com/watch?v=aircAruvnKk&t=180s))

## Provenance

| Field       | Value                    |
|-------------|--------------------------|
| Video ID    | aircAruvnKk              |
| Language    | en                       |
| Transcribed | `captions en`            |
| Summarized  | ollama `gemma3:4b`       |
```

Your model's wording will differ — that is the point of running it locally. The transcript will
not.

## Requirements

- **Java 25**
- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** — `pip install -U yt-dlp`
- **[Ollama](https://ollama.com/)** with a model — `ollama pull gemma3:4b`
- **[whisper](https://github.com/openai/whisper)** + **ffmpeg** — only when a video has no
  captions, or you pass `--captions never`

`watchdown --check` verifies exactly what your settings need, and nothing more.

## Install

```bash
git clone https://github.com/boaglio/watchdown && cd watchdown
bin/watchdown --help          # builds the jar on first run
ln -s "$PWD/bin/watchdown" ~/.local/bin/watchdown
```

## Captions or whisper

Most videos already have a transcript; using it turns a multi-minute run into a few seconds.

| `--captions` | Uses                                                                   |
|--------------|------------------------------------------------------------------------|
| `auto`       | The creator's captions when they exist, otherwise whisper *(default)*  |
| `never`      | Always whisper                                                         |
| `only`       | The creator's captions, else YouTube's automatic ones; never whisper    |

`auto` skips YouTube's *automatic* captions on purpose: they are speech recognition just like
whisper, so watchdown would rather run the model you chose.

## Options

```
watchdown [OPTIONS] [<url>...]

  -F, --file <path>        A local audio or video file instead of a URL (repeatable)
  -o, --output <dir>       Where folders go        (env: WATCHDOWN_ROOT, config: outputDir)
  -m, --model <name>       Ollama model            (config: ollama.model)
  -w, --whisper-model <n>  tiny … large            (config: whisper.model)
  -l, --language <code>    Spoken language         (config: whisper.language)
  -s, --summary-language   Summary language        (config: summary.language)
      --captions <mode>    auto | never | only     (config: captions)
  -v, --verbose            Commands, timings, and where each setting came from
  -f, --force              Ignore the cache and redo every step
      --keep-audio         Keep the downloaded audio in the output folder
      --check              Verify dependencies, then exit
      --init-config        Write a default config file, then exit
```

Settings resolve **flag > environment > config file > default**. `watchdown --init-config` writes
the defaults to `~/.config/watchdown/config.json` as a starting point; `export WATCHDOWN_ROOT=~/notes`
sets the output root for a shell.

Several inputs run in one go, and one failure never stops the rest:

```bash
watchdown https://youtu.be/A https://youtu.be/B --file talk.m4a
```

## Good to know

- **Reruns are cheap.** Audio and transcripts are cached in `~/.cache/watchdown`; summaries never
  are, so changing `-m` and rerunning just works. `--force` ignores all of it.
- **A failed summary still writes the transcript**, plus an `AGENTS.md` saying what went wrong,
  and exits `6`. Exit codes: `2` usage, `3` missing dependency, `4` download, `5` transcription,
  `6` summary. With several inputs you get the highest.
- **Local files work too**: `watchdown --file standup.m4a`. Your file is never moved or deleted.
- **The summaries come from a small local model and can be wrong.** `transcript.md` is the source
  of truth. If your model keeps producing unusable JSON, a bigger one (`-m gpt-oss`) is the fix.

## Development

```bash
./mvnw test                     # offline: no network, no yt-dlp, no whisper, no Ollama
./mvnw verify -Pintegration     # + a real short video, via WATCHDOWN_IT_URL
```

`AGENTS.md` at the repo root is the contract for this project — read it before changing anything.
