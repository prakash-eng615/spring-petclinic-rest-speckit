<!--
Sync Impact Report (temporary review material; remove before commit)
Version change: unversioned scaffold -> 1.0.0 (initial project constitution)
Modified principles: template slots I-V replaced with the five discovery draft principles:
  I. Conditional authorization integrity
  II. Transactional business writes
  III. Shared persistence contracts
  IV. Authoritative API contracts
  V. Executable verification gates
Added sections: Scope and Evidence; Compliance Review; project-specific Governance
Removed sections: none; placeholder/example text replaced
Deferred TODOs: RATIFICATION_DATE; GOVERNANCE_AUTHORITY; REQUIRED_CHECKS
Dependent templates/commands: unchanged; they read the constitution at runtime
Source: prompts/01-discovery/browenfield-discovery-report.md, CONSTITUTION INPUT DRAFT
-->

# Spring PetClinic REST Constitution

## Core Principles

### I. Conditional authorization integrity

Changes MUST preserve authenticated access and the applicable role authorization on business
APIs when the application's security mode is enabled.

Rationale: Changes to any feature can otherwise bypass the access controls of the supported
secured mode.

Verification: Trace affected operations to request authentication and role checks, and review
enabled-mode denial behavior for affected access paths.

### II. Transactional business writes

Business persistence mutations MUST execute within a transaction at the service operation
boundary.

Rationale: A shared transaction boundary prevents partially committed business operations across
features.

Verification: Identify the service transaction enclosing each mutation and examine
commit/rollback behavior for compound operations.

### III. Shared persistence contracts

Changes to shared persistence operations MUST preserve their service-visible contract across
every retained repository implementation.

Rationale: A change to a shared operation can otherwise break an entire selectable
implementation.

Verification: Review each affected implementation and shared regression coverage, identifying
database-specific effects explicitly.

### IV. Authoritative API contracts

Public API interface and model changes MUST originate in the authoritative API contract, with
generated output regenerated rather than hand-edited.

Rationale: Contract drift and overwritten generated changes affect all consumers and subsequent
builds.

Verification: Trace changed API behavior to the contract and handwritten implementation, then
review regeneration/compilation results and editable-source boundaries.

### V. Executable verification gates

Changes MUST pass the repository's automated pull-request verification and configured coverage
gates before merge.

Rationale: Executable regression and coverage checks protect all features from changes that
bypass established quality constraints.

Verification: Review relevant passing PR check results and confirm the verification commands,
thresholds and exclusions were not bypassed.

## Scope and Evidence

This constitution governs cross-feature changes to the Spring PetClinic REST project.
Its five principles derive from the CONSTITUTION INPUT DRAFT in
`prompts/01-discovery/browenfield-discovery-report.md`.

The report's enforcement statuses describe observed repository evidence; they do not
weaken the MUST requirements above. Workflow configuration alone does not prove that
checks passed or that protected branches require them.

The authorization principle applies when security is enabled. The persistence principle
covers retained implementations; it does not certify every database/profile combination.
The API principle governs authoritative interface/model sources and generated output;
it does not assert that all existing HTTP response semantics match the contract.

Technology versions and descriptive patterns remain planning inputs in the discovery
report's TECHNOLOGY AND PATTERNS BRIEF. Unresolved production-security, compatibility,
and migration policies are not additional constitutional requirements.

## Compliance Review

Every feature plan MUST include a Constitution Check against all five principles.
For each applicable principle, the plan MUST identify affected boundaries and planned
verification; any non-applicable principle MUST have an explicit reason.

Every implementation PR MUST provide the corresponding compliance evidence, including
relevant passing automated verification and coverage results before merge. Review MUST
cover authentication/roles, service transactions, retained persistence implementations,
and API contract/generation boundaries wherever affected.

A conflict with a principle MUST be resolved by changing the plan or implementation, or
by an approved constitutional amendment before merge. An unresolved conflict MUST NOT
be treated as an implicit exception or waived by silently disabling verification.

TODO(REQUIRED_CHECKS): designate the GitHub checks that must be required on protected
branches and confirm their enforcement. Until configured, reviewers must verify passing
results; this constitution does not claim branch protection is already active.

## Governance

This document is the project governance rulebook upon ratification. Its requirements
supersede conflicting development guidance within its scope. Formal ratification remains
pending the human decisions recorded below.

Amendments MUST be proposed as a reviewed change to this file that states the changed
rules, rationale, version impact, and any transition needed for existing work. The
proposal MUST assess affected plans and review expectations. An amendment takes effect
only after approval by the designated project governance authority; no waiver authority
is implied while that designation is pending.

TODO(GOVERNANCE_AUTHORITY): designate who ratifies the constitution and approves amendments.
TODO(RATIFICATION_DATE): confirm formal adoption and record its original date in the footer.

Constitution versions follow semantic versioning: MAJOR for backward-incompatible rule
removals or redefinitions; MINOR for added principles/sections or materially expanded
guidance; PATCH for non-semantic clarifications and corrections. Approved amendments
MUST update the version and Last Amended date while preserving the original Ratified date.

Constitution review is required during planning and PR review. The Sync Impact Report
comment is amendment-review scratch material and is expected to be removed before commit;
it is not governance content.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): confirm formal adoption date | **Last Amended**: 2026-10-11
