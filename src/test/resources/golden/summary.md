---
title: "Building a Local Transcription Pipeline"
channel: "Boaglio Labs"
url: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
video_id: "dQw4w9WgXcQ"
duration_seconds: 754
published: "2025-09-17"
language: "en"
generated_at: "2025-09-18T12:00:00Z"
---

# Building a local transcription pipeline

The video walks through a transcription pipeline that runs entirely on your own machine. It combines yt-dlp, whisper and a local model, and caches every step.

## Key points

- Nothing leaves your laptop ([00:14](https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=14s))
- A cache makes reruns cheap ([04:10](https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=250s))

## Why local ([00:00](https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=0s))

The reasons to keep the whole pipeline off the cloud.

## The pipeline ([04:00](https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=240s))

yt-dlp fetches the audio, whisper transcribes it, and the summary comes from a local model.
