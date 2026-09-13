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

## Day 4 — Authentication

- The filter chain finally made sense as a chain: `JwtAuthenticationFilter` never rejects anything. It either
  populates the `SecurityContext` or leaves it empty, and `SecurityConfig` alone decides whether anonymous is
  allowed. That is why the filter needs no list of public paths.
- Stateless auth's real trade-off is revocation: nothing is stored server-side, so an issued token cannot be
  withdrawn. Hence 15 minutes for the access token and a separate long-lived refresh token — and a `typ`
  claim, because without it a refresh token presented as an access token silently buys a 30-day session.
- BCrypt is slow *on purpose* and salts each password itself. Also hashed on the "no such user" path so the
  response time doesn't answer "does this address have an account?".
- Stuck on where an error becomes an HTTP status. First draft had `shared` importing `tenant`'s exceptions,
  which inverts the module dependency. Fixed with `ApplicationException` in `shared.api`: features depend on
  shared, the handler only ever sees the base type.
- Two things I got wrong and the tests caught: `users.email` was unique *per tenant* from Day 3, which makes
  login by address ambiguous (now global), and my error body had a timestamp, so two failed logins weren't
  byte-identical after all.

## Day 5 — Multi-tenancy and RLS

- Wrote the policies, ran the isolation test, watched all of it fail. Root cause: **a superuser bypasses RLS
  unconditionally** — `ENABLE` and even `FORCE` are ignored for them — and `POSTGRES_USER` is always a
  superuser. Proved it on a throwaway table: same query, 2 rows as superuser, 1 row as a plain role. The app
  now connects as `omnicare_app`, created NOSUPERUSER by a container init script.
- `SET LOCAL` vs `SET` stopped being trivia. Connections are pooled, so a plain `SET` outlives the request
  and the next tenant to borrow that connection inherits it — a leak that would only appear under load.
  `set_config(..., true)` is reverted by Postgres at commit, so a connection always returns unscoped.
- Second failure, subtler: `findById` worked but `countByTenantId` returned 0. Spring Data annotates the CRUD
  methods it inherits, not *derived* query methods, so those ran with no transaction — no transaction, no
  `doBegin`, no `app.tenant_id`, no rows. Tenant scoping that hangs off a transaction fails silently exactly
  where there isn't one. Every repository adapter is now `@Transactional`.
- Third, and only a real HTTP call could find it: a thrown `ResponseStatusException` triggers a servlet ERROR
  dispatch, the `OncePerRequestFilter`s don't run again, so `/error` arrives unauthenticated and every 404
  reached the client as a 401. MockMvc never performs that dispatch. Added a `RANDOM_PORT` test that does.
- Defense in depth is now literal: the app filters by tenant, and if it forgets, `select * from conversations`
  returns 3 rows scoped to A, 0 rows unscoped — and 6 to a superuser, which is the whole reason it isn't one.

## Day 6 — Conversation API

- DTOs stopped feeling like boilerplate once I wrote `ConversationResponse`: returning `Conversation` would
  bind the wire format to the domain, so renaming a domain field would break every client. The DTO is the
  contract; the domain is free to change behind it.
- Layering held up — controller binds and validates, service sequences repository calls, domain owns the
  rules. `ConversationService` has no `if` in it worth the name, because the decisions live in `Conversation`.
- Introduced `Replier` as an interface with one fixed-string implementation. Day 7's `LlmProvider` becomes a
  new implementation rather than surgery on the service. Naming it `PlaceholderReplier` and having it *say*
  it isn't a model means it can't quietly survive into a demo.
- Ordering bit me before it broke anything: Postgres keeps microseconds, and a user message plus its reply
  are written inside one request, so both can land on the same timestamp and the transcript order — the order
  the model gets shown — becomes undefined. The reply is now stamped strictly after the question.
- The global handler taught me something sharp. Adding `@ExceptionHandler(Exception.class)` made every 404
  a 500, because the advice runs before Spring's own resolvers. Then the obvious fix still missed:
  `ResponseStatusException` extends `ErrorResponseException`, but `NoResourceFoundException` extends
  `ServletException` and only *implements* `ErrorResponse`. A catch-all is a liability unless you know
  exactly what it is catching.
