# Operations: Missing Visit Idempotency Deadlines

This runbook describes the implemented recovery procedure. Rehearse it on disposable persistent
MySQL/PostgreSQL data for every retained adapter before release. It introduces no administrative
HTTP endpoint and applies within the feature's single-instance guarantee.

## Meaning and detection

Creation commits the visit and complete replay snapshot atomically. A separate service transaction
normally sets a fixed deadline using UTC time sampled after creation commit. If that transaction
fails or the process stops before finalization, both deadline fields remain NULL.

A NULL/NULL row is a committed success, not an abandoned creation. Matching requests replay the
original 201/body/Location; differing requests receive 422. It remains protected indefinitely.
Restart, replay and cleanup must never automatically finalize or delete it.

The finalizer reports failures through existing operational logging without raw or encoded keys,
request bodies or response snapshots. Operators can check a count without selecting clinical data:

```sql
SELECT COUNT(*) AS missing_deadline_count
FROM visit_creation_results
WHERE completed_at_ms IS NULL AND expires_at_ms IS NULL;
```

During normal processing, a count may include a short finalization interval. Quiesce the single
application instance before resolving rows. A mixed NULL/non-NULL pair is an invariant violation
requiring investigation; schema constraints must prevent it.

## Operator recovery

1. Stop/quiesce the application normally, leaving persistent data intact. Use existing database
   maintenance access. Do not run schema/data initialization or reset scripts.
2. Identify the intended result using its composite scope and original response_visit_id in a
   restricted database session. Preserve the historical snapshot even if the live visit has been
   edited or deleted. Do not export keys or clinical snapshots into logs or tickets.
3. Sample current UTC epoch milliseconds in the operator client as `anchor_ms`. Calculate
   `expiry_ms = anchor_ms + 86400000` using checked 64-bit arithmetic. This anchor is after the
   original creation commit. Recovery deliberately grants a full day after this action; the
   snapshot was protected indefinitely beforehand. Never backdate or guess the original commit.
4. Begin a database transaction using the selected client's dialect. Execute the conditional
   update below using bound parameters supported by that client, rather than string concatenation.
   The stored `key_hex` is the existing uppercase hexadecimal identity, not a normalized raw key.

```sql
UPDATE visit_creation_results
SET completed_at_ms = :anchor_ms, expires_at_ms = :expiry_ms
WHERE operation = :operation
  AND owner_id = :owner_id
  AND pet_id = :pet_id
  AND key_hex = :key_hex
  AND response_visit_id = :original_visit_id
  AND completed_at_ms IS NULL
  AND expires_at_ms IS NULL;
```

5. Require exactly one affected row for the intended result. If zero, inspect whether the deadline
   is already established or identity changed; leave it unchanged. Roll back unexpected results.
   Validate the exact 86,400,000 ms difference, then commit. Repeat deliberately for each intended
   row. Never overwrite established deadlines, delete snapshots, change response fields or create
   another visit as recovery.
6. Restart without resetting persistent data. Verify the original request replays the stored result
   and a changed body receives 422. Record maintenance outcome/timing without sensitive contents.

If maintenance commit outcome is uncertain, read the row again and preserve any established pair.
The conditional update cannot renew an existing deadline. Leaving NULL/NULL intact safely continues
indefinite replay protection until resolved.

## Required verification

- All 12 adapter/database combinations: NULL/NULL replays beyond 24 hours, rejects mismatches,
  survives cleanup, and can be finalized exactly once without changing snapshot fields.
- Each adapter on MySQL/PostgreSQL: interrupt after creation commit but before finalization,
  restart without initialization, verify indefinite protection, and rehearse this procedure.
- Delay creation commit while advancing the controllable Clock; verify the deadline anchor is
  sampled after commit return and never shortens the minimum 24-hour post-commit window.
- Inject finalizer rollback and uncertain commit outcome. Creation remains recoverable, and
  maintenance retries cannot renew an established deadline.

## Rehearsal evidence

On 2026-10-11 the restart suites rehearsed this conditional SQL on disposable MySQL 8.4 and
PostgreSQL 16.3 with jdbc, jpa and spring-data-jpa. Every combination preserved indefinite
NULL replay through restart and more than ten days, accepted exactly one operator update,
rejected renewal, and retained the original snapshot. Shared matrix tests verified cleanup
exclusion, finalizer rollback and uncertain finalizer-commit outcome. See
[quickstart.md](quickstart.md#final-execution-evidence) for commands, counts and coverage.
