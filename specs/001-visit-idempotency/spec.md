# Feature Specification: Opt-in Visit Creation Idempotency

**Feature Branch**: `speckit-visit-idempotency`

**Created**: 2026-10-11

**Status**: Draft — validated and ready for planning

**Input**: Complete feature request in
`prompts/03-specify/visit-idempotency-specify-input.md`, with two user clarifications:
compare the validated visit body within the operation/owner/pet scope and ignore other
headers; apply restart durability to persistent-storage deployments only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recover a successful visit after a lost response (Priority: P1)

As a client recording a clinic visit, I want to retry a request whose response was lost
without recording a duplicate visit or losing the original booking result.

**Why this priority**: Safe retries address the duplicate-visit problem directly.

**Independent Test**: Submit a valid keyed creation, discard its response, and repeat it.
The client recovers the same visit identity and location, and the visit count increases once.

**Acceptance Scenarios**:

1. **Given** a valid unused key in an operation/owner/pet scope, **When** a valid creation
   succeeds, **Then** exactly one visit is created and the existing success response is
   returned: status 201, visit response fields and `/api/visits/{id}` resource location.
2. **Given** successful keyed creation less than 24 hours ago, **When** the client retries
   with the same scope, key and validated visit values, **Then** it receives the original
   success status, body, visit identity and location without a new or modified visit.
3. **Given** that same success, **When** the retry changes only JSON property order,
   insignificant JSON whitespace or other request headers while remaining authorized,
   **Then** the original result is returned with no additional visit.
4. **Given** that same success, **When** the created visit is later edited or deleted and
   the original request is retried before expiry, **Then** the original creation result is
   returned; the retry does not modify, restore or recreate the visit.

### User Story 2 - Keep existing clients working (Priority: P1)

As an existing client, I want visit creation to retain its current behavior unless I opt
into safe retries by supplying an idempotency key.

**Why this priority**: Opt-in adoption must preserve current integrations and workflows.

**Independent Test**: Compare unkeyed successful and failing requests with the existing
behavior, including repeated submissions and owner/pet cases.

**Acceptance Scenarios**:

1. **Given** a request without the key header, **When** it succeeds or fails, **Then** its
   validation, status, response fields, location and owner/pet behavior match the existing
   endpoint; newly introduced key errors do not apply.
2. **Given** two identical unkeyed creations that are otherwise accepted, **When** both
   are submitted, **Then** each retains the existing independent creation behavior.
3. **Given** enabled application security, **When** an unauthenticated or unauthorized
   client submits either a first request or a replay, **Then** existing access restrictions
   remain in force; a key does not grant access.
4. **Given** an unrelated endpoint, **When** clients use it, **Then** this feature introduces
   no change to its creation, validation or response behavior.

### User Story 3 - Prevent overlap and conflicting key reuse (Priority: P1)

As a retrying client, I want simultaneous submissions and accidental key reuse to have
predictable outcomes without duplicate visits or changes to the first result.

**Why this priority**: Retries often overlap, and an incorrect retry must not silently
record a different visit under an already used key.

**Independent Test**: Hold one keyed creation in progress, submit overlapping requests,
complete the first, and retry with both matching and changed visit values.

**Acceptance Scenarios**:

1. **Given** creation is in progress for a scope and key, **When** another valid request
   arrives with that scope and key before completion, **Then** it receives 409 and
   `application/problem+json`, whether its body matches or differs; it creates no visit.
2. **Given** overlapping matching requests for an unused scope/key, **When** they are
   processed, **Then** at most one visit is created; requests encountering an in-progress
   attempt receive 409, and matching requests encountering its completed success replay it.
3. **Given** a retained completed success, **When** the same scope/key is used with a
   different validated visit date or description, **Then** the request receives 422 and
   `application/problem+json`, changes nothing and leaves the original result replayable.
4. **Given** a key used for one owner/pet scope, **When** the same key is used for a
   different owner or pet scope, **Then** the new scope is independent and may create a visit
   subject to existing validation; it does not replay or conflict with the other scope.

### User Story 4 - Correct a failed attempt and retry (Priority: P2)

As a client, I want rejected or rolled-back attempts to leave the key reusable so I can
correct the request or recover from failure.

**Why this priority**: A failed attempt must not prevent a later successful booking.

**Independent Test**: Submit invalid keys and invalid visit requests, and cause a creation
attempt to roll back; then submit a valid creation using the same intended scope/key.

**Acceptance Scenarios**:

1. **Given** a blank, empty, repeated, overlong or non-visible-ASCII key header, **When**
   creation is requested, **Then** it receives 400 and `application/problem+json`, creates
   no visit and consumes no key.
2. **Given** an unused valid key and invalid visit data, **When** validation rejects it,
   **Then** no visit or successful key result is retained, and a corrected request may use
   that key successfully.
3. **Given** an accepted keyed attempt whose transaction rolls back, **When** the client
   retries after the failed attempt ends, **Then** the key is available for a new attempt
   and there is no visit or successful replay result from the rolled-back attempt.
4. **Given** a rejected conflicting retry, **When** the original successful request is
   retried again before expiry, **Then** its original result remains available.

### User Story 5 - Retry within a predictable retention window (Priority: P2)

As a client, I want a successful booking result to remain available for a full day,
including across normal restarts of deployments that retain their data.

**Why this priority**: Retry safety must have a clear lifetime and survive ordinary
application maintenance where visits themselves survive.

**Independent Test**: Replay before and at the 24-hour boundary, and restart a
persistent-storage deployment before replaying a completed creation.

**Acceptance Scenarios**:

1. **Given** successful completion at time T, **When** a matching retry is processed
   before T + 24 hours, **Then** it replays the original result; replay does not extend T.
2. **Given** that success in a persistent-storage deployment, **When** the application
   restarts normally within the retention period and the client retries, **Then** the same
   visit identity and original result are returned with no new visit.
3. **Given** a key at or beyond T + 24 hours, **When** it is submitted again, **Then** it may
   be treated as unused and a new accepted creation may return a new visit identity.
4. **Given** an in-memory demo/test deployment, **When** it runs without restarting,
   **Then** the same 24-hour rules apply during that lifetime; persistence across a restart
   that loses its visit data is outside this guarantee.

### Edge Cases

- Header absent versus present with an empty value: only absence selects existing unkeyed behavior.
- Keys of exactly 1 and 128 visible ASCII characters are valid; 129 characters are invalid.
- Multiple header values are invalid even when identical. Visible punctuation within one
  opaque key value is permitted; it must not be confused with repeated header values.
- Key values are case-sensitive and are not trimmed or case-normalized to become valid.
- JSON formatting changes do not change identity; whitespace inside a description string,
  description case, or a changed date changes the validated visit values.
- An omitted optional field and an explicit null are equivalent only where existing
  validation accepts both and produces the same validated visit value.
- A completed failure must not remain indefinitely observable as an in-progress request.
- A lost response after successful completion replays success; rollback before completion
  leaves the key reusable. A normal restart must not expose a successful result for a visit
  that was never successfully created.
- A matching retry observed just after completion replays success; one observed before
  completion receives 409. Retention is measured from completion, not request arrival.
- A changed ownerId or petId creates a different scope; existing owner/pet checks must not
  be broadened or fixed incidentally, even if current behavior is surprising.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST offer opt-in idempotency only on
  `POST /api/owners/{ownerId}/pets/{petId}/visits` through optional `Idempotency-Key`.
- **FR-002**: Without that header, the system MUST preserve existing validation, response
  fields, status codes, location and owner/pet behavior, including independent repeated creates.
- **FR-003**: A key MUST be exactly one non-empty value of 1–128 visible ASCII characters
  (U+0021 through U+007E). Invalid keys MUST receive 400 `application/problem+json` and
  MUST NOT create a visit or consume a key. Repeated/multiple values MUST be rejected.
- **FR-004**: Key scope MUST be operation + ownerId + petId + exact key value. Different
  owner/pet scopes MUST remain independent, with no new user/account dimension in scope.
- **FR-005**: Logical request equality MUST compare the validated visit body values within
  that scope. JSON property order and insignificant JSON whitespace MUST NOT matter;
  other headers MUST NOT be comparison inputs. Existing authorization still applies.
- **FR-006**: The first successful keyed attempt MUST create one visit and return the
  existing success result, including its visit identity and resource location.
- **FR-007**: A matching retry before expiry MUST return the original success status,
  body, visit identity and location and MUST NOT create or modify a visit. Subsequent edits
  or deletion of the visit MUST NOT change that retained creation result or recreate data.
- **FR-008**: Concurrent requests sharing scope/key MUST create at most one visit.
  A valid duplicate encountering an in-progress attempt MUST receive 409
  `application/problem+json` without a new visit, regardless of body equality.
- **FR-009**: After successful completion and before expiry, a valid request with the same
  scope/key but different logical visit values MUST receive 422 `application/problem+json`
  and MUST NOT create or modify anything, including the retained original result.
- **FR-010**: Failed validation and rolled-back creation MUST NOT consume the key. After
  the failed attempt ends, a valid new attempt with that key MUST be possible. Only successful
  creations may establish replayable success outcomes.
- **FR-011**: Successful creation and its replayable result MUST become effective together;
  neither a replayable success without its successfully created visit nor a successful keyed
  creation without retry protection may be exposed as a completed outcome.
- **FR-012**: Results MUST remain replayable for 24 hours from successful completion;
  retries MUST NOT renew retention. At or after expiry, the key MAY be treated as unused.
- **FR-013**: Successful outcomes and their retention deadline MUST survive a normal
  application restart in persistent-storage deployments. In-memory demo/test deployments
  MUST provide the guarantees during their running lifetime only.
- **FR-014**: The new 400, 409 and 422 errors MUST use `application/problem+json` and
  identify the respective invalid-key, in-progress or mismatched-request condition in a
  structured problem response with status, title and explanatory detail. Existing unkeyed
  errors MUST retain their current behavior and representation.
- **FR-015**: Both first attempts and replays MUST preserve existing authentication and
  role restrictions when security is enabled. This feature MUST NOT introduce new access rights.
- **FR-016**: The feature MUST preserve the service-visible behavior across all retained
  repository implementations. Its public request/response contract changes MUST originate
  in the authoritative contract and MUST respect generated-output boundaries.
- **FR-017**: Verification MUST demonstrate the acceptance scenarios, existing unkeyed
  compatibility and configured quality gates before merge; bypassing those gates is not
  an acceptable resolution of a feature failure.
- **FR-018**: The feature MUST NOT extend idempotency to other endpoints, create a general
  idempotency framework or key-administration endpoints, add a replay-marker header, change
  authentication/authorization, fix unrelated owner/pet validation, or promise multi-region
  or disaster-recovery behavior.

### Key Entities *(include if feature involves data)*

- **Visit**: The existing clinic visit, with its identity, pet, date, description and resource
  location; this feature does not redefine the existing visit fields.
- **Scoped key**: An exact client-supplied key associated with a creation operation and
  owner/pet identifiers. It distinguishes independent retry groups.
- **Logical visit request**: The validated visit values compared within a scoped key,
  independent of JSON formatting and unrelated headers.
- **Creation attempt**: Processing in progress for a scoped key; success makes a result
  replayable, while validation failure or rollback leaves the key reusable.
- **Retained success result**: The original successful creation response, its visit identity,
  resource location, completion time and 24-hour retention deadline.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In every accepted keyed retry scenario within retention, the visit count
  increases by exactly one across the original and all matching retries, and 100% of completed
  retries recover the same original visit identity, location and success result.
- **SC-002**: In every overlapping-submission scenario, no more than one visit is created;
  every request observed during processing receives the defined conflict result, and every
  matching request observed after success recovers the original booking.
- **SC-003**: 100% of invalid-key and completed-key-mismatch scenarios produce their defined
  rejection and zero unintended visit creations or modifications; every tested validation
  failure or rollback permits subsequent successful reuse of the key.
- **SC-004**: 100% of retained successful bookings tested across a normal restart in a
  persistent-storage deployment remain recoverable without a duplicate during the original
  24-hour window; retries never extend that window.
- **SC-005**: Existing clients pass all established unkeyed visit-creation acceptance cases
  with unchanged outcomes, and other endpoints retain their existing behavior.
- **SC-006**: A client can complete the lost-response recovery flow by retrying the same
  scoped keyed request and receiving the original booking result, without manual duplicate
  cleanup or a separate visit-lookup step.

## Assumptions

- **Confirmed comparison decision**: Validated visit body plus scoped operation and path IDs
  define equality; other headers are ignored. Present body fields are date and description.
  Accepted equal values compare equally; differing description strings remain different.
- **Confirmed durability decision**: Restart durability applies only to persistent-storage
  deployments; in-memory demo/test guarantees end with that data lifetime. This resolves
  the input's restart requirement against the current in-memory default without changing
  unkeyed behavior or demanding a default deployment change.
- **Key interpretation**: “Visible ASCII” means U+0021–U+007E; spaces and control characters
  are invalid. Equality uses the exact accepted value. Multiple values refer to repeated
  header values, not arbitrary punctuation within one opaque value.
- **Retention boundary**: The guaranteed window is completion time inclusive through, but
  excluding, completion + 24 hours. Expired-key treatment is deliberately permissive as in
  the feature request; physical cleanup timing is not a client guarantee.
- **Replay contents**: “Original result” means original creation status, body and resource
  location; transport-generated metadata such as response time/date is not frozen. No new
  replay-marker response header is needed.
- **Existing compatibility evidence**: `OwnerRestControllerV1.addVisitToOwner` currently
  returns 201 with VisitDto and `/api/visits/{id}` location
  (`src/main/java/org/springframework/samples/petclinic/rest/controller/v1/OwnerRestControllerV1.java:175-187`).
  It assigns petId without an owner lookup; this observation must not be converted into
  unrelated validation changes. The contract's advertised owner/pet 404 behavior is not
  proof that the implementation performs those checks.
- **Dependencies**: This feature depends on existing visit validation, creation, security,
  retained persistence implementations and public contract. The exact technical design and
  verification commands belong to planning. The feature request explicitly declares the
  approved constitution binding; this specification applies all five principles despite
  administrative ratification TODOs still present in that document.
- **Conflict handling**: The restart-scope conflict is resolved by the user's clarification.
  No remaining principle conflict is identified. If planning finds a compatibility or
  constitutional conflict, it must flag it and resolve it before implementation or merge.
