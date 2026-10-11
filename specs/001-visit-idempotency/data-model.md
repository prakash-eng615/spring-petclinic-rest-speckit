# Data Model: Visit Creation Idempotency

## Existing Visit

Unchanged `Visit`/VisitFieldsDto/VisitDto semantics. Creation still assigns the supplied pet ID
and uses existing validation/mapping. No owner-membership fix or new date default. The current
response has id, petId, nullable date and description. Actual database nullability may reject
values accepted by request validation; such rollback must not consume a key.

## VisitCreationScope

| Field | Representation | Constraint |
| --- | --- | --- |
| operation | String / VARCHAR(32) | Constant `addVisitToOwner` for this feature |
| ownerId | Integer / INTEGER | Existing validated path value; no added membership check |
| petId | Integer / INTEGER | Existing validated path value |
| keyHex | String / VARCHAR(256) | Uppercase hexadecimal of 1–128 accepted ASCII bytes; 2–256 chars |

Composite primary key: `(operation, owner_id, pet_id, key_hex)`. Use the same value object for
local registry identity and repository lookup, with Java equals/hashCode over every field.
JPA uses an embeddable, serializable composite ID; JDBC binds each component as parameters.
Raw keys are not trimmed or case-normalized before encoding. Encoding preserves exact equality;
`A` maps to `41`, while `a` maps to `61`. No user ID or application-instance ID is added.

## VisitCreationResult

Proposed table: `visit_creation_results`. One retained success per scope, replaced only after
expiry. These are planned logical columns, not an executable migration.

| Field | SQL representation | Meaning/validation |
| --- | --- | --- |
| scope fields | As above, NOT NULL | Composite identity |
| request_date | DATE, nullable | Validated nullable LocalDate for equality |
| request_description | VARCHAR(255), NOT NULL | Exact validated description, no trim/case normalization |
| response_visit_id | INTEGER, NOT NULL | Original generated visit identity |
| response_pet_id | INTEGER, NOT NULL | Original response pet identity |
| response_date | DATE, nullable | Original response date |
| response_description | VARCHAR(255), NOT NULL | Original response description |
| response_status | INTEGER, NOT NULL | 201 |
| response_location | VARCHAR(255), NOT NULL | Original `/api/visits/{id}` value |
| completed_at_ms | BIGINT, nullable | UTC Clock post-commit deadline anchor T; NULL until finalized |
| expires_at_ms | BIGINT, nullable | T + 86,400,000 milliseconds; NULL means indefinite replay protection |

The complete response record is inserted in the same transaction as the visit, with both
deadline fields NULL. Enforce the pair invariant: both NULL or both non-null with
expires_at_ms = completed_at_ms + 86,400,000. NULL/NULL is a committed success, never durable
in-progress state. It is replayable indefinitely and excluded from expiry lookup/deletion.
After creation commit C, sample UTC Clock T >= C and finalize both fields in a separate service
transaction using a conditional update matching scope, response_visit_id and NULL/NULL.
Do not modify response/request fields. Once set, the deadline is immutable. A failed finalizer
leaves successful replay protection intact; a rollback of the creation itself leaves no result.
Only the immediate creation flow attempts automatic finalization. Interrupted/failed finalization
requires the operator procedure in operations.md; no replay/restart/cleanup may start its clock.

Do not add FK/ORM relationships to Visit, Pet or Owner. Original snapshots must survive their
updates/deletions. Scalar IDs are historical response facts. Reconstruct a new VisitDto from
response columns; do not remap a current Visit or derive Location using a changed URI builder.

Use an expiry index where useful for bounded cleanup. Key scope uniqueness is required on all
four dialects. Standard INTEGER/DATE/VARCHAR/BIGINT suffice; no JSON, LOB, database enum or
vendor-specific upsert is needed. HSQLDB resets this table with its existing demo/test reset;
persistent-profile DDL is additive and never truncates retained results or clinic data.

## Runtime-only CreationAdmission

A concurrent registry maps scopes to active creation ownership tokens. This state exists only
for an admitted creation, never for all retained keys. It is not a database table, persistent
reservation or distributed lease. Registry ownership lasts through creation and immediate finalization proxy return
and is removed with an ownership check in finally. Successful completion is published before
release; failed completion releases without a replay result.

## State transitions

| Observed state | Request | Result/state transition |
| --- | --- | --- |
| Header absent | Any | Existing unkeyed path; no feature record or admission |
| Invalid key/body | New attempt | Reject through applicable validation; no key consumption |
| Active creation within this instance | Valid duplicate, any body | 409; no write by duplicate |
| Unexpired or NULL-deadline retained success | Equal validated date/description | Replay original 201/body/Location; no mutation |
| Unexpired or NULL-deadline retained success | Different validated values | 422; original unchanged |
| No retained success or expired result | Valid request | Try local admission, then recheck within fresh worker transaction |
| Admitted, recheck finds unexpired or NULL-deadline result | Equal/different | Replay/422 without new visit; release admission |
| Admitted, no usable result | Creation commits | Visit and retained result with NULL deadline become visible together |
| Committed result, admission held | Immediate finalization commits | Fixed post-commit deadline established; publish/release |
| Committed result | Finalization fails/process stops | Indefinite replay protection; operator recovery only |
| Admitted, creation fails/rolls back | Any failure | No new visit/success record; release admission |
| Expired result replacement fails | Rollback | Old expired row may remain, but next attempt can still reuse key |
| Persistent normal restart | Completed success | Lookup retained result and same original deadline |
| In-memory data reset | Any | New lifetime; previous visit/results are outside restart guarantee |

Multiple concurrently running instances are outside duplicate-prevention guarantees. Database
uniqueness is defensive integrity, not a promise to coordinate their active requests. Do not
translate unrelated database uniqueness errors into new idempotency responses without proving
that they concern this feature's scope.

## Repository contract

`VisitCreationResultRepository` exposes scoped lookup, insert, conditional expired deletion,
bounded expired-candidate lookup, and conditional one-time deadline finalization. Both expiry
operations exclude NULL deadlines; finalization also matches the original response_visit_id. A scope/result model has identical meaning across:

- JDBC adapter: NamedParameterJdbcTemplate, bound scope parameters and explicit row mapping.
- JPA adapter: EntityManager find/persist/remove or conditional bulk delete with persistence
  context handling; flush constraint errors inside the worker.
- Spring Data JPA adapter: profile-selected repository plus custom fragment where needed,
  consistent with existing overrides; do not create a second repository bean for one profile.

Deletion of expired candidates must match both scope and observed expiry (and expiry <= cutoff).
A cleanup pass must not delete a newly committed replacement of the same scope. Lazy expiry
and replacement inside the admitted creation transaction remain authoritative.

## Invariants and tests

1. Visit write and retained result write share one transaction manager/connection context;
   either both commit or neither does, including failures at flush/commit.
2. Request equality uses Java LocalDate/String equality, never SQL collation or raw JSON.
3. First success snapshot is immutable. Deadline metadata transitions once from NULL/NULL to
   a fixed pair; established deadlines are immutable during replay, restart and mismatch handling.
4. All rejected key/conflict requests cause zero new visit writes or retained-result mutations.
5. At `now >= expiresAt` for a non-null deadline, a key is eligible for replacement; retries
   never renew expiry. NULL deadlines never expire automatically. Delayed commit cannot reduce
   protection below 24 hours after creation commit.
6. Gate ownership cannot be released before commit/rollback, leaked after failure, or claimed
   by completed replays. Tests deliberately hold creation and commit boundaries with latches.
