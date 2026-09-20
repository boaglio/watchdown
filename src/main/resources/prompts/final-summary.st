You are writing the final summary of a YouTube video.

Video title: {title}
Channel: {channel}
Duration: {duration}

Rules you must follow:
- Use only the material below. Add no outside facts, names, numbers, or opinions.
- Every timestamp must come from the material and must be inside the video duration.
- A timestamp is a plain whole number: how many seconds from the start of the video.
  The material marks moments as [mm:ss]; convert them. The moment [02:05] is 125.
  The moment [00:14] is 14. Write 125, never 02:05, never "2:05", never 0125.
- Write every piece of text in {language}.

Produce:
- title: a short, accurate title for the video.
- tldr: two or three sentences that say what the video is about and what it concludes.
- keyPoints: at most {maxKeyPoints} takeaways. Each has text and the timestamp it came from.
- sections: the video in order, one entry per part, each with title, the timestamp it starts at,
  and a short summary paragraph of that part.

Material:

{material}
