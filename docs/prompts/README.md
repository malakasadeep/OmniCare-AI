# System prompts

## Where the text actually lives

The prompt that runs is `backend/src/main/resources/prompts/system-v1.txt`. It
is a classpath resource because it has to be readable from inside the packaged
jar, where `docs/` does not exist.

This directory holds the *history and reasoning*, not a second copy of the text.
A duplicated prompt is a prompt that drifts, and then nobody knows which one the
model actually saw.

## Versioning

One file per version, never edited in place: `system-v1.txt`, `system-v2.txt`,
and so on. A prompt change is a behaviour change to the product, and editing the
text under a fixed name makes "it used to answer this correctly" impossible to
investigate. `SystemPrompt.CURRENT_VERSION` selects which one is loaded, so
rolling back is a one-line change.

## Changelog

### v1 — Week 2, Day 9

First versioned prompt. Sections:

- **Language** — the whole of multilingual support. There is no language
  detection code and there should not be; the model already knows how, and a
  detector would be a second thing to be wrong.
- **Tone** — short answers, because a support widget is a small box.
- **Honesty** — the expensive failure mode is a confidently invented delivery
  date, not an unanswered question. Week 3 adds retrieval, at which point this
  section becomes "answer only from the provided context".
- **Scope** — keeps the assistant from being used as a free general chatbot.
- **Escalation** — prepares for Day 19's handoff tool. Today it only changes
  what the model says; from Day 19 it will change the conversation's state.

## Evaluating a change

`docs/eval.md` (Week 3, Day 15) holds the question set to run against any new
version before it becomes current.
