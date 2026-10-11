# Quickstart Validation: Visit Creation Idempotency

This guide validates the implemented visit idempotency feature.
Use disposable clinic test data: these scenarios create and may delete visits.

## Prerequisites

- Java 17+, the checked-in Maven wrapper, curl and jq.
- Run commands from the repository root. The existing Maven commands may fetch dependencies.
- For durable restart checks, Docker Compose or an existing disposable MySQL/PostgreSQL database.
  Existing runtime URL/user/password environment overrides apply; do not use production data.
- The authoritative operation must be regenerated from `src/main/resources/openapi.yml`.
  Contract: [contracts/visit-idempotency.md](contracts/visit-idempotency.md).
  Storage/lifecycle: [data-model.md](data-model.md).

## 1. Build, regenerate and verify

Existing repository gate:

```sh
./mvnw -B verify
```

Expected: compilation/generation and existing/new tests pass, line coverage >=85%, branch
coverage >=66%, with existing generated API/DTO exclusions only. No skip flags or broader
exclusions. A missing dependency or environment failure must be reported, not called a pass.

Focused suites:

```sh
./mvnw -Dtest='VisitIdempotency*Tests' test
```

Expected coverage of coordinator, MVC/header errors, real transactions, retained adapters,
Clock boundaries and security. Commit-sensitive tests must run without a surrounding test
transaction and must observe committed state from independent transactions.

For environments that block Mockito self-attachment, use the existing Mockito agent (locate
its version through the local Maven dependency tree/cache), preserving JaCoCo:

```sh
export JAVA_TOOL_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar"
./mvnw -B verify
```

Generated `OwnersApi.addVisitToOwner` has parameters in this order:
`Integer ownerId, Integer petId, VisitFieldsDto visitFieldsDto, String idempotencyKey`.
The optional header has generated `@Pattern`, `@Size` and `@RequestHeader` annotations;
raw servlet header validation additionally enforces cardinality without comma splitting.
`VisitFieldsDto` and `VisitDto` remain unchanged. Generated sources reside under `target/`.

## 2. First creation and matching replay

Start the existing demo command in one terminal:

```sh
./mvnw spring-boot:run
```

Default endpoint base is `http://localhost:9966/petclinic`. Default security is disabled;
for enabled mode, repeat requests with authorized test credentials and verify denials
separately. Use a fresh key for each guide run.

```sh
VISIT_BASE='http://localhost:9966/petclinic'
VISIT_KEY="guide-$(date +%s)-$$"
VISIT_ENDPOINT="$VISIT_BASE/api/owners/1/pets/1/visits"
curl -sS -D /tmp/petclinic-visit-first.headers -o /tmp/petclinic-visit-first.json \
  -w '%{http_code}\n' -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $VISIT_KEY" \
  --data '{"date":"2026-10-11","description":"idempotency guide"}' "$VISIT_ENDPOINT"
curl -sS -D /tmp/petclinic-visit-replay.headers -o /tmp/petclinic-visit-replay.json \
  -w '%{http_code}\n' -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $VISIT_KEY" -H 'X-Client-Attempt: 2' \
  --data '{ "description": "idempotency guide", "date": "2026-10-11" }' "$VISIT_ENDPOINT"
jq -S . /tmp/petclinic-visit-first.json
jq -S . /tmp/petclinic-visit-replay.json
```

Expected: both return 201, bodies are equal after JSON formatting normalization, and both
Location headers are the same `/api/visits/{id}`. Confirm from the visit list that exactly one
new visit was created. Replaying after editing/deleting that visit returns its original body
and Location, without restoring or modifying it; the automated suite proves this case.

## 3. Reject changed requests and invalid keys

```sh
curl -sS -i -H 'Content-Type: application/json' -H "Idempotency-Key: $VISIT_KEY" \
  --data '{"date":"2026-10-11","description":"different visit"}' "$VISIT_ENDPOINT"
curl -sS -i -H 'Content-Type: application/json' -H 'Idempotency-Key;' \
  --data '{"date":"2026-10-11","description":"invalid key"}' "$VISIT_ENDPOINT"
curl -sS -i -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: repeated' -H 'Idempotency-Key: repeated' \
  --data '{"date":"2026-10-11","description":"repeated header"}' "$VISIT_ENDPOINT"
```

Expected: changed request gets 422; empty and repeated values get 400. Each has
`Content-Type: application/problem+json`, applicable status/title/detail, and zero additional
visits. The original key remains replayable after mismatch. A fresh single opaque key with
comma punctuation is valid; automated raw-header tests verify no comma splitting.

Send two otherwise accepted creations without Idempotency-Key: each must independently
create a visit as before. Check existing invalid body and owner/pet edge cases against the
characterization baseline, not a new membership rule. Other endpoints must remain unchanged.

## 4. Deterministic automated acceptance matrix

| Coverage | Required proof |
| --- | --- |
| FR-001–005 | Header absent/present; length 1/128/129; empty/blank/control/non-ASCII; repeated values; comma punctuation; key case; scope independence; reordered JSON; other headers; nullable date; exact description changes |
| FR-006–009 | Original 201/body/Location; immutable snapshot after edit/delete; two completed replays both succeed; latch-held first transaction gets 409 for matching/differing duplicates, then replay or 422 after commit |
| FR-010–011 | Invalid body then corrected same key; failure after visit write; result insert/flush/commit failure; neither visit nor success remains; admission released; subsequent attempt succeeds |
| FR-012–013 | Clock before/at/after T+24h; T sampled after delayed creation commit; no refresh; finalizer failure/crash preserves indefinite replay; operator-only missing-deadline recovery; expired replacement rollback; persistent restart preserves established or NULL deadlines |
| FR-014–015 | New problem media type/status; generated parameter-validation errors map correctly; legacy errors unchanged; real filter-chain anonymous/wrong-role rejection and authorized replay |
| FR-016–018 | Shared contract across retained adapters/dialects; no owner/pet fix, new authorization, other endpoint behavior, replay marker or cross-instance guarantee; full existing quality gates |
| Cleanup | Expired-candidate deletion cannot remove a fresh replacement; delayed/disabled cleanup does not alter lazy expiry correctness |

Use latches/barriers and bounded test waits, not sleeps or racing two curl commands. Force both
pre-commit overlap and post-commit replay. Keep the active gate through deliberately delayed creation commit and immediate deadline
finalization; release after their proxy returns. Advance Clock during a held creation commit
and assert the deadline anchor is sampled afterward. Finalization rollback must leave the
committed visit and indefinite replay protection intact; creation rollback leaves neither. Do not require cross-instance duplicate prevention.

All four concrete database runners extend `AbstractVisitIdempotencyTests`, which implements
the reusable concurrency, rollback, retention/finalization and cleanup JUnit test interfaces.
Each interface uses the same real-service fixture accessors; default test methods are inherited
by every concrete runner. Header/MVC/security tests run separately. The test-only profiles
resolver validates `petclinic.test.repository` and selects exactly one adapter.
Run the shared real-adapter suite for H2/HSQLDB with `jdbc`, `jpa`, `spring-data-jpa`. For MySQL
and PostgreSQL, use environment-gated suites `VisitIdempotencyMysqlTests` and
`VisitIdempotencyPostgresTests`, each driven by the test-only property
`petclinic.test.repository` selecting the retained adapter. Configure the existing
MYSQL_URL/MYSQL_USER/MYSQL_PASS or POSTGRES_URL/POSTGRES_USER/POSTGRES_PASS variables.

```sh
for VISIT_ADAPTER in jdbc jpa spring-data-jpa; do
  ./mvnw -Dtest=VisitIdempotencyH2Tests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
  ./mvnw -Dtest=VisitIdempotencyHsqlTests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
  ./mvnw -Dtest=VisitIdempotencyMysqlTests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
  ./mvnw -Dtest=VisitIdempotencyPostgresTests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
done
```

The persistent suites explicitly skip with an unavailable-configuration reason if their URL
environment variable is missing. A skipped external matrix is not executed coverage. Repeat the shared assertions, case-distinct keys,
null-date rollback behavior where applicable, and restart assertions on persistent dialects.

## 5. Persistent restart validation

Example existing Compose service and profile invocation:

```sh
docker compose --profile mysql up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql,spring-data-jpa
```

Perform step 2 and retain its key, body, Location and completion deadline from test inspection.
Stop only the application normally; leave the database running with its data intact. Restart:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql,spring-data-jpa \
  -Dspring-boot.run.arguments=--spring.sql.init.mode=never
```

Retry with the original key/body within retention. Expected: same 201/body/Location and no new
visit; original deadline remains fixed. Repeat using PostgreSQL and both other adapters through
the automated matrix. Initialize once, then disable startup init for retained-data validation;
never run a schema-reset script between the two application contexts. Also interrupt the
process between creation commit and deadline finalization: verify NULL/NULL survives restart,
matching replay works even after 24 hours, changed body gets 422, cleanup skips the row and
only the operator procedure in [operations.md](operations.md) may establish its deadline. HSQL's destructive reset
and default in-memory H2 are not evidence of persistent restart durability.

## 6. Existing smoke checks and evidence

With the application running and node/jq available, run the existing script:

```sh
bash postman-tests.sh
```

It may fetch Newman packages and creates API data/reports. Expected: existing scenarios still
pass plus narrowly added keyed replay/error checks. Record actual command results, tested
profiles, coverage, generated contract/signature checks and any unavailable external databases.
This guide and the planning gate are not substitutes for those implementation-time results.

## Implementation evidence — 2026-10-11

Baseline: Java 21.0.2 compiles with the repository's Java 17 release target. The first
OwnerRestControllerV1Tests run failed because Mockito could not self-attach inside the sandbox.
Supplying the existing Mockito 5.23.0 jar with JAVA_TOOL_OPTIONS=-javaagent:<local jar path>
resolved instrumentation: 29 existing tests passed. JaCoCo instrumentation remained enabled.
No coverage threshold/exclusion changed. Exact baseline response: 201 with VisitDto and
Location /api/visits/{id}; the controller assigns the supplied pet ID without owner lookup.
Missing/mismatched owner therefore does not independently reject a visit. Missing pet fails
through existing database integrity handling (404). Missing/null date maps to null: HSQLDB
accepts it, whereas the existing H2 NOT NULL visit_date constraint can reject it. Invalid
body validation uses the existing problem response. Additional characterization assertions
are run before feature integration; these observations do not introduce membership fixes.

Foundation verification: shared storage suite passed 3 tests per combination on H2/HSQLDB
with jdbc, jpa and spring-data-jpa (18 executions, zero failures/errors/skips). The Spring
Data fragment uses the conventional VisitCreationResultRepositoryCustomImpl name and shared
JPA operations without exposing a second service repository bean.

US1–US4: replay/compatibility/security/validation tests pass. Shared concurrency and rollback
checks passed 9 tests per H2/HSQLDB combination on all three adapters (54 executions in the
latest complete rounds). Driver commit rejection is injected by a test-only connection wrapper
that rolls back and throws at Connection.commit; production transaction configuration is unchanged.
The enabled-security wrong-role baseline currently returns 500 through the existing generic
advice; keyed first/replay requests preserve that rejection and perform no writes. Anonymous
requests return 401. No authorization or unrelated error policy was changed.

US5: JDBC and plain JPA each passed all four database suites (69 tests per adapter, zero
failures/errors/skips). Spring Data JPA passed 68 tests before the additional isolated H2
lifetime check; that check is included in the full build below. Shared retention tests also
cover nullable dates using each dialect's existing constraint, finalizer rollback, an exception
after finalizer commit, indefinite replay, operator CAS, exact expiry and replacement rollback.
Restart helpers explicitly set spring.profiles.active to avoid adding the test resource's
default adapter. SQL initialization is disabled in both persistent restart contexts.

Operator recovery rehearsal: passed on disposable MySQL 8.4 and PostgreSQL 16.3 for all three
adapters. The test interrupts finalization, closes the creation context, reopens the same DB,
advances Clock by more than ten days, verifies replay/mismatch, then executes the runbook's
bound conditional UPDATE in a service transaction. The first update affects one row, the
second zero; snapshot is unchanged and the stored deadline is exactly 24 hours after the
operator anchor. Shared cleanup tests independently prove NULL exclusion and stale-candidate
replacement safety. Repeating the feature DDL twice retains the original result.

### Final execution evidence

The final clean build used the existing Mockito agent via JAVA_TOOL_OPTIONS and both disposable
persistent database configurations. Command: `./mvnw -B clean verify`. Result: **BUILD SUCCESS**,
326 tests, zero failures/errors/skips. Clean regeneration and compilation passed. JaCoCo reports
**1538/1791 lines (85.87%)** and **324/470 branches (68.94%)**; the unchanged >=85% / >=66%
gates passed. Only the existing generated REST API/DTO exclusions apply. The first clean run
passed its tests but missed branch coverage; focused generated-method-validation and legacy
fallback tests resolved that gap. No threshold, exclusion, dependency or skip flag was changed.

| Database | jdbc | jpa | spring-data-jpa |
| --- | --- | --- | --- |
| H2 | 17 passed | 17 passed | 17 passed |
| HSQLDB | 16 passed | 16 passed | 16 passed |
| MySQL 8.4 | 18 passed | 18 passed | 18 passed |
| PostgreSQL 16.3 | 18 passed | 18 passed | 18 passed |

All 12 combinations executed with zero failures/errors/skips (207 matrix test executions).
JDBC/JPA used the four-runner focused Maven command with `-Dpetclinic.test.repository=jdbc`
and `=jpa`; the final Spring Data JPA results are included in the clean full build. Every
runner inherits the same real transaction/concurrency/rollback/retention/cleanup assertions.
Persistent runners additionally verify schema retention and context restart. H2 additionally
closes and reopens an isolated in-memory database and proves the previous lifetime is gone.
External configuration was available throughout final validation; none was counted as a skip.

The disposable containers used local ports 13306 (MySQL) and 15432 (PostgreSQL), with no host
volumes. They and the packaged smoke-test application were stopped after verification.
Test-only support consists of the profile resolver, controllable Clock, repository transaction
hooks and commit-failure connection wrapper; none changes production transaction configuration.

Packaged application smoke: `java -jar target/spring-petclinic-rest-4.0.2.jar` with a fresh H2
lifetime and security disabled. Curl verified identical first/replay 201 body/Location,
422 mismatch, 400 empty/repeated headers with problem media/type, original replay after every
error and independent unkeyed creations. `npm_config_yes=true bash postman-tests.sh` passed:
12 requests, 17 assertions, zero failures, preserving both original scenarios. Newman HTML
report: `src/test/postman/reports/report.html` (ignored generated output).

### Requirement and constitution audit

| Requirement | Implementation and executable evidence |
| --- | --- |
| FR-001–003 | Only the owner/pet visit POST routes to the coordinator; absent header retains the original statements. Replay/Compatibility/Validation tests cover unkeyed behavior, cardinality, visible ASCII, boundaries, commas and failed-body reuse. |
| FR-004–005 | VisitCreationScope stores operation/owner/pet/exact hex key; result equality compares nullable date and exact description. Conflict/Replay tests prove owner-only and pet-only independence, case, JSON order/format and ignored unrelated headers. |
| FR-006–007 | Atomic historical scalar snapshot with no visit FK. Replay tests compare original status/body/Location after visit edits and deletion; matrix tests independently count committed visits. |
| FR-008–009 | Owner-checked local admission remains held through worker/finalizer proxy return. Latch tests assert prompt 409 for matching/different active bodies, other-scope progress and completed replay/422 without mutation. |
| FR-010–011 | Transactional worker encloses visit and snapshot with rollbackFor Exception. All adapters exercise insert/flush/before-commit/driver-commit/checked failures, independent committed-state reads and corrected retries. |
| FR-012–013 | Clock tests prove post-commit sampling, fixed expiry, exact/after-boundary reuse, replacement rollback, finalizer rollback/uncertain commit, indefinite NULL protection and one-time recovery. Persistent restart and in-memory lifetime tests establish the documented deployment boundary. |
| FR-014–015 | Narrow problem handlers use explicit media/type/status/title/detail; generated method-validation and fallback tests protect unrelated errors. Enabled filter-chain tests verify anonymous and wrong-role denial, authorized creation/replay and user-independent key scope. |
| FR-016–018 | Three retained adapters/four dialects pass; authoritative OpenAPI regenerated during clean build, DTO schemas unchanged. No new runtime dependencies, endpoints, replay header, membership fix or cross-instance mechanism. |
| SC-001 | Matrix/replay tests assert one committed visit and identical original identity/status/body/Location. |
| SC-002 | Real-commit and delayed-finalizer latch tests reject overlap without waiting; gate release is owner checked. |
| SC-003 | Validation/Conflict tests assert defined errors, zero mutation and original result preservation. |
| SC-004 | Both persistent dialects and every adapter preserve established and NULL deadlines through context restart. |
| SC-005 | Owner characterization, Compatibility/Security suites, all existing tests and existing Newman scenarios pass. |
| SC-006 | Curl and Newman complete first/lost-response replay using the same key and no extra lookup. |

Constitution I: the existing conditional authentication and OWNER_ADMIN method guard remain
in place; enabled-mode denial tests pass. II: worker, finalizer and cleanup mutations have
service transaction boundaries; coordinator forbids an outer transaction. III: the complete
12-combination matrix passes. IV: only openapi.yml is authoritative; generated OwnersApi
matches its four-parameter operation and generated DTO schemas remain unchanged. V: the
fresh full verification and unchanged coverage gates pass; remote PR checks/branch protection
were not claimed or modified.

Scope/logging review: feature logging records only error class/finalization failure and directs
operators to recovery; it does not log raw/encoded keys, bodies or snapshots. Keyed exception
advice avoids exception text/SQL and validation-field logging; legacy unkeyed behavior is
preserved. Existing framework DEBUG/SQL diagnostics remain governed by repository logging
configuration. No security configuration, unrelated endpoint implementation, owner/pet
membership rule or generated source was edited. There are no implementation verification
blockers. Single-instance guarantees and persistent-only restart durability remain explicit;
NULL deadlines require the documented operator recovery. Constitution administrative TODOs
remain the previously recorded human governance decisions.


## Convergence verification — T053–T055

This pass adds verification without changing production behavior. The new shared creation-phase
test holds the transaction before snapshot insertion, independently of the existing commit and
finalizer latches. Matching/differing duplicate threads have a test-only retained-result storage
guard that fails if any repository read/write is attempted. The HTTP counterpart exercises the
real MVC/controller/advice path and checks the complete 409 problem, timestamp, empty validation
errors, absence of replay/Retry-After headers, no additional committed data and successful replay
once the original completes.

The shared adapter base now edits the live visit's pet/date/description in a committed transaction,
then deletes it in another. Independent snapshot reads and replay assertions preserve every
original response value and both deadline fields, while counts prove no replacement visit/result
is created and the deleted visit remains absent.

`VisitIdempotencyNullableDateTests` uses an isolated HSQLDB MVC context and a controlled Clock.
Both omitted-to-null and null-to-omitted JSON sequences produce one identical original 201 result;
advancing Clock by an hour does not renew its deadline. Unkeyed omitted/null requests independently
create visits with null dates. `VisitIdempotencyH2NullableDateTests` proves both forms retain H2's
existing NOT NULL rejection for keyed and unkeyed requests; a corrected request reuses each failed
key. Existing replay tests now assert id, petId, date, description and the exact Location.

Focused validation: 49 tests passed, zero failures/errors/skips. The first sandboxed attempt
could not bind the existing in-memory lifecycle test's ephemeral web server; re-running with
local socket access passed. The existing Mockito agent workaround remains in use, preserving
JaCoCo. The Postman report ignore entry was corrected from a nonmatching `./src/...` pattern to
an anchored `/src/...` pattern; generated reports are now confirmed ignored.

### Expanded matrix results

The latest counts below supersede the earlier 207-execution matrix. All persistent databases
were available; every combination executed with zero failures/errors/skips.

| Database | jdbc | jpa | spring-data-jpa |
| --- | --- | --- | --- |
| H2 | 19 passed | 19 passed | 19 passed |
| HSQLDB | 18 passed | 18 passed | 18 passed |
| MySQL 8.4 | 20 passed | 20 passed | 20 passed |
| PostgreSQL 16.3 | 20 passed | 20 passed | 20 passed |

Total: 231 executions, 77 per adapter. These include both new inherited scenarios plus every
existing commit/concurrency/rollback/retention/cleanup and persistent restart assertion.
Each adapter used the same command, substituting jdbc, jpa or spring-data-jpa:

```sh
./mvnw -B \
  -Dtest=VisitIdempotencyH2Tests,VisitIdempotencyHsqlTests,VisitIdempotencyMysqlTests,VisitIdempotencyPostgresTests \
  -Dpetclinic.test.repository=jdbc test
```

The commands retained JAVA_TOOL_OPTIONS for the existing Mockito agent and used MYSQL_URL /
MYSQL_USER / MYSQL_PASS and POSTGRES_URL / POSTGRES_USER / POSTGRES_PASS against disposable
local ports 13306 and 15432. New transaction hooks exist only in test sources and are imported
through TestConfiguration. Production services, adapters, schemas, security and OpenAPI remain
unchanged in this convergence implementation pass.

### Final convergence gate

`./mvnw -B clean verify` passed with the existing Mockito agent and both disposable persistent
databases enabled: **342 tests, zero failures/errors/skips**. Fresh JaCoCo results are
**1545/1802 lines (85.74%)** and **325/470 branches (69.15%)**. Both unchanged >=85% line and
>=66% branch gates passed; no exclusion, threshold, dependency or skip flag changed. These
are the latest full-build figures and supersede the earlier pre-convergence totals. OpenAPI
regeneration/compilation and every existing regression suite passed as part of this clean build.

T053, T054 and T055 are complete. Checklist markers were read only (16/16 checked); the
55-task implementation ledger has no remaining unchecked tasks. No production behavior change
was required. Existing curl/Newman results above remain the prior packaged-application smoke
evidence; this pass did not rerun them because only tests, documentation and an ignore pattern
changed. The two no-volume disposable database containers were stopped after validation.
