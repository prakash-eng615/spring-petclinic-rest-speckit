# Research: Visit Creation Idempotency

Date: 2026-10-11. Source of existing architecture: discovery report section 3,
TECHNOLOGY AND PATTERNS BRIEF. This research resolves design unknowns without building,
installing dependencies, starting services or modifying application code. Two read-only
research agents inspected persistence and contract/test concerns; findings are consolidated here.

## 1. Runtime and existing behavior

**Decision:** Retain Java 17, Maven wrapper 3.9.16 and the existing Boot 4.1.1 stack.
No runtime version change is part of the feature.

**Rationale:** The cached parent POM at
`~/.m2/repository/org/springframework/boot/spring-boot-starter-parent/4.1.1/spring-boot-starter-parent-4.1.1.pom:14-16`
explicitly sets Java 17/compiler release. This resolves an uncertainty in the brief;
it does not prove a successful build. `pom.xml` and wrapper declarations remain authoritative.
`OwnerRestControllerV1.addVisitToOwner:175-187` returns 201/VisitDto/Location and assigns petId
without owner lookup. `VisitMapper` maps optional null date without introducing a default.

**Alternatives considered:** Upgrading Java or correcting owner/pet behavior is unnecessary
scope expansion. Inferring runtime compatibility from version numbers alone is insufficient.

## 2. Concurrent creation and transaction boundaries

**Decision:** Feature-specific local active-create registry plus durable completed-result lookup.
Read completed results without claiming a creation gate; claim only for absent/expired results,
and recheck in a fresh worker transaction. Hold the gate through creation commit/rollback and,
for new committed success, the immediate deadline-finalizer proxy return.
Check active creation before database reads so a transaction lock cannot turn a required 409
into waiting. Handle a just-finished admission race by rereading completed results.

**Rationale:** The user explicitly limited the guarantee to one running instance. A local
atomic put-if-absent provides immediate collision detection without persisted reservations.
The worker is a separately proxied service, and ClinicService.saveVisit joins its transaction.
The coordinator rejects ambient transactions so it cannot release a gate before an outer
commit. Spring's proxy rules require external invocation, not self-invocation.
See [Spring transaction annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

**Alternatives considered:** Database locks alone can block duplicates; a persistent IN_PROGRESS
lease requires recovery rules unnecessary here; holding a gate only inside a transactional
method releases it before the interceptor commits. Claiming all replays produces false 409s.
Cross-instance coordination remains out of scope, not an added instance component of key scope.

## 3. Atomic retained results and replay snapshot

**Decision:** Store visit creation and immutable response snapshot in one service transaction.
Persist only completed-success records, with request comparison values and response scalar
values, status, location and initially NULL retention metadata. Only deadline metadata may
transition once in a separate post-creation-commit service transaction; snapshot fields stay immutable. No foreign keys to live owner, pet or visit.

**Rationale:** Existing JDBC assigns generated visit ID, JPA uses persist/IDENTITY, and Spring
Data uses the same model (`JdbcVisitRepositoryImpl.save`, `JpaVisitRepositoryImpl.save`,
`SpringDataVisitRepository`, `BaseEntity`). A snapshot reproduces original fields after edits
or deletion. Retained records are independent history, not live entity relationships.

**Alternatives considered:** A post-commit cache write can lose retry protection. Storing only
visit ID cannot replay a deleted visit or the original body. Serialized JSON is viable, but
four response scalar fields are simpler and avoid JSON/LOB dialect and serializer-version
coupling. The Location and status are retained explicitly rather than recomputed on replay.

## 4. Repository and schema portability

**Decision:** Add one retained-result repository abstraction with JDBC, JPA and Spring Data
implementations following existing profile selection. Use an embeddable composite scope ID,
uppercase hex of the accepted ASCII key, DATE fields, VARCHAR descriptions and BIGINT UTC
millisecond timestamps. Compare request date/description in Java, not SQL collation.

**Rationale:** Existing profiles remain supported. Hex is reversible and collision-free for
accepted keys; uppercase hex of `A` differs from that of `a` even with case-insensitive SQL
collation. No new arbitrary key hash or dialect-specific JSON/upsert is necessary. Existing
transaction managers must cover both writes; tests prove participation for each adapter.

**Alternatives considered:** Raw VARCHAR uniqueness changes exact equality on some collations.
Digest-only request equality introduces unnecessary canonicalization/collision handling.
A new standalone JDBC connection may not join the visit transaction. Although
[JpaTransactionManager supports participating JDBC access](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html),
matching current adapters avoids relying on mixed-access configuration for this feature.

## 5. Equality, raw headers and errors

**Decision:** Compare validated nullable LocalDate and exact description. Ignore formatting,
unknown body properties discarded by existing binding, and unrelated headers. Count raw servlet
header occurrences, preserve one opaque value, and enforce 1–128 U+0021–U+007E without trimming.
A comma in one value is punctuation, not a list separator. Keep optional scalar OpenAPI binding;
it does not replace raw cardinality checks.

**Rationale:** Existing DTO binding removes JSON formatting differences. Spring collection
header conversion splits comma-separated text, which conflicts with opaque punctuation;
see [Spring request headers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestheader.html).
The current generated OwnersApi uses validation; generated size/pattern constraints may reject
before controller code. Implement narrow handling for violations of the new header parameter
(including applicable method-validation exception types), delegating all unrelated failures
to existing advice behavior. Test regenerated signatures and actual MVC dispatch.

**Alternatives considered:** Raw JSON hashes distinguish semantically equal requests. Trimming
keys changes identity. Globally replacing validation/error advice changes legacy callers.
New domain exceptions map only this feature's 400/409/422 to explicit problem media type;
Spring supports ProblemDetail from exception handlers
([Spring error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)).

## 6. Retention, reset lifetimes and cleanup

**Decision (revised by user clarification):** Commit visit and replay snapshot atomically with
NULL deadline fields, then sample UTC Clock after proxy commit return and conditionally finalize
T and T + 24 hours in a separate service transaction. This guarantees at least 24 hours after
creation commit regardless of its latency. NULL deadlines protect success indefinitely; failed
or interrupted finalization requires operator recovery, never automatic finalization on replay
or restart. Existing deadlines never change. Expiry checks use non-null `now >= expiresAt`;
replay never updates retention. Reuse deletes/replaces an expired row in the creation transaction.
Add bounded cleanup with conditional deletes against the observed expiry, outside creation
transactions; correctness never depends on cleanup running.

**Rationale:** Clock and BIGINT avoid dialect timezone/precision differences and enable exact
boundary tests. Completion/commit ordering and delayed-work tests must ensure early admission
timestamps or delayed commits do not shorten retention. There is no durable in-progress state
to recover; a NULL-deadline row is already a committed successful response.
Persistent restart tests initialize data once and restart with SQL init disabled; HSQL's
existing table-drop script and H2's default memory URL are intentionally not durable profiles.

**Alternatives considered:** Refreshing TTL on replay contradicts the request. A cache-only TTL
cannot survive restart. Cleaning all rows on startup destroys successful retry history.
Unrelated PostgreSQL unnamed-index initialization drift is recorded, not repaired here.

## 7. Verification and security

**Decision:** Keep the existing role guard before creation/replay. Use full-context security
checks, deterministic concurrent tests with latches, shared real-adapter tests, fault injection,
Clock-boundary tests, persistent context restart and existing unkeyed regressions. Exercise all
three adapters on each configured database through reusable tests and external DB credentials.

**Rationale:** Standalone MockMvc and mocked service tests cannot prove filter-chain security,
transaction commit, or database equality. Tests sensitive to real commit use no ambient test
transaction; failure injection includes retained-result insert and deferred flush/commit failure.
Existing wrapper verify and Newman/JaCoCo gates remain mandatory.

**Alternatives considered:** Timing sleeps produce flaky overlap tests. Testing only the default
adapter leaves retained supported profiles unverified. New Testcontainers infrastructure is not
required; external databases supplied by the validation environment suffice.

## Research disposition

All feature-design unknowns are resolved. No new business clarification or constitutional
exception is needed. Build success, generated-code compilation, actual SQL dialect execution,
coverage and deployed behavior are implementation verification obligations, not research claims.
No application or infrastructure configuration was changed during this research.
