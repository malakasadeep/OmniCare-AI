# Backlog

Things deliberately left out of the 5-week plan, recorded rather than built.

## RLS on `tenants` and `users`

V3 puts row level security on `conversations`, `messages` and `documents` but
not on `tenants` or `users`. Login has to find a user by a globally unique email
*before* any tenant is known, so a tenant-scoped policy on `users` would make
logging in impossible.

The proper fix is two database roles: one the auth path uses to resolve an
identity (reaching `users`/`tenants` only), and one everything else uses, with
RLS on all five tables. Until then those two tables are guarded by
application-level checks only.

## Refresh token endpoint

Refresh tokens are issued and verifiable (`JwtService.parseRefreshToken`) but no
`POST /api/auth/refresh` exists yet, so a client gets 15 minutes and then has to
log in again.

## Token revocation

Stateless JWTs cannot be withdrawn before they expire. A deleted user keeps a
working access token for up to 15 minutes — `/api/me` re-checks the database,
but other endpoints do not. A deny-list keyed on the `jti` claim would close it.

## Visitor text in the streaming URL

`GET /api/conversations/{id}/stream?message=...` puts the visitor's message in
the query string, where access logs and proxies will record it. It is a GET
because the browser's `EventSource` cannot POST. The fix is either a POST that
returns a stream (needs `fetch` + `ReadableStream` in the widget rather than
`EventSource`), or posting the message first and having the stream read it back.

## Message ordering depends on timestamps

Transcript order comes from `created_at`, and the service stamps a reply
strictly after its question so the two cannot collide in the same microsecond.
A monotonic per-conversation sequence column would make the ordering structural
rather than something each writer has to remember.
