Enhance the existing visit-creation endpoint, POST /api/owners/{ownerId}/pets/{petId}/visits, with opt-in idempotent behavior via an optional Idempotency-Key request header.

Problem: clients that retry after a timeout, lost response, or network interruption can create duplicate visits.

Compatibility: requests without Idempotency-Key must behave exactly as they do today (validation, response fields, status codes, owner/pet behavior).

Behavior for requests with a valid key:
- First successful request creates the visit and returns the existing success response.
- A retry with the same key and the same logical request within the retention period creates no new visit and returns the original result, including the same visit identity and resource location.
- "Same logical request" means semantic equivalence, not byte equality: JSON property order and insignificant whitespace must not matter. [State here which parts count: body only, or also path/other headers; otherwise leave as a clarification.]
- The same scoped key reused for a different logical request is rejected and creates or modifies nothing.
- If matching requests overlap, at most one visit is created. Define observable behavior for a duplicate arriving while the original is in progress and for one arriving after it completed.
- A request that fails validation, or a transaction that rolls back, does not consume the key; a later request may reuse it.
- Outcomes of successful keyed requests survive a normal application restart.
- Retention: 24 hours from successful completion. After expiry the key may be treated as unused.
- Scope: operation + ownerId + petId + key value. The same key with a different owner/pet is independent.

Key validity: a single non-empty value of 1-128 visible ASCII characters. Blank, multiple values, over 128 characters, or control characters are rejected without creating a visit or consuming the key.

Error behavior (decided; use application/problem+json for errors introduced here):
- Invalid key: 400
- Duplicate while original is in progress: 409
- Same scoped key, different logical request, after completion: 422

Out of scope: other endpoints, a general idempotency framework, unrelated owner/pet validation fixes, authentication/authorization changes, multi-region or DR guarantees, key-administration endpoints, a replay-marker response header.

The approved constitution is binding. Flag any conflict with it or with verified compatibility constraints rather than overriding it. Raise only genuine ambiguities as clarification questions.