# Implementation Plan: Opt-in Visit Creation Idempotency

**Branch**: `001-visit-idempotency` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

**Input**: `specs/001-visit-idempotency/spec.md`; existing architecture from section 3,
TECHNOLOGY AND PATTERNS BRIEF, in `prompts/01-discovery/browenfield-discovery-report.md`.
The spec's earlier feature-branch metadata is historical; setup resolved the current branch above.

## Summary

Add optional keyed retries only to owner/pet visit creation. Preserve the existing unkeyed
controller path. A feature-specific coordinator uses a nonblocking process-local creation gate;
a separate transactional service creates the visit and immutable retained response together.
Completed results live in the selected clinic database through JDBC, JPA or Spring Data JPA
adapters. Replays return stored response values, never the current Visit. No distributed lock,
new framework, authentication change, or owner/pet validation correction is introduced.

## Technical Context

**Language/Version**: Java 17. The locally cached Boot 4.1.1 parent explicitly sets
`java.version=17` and compiler release to that property; see research.md. Wrapper Maven 3.9.16.

**Primary Dependencies**: Existing Spring Boot 4.1.1, MVC, Security, Jakarta Validation,
Spring transactions/JDBC/JPA, Jackson 3, MapStruct 1.6.3, springdoc 3.1.0 and OpenAPI Generator
7.25.0. No new runtime library or migration framework is needed.

**Storage**: Existing H2/HSQLDB/MySQL/PostgreSQL profiles; add a retained-success table with
three repository adapters. Restart durability is tested on MySQL/PostgreSQL with retained data.
Default in-memory demo/test storage retains guarantees only during its lifetime.

**Testing**: Existing JUnit Jupiter, Mockito, AssertJ, MockMvc, Spring Boot/security tests;
shared real-adapter tests, explicit transaction/commit tests, a controllable Clock, full-filter
security tests, persistent restart tests and existing Newman smoke verification.

**Target Platform**: Existing JVM backend deployment, port 9966, context `/petclinic/`.

**Project Type**: Single Maven REST web service; no new deployable unit.

**Performance Goals**: No new throughput/latency SLO was requested. Admission of an overlapping
create is nonblocking: return 409 instead of waiting for that create's transaction. Retained
replay performs an indexed lookup and no visit write. Gate memory scales with active creates,
not retained results; bounded cleanup removes expired rows without changing correctness.

**Constraints**: Exact 1–128 U+0021–U+007E key; scope includes operation/owner/pet/key only;
validated date/description equality; 24-hour retention; all five constitutional principles;
existing 201/body/Location and unkeyed error behavior; no side effects for new rejections.

**Scale/Scope**: One endpoint, one running-instance guarantee, three retained repository
implementations and four configured database dialects. Shared-data multi-instance processing
has no duplicate-prevention guarantee. Volume sizing is not an acceptance requirement;
retained live rows are approximately successful keyed creations per day.

## Constitution Check

Pre-research gate: PASS. The feature can fit every principle without an exception.
Post-design gate: PASS at design level, with implementation verification still required.

| Principle | Design evidence and required verification |
| --- | --- |
| I. Conditional authorization integrity | Keep current method role guard and filter chain; both replay and creation execute behind them. Full-context anonymous/wrong-role tests, not just standalone MockMvc. |
| II. Transactional business writes | One proxied service transaction encloses visit persistence and retained success. Coordinator holds admission through commit/rollback; rollback/commit-failure tests must prove no partial result. |
| III. Shared persistence contracts | One retained-result repository contract, three selected adapters, shared tests; database dialect schema and key equality tests. No adapter is retired or silently excluded. |
| IV. Authoritative API contracts | Implement the contract delta in `src/main/resources/openapi.yml`, then regenerate. The feature contract is a design artifact, not a competing generator input. |
| V. Executable verification gates | Preserve wrapper verify, 85% line / 66% branch gates and Newman checks. Run full regression verification plus new behavior tests before merge; no new coverage exclusions. |

Administrative constitution TODOs remain outside this plan; the user explicitly made the
constitution binding. These PASS results are design review, not claims of passing tests.

## Project Structure

### Documentation (this feature)

```text
specs/001-visit-idempotency/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── operations.md
├── tasks.md
├── contracts/visit-idempotency.md
└── checklists/requirements.md
```

`tasks.md` contains the dependency-ordered implementation and verification work.
`operations.md` defines recovery of committed snapshots with missing deadlines.

### Source Code (repository root)

```text
src/main/java/org/springframework/samples/petclinic/
├── rest/controller/v1/OwnerRestControllerV1.java    # existing endpoint, optional branch
├── rest/idempotency/                              # raw header validation, dedicated errors
├── rest/advice/ExceptionControllerAdvice.java     # narrow error handlers only
├── service/                                      # coordinator, proxied worker/finalizer, read service
├── model/                                        # scope ID and immutable success snapshot
├── repository/                                   # retained-result interface
│   ├── jdbc/                                     # JDBC retained-result adapter
│   ├── jpa/                                      # EntityManager retained-result adapter
│   └── springdatajpa/                             # Spring Data adapter/custom fragment
└── config/                                       # injectable UTC Clock if no existing bean
src/main/resources/
├── openapi.yml
└── db/{h2,hsqldb,mysql,postgres}/schema.sql
src/test/java/org/springframework/samples/petclinic/
├── rest/controller/                              # compatibility and full MVC/security tests
└── service/visitIdempotency/                      # shared adapter/concurrency/restart tests
src/test/postman/                                 # extend existing smoke scenario narrowly
```

**Structure Decision**: Extend current packages and adapter selection. New source paths are
proposed work, not files created during planning. Generated API/DTO/mapper implementations stay
under `target/generated-sources` and are never manually edited.

### Execution design

1. Generated optional scalar header documents the public contract. Inspect raw servlet header
   enumeration for exact cardinality and characters; do not use collection binding/comma splitting.
   No header selects the existing creation statements unchanged. Validate the request body through
   existing binding/validation; authorization remains in force for both paths.
2. Canonical scope is operation token `addVisitToOwner`, integer owner/pet IDs and exact accepted
   key. Compare nullable LocalDate and exact description in Java; never compare raw JSON or SQL
   text collation. Missing/null date follows existing mapper behavior, not a new date default.
3. Check the local active-create registry before potentially blocking database reads. An active
   owner means 409. Otherwise read an unexpired or NULL-deadline completed row using a short completed read
   transaction; match gives replay and difference gives 422. Completed replay does not acquire
   a creation gate, so parallel replays do not spuriously conflict.
4. On absent/expired result, atomically try to claim that scope in the registry. On failure,
   re-evaluate local completion/publication state and return 409 only while a create is active;
   if it just completed, redo lookup. The winner calls a different proxied service bean with a
   fresh transaction and rechecks the row, eliminating the lookup/admission race.
5. Inside that transaction: replay/conflict if a fresh unexpired or NULL-deadline completed row appeared; otherwise delete
   an expired row for this scope, map the same VisitFieldsDto, assign the same pet stub, call
   existing ClinicService.saveVisit (joining this transaction), materialize response snapshot,
   flush persistence constraints, and insert retained success. Any failure rolls back all writes.
6. Atomically commit the visit and complete immutable response snapshot with both deadline
   fields NULL. NULL/NULL means a committed success protected indefinitely, not an in-progress
   reservation. After the worker proxy successfully returns (commit C), sample UTC Clock T >= C
   and call a separate proxied `VisitCreationResultFinalizationService` transaction to conditionally
   set both fields once: T and T + 86,400,000 ms. Match scope, original response_visit_id and
   NULL/NULL to avoid modifying a replacement or established deadline. The controller returns
   the original 201 only after the creation commit; finalization is attempted before gate release.
   A finalization rollback/error cannot roll back the already committed visit or make its key
   reusable: preserve replay protection, record a safe operational error and return the stored 201.
   Hold admission through worker and finalizer proxy return; publish completion before finally
   releasing it. Read a missing deadline as indefinitely replayable. Never automatically finalize
   it on replay, cleanup or restart: an operator follows operations.md. Established deadlines are
   immutable. Test delayed creation, flush, commit and finalization, crash between transactions,
   finalizer rollback/uncertain outcome, and restart with NULL deadlines. No finite pre-commit
   padding can satisfy the minimum post-commit window.
7. Replay reconstructs VisitDto from retained scalar snapshot, returns original 201 and stored
   Location, and never looks up the live visit. No replay marker. Expired reuse is atomic with
   its replacement; failed replacement leaves the key available for the next attempt.

The coordinator must not participate in an ambient transaction. Keep read transactions separate
from the worker transaction so repeatable-read snapshots do not hide the second lookup. Use a
separate bean (no self-invocation) for transactional work and rollback on all worker failures.

### Persistence and validation strategy

Add only this feature's table to all four existing schema variants. For persistent deployments,
DDL is additive CREATE TABLE IF NOT EXISTS without result seeds or destructive alteration.
HSQL's existing reset script must also reset this table for fresh demo/test lifetimes. Introduce
no Flyway/Liquibase policy. Use composite scope identity with uppercase hexadecimal key storage
to preserve exact key equality even under MySQL's case-insensitive collations. See data-model.md.

Implement bounded expired-result cleanup in a separate service transaction, using the same
repository contract. A periodic invocation may delete a limited batch by immutable scope and
observed non-null expiry; conditional deletion must never remove a replacement or a NULL-deadline success.
No controller/admin endpoint is added. Lazy per-scope expiry remains authoritative even if the
cleanup pass is delayed or disabled. Do not log raw keys, request bodies or stored snapshots;
use existing SLF4J with outcome/status and safe identifiers only.

Validation covers all FR-001–018 using the matrix in quickstart.md. All three adapters execute
the shared service suite on HSQLDB and H2; MySQL and PostgreSQL execute the same suite for each
adapter plus restart/key-collation cases. `AbstractVisitIdempotencyTests` is the shared runner
base. Reusable JUnit test interfaces `VisitIdempotencyConcurrencyTests`,
`VisitIdempotencyRollbackTests`, `VisitIdempotencyRetentionTests` and
`VisitIdempotencyCleanupTests` contribute default test methods through that base, using
`VisitIdempotencyTestSupport` fixture accessors. The H2/HSQLDB/MySQL/PostgreSQL concrete runners
extend the same base and select the adapter through test-only `petclinic.test.repository`;
use a test-only ActiveProfilesResolver to validate the selector and isolate each application
context. Execute the common assertions in every one of the 12 combinations; persistent runners
add restart assertions. MVC/header/security suites run separately in full application contexts. Use existing drivers and environment-supplied test
databases, not a new production dependency. Tests use separate threads/transactions and latches;
commit-sensitive tests must not inherit a rollback-only test transaction. Keep existing
unkeyed/owner/pet outcomes as characterization assertions even where docs disagree.

## Complexity Tracking

No constitutional violation or exception is needed. Added coordinator, worker, finalizer and
small read boundary are justified by nonblocking 409 behavior and transaction commit ordering.
Deadline metadata is finalized in a second service transaction because its anchor must be sampled
after creation commit; atomic visit/snapshot protection already exists before that transaction.
Interrupted finalization deliberately requires operator recovery and retains the key indefinitely. A completed
row alone cannot signal an uncommitted attempt, while an in-memory cache alone cannot survive
restart. Research records the simpler alternatives and why they do not meet these requirements.
