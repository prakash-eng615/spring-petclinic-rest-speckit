# 1. SNAPSHOT

- Branch: `speckit-visit-idempotency`; inspected on 2026-10-11.
- Commit: `4cd8e1b0cd42578e882247d8801f6be5d402f118`.
- Initial working tree: no tracked changes; six untracked prompt files (listed in section 4).
- Stack: Java / Spring Boot 4.1.1 / Maven / Spring MVC / JDBC and JPA / generated OpenAPI contracts.
- Deployable units: one backend application (`PetClinicApplication.main`, `pom.xml:6-17`); Jib image publishing; two optional database services in `docker-compose.yml`.
- Scope: local repository only; no builds, tests, dependency installation, application startup, or remote inspection performed.
- Unverified: inherited effective Java/compiler and dependency versions, generated output, runtime behavior, CI results, Sonar settings, branch protection, deployed environments and external clients.
- Final working-tree delta: only this requested report added; existing files unchanged. Discovery was read-only except this explicitly requested output.

# 2. CONSTITUTION CANDIDATES

**A. PROJECT CONSTITUTION INPUT — candidates for `/speckit.constitution`.** Ranked by blast radius; these are evidence-backed candidate rules, not an already adopted constitution. ENFORCED below describes checked-in enforcement configuration, not proof that a check has passed or that GitHub requires it before merging. Human ratification of the constitution remains necessary.

Repository-relative references use `path:lines` and Java symbols. To keep references compact, `J` means `src/main/java/org/springframework/samples/petclinic/`, `T` means `src/test/java/org/springframework/samples/petclinic/`, and `R` means `src/main/resources/`. Expand these prefixes when following a reference.

### Candidate 1: Preserve authorization when security is enabled

- **Rule:** Changes MUST preserve authenticated access and the applicable role authorization on business APIs when the application's security mode is enabled.
- **Why durable and cross-feature:** A bypass exposes any protected operation regardless of feature or persistence implementation; the rule is conditional on the existing supported security mode.
- **Evidence:** VERIFIED FACT; ENFORCED by configured authentication and method-security interception, with sampled role declarations FOLLOWED. `J/security/BasicAuthenticationConfig.java:18-52` enables method security, requires authentication for every request and selects HTTP Basic with database users/roles. `J/security/DisableSecurityConfig.java:15-31` deliberately permits requests in disabled mode. All **4 of 4 sampled business controllers** declare role restrictions: `J/rest/controller/v1/VisitRestControllerV1.java:55-102`, `UserRestControllerV1.java:46-52`, and both `v2/*RestControllerV2.java:31-38`. `J/security/Roles.java:5-10` supplies the role names. Three sampled controller test files use authorized mock users; these do not establish complete unauthorized/wrong-role coverage. Verification sample: **10 source/test files total**, including the two security configurations, role constants, four controllers and three tests.
- **Plan/PR check:** Identify security-mode effects and endpoint roles; check that enabled-mode changes retain request authentication and role checks, with denial behavior verified for affected access paths.
- **Violating change:** Adding an authenticated business mutation without the required role restriction, or changing enabled-mode request rules to permit all requests.
- **Exception/amendment path:** None found; a new public business endpoint needs a human governance decision.
- **Status:** VERIFIED-BACKED. This establishes neither secure-by-default behavior nor a production security policy.

### Candidate 2: Keep persistence mutations transactional

- **Rule:** Business persistence mutations MUST execute within a transaction at the service operation boundary.
- **Why durable and cross-feature:** Partial commits can corrupt relationships and compound operations across unrelated features.
- **Evidence:** VERIFIED FACT; FOLLOWED for boundary placement; transaction behavior is configured through annotations. **2 of 2 sampled service implementations** annotate mutation methods with Spring `@Transactional`: `J/service/ClinicServiceImpl.java:78-99,114-123,141-167,213-235` and `J/service/UserServiceImpl.java:16-34`. Clinic read operations use `readOnly=true` (`ClinicServiceImpl.java:65-74,239-254`). `J/rest/controller/v1/VisitRestControllerV1.java:99-108` additionally annotates deletion, so service-only annotation placement is not a rule. `T/service/clinicService/AbstractClinicServiceTests.java:244-287` has transactional CRUD tests, but these do not demonstrate multi-step rollback under failure. Verification sample: **4 files**, two services, one controller and one test base.
- **Plan/PR check:** Locate the service transaction encompassing each mutation and all steps intended to commit together; examine failure behavior for compound writes.
- **Violating change:** Splitting one compound business write across independently committed service calls.
- **Exception/amendment path:** None found.
- **Status:** VERIFIED-BACKED; no architecture test enforces annotation placement and no rollback proof was executed.

### Candidate 3: Preserve the shared persistence contract across retained implementations

- **Rule:** Changes to shared persistence operations MUST preserve their service-visible contract across every retained repository implementation.
- **Why durable and cross-feature:** A shared interface change can break startup, persistence behavior or data integrity for an entire selectable implementation.
- **Evidence:** VERIFIED FACT; FOLLOWED, with partial ENFORCED coverage through compilation and shared tests. `J/repository/VisitRepository.java:35-51` defines the shared visit operations. **3 of 3 sampled repository implementations** expose that contract under selectable profiles: `jdbc/JdbcVisitRepositoryImpl.java:56-69,128-144`, `jpa/JpaVisitRepositoryImpl.java:42-81`, `springdatajpa/SpringDataVisitRepository.java:30-31`; the sampled Spring Data deletion extension is in `springdatajpa/SpringDataVisitRepositoryImpl.java:30-43`. **4 of 4 sampled clinic test subclasses** inherit `AbstractClinicServiceTests`: `T/service/clinicService/ClinicServiceH2JdbcTests.java:29-35`, `ClinicServiceHsqlJdbcTests.java:29-32`, `ClinicServiceJpaTests.java:17-26`, `ClinicServiceSpringDataJpaTests.java:15-24`. Shared CRUD assertions: `AbstractClinicServiceTests.java:244-287`. Verification sample: **10 files** (interface, four adapter/extension files, four profile test subclasses and test base). Profile declarations additionally appear in `R/application.properties:3-20`.
- **Plan/PR check:** Map an affected shared operation to each retained implementation and relevant shared tests; explicitly identify database-specific SQL/schema impact. This is not certification of every database/repository combination.
- **Violating change:** Implementing a new shared operation for the default adapter while leaving another retained adapter unable to supply it.
- **Exception/amendment path:** None found; retiring an implementation requires an explicit human decision and corresponding support/documentation changes.
- **Status:** VERIFIED-BACKED; complete database equivalence is not evidenced.

### Candidate 4: Respect authoritative contracts and generated-code boundaries

- **Rule:** Public API interface and model changes MUST originate in the authoritative API contract, with generated output regenerated rather than hand-edited.
- **Why durable and cross-feature:** Contract drift affects consumers and regeneration can erase hand edits across all features.
- **Evidence:** VERIFIED FACT; FOLLOWED for source-of-truth discipline; partial ENFORCED compilation coupling. `readme.md:188-219` documents API-first generation. `pom.xml:347-405` generates interfaces and DTOs from `R/openapi.yml` into the build output; `pom.xml:409-433` configures mapper generation. **4 of 4 sampled controllers** implement generated API interfaces and use generated DTOs (candidate 1 controller paths, class declarations and imports); **2 of 2 sampled mapper interfaces** use MapStruct (`J/mapper/VisitMapper.java:14-26`, `UserMapper.java:15-30`). Verification sample: **7 non-config files**, those six Java files and `R/openapi.yml:1-31,139-205,872-924`. Generated artifacts were not produced or inspected; compilation does not detect all semantic HTTP mismatches. This candidate concerns the editable contract/generation boundary; the existing response-semantic contradictions are not claimed compliant with a broader contract-fidelity rule.
- **Plan/PR check:** Trace each changed endpoint/schema to the contract, handwritten controller and mapper; review regeneration and compilation results and ensure generated output is not used as the editable source.
- **Violating change:** Changing only a generated DTO, losing the change on the next generation.
- **Exception/amendment path:** None found.
- **Status:** VERIFIED-BACKED; changing the authoritative contract mechanism would require human amendment.

### Candidate 5: Preserve executable quality gates

- **Rule:** Changes MUST pass the repository's automated pull-request verification and configured coverage gates before merge.
- **Why durable and cross-feature:** Bypassing executable regression gates can ship failures in any feature; the rule follows the checked-in gates instead of freezing tool versions in governance.
- **Evidence:** VERIFIED FACT; ENFORCED as workflow/build failure configuration; required-before-merge enforcement is UNKNOWN. `.github/workflows/maven-build-pull-request.yml:3-23` runs wrapper `verify`; `pom.xml:273-329` configures bundle coverage checks (85% lines, 66% branches), excluding generated API/DTO classes. `.github/workflows/newman-pipeline.yml:3-49` runs API smoke checks on pull requests; `postman-tests.sh:62-73` propagates Newman failure. Master additionally invokes Sonar (`.github/workflows/maven-build-master.yml:23-27`), whose external policy is unverified. **3 of 3 inspected PR/master verification workflows** invoke verification or smoke testing; **1 of 1 inspected shell runners** exits nonzero on Newman failure. Workflow/manifest files are exempt from the code sampling cap; the runner is the sole non-config file needed for this candidate.
- **Plan/PR check:** Supply passing relevant PR check results and retained coverage thresholds; inspect changes to workflow triggers, exclusions and skip flags. Establish required GitHub checks as a human governance decision.
- **Violating change:** Disabling coverage checks or skipping failing regressions to make a feature's PR appear successful.
- **Exception/amendment path:** None found; no waiver process is documented.
- **Status:** VERIFIED-BACKED for executable gates; HUMAN-DECISION-REQUIRED for protected-branch enforcement and waivers.

**Candidates considered and rejected or downgraded**

- **Backward compatibility:** PROPOSED POLICY / ASPIRATIONAL; no existing guarantee found. `terms.txt:24` explicitly permits endpoint/structure changes without notice; versioned additions and tests demonstrate coexistence, not a binding compatibility promise. Not in the draft.
- **Secure by default:** PROPOSED POLICY / ASPIRATIONAL; **1 of 1 main default configurations** disables security (`R/application.properties:47-49`); intentionally contradicted by the default mode. Not in the draft.
- **Encode and never disclose credentials:** PROPOSED POLICY / ASPIRATIONAL; sampled user creation has **two contrary paths**: unencoded service persistence (`UserServiceImpl.saveUser`) and a password-bearing response mapping (`UserRestControllerV1.addUser`, `UserMapper.toUserDto`). Three of three files show that path; runtime output was not executed. Validation advice also reflects rejected field values (`ExceptionControllerAdvice.java:126-143`). This is a governance/remediation question, not a currently followed invariant.
- **Uniform errors for every failure:** CLAIMED / UNKNOWN as a universal rule; `R/openapi.yml:1900-1953` calls ProblemDetail the schema for all errors, but the sampled visit controller has bodyless 404 paths (`VisitRestControllerV1.java:59-60,69-70`). **1 of 1 sampled controller paths examined for this claim** contradicts uniform bodies. Error format belongs in the brief with limits.
- **Non-destructive, versioned migrations:** PROPOSED POLICY / ASPIRATIONAL; no migration framework found in the POM/file inventory; **1 of 4 inspected schema scripts** begins with table drops (HSQLDB), and H2 config enables ORM updates. No uniform upgrade-safety rule is evidenced.
- **Certify every DB/profile combination:** UNKNOWN; four database configurations and three adapter profiles exist, but the **4 inspected clinic test subclasses** cover only H2/JDBC and HSQLDB with three adapters. Complete matrix support is unverified.
- **Architectural dependency tests:** None found in inspected build/test inventory; layering is descriptive beyond the transaction and generated-contract candidates.
- **Observability/operational SLOs:** None found; dependencies and logging are implementation facts, not evidenced operational requirements.
- **Dependency/license allowlist:** None found; Apache 2.0 licensing and dependency-update automation do not establish an allowlist. RAT is explicitly skipped in reporting (`pom.xml:200-206`).
- **Change management through flags, migrations or release approvals:** None found beyond existing profiles, build/version declarations and publication workflows; no evidenced governance process.
- **Framework versions, injection style, pagination, mapper conventions, formatting:** Belong in the technology brief or are stylistic/feature-specific; excluded from the constitution draft.

**Sampling audit:** No claim was pursued beyond ten files. Samples overlap and are not independent statistical estimates. Security: 10 files; transactions: 4; adapter contract: 10; API generation: 7; layering: 10 (four controllers, two services, shared visit interface and three profile adapters); mapper convention: 2; error handling: 3 (contract, advice, visit controller); entity mapping: 2 (`Visit`, `User`); JVM test arrangement: 9 (four profile subclasses, shared base, test config, three controllers); SQL initialization/data: 8 (four schemas, four seeds); Newman/JMeter artifact presence: 1 each; call-monitor implementation: 1. Config/CI/manifests read during initial discovery are excluded from this cap. Stop points were the confirming samples; statements are limited to those samples.

# 3. TECHNOLOGY AND PATTERNS BRIEF

**B. TECHNOLOGY AND PATTERNS BRIEF — descriptive input for `/speckit.plan`.** All entries are VERIFIED FACT from repository contents. Compliance labels describe observed implementation/configuration, not recommendations or runtime certification.

| Area | Verified planning reference |
| --- | --- |
| Language/runtime/build | Java; a single Maven project with Boot parent **4.1.1**, application version **4.0.2** (`pom.xml:6-17`). Wrapper distribution **3.9.16** (`.mvn/wrapper/maven-wrapper.properties:1-4`); POM Maven requirement property **3.9.9** (`pom.xml:42,457-466`). All four workflow files select Temurin **17**. Java minimum/compiler release is inherited and referenced, not assigned in this POM (`pom.xml:266,449-453`); its resolved value was not inspected. ENFORCED configuration only. |
| Frameworks/libraries | Boot starters declare actuator, AspectJ, cache, JPA, JDBC, MVC, security and validation (`pom.xml:54-87`); declared versions: Spring Data JDBC core **1.2.1.RELEASE**, springdoc **3.1.0**, nullable Jackson helper **0.2.11**, MapStruct **1.6.3**, OpenAPI Generator **7.25.0**, JaCoCo **0.8.15**, Jib **3.5.2** (`pom.xml:20-45,115-125,159-176`). Jackson core/databind use `tools.jackson` coordinates (`pom.xml:144-152`). Other dependency versions are parent-managed, not resolved here. The JAXB version property **2.3.1** is declared but not attached to the Jakarta JAXB dependency (`pom.xml:27,178-181`). |
| Structure/layering | One application entry point (`J/PetClinicApplication.java:7-12`), with controller, DTO/API, mapper, service, model and repository packages. **4/4 sampled controllers** delegate through services/mappers; **2/2 services** use repository interfaces; the sampled visit interface has three selectable adapter implementations (candidate references). FOLLOWED, without an inspected architecture gate. Handwritten mapper interfaces are sources; implementations are generated (`pom.xml:409-433`). |
| Persistence/configuration | H2 and HSQLDB in-memory URLs, MySQL/PostgreSQL external URLs and environment overrides are configured (`R/application-h2.properties:1-16`, `application-hsqldb.properties:3-7`, `application-mysql.properties:2-7`, `application-postgres.properties:1-6`). Main defaults: `h2,spring-data-jpa`; tests: `hsqldb,spring-data-jpa` (`R/application.properties:20`, `src/test/resources/application.properties:19`). Database and adapter profiles are separate selections (`R/application.properties:3-18`). Compose declares MySQL **8.4** and PostgreSQL **16.3** services (`docker-compose.yml:1-26`). These are configured options, not an executed support matrix. FOLLOWED. |
| Data access/transactions | Sampled visit adapters use named-parameter JDBC/SimpleJdbcInsert, EntityManager JPA, and a Spring Data repository with an EntityManager deletion extension (candidate 3 references). **2/2 inspected entities**, `J/model/Visit.java:27-49` and `User.java:16-31`, use Jakarta persistence annotations; Visit uses LocalDate and `@NotEmpty`. Service mutation transactions and read-only clinic reads are described in candidate 2. `spring.jpa.open-in-view=false` in both main/test config. FOLLOWED. |
| Schema/data initialization | Spring SQL initialization selects `db/${spring.sql.init.platform}/schema.sql` and `data.sql` and main mode is `always` (`R/application.properties:27-32`). H2 also sets `ddl-auto=update`; HSQLDB sets `none` (profile configs). All **4/4 sampled schema scripts** define relational tables/FKs, with differing nullability and delete behavior (`R/db/{h2,hsqldb,mysql,postgres}/schema.sql`, visit definitions at 54-60, 59-66, 49-55, 46-52 respectively). **4/4 seed scripts** contain sample data, with plain inserts in H2/HSQLDB, INSERT IGNORE in MySQL and conditional inserts/ON CONFLICT in PostgreSQL. No Flyway/Liquibase dependency or versioned migration inventory was found. FOLLOWED; differences are in section 4. |
| API shape/versioning | Contract declares OpenAPI **3.0.1**, API version **1.0**, base server `http://localhost:9966/petclinic/api` (`R/openapi.yml:1-10`). Main server port **9966**, context `/petclinic/` (`R/application.properties:24-25`). V1 package maps under `/api`, with unversioned visit/user paths; v2 owner/pet pagination paths are `/api/v2/owners` and `/api/v2/pets` (`R/openapi.yml:139-205,872-924`; sampled v2 controllers; `T/rest/controller/V2RestControllersTests.java:96-133`). **4/4 sampled controllers** use ResponseEntity/DTOs and CrossOrigin exposing `errors, content-type`; visit create returns 201/Location, update/delete 204, and missing results 404 (`VisitRestControllerV1.java:55-108`). FOLLOWED; some response-body details conflict. |
| Errors/validation | Advice constructs Spring ProblemDetail with type URL, exception-class title, generic detail, timestamp and schemaValidationErrors; exception handlers return 500, data-integrity 404 and method-argument validation 400 (`J/rest/advice/ExceptionControllerAdvice.java:63-147`). Validation entries include field, rejectedValue and defaultMessage. Contract fields/required lists: `R/openapi.yml:1900-1965`. Generator enables Bean Validation/Jakarta (`pom.xml:372-385`); sampled visit controller tests assert invalid description gives 400 (`T/rest/controller/VisitRestControllerV1Tests.java:172-185,209-218`). FOLLOWED for these paths, CLAIMED for universal errors; generated annotations/runtime handling were not inspected. |
| JVM tests/data | JUnit Jupiter, Mockito, AssertJ, Spring Boot testing and Spring Security testing are declared/used (`pom.xml:127-142`; `T/service/clinicService/AbstractClinicServiceTests.java:18-35`; controller-test imports). **4/4 sampled profile subclasses** reuse shared clinic tests; writes shown in the base are transactional. **3/3 sampled controller tests** use standalone MockMvc with advice and in-code fixtures; Visit/V2 replace ClinicService using `@MockitoBean`, User declares local `@Mock` instead (`VisitRestControllerV1Tests.java:54-105`, `V2RestControllersTests.java:37-93`, `UserRestControllerV1Tests.java:27-62`). SQL seeds initialize database fixtures (initialization config above); base assertions rely on fixed sample owners/pets (`AbstractClinicServiceTests.java:62-96`). FOLLOWED; security filters and full rollback scenarios are not proven by these tests. |
| Other testing | A Postman collection includes executable status/data assertions and owner creation (`src/test/postman/petclinic-nonregressiontests.postman_collection.json:11-61`); the shell runner executes Newman and writes one HTML report (`postman-tests.sh:62-73`), with PR smoke workflow wiring. One JMeter 5.6.3 test-plan artifact contains parameterized load settings and HTTP CRUD samplers (`src/test/jmeter/petclinic-jmeter-crud-benchmark.jmx:2-75`); invocation is documented in its README:29-36, with no load-test CI invocation found in the four workflows. ENFORCED configuration for Newman; FOLLOWED artifact presence for JMeter. |
| Security/config/secrets | Optional HTTP Basic backed by SQL users/roles, BCrypt encoder bean and method role checks (candidate 1). Main authentication flag is false; test flag true (`R/application.properties:47-49`, `src/test/resources/application.properties:38-43`). MySQL/PostgreSQL URL/user/password placeholders allow environment overrides with sample defaults; Compose also contains sample credentials. GitHub workflows use secret references for Sonar/GitHub/Docker Hub (`maven-build-master.yml:24-27`, `docker-build.yml:16-20`). User creation service does not call an encoder (`J/service/UserServiceImpl.java:16-34`), despite its configured bean; mapper/controller return UserDto (`J/mapper/UserMapper.java:26-28`, `UserRestControllerV1.java:48-52`). FOLLOWED observed configuration; no stronger credential-handling invariant inferred. |
| Observability | Actuator dependency and build-info goal exist (`pom.xml:56-59,250-269`); README documents `/petclinic/actuator/health` (`readme.md:35-36`), not exercised here. Logback console pattern/root INFO and application DEBUG are configured (`R/logback.xml:10-20`); advice logs errors/warnings and validation details (`ExceptionControllerAdvice.java:82-85,99-104,140-143`). `J/util/CallMonitoringAspect.java:37-95` defines repository-call timing/counts with JMX annotations; activation was not established from inspected entry/config files. No explicit external metrics exporter, tracing requirement or SLO configuration appears in the inspected POM/runtime config. FOLLOWED logging configuration; health route CLAIMED, call-monitor activation UNKNOWN. |
| Verified command definitions | `./mvnw spring-boot:run` (`readme.md:21-25`); `mvn clean install` (generation/build, `readme.md:216-219`); `./mvnw -B verify` (PR workflow:23); `bash postman-tests.sh` (Newman workflow:40-42); JMeter command (JMeter README:33-36); `mvn compile jib:build -X -DjibSerialize=true` (Docker workflow:27-28). These definitions exist; none was run. Jib publishes externally; Newman may fetch packages and mutates API data. |
| Current vs older patterns | Recent path history `28a1c2c` separates controllers into v1/v2; sampled code confirms separate generated interfaces for v2. Earlier `a3884f7` paths include a combined `V2RestController.java`, absent from the current inventory. `f2670f9` records movement of owner-page mapping into OwnerMapper. Current sampled direction: separate version packages and mapper delegation; old combined-controller paths are historical, not templates to extend. This is a VERIFIED FACT about path history/current declarations, not a deprecation claim against JDBC/JPA. All three adapters remain configured. |

# 4. CONTRADICTIONS AND STALE-SOURCE RISKS

- **VERIFIED FACT / conflicting instructions:** The discovery prompt prohibits file changes and asks for a named report. Its explicit output request was treated as a narrow exception. Initial `git status --short --untracked-files=all` listed only `prompts/01-discovery/01-brownfield-discovery-prompt.md`, `prompts/02-constitution-creation/agent-constitution-prompt.md`, `prompts/03-specify/agent-specify-prompt.md`, `prompts/03-specify/visit-idempotency-specify-input.md`, `prompts/04-plan/agent-plan-prompt.md`, and `prompts/commands.md`. Final status retains those six and adds `prompts/01-discovery/browenfield-discovery-report.md`; tracked diff remains empty. The requested literal “no changes” end condition cannot coexist with saving the report.
- **UNKNOWN / runtime/build compatibility:** Recent history updates Boot to 4.1.1 (`9fb47d8`) and wrapper to 3.9.16 (`0979be3`), while all four workflow files select Java 17 and POM Java minimum/compiler release comes from the parent. Compatibility and effective versions remain unresolved; neither a successful build nor a definitive Java incompatibility is asserted. README's generic Maven 3 prerequisite (`readme.md:254-258`) is less specific than the POM requirement.
- **VERIFIED FACT / documentation mismatch:** README calls runtime docs OAS 3.1 (`readme.md:44`), while the build's input contract declares 3.0.1 (`R/openapi.yml:1`). Runtime springdoc output was not fetched, so whether these represent intentionally different documents remains UNKNOWN.
- **VERIFIED FACT / schema differences:** H2 visit fields are NOT NULL with cascading deletion; HSQLDB/MySQL allow nullable date/description and do not declare cascades; PostgreSQL also permits nullable visit pet_id. HSQLDB drops/recreates tables (`schema.sql:1-9`); H2 combines SQL initialization with ORM update. These affect portability and deletion semantics; no uniform schema/migration guarantee is inferred.
- **VERIFIED FACT / initialization caveat:** PostgreSQL config says SQL is idempotent (`R/application-postgres.properties:5-6`), but schema.sql has unconditional unnamed CREATE INDEX statements (`:6,12,24,34,43-44,52`). INFERENCE: repeated initialization may add redundant indexes. H2 indexes are also unconditional, and seed inserts are plain. Restart safety was not tested; an “all initialization is idempotent” rule is unsupported.
- **VERIFIED FACT / contract-body divergence:** Contract advertises ProblemDetail for visit 404s (`R/openapi.yml:1219-1224,1332-1337`), while sampled visit controller returns bodyless 404s. Visit update returns a DTO with 204 (`VisitRestControllerV1.java:88-96`) and tests expect JSON content type (`VisitRestControllerV1Tests.java:188-199`); contract instead specifies 200 with an application/json Visit body (`R/openapi.yml:1231-1265`). Actual wire-body behavior is UNKNOWN; none of these sources establishes a clean uniform convention.
- **VERIFIED FACT / security boundary:** README promises security when enabled (`readme.md:222-238`), and the conditional configurations support that intent; it does not promise enabled-by-default or secure credential lifecycle. The BCrypt bean, unchanged saveUser password and password-bearing DTO mapping are distinct facts; actual stored/returned values were not executed. Advice also reflects rejected input values. Do not silently turn these into an existing secrecy invariant.
- **VERIFIED FACT / testing-doc drift:** `src/test/postman/README.md:47-49` promises folder-specific executions/reports, while `postman-tests.sh:62-65` invokes the entire collection once and writes `report.html`. Smoke coverage should be read from the collection, not assumed from that description.
- **VERIFIED FACT / legacy comments:** `JpaVisitRepositoryImpl.java:32-34` mentions XML mappings, while inspected Visit/User entities use annotations; no `orm.xml` appears in the file inventory. `AbstractClinicServiceTests.java:45-47` says each test is transactional, but annotations are method-specific (compare `:62-81`). Use code/annotations for the brief and retain these discrepancies as documentation risks.
- **UNKNOWN / external enforcement and operations:** No repository AGENTS.md, CODEOWNERS, PR template, architecture-rule configuration, pre-commit configuration, ADR or amendment process was found in the inspected file inventory. GitHub required checks, active CI/Sonar policy, deployed authentication, private dependencies and client compatibility requirements were not inspected remotely. No claim about their actual absence outside this checkout is made.

# 5. UNRESOLVED HUMAN QUESTIONS

All are **UNKNOWN** governance matters with **HUMAN-DECISION-REQUIRED** status:

1. Is this project permanently educational as `terms.txt:3-6` states, or intended for deployment with stronger security/data requirements? Should enabling authentication by default, password encoding/redaction and validation-value redaction become approved remediation policies?
2. Which database/repository combinations are binding support commitments, and what process authorizes retiring one? Should database-schema parity and upgrade-safe migrations become explicit requirements despite current differences?
3. Is backward compatibility a new requirement despite `terms.txt:24`; if so, which endpoints, schemas, clients and deprecation periods are protected?
4. Which CI checks must block merging, who can approve a waiver, and how are coverage or security-policy amendments approved? Who ratifies/version-controls the constitution?

# 6. CONSTITUTION INPUT DRAFT

**A. PROJECT CONSTITUTION INPUT — draft only.** The following contains only the evidence-backed candidates above. Framework/tool versions and detailed implementation patterns remain in section 3. Ratification is a human decision; these principles are not established governance merely because this report proposes their wording.

### Principle 1: Conditional authorization integrity

Changes MUST preserve authenticated access and the applicable role authorization on business APIs when the application's security mode is enabled.

Rationale: Changes to any feature can otherwise bypass the access controls of the supported secured mode.

Verification: Trace affected operations to request authentication and role checks, and review enabled-mode denial behavior for affected access paths.

Status: ENFORCED

### Principle 2: Transactional business writes

Business persistence mutations MUST execute within a transaction at the service operation boundary.

Rationale: A shared transaction boundary prevents partially committed business operations across features.

Verification: Identify the service transaction enclosing each mutation and examine commit/rollback behavior for compound operations.

Status: FOLLOWED

### Principle 3: Shared persistence contracts

Changes to shared persistence operations MUST preserve their service-visible contract across every retained repository implementation.

Rationale: A change to a shared operation can otherwise break an entire selectable implementation.

Verification: Review each affected implementation and shared regression coverage, identifying database-specific effects explicitly.

Status: FOLLOWED

### Principle 4: Authoritative API contracts

Public API interface and model changes MUST originate in the authoritative API contract, with generated output regenerated rather than hand-edited.

Rationale: Contract drift and overwritten generated changes affect all consumers and subsequent builds.

Verification: Trace changed API behavior to the contract and handwritten implementation, then review regeneration/compilation results and editable-source boundaries.

Status: FOLLOWED

### Principle 5: Executable verification gates

Changes MUST pass the repository's automated pull-request verification and configured coverage gates before merge.

Rationale: Executable regression and coverage checks protect all features from changes that bypass established quality constraints.

Verification: Review relevant passing PR check results and confirm the verification commands, thresholds and exclusions were not bypassed.

Status: HUMAN-DECISION-REQUIRED

Governance note: No constitution amendment, ratification, waiver or versioning procedure was found. Humans must designate that process and decide required-before-merge checks; checked-in workflows alone do not establish protected-branch enforcement. Until ratified, this is input to `/speckit.constitution`, not a binding rulebook.
