# Quickstart Validation: Visit Creation Idempotency

This is a post-implementation validation guide. Planning has not created the feature code,
new test classes, schema or OpenAPI changes yet. Existing commands are identified below;
proposed test names/options must be implemented by the subsequent task/implementation phases.
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

Proposed focused suites (to be created during implementation):

```sh
./mvnw -Dtest='VisitIdempotency*Tests' test
```

Expected coverage of coordinator, MVC/header errors, real transactions, retained adapters,
Clock boundaries and security. Commit-sensitive tests must run without a surrounding test
transaction and must observe committed state from independent transactions.

## 2. First creation and matching replay

Start the existing demo command in one terminal after implementation:

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
| FR-012–013 | Controllable Clock before T+24h and at/after T+24h; no refresh on replay; delayed creation does not anchor TTL at arrival; expired replacement rollback; persistent normal restart with original deadline |
| FR-014–015 | New problem media type/status; generated parameter-validation errors map correctly; legacy errors unchanged; real filter-chain anonymous/wrong-role rejection and authorized replay |
| FR-016–018 | Shared contract across retained adapters/dialects; no owner/pet fix, new authorization, other endpoint behavior, replay marker or cross-instance guarantee; full existing quality gates |
| Cleanup | Expired-candidate deletion cannot remove a fresh replacement; delayed/disabled cleanup does not alter lazy expiry correctness |

Use latches/barriers and bounded test waits, not sleeps or racing two curl commands. Force both
pre-commit overlap and post-commit replay. Keep the active gate through a deliberately delayed
commit; release after rollback. Do not require cross-instance duplicate prevention.

Run the shared real-adapter suite for H2/HSQLDB with `jdbc`, `jpa`, `spring-data-jpa`. For MySQL
and PostgreSQL, use environment-gated proposed suites `VisitIdempotencyMysqlTests` and
`VisitIdempotencyPostgresTests`, each driven by proposed test-only property
`petclinic.test.repository` selecting the retained adapter. Configure the existing
MYSQL_URL/MYSQL_USER/MYSQL_PASS or POSTGRES_URL/POSTGRES_USER/POSTGRES_PASS variables.

```sh
for VISIT_ADAPTER in jdbc jpa spring-data-jpa; do
  ./mvnw -Dtest=VisitIdempotencyMysqlTests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
  ./mvnw -Dtest=VisitIdempotencyPostgresTests -Dpetclinic.test.repository="$VISIT_ADAPTER" test
done
```

The new suites must fail or explicitly report missing database configuration, not present a
skipped external matrix as executed coverage. Repeat the shared assertions, case-distinct keys,
null-date rollback behavior where applicable, and restart assertions on persistent dialects.

## 5. Persistent restart validation

Example existing Compose service and profile invocation, after the feature table is implemented:

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
never run a schema-reset script between the two application contexts. HSQL's destructive reset
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
