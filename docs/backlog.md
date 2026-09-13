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
