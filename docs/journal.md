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

## Day 7 — The LlmProvider abstraction

- Strategy finally clicked as a *boundary* rather than a pattern to recite. `LlmRequest`/`LlmResponse`
  mention no vendor; every snake_case field, lower-cased role and `choices[0].message.content` burrow lives
  inside `GroqProvider`. The rule "no vendor names in these types" is what makes the rest true.
- Open/Closed is visible in the factory: it takes `List<LlmProvider>` and indexes by `name()`, so adding a
  provider is a new bean plus one config value and the factory is never edited. A `switch` would have been
  the same number of lines and the wrong shape.
- The fake provider lives in main, not test sources. That is what makes "flip one value and the model swaps"
  a real claim, and what lets a fresh clone run with no key.
- Tested Groq without a key or a network by stubbing `ExchangeFunction`, which checks both translations:
  the JSON sent and the JSON parsed. `MockClientHttpRequest` needs an explicit write handler or it keeps
  nothing and `getBody()` complains the body is not set.
- Got a condition wrong in a way that was worth writing down. `api-key: ${GROQ_API_KEY:}` means an unset
  variable yields a property that is *present and empty*, and `@ConditionalOnProperty(name = "api-key")`
  matches it. So the guard I added against a missing key did nothing. Now `@ConditionalOnExpression` on a
  non-blank value, and selecting groq without a key fails at startup naming the known providers.

## Day 8 — Streaming with SSE

- SSE over WebSocket because this is one-way and short-lived: the server talks, the browser listens, and
  `EventSource` reconnects on its own. A WebSocket would be two pipes where one is needed. The cost is that
  `EventSource` can only GET, which is why the message rides in the query string.
- "Never hold a transaction open while streaming" stopped being advice. A model can take tens of seconds;
  a transaction spanning that pins a pooled connection and its locks for the whole time, so a handful of
  concurrent visitors would drain the pool. The question commits before the first token, the answer after
  the last, and nothing is open in between.
- The trap I did not see coming: `TenantContext` is a `ThreadLocal` set by a servlet filter on the *request*
  thread, but `doFinally` runs on whichever thread the stream ended on. Without re-establishing the tenant
  there, the final write has no `app.tenant_id` and RLS rejects it — the reply would simply vanish.
- Tokens go over the wire as JSON, not raw text, because SSE strips one space after `data:`. Models emit
  " world" rather than "world", so raw fragments would arrive with the spaces eaten and the answer would
  read as one long run-on word. There is a test for exactly that.
- A closed tab was being logged as an ERROR with a stack trace, and then the catch-all failed a second time
  trying to write JSON into a committed `text/event-stream` response. Naming `ClientAbortException` did not
  help — on this platform a bare `IOException` propagates. The honest discriminator is whether the response
  is already committed: if it is, there is nobody left to tell.

## Day 9 — Prompt building and the context window

- The sliding window really is cache eviction in a hat: fixed capacity, entries of varying size, and a
  policy for what to throw out. Oldest-first, because in support the last few exchanges carry the meaning
  and the opening pleasantries carry none. Two entries are pinned — the system prompt and the current
  question — since dropping the question would mean confidently answering something nobody asked.
- Found a subtler rule while writing the tests: if eviction leaves an assistant turn whose question is gone,
  that turn is an answer to nothing and reads to the model as an unprompted assertion of fact. The window
  now drops orphaned answers too.
- Token counting is an *estimate* and the honest version of that matters. Four characters per token holds
  for English and badly under-counts non-Latin scripts — so the count is least accurate exactly where this
  product promises to work. Hence a budget well under the model's real window rather than at it.
- Multilingual support turned out to be a prompt feature, not a code feature. There is no language detection
  anywhere; the model already knows how, and a detector would be a second thing to be wrong.
- Put the prompt in a versioned resource rather than a Java string, with `docs/prompts/` holding the history
  and reasoning rather than a second copy — a duplicated prompt drifts, and then nobody knows which text the
  model actually saw. Verified the window at runtime: the same 122-message conversation sends 122 messages
  at budget 8000 and 14 at budget 800, and stays answerable either way.
