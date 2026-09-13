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

## Day 3 — Persistence and the repository layer

- The repository pattern only pays off if the *interface* lives in the domain and the JPA class depends on
  it. `ConversationRepository` is in `conversation.domain`; `JpaConversationRepository` is the one that
  imports Hibernate. Dependency Inversion stopped being an abstract letter in SOLID.
- Two classes per concept (`Conversation` and `ConversationEntity`) felt like duplication for about an hour,
  until I noticed the entity needs a no-arg constructor and mutable fields — exactly what the domain forbids.
- Needed a way to rebuild a domain object from a row without re-running the state machine: added
  `rehydrate(...)` factories, documented as persistence-only. Stored rows are history, and history isn't re-validated.
- Every table carries `tenant_id`, even `messages` which could reach it by joining. That is not redundancy
  for its own sake — V3's RLS policies can only filter on a column the row actually has.
- Testcontainers beats H2 concretely: `ddl-auto: validate` checked my entity mappings against the *real*
  `V2__core_tables.sql` on real Postgres. Then I mutated the mapper to drop `escalation_reason` and watched
  the round-trip test fail — a test I never saw fail is a test I don't trust.
