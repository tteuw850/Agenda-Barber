# F04-03 - Write idempotency (phase 1, LOCAL)

Date: 2026-10-31
Branch: feat/f04-03-write-idempotency
Base: feat/f04-02-safe-read-retries (5be6de6)
Worktree: D:\appeber-f04-03

## Scope implemented
- A common CreateIdempotencyExecutor protects POST /api/v1/clients and POST /api/v1/services when an Idempotency-Key is supplied.
- It validates the key ([A-Za-z0-9._:-], 8 to 128 characters), binds it to the authenticated user, barber, operation and request, and checks the stored SHA-256 digest.
- Existing idempotency_records (Flyway V1) stores the resource identity in the same database transaction as the insert. SELECT ... FOR UPDATE on the barber serializes concurrent creation for the same owner. The second identical request reads the existing resource instead of creating a second one.
- A changed payload or operation reusing the key returns ProblemDetail 409; an invalid key returns 400.
- Header omission still follows the legacy create flow. This is a COMPATIBILITY transition, not a full guarantee for outdated clients.
- Android new-client and new-service forms now send a UUID key, retain it for the same form on a retry, and rotate it when the submitted data changes.
- No Flyway migration was added or executed on Neon. Auth and other non-idempotent POST endpoints were not given automatic retries.

## Verified
- mvn -B -ntp -DskipTests verify => BUILD SUCCESS, Checkstyle 0.
- mvn -B -ntp -Dtest=CreateIdempotencyIntegrationTests test => 3/3 passing, PostgreSQL 17 Testcontainers.
- mvn -B -ntp verify => BUILD SUCCESS, 82 tests executed, 81 passed, 1 skipped (NeonConnectionSmokeTest), 0 failures and 0 errors.
- New integration tests verify replay, conflicting body/operation, rollback and simultaneous same-key calls.
- Android build NOT verified locally: neither android-app/gradlew.bat nor gradle on PATH. No installation or remote CI was triggered.

## Known limitations / next work
- Replay returns the currently readable resource identified by resource_id, not a frozen HTTP response snapshot; later deletion may prevent a replay response, without recreating the resource.
- Missing-key requests are not protected; enforce keys only after checking compatibility and updating all Android writers.
- Remaining mutation audit: POST recurring/single schedule blocks, changes and deletions of clients, services and expenses, appointment edits/start/complete/cancel, working-hours changes, and auth-session actions (separate security semantics).
- Add durable response replay/status where required, test post-commit timeout and deleted-resource replay, and ensure Android reuses the key across process restarts if this becomes a delivery requirement.
- Run Android ktlint, unit tests, lint, assemble and APK testing in an authorized Android build environment.
- Homologate with the development database; no Neon production or development mutation was performed in this branch.

## Safety boundary
No push, pull request, merge, deployment, AWS or Render changes, migration on Neon, or physical-device installation. F04-03 remains IN PROGRESS.
