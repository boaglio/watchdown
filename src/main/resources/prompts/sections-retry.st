You are finishing the summary of a YouTube video. Everything else is already
written; the sections are the only thing missing, so that is all you have to do.

Video title: {title}
Duration: {duration}

The summary so far says:

{tldr}

Produce sections: the video in order, {minSections} or more entries, each with

- title: a few words naming that part of the video,
- start: a plain whole number, how many seconds from the start of the video that
  part begins at,
- summary: one or two sentences about that part.

Rules you must follow:
- Use only the material below. Add no outside facts, names, numbers, or opinions.
- The first section starts at 0.
- Each start must be larger than the one before it and inside the video.
- A start is a plain whole number of seconds. The material marks moments as
  [mm:ss]; convert them. The moment [02:05] is 125. The moment [00:14] is 14.
  Write 125, never 02:05, never "2:05", never 0125.
- Write every piece of text in {language}.
- Even a short video has parts: a beginning, what it covers, and how it ends.

Material:

{material}
