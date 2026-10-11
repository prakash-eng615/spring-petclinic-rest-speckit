# Tasks: Opt-in Visit Creation Idempotency

**Input**: Design documents in `specs/001-visit-idempotency/`.
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/visit-idempotency.md`, `quickstart.md`, and `.specify/memory/constitution.md`.
**Tests**: Required by FR-017 and the constitutional gates. Write acceptance tests before the corresponding implementation; new behavior should initially fail. Compatibility characterization should pass against the baseline. Commit-sensitive tests must use real commits and independent observation transactions, never a surrounding rollback-only test transaction.
**Organization**: Five story phases in specification order (P1, P1, P1, P2, P2), after shared prerequisites. This file schedules work; unchecked tasks are not evidence of implementation or passing checks.

## Format and Path Conventions

Each task uses `- [ ] Tnnn [P?] [USn?] Description with file path`. Paths are relative to the repository root. Proposed Java classes follow the existing package conventions. `[P]` means independent files within the explicitly stated ready batch; finish preceding dependencies before starting that batch. Story labels appear only in story phases. Generated files under `target/generated-sources/` are build outputs and must never be hand edited.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish compatibility evidence and reusable deterministic test infrastructure without changing application behavior.

- [ ] T001 Characterize the current unkeyed endpoint using `src/test/java/org/springframework/samples/petclinic/rest/controller/OwnerRestControllerV1Tests.java` and record exact success, validation, nullable-date, missing-owner, missing-pet and mismatched-owner/pet outcomes in `specs/001-visit-idempotency/quickstart.md`; preserve observed behavior even where OpenAPI advertises different membership handling.
- [ ] T002 Create controllable UTC Clock, latch/commit hooks and independent-transaction count helpers in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyTestSupport.java`; use bounded waits, disposable data and no sleep-based race assertions or new runtime dependencies.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared identity, storage and real-adapter test wiring. Complete this phase before story implementation.

- [ ] T003 Create serializable JPA-embeddable scope with full equals/hashCode in `src/main/java/org/springframework/samples/petclinic/model/VisitCreationScope.java`: operation String/VARCHAR(32), "Constant `addVisitToOwner` for this feature"; ownerId Integer/INTEGER, "Existing validated path value; no added membership check"; petId Integer/INTEGER, "Existing validated path value"; keyHex String/VARCHAR(256), "Uppercase hexadecimal of 1–128 accepted ASCII bytes; 2–256 chars". Composite identity is `(operation, owner_id, pet_id, key_hex)` with every field NOT NULL; add neither user nor instance ID, trimming nor case normalization.
- [ ] T004 Create immutable retained scalar entity in `src/main/java/org/springframework/samples/petclinic/model/VisitCreationResult.java` (depends on T003), quoting and implementing the data-model constraints: scope fields "As above, NOT NULL", "Composite identity"; request_date "DATE, nullable", "Validated nullable LocalDate for equality"; request_description "VARCHAR(255), NOT NULL", "Exact validated description, no trim/case normalization"; response_visit_id "INTEGER, NOT NULL", "Original generated visit identity"; response_pet_id "INTEGER, NOT NULL", "Original response pet identity"; response_date "DATE, nullable", "Original response date"; response_description "VARCHAR(255), NOT NULL", "Original response description"; response_status "INTEGER, NOT NULL", "201"; response_location "VARCHAR(255), NOT NULL", "Original `/api/visits/{id}` value"; completed_at_ms "BIGINT, nullable", "UTC Clock post-commit deadline anchor T; NULL until finalized"; expires_at_ms "BIGINT, nullable", "T + 86,400,000 milliseconds; NULL means indefinite replay protection". Enforce both NULL or both non-null with the exact 24-hour difference; response snapshot immutable, deadline pair assigned once after creation commit. Use Java LocalDate/String equality and no Visit/Pet/Owner FK or ORM relationships; reconstruct a fresh response from snapshot fields.
- [ ] T005 Define scoped find, insert, flush, bounded expired-candidate lookup, conditional expired delete and one-time deadline finalization in `src/main/java/org/springframework/samples/petclinic/repository/VisitCreationResultRepository.java` (depends on T004); deletion matches scope, observed non-null expiry and expiry <= cutoff; finalization matches scope, original response_visit_id and NULL/NULL, atomically assigning both fields; every adapter uses its service transaction context and no expiry operation deletes NULL-deadline successes.
- [ ] T006 [P] Implement profile-selected bound-parameter JDBC mapping in `src/main/java/org/springframework/samples/petclinic/repository/jdbc/JdbcVisitCreationResultRepositoryImpl.java` after T005; preserve nullable dates, all snapshot values and exact encoded key equality without opening an independent connection/transaction.
- [ ] T007 [P] Implement profile-selected EntityManager adapter in `src/main/java/org/springframework/samples/petclinic/repository/jpa/JpaVisitCreationResultRepositoryImpl.java` after T005; flush within the worker transaction and handle persistence-context state after conditional expiry deletion.
- [ ] T008 [P] Implement Spring Data adapter and required custom fragment in `src/main/java/org/springframework/samples/petclinic/repository/springdatajpa/SpringDataVisitCreationResultRepository.java`, `src/main/java/org/springframework/samples/petclinic/repository/springdatajpa/VisitCreationResultRepositoryCustom.java` and `src/main/java/org/springframework/samples/petclinic/repository/springdatajpa/VisitCreationResultRepositoryImpl.java` after T005; follow existing adapter overrides and expose exactly one selected result repository bean.
- [ ] T009 [P] Add `visit_creation_results` with T003–T004 column constraints, composite primary key and expiry index in `src/main/resources/db/h2/schema.sql` after T005; no result seeds, FK relationships or changes to existing clinic tables.
- [ ] T010 [P] Add `visit_creation_results` with T003–T004 column constraints, composite primary key and expiry index in `src/main/resources/db/hsqldb/schema.sql` after T005; integrate its drop/recreate into the existing demo/test reset without changing clinic semantics.
- [ ] T011 [P] Add additive `CREATE TABLE IF NOT EXISTS` for `visit_creation_results` with T003–T004 column constraints, composite primary key and dialect-compatible expiry index in `src/main/resources/db/mysql/schema.sql` after T005; retain existing rows on initialization, no seeds/truncation/new migration framework, and preserve case-distinct keys through hex encoding.
- [ ] T012 [P] Add additive `CREATE TABLE IF NOT EXISTS` for `visit_creation_results` with T003–T004 column constraints, composite primary key and safely repeatable expiry-index DDL in `src/main/resources/db/postgres/schema.sql` after T005; retain existing data and avoid unrelated schema fixes.
- [ ] T013 [P] Add injectable UTC Clock configuration in `src/main/java/org/springframework/samples/petclinic/config/VisitIdempotencyConfig.java` after T005, reusing an existing compatible bean if present and allowing the controllable test Clock to override it.
- [ ] T014 Create shared real-adapter storage assertions and the common fixture base in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/AbstractVisitIdempotencyTests.java` after T006–T013; cover scoped round-trip, nullable fields, case-distinct keys, composite uniqueness, immutable snapshot fields, conditional expiry deletion, one-time finalization, NULL-deadline protection and transaction rollback with independent committed-state reads. Expose real coordinator/worker/Clock/count/commit-fault fixture accessors for reusable JUnit behavioral test interfaces added by later stories.
- [ ] T015 Wire and run six H2/HSQLDB adapter combinations in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyH2Tests.java` and `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyHsqlTests.java` after T014; make both concrete runners extend AbstractVisitIdempotencyTests, with test-only adapter selection from petclinic.test.repository (default spring-data-jpa), and create src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyProfilesResolver.java implementing ActiveProfilesResolver to reject unsupported selectors and isolate database/adapter contexts. Verify repository selection/schema/storage; inherit all later behavioral interface tests through the same base. Run each runner once per adapter, yielding six combinations.

**Checkpoint**: All retained adapters have the same persisted identity and snapshot contract; in-memory dialect wiring passes before orchestration starts.

## Phase 3: User Story 1 — Recover a successful visit after a lost response (Priority: P1)

**Goal**: First success and matching retries return one original visit result, including after live visit mutation/deletion.
**Independent Test**: Submit a valid keyed request, discard its response, retry with reordered JSON and unrelated headers, and assert one visit plus identical 201/body/Location; edit/delete the visit and assert the original snapshot still replays without writes.

### Tests for User Story 1

- [ ] T016 [P] [US1] Add first/replay MVC contract tests in `src/test/java/org/springframework/samples/petclinic/rest/controller/VisitIdempotencyReplayTests.java`; assert existing four body fields, original Location, reordered JSON/formatting, ignored unrelated headers, accepted missing/null date equality, and no replay marker.
- [ ] T017 [P] [US1] Extend `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/AbstractVisitIdempotencyTests.java` with first success, matching replay, one committed visit/result and immutable replay after visit update/delete; execute through the T015 contexts and compare persisted scalar snapshots rather than current Visit mapping.

### Implementation for User Story 1

- [ ] T018 [US1] Implement dedicated short read transaction in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationResultReadService.java`; return unexpired or NULL-deadline retained successes using the Clock, independently of a later worker transaction and its database snapshot.
- [ ] T019 [US1] Implement separate proxied transactional worker in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationTransactionService.java`: fresh in-transaction recheck; replay/mismatch or conditional expired removal; existing VisitMapper and pet stub; existing ClinicService.saveVisit joining the same transaction; materialize/flush visit and persist the immutable result with NULL/NULL deadline; rollback creation on all worker failures and return only through successful proxy commit. Also implement separate proxied src/main/java/org/springframework/samples/petclinic/service/VisitCreationResultFinalizationService.java to accept a post-creation-commit Clock sample T and conditionally finalize T/T+86,400,000 in its own transaction; never sample the retention anchor before the creation proxy returns. Preserve nullable-date mapping and existing owner/pet semantics.
- [ ] T020 [US1] Implement runtime-only nonblocking ownership registry in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationAdmission.java`; scope-to-token entries exist only for active creations, with atomic claim and owner-checked release, never a persistent reservation or retained-key cache.
- [ ] T021 [US1] Implement coordinator in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationCoordinator.java` after T018–T020: stay outside ambient transactions, check active creation before DB reads, replay completed matching results without claiming, distinguish mismatch, claim only absent/expired scopes, re-read when a lost claim has just completed, call proxied worker and, only for a newly committed success, sample UTC Clock T and call the separate finalizer. Keep ownership through both proxy returns before completion publication/finally release. If finalization fails, preserve the committed 201 and indefinite replay protection, emit a safe operational error, and require operator recovery; never automatically finalize on replay/restart or re-create a committed visit.
- [ ] T022 [US1] Implement raw servlet header validation in `src/main/java/org/springframework/samples/petclinic/rest/idempotency/IdempotencyKeyValidator.java`: absent means unkeyed; exactly one value, 1–128 U+0021–U+007E characters; no trimming/case normalization/comma splitting; repeated identical values invalid; encode accepted bytes using T003 semantics.
- [ ] T023 [US1] Add feature-specific invalid-key/in-progress/mismatch exceptions in `src/main/java/org/springframework/samples/petclinic/rest/idempotency/VisitIdempotencyException.java` and narrow handlers in `src/main/java/org/springframework/samples/petclinic/rest/advice/ExceptionControllerAdvice.java`; use the exact 400/409/422 types/titles from `specs/001-visit-idempotency/contracts/visit-idempotency.md`, explicit application/problem+json and existing ProblemDetail fields with empty schemaValidationErrors; echo no raw key/body and preserve unrelated handlers.
- [ ] T024 [US1] Update only addVisitToOwner in `src/main/resources/openapi.yml` with optional scalar Idempotency-Key, minLength 1, maxLength 128, pattern `^[!-~]{1,128}$`, opaque/cardinality description, existing 201 Location, 409/422 problems and additional 400 problem media without removing legacy JSON; regenerate using the Maven lifecycle in `pom.xml`, inspect the generated signature and never hand edit generated API/DTOs.
- [ ] T025 [US1] Integrate the regenerated operation and validator/coordinator in `src/main/java/org/springframework/samples/petclinic/rest/controller/v1/OwnerRestControllerV1.java` after T021–T024; retain the no-header creation statements and existing role guard, preserve binding/validation, return committed original or retained response with explicit stored status/Location, update signature-dependent calls and mock construction in src/test/java/org/springframework/samples/petclinic/rest/controller/OwnerRestControllerV1Tests.java in this same task, compile all tests, then run T016–T017 and existing owner-controller tests before the US1 checkpoint.

**Checkpoint**: US1 is a demonstrable recovery increment. Atomic writes, fixed retention and gate ownership are required even at this checkpoint; remaining stories are still required before release.

## Phase 4: User Story 2 — Keep existing clients working (Priority: P1)

**Goal**: Existing unkeyed clients and authorization continue to behave identically.
**Independent Test**: Two unkeyed requests create two visits; legacy valid/invalid and owner/pet cases match T001; with security enabled anonymous/wrong-role callers cannot create or replay, while an authorized caller can do both.

### Tests for User Story 2

- [ ] T026 [P] [US2] Add T001 baseline regression assertions in `src/test/java/org/springframework/samples/petclinic/rest/controller/VisitIdempotencyCompatibilityTests.java`; cover independent unkeyed creates, legacy status/body/Location and validation media, nullable-date/owner/pet edge behavior, zero feature records/admissions for absent header and unchanged unrelated owner/pet/visit endpoints.
- [ ] T027 [P] [US2] Add real application filter-chain and method-security tests in `src/test/java/org/springframework/samples/petclinic/rest/controller/VisitIdempotencySecurityTests.java`; explicitly enable security and test anonymous, authenticated wrong-role and OWNER_ADMIN callers for both first request and stored replay, plus disabled-security behavior and zero writes on denial.

### Implementation for User Story 2

- [ ] T028 [US2] Reconcile optional-header integration with the T001 baseline in `src/main/java/org/springframework/samples/petclinic/rest/controller/v1/OwnerRestControllerV1.java`; ensure no-header calls bypass all feature lookup/gate/write work and both keyed paths retain the existing authorization and validation boundary; resolve T026–T027 failures without adding owner-membership checks or changing security configuration.
- [ ] T029 [US2] Complete broader compatibility regression assertions in `src/test/java/org/springframework/samples/petclinic/rest/controller/OwnerRestControllerV1Tests.java` using the signature/mock updates already completed in T025; run existing controller/security regressions and T026–T027 and preserve all existing observable contracts.

**Checkpoint**: Keyed recovery works alongside legacy clients and the actual security stack.

## Phase 5: User Story 3 — Prevent overlap and conflicting key reuse (Priority: P1)

**Goal**: One running instance prevents overlapping creation and rejects completed mismatches without modifying the original success.
**Independent Test**: Hold a creation/commit with latches; matching and differing duplicates get prompt 409 and zero writes. After commit they get replay/422 respectively; simultaneous completed replays both succeed, and distinct scopes progress independently.

### Tests for User Story 3

- [ ] T030 [P] [US3] Add reusable JUnit default-test interface for real-thread/transaction concurrency in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyConcurrencyTests.java`; latch creation and commit separately, assert bounded nonblocking 409 before potentially blocking DB reads for both duplicate bodies, exactly one visit, post-commit replay/mismatch, parallel completed replays without 409, and no gate leaks or early release. Implement this interface on AbstractVisitIdempotencyTests so all four database runners inherit the same tests for each adapter, including a held finalization transaction; commit/fault hooks come from the common fixture accessors.
- [ ] T031 [P] [US3] Add scope/equality contract tests in `src/test/java/org/springframework/samples/petclinic/rest/controller/VisitIdempotencyConflictTests.java`; different owner/pet scopes and case-distinct keys are independent, comma is opaque, changed date/description case/internal whitespace yields 422, and 409/422 have exact problem fields/media with zero mutations and the original success still replayable.

### Implementation for User Story 3

- [ ] T032 [US3] Complete ownership race handling in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationAdmission.java` against T030: atomically distinguish an active owner from a just-finished claim, prevent stale-token release, and keep memory proportional to active creations within this instance only.
- [ ] T033 [US3] Complete concurrent result/admission handoff in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationCoordinator.java` after T032; repeat completed lookup after a just-finished claim, enforce fresh worker recheck and exact validated-body comparison, publish completion before release, and avoid holding creation gates for completed replays.
- [ ] T034 [US3] Reconcile 409/422 handling in `src/main/java/org/springframework/samples/petclinic/rest/advice/ExceptionControllerAdvice.java` with T030–T031 and rerun both suites; preserve the retained snapshot/deadline, emit no sensitive request data or extra replay/Retry-After headers, and never translate unrelated database uniqueness failures into feature conflicts.

**Checkpoint**: Deterministic overlap/conflict evidence proves the single-instance guarantee; no cross-instance guarantee is claimed.

## Phase 6: User Story 4 — Correct a failed attempt and retry (Priority: P2)

**Goal**: Invalid, rejected and rolled-back attempts consume no successful key; corrected attempts can reuse it.
**Independent Test**: Reject an invalid key/body, then succeed with a corrected request; inject failures after visit write, at result insert/flush and at commit and observe neither committed visit nor result, released admission, and successful retry. A 422 must leave the original replayable.

### Tests for User Story 4

- [ ] T035 [P] [US4] Add raw-header and generated-method validation tests in `src/test/java/org/springframework/samples/petclinic/rest/controller/VisitIdempotencyValidationTests.java`; exercise lengths 1/128/129, empty/blank/control/non-ASCII, repeated identical/different values, punctuation/comma, no trimming, case preservation, malformed/invalid body then valid same-key retry, 400 problem fields and zero consumed results. Test servlet-level values where an HTTP client would normalize/reject them.
- [ ] T036 [P] [US4] Add reusable JUnit default-test interface for real-commit failure injection in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyRollbackTests.java` for all three adapters through T015 wiring: failures after visit write, during result insert/flush and transaction commit, checked/unchecked worker failure, expired replacement rollback, independent-state assertions and subsequent reuse; never substitute mocked service success for transactional proof. Implement the interface on AbstractVisitIdempotencyTests to run unchanged assertions in all 12 combinations. Separately assert finalizer failure leaves the committed visit/snapshot intact and indefinitely replayable rather than treating that metadata failure as creation rollback.

### Implementation for User Story 4

- [ ] T037 [US4] Reconcile exact cardinality/ASCII rejection in `src/main/java/org/springframework/samples/petclinic/rest/idempotency/IdempotencyKeyValidator.java` with T035; inspect raw getHeaders enumeration before scalar binding can lose multiplicity and ensure invalid keys never reach admission or persistence.
- [ ] T038 [US4] Handle generated Idempotency-Key parameter violations narrowly in `src/main/java/org/springframework/samples/petclinic/rest/advice/ExceptionControllerAdvice.java`, including applicable ConstraintViolationException/HandlerMethodValidationException paths observed from T024; map only this new header constraint to 400 problem+json and preserve legacy body/path/unrelated exception representations.
- [ ] T039 [US4] Complete rollback/flush boundaries in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationTransactionService.java` and failure release in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationCoordinator.java` against T035–T036; prohibit transactional self-invocation. Require visit and replay snapshot writes to share one creation transaction. Finalize deadline metadata in a separate proxied service transaction after creation commit, as specified in T019/T021. Finalization failure must preserve the committed visit and replay snapshot. Retain admission until proxy failure returns and run the failure suites across all retained adapters.

**Checkpoint**: No failure path leaves partial success or a poisoned local key.

## Phase 7: User Story 5 — Retry within a predictable retention window (Priority: P2)

**Goal**: Retain original success for at least 24 hours after creation commit using a fixed post-commit deadline; protect missing-deadline successes indefinitely until operator recovery, preserve both states on persistent restart and permit new creation at established expiry.
**Independent Test**: Control Clock at just before/exactly/after T+24h, verify retries never extend expiry, delay creation/flush to prove T is not arrival, restart persistent contexts without resetting data and replay the original identity/deadline. In-memory reset begins a new lifetime.

### Tests for User Story 5

- [ ] T040 [P] [US5] Add reusable JUnit default-test interface for Clock-boundary and finalization tests in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyRetentionTests.java`; implement this interface on AbstractVisitIdempotencyTests for all 12 combinations. Delay creation/flush and the actual creation commit while advancing Clock, assert T is sampled only after proxy commit return and protection lasts at least 24 hours afterward. Cover finalizer delay/rollback, NULL-deadline replay/mismatch after more than 24 hours, crash between commits, no automatic recovery, one-time operator finalization/CAS rejection of existing deadlines, replay before expiry, new identity at/after expiry, no renewal, expired replacement rollback/retry and in-memory lifetime scope.
- [ ] T041 [P] [US5] Create shared persistent restart assertions in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyRestartTestSupport.java`; commit a keyed response in one application context, close normally, open a new context against the same disposable database with initialization disabled, and verify same 201/body/Location/id and original deadline through expiry without any retained runtime registry.
- [ ] T042 [US5] Implement environment-gated MySQL suite in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyMysqlTests.java` after T041; extend AbstractVisitIdempotencyTests and use VisitIdempotencyProfilesResolver, MYSQL_URL/MYSQL_USER/MYSQL_PASS and test-only petclinic.test.repository to inherit storage/replay/concurrency/rollback/retention/cleanup assertions unchanged and add case-collation, schema-retention and restart assertions (including NULL-deadline restart) for jdbc/jpa/spring-data-jpa, explicitly reporting unavailable configuration.
- [ ] T043 [P] [US5] Implement environment-gated PostgreSQL suite in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyPostgresTests.java` after T041, independently of T042; extend AbstractVisitIdempotencyTests with the same profiles resolver and inherited behavioral interfaces; use POSTGRES_URL/POSTGRES_USER/POSTGRES_PASS and test-only petclinic.test.repository to run the same assertions, including NULL-deadline restart, for all three adapters, with initialization disabled across restart and explicit unavailable-configuration reporting.
- [ ] T044 [P] [US5] Add reusable JUnit default-test interface for bounded cleanup races in `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/VisitIdempotencyCleanupTests.java` after T041; implement this interface on AbstractVisitIdempotencyTests for all 12 combinations; force candidate replacement before deletion and prove its fresh row survives, exclude NULL-deadline successes even after prolonged Clock advancement, bound batch size, and verify disabled/delayed cleanup does not change lazy expiry behavior.

### Implementation for User Story 5

- [ ] T045 [US5] Implement separate transactional bounded expired-result cleanup in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationResultCleanupService.java` using the shared repository and Clock; condition every delete on scope/observed expiry/cutoff, never delete a replacement or expose an admin endpoint, and keep lazy expiry authoritative regardless of periodic invocation.
- [ ] T046 [US5] Reconcile expiry replacement and post-commit finalization in `src/main/java/org/springframework/samples/petclinic/service/VisitCreationTransactionService.java` and `src/main/java/org/springframework/samples/petclinic/service/VisitCreationResultReadService.java` against T040–T044; include src/main/java/org/springframework/samples/petclinic/service/VisitCreationResultFinalizationService.java and the coordinator: enforce only non-null now >= expiresAt as unused, preserve NULL-deadline success until operator action and existing deadlines across restart, and run all shared behavioral interfaces plus available persistent restart suites.

**Checkpoint**: Fixed-window guarantees have deterministic lifetime and persistent restart evidence; unavailable external databases remain recorded verification gaps.

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Complete all-story verification and review evidence without widening scope.

- [ ] T047 [P] Extend `src/test/postman/petclinic-nonregressiontests.postman_collection.json` with a narrowly scoped first/replay, mismatched-body and invalid-header sequence using fresh keys/disposable visit data; assert original body/Location and new problem media while retaining every existing scenario.
- [ ] T048 [P] Update `specs/001-visit-idempotency/quickstart.md` with implemented suite/profile commands and test-only properties, exact generated signature checks, single-instance/persistent-only restart scope, and an execution-evidence table; validate the operator procedure in specs/001-visit-idempotency/operations.md on disposable persistent data, including interrupted finalization, safe conditional update, immutable existing deadlines and cleanup exclusion; remove provisional test-name wording only after those suites exist.
- [ ] T049 Run `./mvnw -B verify` using `pom.xml` and record actual result/coverage in `specs/001-visit-idempotency/quickstart.md`; require existing >=85% line and >=66% branch coverage, preserve generated-only exclusions, inspect generated API/DTO changes, and resolve failures without skips, lowered thresholds or new runtime dependencies.
- [ ] T050 Execute the six H2/HSQLDB and six MySQL/PostgreSQL adapter combinations, including inherited real commit/concurrency/rollback/retention/finalization/cleanup cases in every combination and restart cases on persistent dialects only, using the suites under `src/test/java/org/springframework/samples/petclinic/service/visitIdempotency/`; record exact profile results and unavailable external infrastructure in `specs/001-visit-idempotency/quickstart.md`, never counting a skipped database as passing coverage.
- [ ] T051 Run first/replay/error curl scenarios and `bash postman-tests.sh` against the running application per `specs/001-visit-idempotency/quickstart.md`; record outcomes there, retaining disposable-data precautions and resolving smoke regressions.
- [ ] T052 Audit FR-001–018 and SC-001–006 against implementation/tests and all five constitution principles, recording evidence or outstanding blockers in `specs/001-visit-idempotency/quickstart.md`; inspect for raw-key/body/snapshot logging, changes to unrelated endpoints/auth/owner-pet rules, hand-edited generated files, distributed guarantees or extra framework/dependency scope before marking work complete.

## Dependencies & Execution Order

```text
Setup T001–T002
  -> Foundation T003 -> T004 -> T005
       -> parallel adapter/schema/Clock batch T006–T013
       -> shared storage assertions T014 -> six-context wiring T015
  -> US1 T016–T025 (first recovery milestone)
  -> US2 T026–T029 (compatibility/security)
  -> US3 T030–T034 (overlap/conflicts)
  -> US4 T035–T039 (failure/reuse)
  -> US5 T040–T046 (retention/restart/cleanup)
  -> Polish T047–T052 (release evidence)
```

All story phases depend on the completed foundation. US2–US5 exercise the US1 creation path and therefore depend on US1 for execution; each has its own independently runnable acceptance tests. US4 additionally relies on the completed US3 gate/handoff behavior for failure races. US5 cleanup/replacement failures rely on US4 rollback correctness. Prefer the listed order for a single implementer. Test authoring in different story files can overlap after foundation; this does not remove dependencies for execution or story completion.

Within US1, T016–T017 precede implementation; T018 -> T019 -> T020 -> T021 -> T022 -> T023 -> T024 -> T025 is the conservative serial order. T025 includes all generated-signature test-call and mock updates; T029 cannot be a prerequisite for compiling or validating US1. In later stories, write their tests first, then serialize implementation changes. Join T040–T041 before the T042–T044 batch; T045 requires cleanup tests T044; T046 requires all preceding US5 tasks. T049 follows T047–T048; T050–T052 follow successful build and use the same disposable databases/application serially unless explicitly isolated.

Shared-file edits must never run concurrently: coordinator (T021/T033/T039), worker (T019/T039/T046), advice (T023/T034/T038), controller (T025/T028), shared base/interface wiring (T014/T017/T030/T036/T040/T044), and quickstart evidence (T001/T048–T052). `[P]` is permission to work on different files after prerequisites, not a directive to share mutable test data concurrently.

## Parallel Execution Examples

| Ready batch | Independent tasks | Constraint |
| --- | --- | --- |
| Foundation, after T005 | T006, T007, T008, T009, T010, T011, T012, T013 | Different adapter, schema and config files; join before T014 |
| US1, after foundation | T016 + T017 | MVC test file vs shared service assertions; both before production work |
| US2, after US1 | T026 + T027 | Compatibility tests vs full-security tests; separate contexts/data |
| US3, after US2 | T030 + T031 | Concurrency tests vs scope/problem contract tests |
| US4, after US3 | T035 + T036 | Header/method validation tests vs real-transaction failure tests |
| US5, after US4 | T040 + T041 | Clock-retention tests vs restart support |
| US5, after T040–T041 | T042 + T043 + T044 | MySQL suite, PostgreSQL suite and cleanup tests; independent files/data |
| Polish, after all stories | T047 + T048 | Postman collection vs quickstart guide; join before verification |

## Requirement Traceability

| Requirements | Main tasks |
| --- | --- |
| FR-001–005: opt-in, compatibility, header, scope, equality | T001, T003, T016, T021–T026, T028, T031, T035, T037–T038 |
| FR-006–007: original success and immutable replay | T004–T019, T025 |
| FR-008–009: overlap and mismatch | T020–T023, T030–T034 |
| FR-010–011: failure reuse and atomicity | T005–T008, T014, T019, T036, T039 |
| FR-012–013: retention and restart | T009–T013, T019, T040–T046 |
| FR-014–015: problem responses and authorization | T023–T024, T027–T028, T031, T035, T038 |
| FR-016–018: retained repositories, generated contract, verification, scope | T006–T015, T024, T029, T042–T043, T047–T052 |
| SC-001–006 | US1 replay, US3 overlap, US4 no-write/failure reuse, US5 restart, US2 compatibility; consolidated evidence T052 |

## Implementation Strategy

1. Complete Setup and Foundation, proving every retained adapter can commit/read/roll back the same result contract.
2. Deliver US1 as the first internal MVP/demo: lost-response recovery and immutable replay. Keep its transaction, gate and fixed-expiry invariants intact rather than replacing them with temporary in-memory successes.
3. Add US2 compatibility/security, US3 deterministic overlap/mismatch, US4 failure reuse, and US5 boundary/restart/cleanup evidence. Run each story's independent tests at its checkpoint and rerun affected earlier assertions when shared code changes.
4. Complete the full build/coverage, dialect-adapter matrix, curl/Newman checks and constitution review. All five stories are release scope; an MVP demo or unavailable external database is not a full release pass.

## Notes

- No new deployable unit, generic idempotency framework, auth policy, membership correction, persistent in-progress lease, new runtime library or migration framework is scheduled.
- Normal restart durability applies only to persistent deployments. Cross-instance duplicate prevention is out of scope.
- Only mark a task checked after its concrete change and stated verification are complete. Record blocked checks honestly and keep dependent release evidence incomplete.
