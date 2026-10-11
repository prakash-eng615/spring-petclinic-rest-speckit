Perform a read-only brownfield discovery of this repository. The output feeds two later steps in a GitHub Spec Kit workflow, so keep the two deliverables strictly separate:

A. PROJECT CONSTITUTION INPUT (consumed by /speckit.constitution)
B. TECHNOLOGY AND PATTERNS BRIEF (consumed by /speckit.plan)

## What a Spec Kit constitution is
A short set of numbered, cross-feature principles plus a governance note. It is the binding rulebook that every future spec, plan, and task list is checked against ("Constitution Check" in planning; conflicts are treated as critical issues in analysis). Each principle must therefore be:
- durable (still true and enforceable a year from now)
- cross-feature (applies to any future change, not one feature)
- high blast radius if violated (compatibility, data integrity, security, operations, quality gates)
- answerable yes/no when reviewing a plan or PR
It is NOT a description of the codebase, a coding-style guide, a tool manual, or a list of generic best practices. Tech stack and implementation patterns belong in deliverable B, not here.

## Constraints
- Do not modify repository files, initialize Spec Kit, create constitution.md, or install dependencies.
- Do not run builds or tests unless clearly side-effect-free. Run `git status` at the start and end and report any difference.
- State anything you could not inspect or verify (could not run the build, external CI pipelines, private modules).

## Verification limit
For each claimed pattern or rule, inspect at most 10 files to confirm it. Stop as soon as the pattern is confirmed or the limit is reached. If it is still unclear after 10 files, downgrade it to INFERENCE or UNKNOWN and move on. Do not keep digging. Report consistency as a sample ("8 of 10 inspected files follow this"), not as a repo-wide claim. Config, CI, and manifest files read in steps 1-2 do not count toward this limit.

## Method
1. Start with the strongest evidence of enforced rules: CI/CD, linters/formatters, architecture or dependency-rule tests, migration tooling, pre-commit hooks, PR templates, CODEOWNERS, build wrappers/profiles.
2. Then manifests and dependency declarations, containers, runtime configuration.
3. Then README, contributor, architecture, and ADR docs.
4. Then sample code and tests from the main modules to confirm patterns, within the verification limit above.
5. Use Git history only to tell legacy patterns (do not extend) from the current direction (follow for new work) by looking at recently changed paths. Do not analyze ownership, churn statistics, or release history.
6. Cross-check docs against code, tests, build config, and CI. Never silently resolve contradictions. If there are multiple modules or stacks, say whether rules are repo-wide or per-module.

## Classification
Label every material finding:
- VERIFIED FACT: directly supported by repository evidence
- INFERENCE: reasonable interpretation, or a pattern not confirmed within the verification limit
- PROPOSED POLICY: possible rule needing human approval
- UNKNOWN: insufficient or conflicting evidence

For each pattern or rule also give a compliance level: ENFORCED (CI/lint/tests fail on violation), FOLLOWED (consistent but unenforced), CLAIMED (documented but code deviates), or ASPIRATIONAL. Cite repo-relative paths with symbols/sections/line ranges where practical.

Do not propose a rule the codebase currently violates unless labeled ASPIRATIONAL with the violation count.

## Rule areas to consider for the constitution (write "none found" where absent)
Backward compatibility (public APIs, schemas, migrations) · architectural/dependency boundaries and generated-code zones · testing gates required before merge · security and data-handling invariants · persistence/transaction rules and supported DB profiles · observability and operational requirements · dependency/license policy · change management (migrations, flags, release).

## Output: exactly these sections

**NOTE** write this analysis with file name browenfield-discovery-report.md in prompts/01-discovery directory.

1. SNAPSHOT (max 8 lines): branch, commit, working-tree state, stack in one line, deployable units, what could not be inspected.

2. CONSTITUTION CANDIDATES (max 5-7, ranked by blast radius if violated). For each:
   - Rule: one MUST / MUST NOT / SHOULD sentence
   - Why durable and cross-feature
   - Evidence (paths, symbols, line ranges) with classification, compliance level, and sample counts
   - How a plan or PR is checked against it
   - Example of a violating change
   - Exception/amendment path (or "none found")
   - Status: VERIFIED-BACKED or HUMAN-DECISION-REQUIRED
   Then list notable candidates considered and rejected or downgraded, one line each with the reason (generic, feature-specific, contradicted by code, unconfirmed within the verification limit, or belongs in the technology brief).

3. TECHNOLOGY AND PATTERNS BRIEF (for /speckit.plan; descriptive, not binding). Verified facts only, each with a path reference:
   - Languages, runtime versions, frameworks, build tool, key libraries and versions
   - Module/package structure and layering
   - Persistence: database(s), migration tool, ORM/data-access style, transaction conventions
   - API conventions: style, error format, validation approach, versioning
   - Testing: frameworks, test types present, how tests are run, test data approach
   - Security/config handling: authN/authZ mechanism, secrets/config sources
   - Observability: logging, health checks, metrics
   - Build/run/test commands that were verified to exist
   - Current patterns to follow vs legacy patterns to avoid extending
   Keep it a compact reference, not a file inventory.

4. CONTRADICTIONS AND STALE-SOURCE RISKS: only those affecting a candidate or the brief. Report them; do not resolve them.

5. UNRESOLVED HUMAN QUESTIONS: only high-value governance questions the repository cannot answer.

6. CONSTITUTION INPUT DRAFT: evidence-backed candidates only, using this template per principle:
   ### Principle N: <short name>
   <One MUST / MUST NOT / SHOULD sentence.>
   Rationale: <one sentence>
   Verification: <how a plan or PR is checked>
   Status: ENFORCED | FOLLOWED | HUMAN-DECISION-REQUIRED
   End with a short governance note (amendment process, if evidenced). Mark anything needing a human decision.

Before finishing, verify: every candidate has evidence and sample counts; no pattern exceeded the 10-file limit (unconfirmed ones are downgraded); nothing generic, stylistic, or feature-specific remains in the constitution draft; technology details live only in the brief; contradictions are reported, not resolved; `git status` shows no changes.