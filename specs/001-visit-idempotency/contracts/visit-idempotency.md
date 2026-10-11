# Public Contract Delta: Owner/Pet Visit Creation

Design artifact for implementing `src/main/resources/openapi.yml`, operation
`addVisitToOwner`. That file remains the sole API generator input. Do not replace the rest
of its contract or regenerate from this document. Paths below exclude the deployment context
`/petclinic`; existing server and response schemas remain in force.

## Operation and request

`POST /api/owners/{ownerId}/pets/{petId}/visits`

Existing body: VisitFields (`date`: optional nullable date where existing binding accepts it;
`description`: required, length 1–255). Keep existing request/body validation and owner/pet
behavior. The optional header is named `Idempotency-Key` (HTTP field name case-insensitive).

| Header property | Contract |
| --- | --- |
| Absence | Entirely existing unkeyed creation behavior |
| Presence | Exactly one opaque value; no trimming or comma splitting |
| Characters | U+0021–U+007E inclusive; visible ASCII only |
| Length | 1–128 characters inclusive |
| Equality | Exact value, case-sensitive |
| Multiple values | Reject even if values are identical |

Represent the header as optional `type: string`, `minLength: 1`, `maxLength: 128`,
`pattern: '^[!-~]{1,128}$'` in the authoritative OpenAPI parameter. Its description must also
state cardinality and opaque punctuation semantics; generated scalar binding cannot prove
cardinality. Implement narrow key-constraint exception mapping where generation adds method
validation before controller execution. Do not globally alter unkeyed error handling.

Scope is `addVisitToOwner + ownerId + petId + key value`. Equality within scope compares only
the validated visit date and description. JSON order, insignificant JSON whitespace and
unrelated headers do not matter. Whitespace within description and description case matter.
Missing/null optional dates compare equally if binding accepts both as the same value.
Other accepted/discarded properties retain existing binding behavior.

## Outcome table

Authorization and existing body validation still apply; no key bypasses them. The guarantee
applies within one running instance, and to its normal restart with persistent storage.

| Situation | Status | Media type | Effect |
| --- | --- | --- | --- |
| Header absent | Existing outcome | Existing representation | Existing behavior, independent creates |
| First successful keyed request | 201 | application/json | One visit; snapshot becomes durable with visit |
| Completed matching replay before expiry | 201 | application/json | Original body and Location; no visit mutation |
| Invalid key | 400 | application/problem+json | No visit or consumed key |
| Valid duplicate while create is active | 409 | application/problem+json | No duplicate write; body equality does not change status |
| Completed scope/key with changed logical body | 422 | application/problem+json | No writes; original success retained |
| Body validation or persistence failure | Existing applicable error | Existing applicable representation | No successful key consumption; rollback leaves reusable key |
| Expired scope/key | Normal new attempt allowed | Applicable representation | May create a new visit under the same key |

A request observed after successful completion gets replay/422, not an in-progress response.
Concurrent completed replays must not spuriously produce 409. Different scopes are independent.
Different concurrently running instances are outside the duplicate-prevention guarantee.

## Success representation

Retain existing Visit schema and status 201. Add/document its existing response header:

- `Location`: URI reference `/api/visits/{id}` with the original created visit ID.
- Body fields: original `id`, `petId`, `date`, `description`, including existing null handling.
- Replays return those retained values even if the visit has changed or been deleted.
- Transport-generated metadata may change. No new replay-marker header is introduced.

## New problem responses

Add 409 and 422 responses to this operation; add `application/problem+json` alongside the
existing 400 representation (do not remove `application/json` for legacy validation).
Each new problem response uses the existing ProblemDetail schema fields: `type`, `title`,
`status`, `detail`, `timestamp`, `schemaValidationErrors` (empty array for these key errors).
Use explicit `application/problem+json` content type; do not rely on negotiation defaults.

| Status | type | title |
| --- | --- | --- |
| 400 | urn:petclinic:problem:invalid-idempotency-key | Invalid idempotency key |
| 409 | urn:petclinic:problem:visit-creation-in-progress | Visit creation in progress |
| 422 | urn:petclinic:problem:idempotency-request-mismatch | Idempotency request mismatch |

Detail describes the reason/action (provide one valid key; retry after the active attempt;
use a new key for a different creation). Do not echo key values or visit data. Keep all
existing unrelated exception handlers and representations intact. No Retry-After requirement
or additional header is added.

## Retention and recovery

Visit and response snapshot commit together at C. A deadline anchor T sampled afterward
(T >= C) establishes expiry T + 24 hours, guaranteeing at least 24 hours after commit. At or
after an established expiry this design treats the key as unused. Replays and rejections never
extend T. If deadline finalization fails or is interrupted, the committed result remains
replayable indefinitely, including across persistent restart; only the operator procedure in
../operations.md may establish its deadline. There is no automatic deadline recovery or key
administration endpoint. Failure of this metadata write does not undo creation or change its 201.
On persistent restart, original identity, response values, Location and deadline remain.
In-memory demo/test results last only for their data lifetime. Rolled-back/invalid attempts
create no retained success; a corrected request can reuse the intended key.

## Compatibility obligations

Characterize the current owner/pet behavior rather than implementing the contract's advertised
membership 404 as a new check. Keep current role authorization. Extend the existing OpenAPI
operation, regenerate API/DTOs using Maven, and prove the no-header path remains compatible.
Contract validation must cover raw repeated values, punctuation, generated parameter constraints,
media types, first/replay result equality and unrelated endpoint regression behavior.
