# Journal

Five lines a day: what I learned, where I got stuck.

---

## Day 2 — Domain model (OOP core)

- Encapsulation is about *behaviour*, not visibility: `Conversation` has no setters at all, and every
  status change funnels through one private `transitionTo` gate that validates before it mutates.
- Putting the legal-transition table in the enum (`ConversationStatus`, `DocumentStatus`) rather than in
  `if` chains inside the entity means the rules are readable in one place and testable on their own.
- Entity vs value object landed concretely: ids and `Email` are records compared by value; `Conversation`,
  `Tenant`, `User`, `Document` are compared by identity, so two tenants with the same name are still two tenants.
- Stuck on where the retry budget belongs. Put `MAX_ATTEMPTS` inside `Document` so the worker reports an
  outcome and the *document* decides retry-vs-give-up — the worker never gets a vote.
- Wrote `Guards.requireNonBlank` after seeing the fourth copy of the same null/blank check, and deleted
  `DocumentStatus.isTerminal()` — nothing called it, and I had written it without a failing test first.
