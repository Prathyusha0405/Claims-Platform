# AI Working Journal

A record of how AI assistance was used to design and implement the CHUBB APAC Claims Management Platform.

The journal captures:

- Prompts provided to AI
- Accepted outputs
- Rejected outputs
- Manual modifications
- Verification performed
- Key architectural decisions

Tool Used:

Claude Code

Development Approach:

- AI assisted development
- Human reviewed all generated output
- Feature-by-feature implementation
- Architecture-first approach

---

# 1. Solution Architecture

## Prompt

Design the architecture for a claims management platform supporting claimants, claims officers, and managers.

## Accepted

Microservices architecture:

- Auth Service
- Claim Service
- Assessment Service
- Angular Frontend

## Rejected

Single monolithic backend application.

## Manual Changes

Added dedicated Assessment Service for workload management and dashboards.

## Reasoning

The assessment emphasizes architecture decisions. Separating authentication, claims, and assessments provides clear service boundaries while remaining achievable within the assessment timeframe.

---

# 2. Technology Selection

## Prompt

Recommend a technology stack for the platform.

## Accepted

Backend:

- Java 21
- Spring Boot 3
- Spring Security
- JWT
- Spring Data JPA
- Flyway
- H2 Database
- OpenAPI

Frontend:

- Angular
- Angular Material
- Reactive Forms

## Rejected

- PostgreSQL
- MySQL
- Lombok

## Manual Changes

H2 Database selected to simplify local execution and review.

The build actually uses JDK 27 (the only JDK installed) and the front end was scaffolded with Angular 22, instead of the
Java 21 and Angular 19 listed above and in the brief. See sections 17 and 19.

## Reasoning

The focus of the assessment is software design and backend implementation rather than database infrastructure.

---

# 3. Authentication Service

## Prompt

Generate an authentication service using JWT and role-based access control.

## Accepted

Components:

- User Entity
- Role Enum
- Authentication Service
- Security Configuration
- JWT Service
- JWT Filter
- Auth Controller

Roles:

- CLAIMANT
- OFFICER
- ADMIN

## Rejected

Session-based authentication.

OAuth integration.

## Manual Changes

Adjusted package layout to match project conventions.

## Reasoning

JWT supports stateless security and aligns naturally with a microservices architecture.

---

# 4. Database Design

## Prompt

Generate database design and migrations.

## Accepted

Flyway used for schema versioning.

Separate schemas per microservice.

Migration strategy:

- V1__schema.sql
- V2__seed_data.sql

## Rejected

Hibernate schema auto-generation.

## Manual Changes

Added audit-related columns and indexes.

## Reasoning

Database versions should be explicit, consistent, and repeatable across environments.

---

# 5. Claims Domain

## Prompt

Design the claims processing domain.

## Accepted

Entities:

- Claim
- ClaimHistory
- Notification

Claim lifecycle:

- SUBMITTED
- UNDER_REVIEW
- MORE_INFO_REQUIRED
- APPROVED
- REJECTED
- SETTLED

## Rejected

Embedding assessment information directly inside Claim.

## Manual Changes

Added ClaimHistory for auditability.

## Reasoning

Claims and assessments represent separate business responsibilities.

---

# 6. Assessment Domain

## Prompt

Design claim assessment workflow.

## Accepted

Entities:

- ClaimAssessment
- ClaimNote

Features:

- Assign Claim
- Approve Claim
- Reject Claim
- Request Information

## Rejected

Storing officer notes directly on claims.

## Manual Changes

Added support for officer workload metrics.

## Reasoning

Assessment activities are operational functions and belong in a dedicated service.

---

# 7. Event Driven Design

## Prompt

Implement system events.

## Accepted

Event Publisher abstraction.

Components:

- ClaimEventPublisher
- LocalClaimEventPublisher

Events:

- CLAIM_CREATED
- CLAIM_ASSIGNED
- CLAIM_APPROVED
- CLAIM_REJECTED
- ADDITIONAL_INFO_REQUESTED

## Rejected

Direct Kafka implementation.

## Manual Changes

Current implementation logs events and triggers notifications.

## Reasoning

Local execution requirements made Kafka impractical. Event abstraction preserves future extensibility without affecting business logic.

---

# 8. Search and Filtering

## Prompt

Generate claims search functionality.

## Accepted

Filters:

- Claim Number
- Policy Number
- Status
- Date Range
- Assigned Officer

Features:

- Pagination
- Sorting

## Rejected

Simple search-by-id implementation only.

## Manual Changes

Page size restrictions added.

## Reasoning

Claims officers need efficient access to large claim volumes.

---

# 9. Dashboard Design

## Prompt

Design dashboard metrics.

## Accepted

Metrics:

- Total Claims
- Pending Claims
- Approved Claims
- Rejected Claims
- Officer Workload
- Liability Exposure

## Rejected

Dashboard limited to claim counts.

## Manual Changes

Added workload distribution view.

## Reasoning

Managers require operational visibility in addition to claim status information.

---

# 10. Liability Exposure

## Prompt

Calculate liability exposure.

## Accepted

Formula:

SUM(claimAmount)

WHERE status != SETTLED

## Rejected

Calculation based only on approved claims.

## Manual Changes

None.

## Reasoning

Outstanding liability should represent unresolved financial exposure.

---

# 11. Seed Data

## Prompt

Generate realistic demo data.

## Accepted

Created:

- Users
- Claims
- Claim History
- Notifications
- Assessments
- Officer Notes

## Rejected

Minimal sample dataset.

## Manual Changes

Expanded dataset for dashboard demonstrations.

## Reasoning

Large realistic datasets improve dashboard validation and walkthrough quality.

---

# 12. API Documentation

## Prompt

Generate OpenAPI documentation.

## Accepted

Swagger UI enabled for all services.

URLs:

Auth Service

http://localhost:8084/swagger-ui/index.html

Claim Service

http://localhost:8082/swagger-ui/index.html

Assessment Service

http://localhost:8083/swagger-ui/index.html

## Rejected

Manual endpoint documentation only.

## Manual Changes

Added request and response examples.

## Reasoning

Swagger improves API discoverability and testing.

---

# 13. Angular Frontend

## Prompt

Generate Angular architecture for claimant, officer, and admin users.

## Accepted

Modules:

- Auth
- Claimant
- Officer
- Admin
- Core
- Shared

## Rejected

Single-module application.

## Manual Changes

Role-specific navigation structure.

## Reasoning

Feature-based modules improve maintainability and scalability.

---

# 14. Authentication Frontend

## Prompt

Generate Angular authentication flow.

## Accepted

Features:

- Login
- Registration
- Route Guards
- JWT Interceptor

## Rejected

Local mock authentication.

## Manual Changes

None.

## Reasoning

Backend and frontend security models should remain consistent.

---

# 15. Testing Strategy

## Prompt

Generate testing strategy.

## Accepted

Backend:

- Service Tests
- Controller Tests
- Security Tests

Frontend:

- Component Tests

## Rejected

Manual testing only.

## Manual Changes

Additional dashboard verification scenarios.

## Reasoning

Critical business logic should be validated through automated tests.

---

# 16. Verification Activities

The following validations were performed:

✅ Flyway migrations execute successfully

✅ JWT authentication flow works

✅ User registration and login tested

✅ Claim creation tested

✅ Claim search verified

✅ Assessment workflows tested

✅ Dashboard metrics validated

✅ Liability exposure calculations verified

✅ Swagger documentation accessible

✅ Angular application successfully connected to backend APIs

---

# 17. Test Execution and Coverage

## Prompt

run all the test cases and add test count and test coverage in README.md and update AI WORKING JOURNALS

## Accepted

Backend: every test run on JDK 27 with JaCoCo 0.8.15.

- auth-service 79, claim-service 212, assessment-service 131: **422 tests, 0 skipped**.
- Coverage: instructions 97.4%, branches 81.4%, lines 97.2%, methods 97.2%.
- Lowest branch coverage is auth-service at 61.8%; the uncovered lines are mostly the `GlobalExceptionHandler` classes and the
  `main` methods.

Frontend: every test run with Vitest and its V8 coverage provider.

- **234 tests in 19 files, all passed.**
- Coverage: statements 67.3%, branches 73.9%, functions 69.6%, lines 69.7%.
- The officer and admin pages are the gap: claim-review 1% of lines, my-assigned-claims 5%, admin dashboard 35%,
  unassigned-queue 52%.

Both sets of numbers, how they were measured and the commands to reproduce them are now in the README ("Tests" section).

A flaky test was found and diagnosed: `OfficerWorkflowIntegrationTest.anOfficerTakesAClaimAsksForInformationAndApprovesIt`
failed in the first full backend run and passed when assessment-service was run again. It mints one JWT, then compares it
with the token its request helper mints later. A JWT records its issue time to the second, so the two differ whenever the
clock ticks over between them. The cause is in the test, not in the service.

## Rejected

Reporting the backend as "422 passed" from one clean run: it was not one. The first run had 1 failure (assessment-service,
130 of 131), and the 422 comes from that run for auth and claim plus a second run of assessment alone.

Adding JaCoCo to the `pom.xml` and the coverage provider to `package.json`. Coverage was measured with the JaCoCo goals run
from the command line, in a scratch copy of `backend/`, and with `npm install --no-save` for the front end, so the project's
build files and lockfile were not changed.

Changing the flaky test without being asked.

## Manual Changes

None to the code. The tests ran on JDK 27 (the only JDK installed), not the Java 21 listed in section 2. The coverage
output folder the front-end run created was deleted afterwards.

## Reasoning

A pass count says nothing about what the tests touch, and a coverage figure says nothing about whether the tests assert
anything. Reporting both, with the run's caveats, is more honest than either alone. The two numbers also qualify section 16:
the backend workflows are tested to a high level, while the officer review screen and the admin dashboard are only verified
by a one-off headless-browser walk-through (its script was not kept in the repository), not by tests that run with the build.

Open choices for the owner: fix the flaky test (capture the token the request used), and decide whether JaCoCo and the Vitest
coverage package should become part of the build, possibly with a coverage threshold.

---

# 18. Port Conflict: auth-service Moved to 8084

## Prompt

> both auth-service and frontend are using 8081

> change auth-service port to 8084

> update CLAUDE.md to 8084 too

## Accepted

Investigation first. The front end does not listen on 8081 in the repository: `angular.json` sets no port, so `ng serve`
uses 4200, and 8081 appears only in `proxy.conf.json` as the target the dev server forwards `/api/auth` and `/api/users`
to. Nothing was listening on 8081, 4200 or the other service ports when it was checked, so the clash could not be
reproduced and its cause was not found. The likeliest explanations were an IDE run configuration or command passing
`--port 8081`, or a stale process; neither was confirmed.

The port was then changed as asked: `application.yml` (auth-service), `proxy.conf.json`, the comment in `endpoints.ts`,
the README, `architecture.md`, the Swagger URL in section 12 of this journal, and finally `CLAUDE.md`. No file in the
repository mentions 8081 any more.

Verification: the three services were built from a clean copy of `backend/` and started on 8084, 8082 and 8083; a sign-in
through the already-running Angular dev server (port 4200) reached auth-service and was answered with its 401 for a bad
password, which shows the proxy points at 8084. No test refers to a fixed service port, so the test suites were not
affected.

## Rejected

Pinning `"port": 4200` in `angular.json` as a guess at the cause. It would not have overridden a `--port` flag, and the
cause was unknown.

Editing `CLAUDE.md` before being asked. It was reported as the one remaining file with 8081, then changed on request.

## Manual Changes

The choice of 8084 and the decision to update `CLAUDE.md` were the owner's.

## Reasoning

A port is a contract between four places (service config, dev-server proxy, documentation, brief). Changing one and
searching the repository for the old number afterwards keeps them from drifting apart. If the clash returns on the
owner's machine, the first thing to check is which process holds the port (`netstat -ano | findstr :8084`).

---

# 19. Documentation Review and Update

## Prompt

> Can you update all docs these are for Client interview i got less score on docs

## Accepted

The reason for the low score was not stated, so the docs were reviewed against what a reader needs and against the code.
Problems found:

- `frontend/claims-portal/README.md` was still the untouched Angular CLI template.
- This journal said Java 21 while the build uses JDK 27.
- The README had no screenshots, no mapping from the brief to the code, and no API examples.
- `architecture.md` had no data-model diagram and did not state its assumptions.
- The documented auth port was out of date after section 18.

Changes made:

- **README.md**: restructured (contents, at-a-glance diagram and ports, quick start first, a tour of the app, a requirements
  coverage table, API tables, test results, documentation map, troubleshooting for ports and the dev-server proxy).
- **docs/api-guide.md** (new): one claim followed from filing to approval with `curl`, the dashboards, and every error
  shape, using real responses from the running services.
- **docs/screenshots/** (new): eight screenshots of the running app on the seed data, taken with headless Chrome. The
  capture only signs in and looks; it changes no data.
- **architecture.md**: assumptions, quality attributes, a ports table, an ER diagram of the six tables (real foreign keys
  and logical cross-service references drawn differently), a decision row for port 8084, and the flaky test in the gaps.
- **frontend/claims-portal/README.md**: rewritten for this app (run, routes, layout, how it works, testing, gaps).
- **This journal**: the correction in section 2, sections 18 and 19, more Key Decisions and Future Enhancements.

Verification performed on 2026-10-23:

- All three services built and started from a clean copy of `backend/`; the API journey in the guide was run against them and
  its responses copied from the output.
- Full backend suite in one run: 79 + 212 + 131 = 422 tests, 0 failures. Front-end suite: 234 tests in 19 files, all passed.
- Statements in the new tables were checked against the repository: no Lombok, no PostgreSQL, MySQL or Kafka dependency, no
  Dockerfile, no `@Autowired` in production code (22 test classes use it), all 36 `dto` types are records.
- A first draft of the API guide said a bad paging value returns 400. Checking showed `size=1000` is clamped to 100 and
  `page=-1` returns 200, so the guide was corrected.
- All five Mermaid diagrams (one in the README, four in `architecture.md`, including the new ER diagram) were parsed with
  Mermaid 11.4.0's own parser in headless Chrome, with no errors. That checks syntax, not how a particular site renders them.
- Every relative link and heading anchor in the docs was checked to resolve.

## Rejected

Inventing prompts or outcomes for sections 1 to 16. They are the owner's record and were left as written, apart from the
factual note in section 2.

Re-measuring coverage. The 2026-10-22 figures are kept and labelled with their date; only the test counts were re-run.

Deleting the stray file `0` in the repository root (8 bytes, tracked in git, not part of the project), fixing the flaky
test, and adding screenshot capture to the repository. None was asked for; the capture scripts were left in the scratch
area.

## Manual Changes

None recorded yet. The owner should read the new wording and correct anything that does not match their own account of how
the work was done, since this journal is their record.

## Reasoning

Reviewers of a take-home read the README first and the journal to judge process, so those carry the most weight: a reader
should be able to run the system in minutes, see what it does without running it, and see which parts of the brief are
met and which are not. Documents that state their gaps and dates (coverage measured a day earlier, a flaky test not fixed,
a settle endpoint missing) are easier to trust than ones that claim everything.

---

# 20. Business Context Added to the Docs

## Prompt

> Chubb APAC processes motor and property claims across six markets. Today the process is fragmented: claimants submit by
> phone or email and wait with no visibility into what is happening. Claims staff manage incoming work from shared inboxes
> and spreadsheets, with no consolidated view of their workload. Managers have no real-time picture of outstanding claims
> or liability exposure.

The message carried no instruction. It was read as the scenario the documentation should be anchored to, following the
request in section 19 to improve the docs.

## Accepted

The scenario is now stated at the top of the README ("Business context") and in `architecture.md` section 1, with a table
that maps each group (claimants, claims staff, managers) to the capability that answers its problem and, in the
architecture, to the design choice behind it.

Before claiming coverage, the scenario was checked against the data. The seed policies do cover exactly six markets
(AU, HK, JP, MY, NZ, SG) and two lines of business (motor: claim type `AUTO`, 146 claims, policies `MTR-...`; property:
`HOME`, 104 claims, policies `PRP-...`).

## Rejected

Saying the platform supports six markets. It does not model a market: the market is only a segment of the policy number
text, there is no market field, filter or dashboard, and amounts are single-currency. Both documents now say so, list it as
a known limitation, and describe the fix (a `market` and a `currency` column, a search filter, and exposure reported per
currency). Likewise the docs do not claim email or SMS alerts; notifications are in-app only.

## Manual Changes

None recorded. The owner should confirm the gap is described the way they want to present it.

## Reasoning

A reviewer reading the brief's scenario will test the solution against it. Mapping each stated problem to a feature shows the
design follows from the need, and naming the one place where the demo falls short of "six markets" is better than letting an
interviewer find it.

---

# 21. Market Field and Filter

## Prompt

> add the market field and filter

This followed section 20, which had found that the six markets existed only as text inside policy numbers.

## Accepted

A required `market` on every claim, one of six ISO country codes (`AU`, `HK`, `JP`, `MY`, `NZ`, `SG`), chosen by the
claimant when filing.

- **claim-service:** a `Market` enum; a `market` column with a CHECK constraint and an index in `V1__schema.sql`; a value on
  all 250 seed rows in `V2__seed_data.sql`, taken from each policy number and cross-checked against the row's own comment
  (it gives AU 70, HK 30, JP 40, MY 35, NZ 25, SG 50, the split already documented in the seed header); `market` required on
  the create request and present on every claim response; an exact `market` search filter whose unknown values are a 400 that
  lists the allowed ones; and `market` as a sortable field.
- **assessment-service:** `market` on the work-queue rows and an optional `market` parameter on
  `GET /api/officer/claims/unassigned`, passed straight to claim-service so that validation stays in one place.
- **Angular app:** a required Market select on the claim form, a sortable Market column in every claim list, a Market filter in
  the search bar, a market select on the officer's work queue, and the market on the claim details.
- **Tests:** 14 new backend tests and 6 new front-end tests (the first spec for an officer page), plus updates to the existing
  ones. Counts are now 436 backend (79 + 221 + 136) and 240 front end in 20 files.
- **Docs:** README, `architecture.md`, `docs/api-guide.md` and the front-end README; five screenshots retaken and one added.

Verification performed on 2026-10-23:

- The full backend suite and the front-end suite were run. In the full backend run assessment-service had one failure, the
  known flaky `OfficerWorkflowIntegrationTest` (a JWT compared with one minted a second later); on its own it passed 136 of 136.
  Front end: 240 of 240. The production build passes and the bundle size is unchanged (505.63 kB).
- The three services were started from freshly built jars and a 20-step script checked the real behaviour: the six seeded counts
  through the API, the work queue narrowed to a market, an unknown market refused on search, create and the queue, a claim filed
  in New Zealand found by that market only and visible in the New Zealand queue, sorting by market, and the dashboard still
  adding up (251 claims).
- The changed screens were driven in a real browser (headless Chrome) and photographed; the work queue filtered to Japan shows
  only Japanese claims.

## Rejected

Deriving the market from the policy number. A policy number is free text (`POL-2026-12345` has no country in it), and the one
thing that must be reliable for filtering is a field. The cost is that nothing checks the market against the policy number.

A `V3` migration. `FLYWAY_TARGET=1` must still mean "schema only", and a `V3` would have broken that, so `V1` and `V2` were
edited in place. That is only acceptable because every database here is in memory and rebuilt on each start; against a database
that exists anywhere it would be a `V3` that adds the column, backfills it and then makes it NOT NULL.

Per-market dashboards and a currency column. Neither was asked for; they are the stated next step.

Fixing the flaky test, which failed again in the full backend run. Not asked for.

Stopping the services started from IntelliJ on ports 8083 and 8084 (and the dev server on 4200) to run the checks. They were
the owner's, so the checks ran on spare ports (18082 to 18084) with a second dev server on 4300 instead.

## Manual Changes

None recorded. The owner's running services still hold the old code and the old schema: claim-service and assessment-service
need restarting from IntelliJ before the new field appears in the app (the dev server on 4200 picks up the front-end changes
by itself).

## Reasoning

The brief's business context is six markets, and a field that can be filtered is what turns that sentence into something a
claims officer can use. Choosing a required, validated list over free text keeps the data clean; leaving it to the claimant
rather than inferring it keeps the rule simple and visible. Stating what was not done (no cross-check, no per-market
dashboard, one currency) is deliberate, so the limits are known before an interviewer asks.

---

# 22. Currency and Market-Aware Dashboards

## Prompt

> update dashboards and currency

This followed section 21, which left two gaps in the docs: the dashboards still added every market together, and amounts had
no currency.

## Accepted

**Currency.** Each market has one currency (AU AUD, HK HKD, JP JPY, MY MYR, NZ NZD, SG SGD). Every claim now has a
`currency`, set by claim-service from the market when the claim is filed. The client never sends it (a `currency` in the
request body is ignored), and the database refuses any claim whose currency does not match its market. The 250 seed claims
were converted from their original USD-equivalent amounts into local currency at fixed rates (yen as whole numbers), so the
demo has realistic amounts such as ¥506,850 and S$5,184.00.

**Dashboards.** All three take an optional `market`. Claim-service's statistics are now grouped by market, currency and
status, each amount in its own currency. Assessment-service converts them to USD at fixed, indicative rates held in its
configuration (`app.dashboard.rates`), converting each (market, status) total on its own and summing the rounded parts so
every breakdown adds up to its total. The responses gain `reportingCurrency`, `market` and a `byMarket` list that is never
narrowed by the filter: each market's exposure in its own currency and in USD, next to the others.

**Angular app.** Amounts everywhere are shown in their own currency with the ISO code (`SGD 5,184.00`, `JPY 506,850`); the
claim form's amount prefix follows the chosen market; the admin dashboard has a Market selector, reports in USD and has a "By
market" table.

**How it was done.** The Angular half was delegated to a forked sub-agent working in parallel against a written API contract,
while the backend, the seed data and the verification were done in the main session. The sub-agent's report was not taken on
trust: its test suite was re-run here (257 of 257), its main screens were photographed and checked, the production build was re-run, and the end-to-end script
below ran against the real services.

**Tests.** 31 more backend tests (and rework of the existing dashboard tests) and 17 more front-end tests (the first spec for the
admin dashboard and for the money formatter). Counts are now 467 backend (79 + 225 + 163) and 257 front end in 22 files.

Verification performed on 2026-10-23:

- The backend was run in full and, after one assertion was updated, claim-service and assessment-service were re-run
  together and passed (225 and 163). Front end 257 of 257; the production build passes and the bundle is 501.15 kB (it fell
  by about 4 kB because Angular's `CurrencyPipe` is no longer used).
- A 29-step script drove the real services on the pristine seed and compared them with figures computed independently from
  the seed SQL: the liability exposure of 2,674,303.58 USD over 214 claims, each of the six markets in its own currency and in
  USD (for example Japan 90,401,325 JPY = 602,675.50 USD), every officer's open exposure, the market filter on all three
  dashboards, an unknown market refused with a 400, a client-sent currency ignored, and the exposure moving by exactly the
  converted amount when claims in yen, Australian dollars and Singapore dollars were filed.
- The changed screens were driven in a real browser and photographed.

## Rejected

Letting the claimant choose the currency. It would allow a Singapore claim in yen by mistake, and nothing in the brief asks
for it; it is derived from the market instead. The cost is that a claim cannot be in another currency, which is stated.

Live exchange rates. There is no rates service here, so fixed rates in configuration are used and every place that shows a
converted figure says they are indicative. A real deployment would take dated rates from a service.

Adding up amounts of different currencies as they stand, which is what the old dashboards would now have done.

A `V3` migration, for the reasons given in section 21: `V1` and `V2` were edited in place, which is acceptable only because
every database here is in memory. The seed's USD exposure moved from 2,674,303.50 to 2,674,303.58 through rounding.

Fixing the flaky `OfficerWorkflowIntegrationTest`, which failed again in the first full backend run (it has now failed three
times under load, and passes when re-run). Not asked for.

Stopping the owner's own services to run the checks; they ran on spare ports (18082 to 18084) with a second dev server on 4300.

## Manual Changes

None recorded. The owner's running claim-service and assessment-service need restarting from IntelliJ: claim-service has a new
column and assessment-service a new configuration block. The exchange rates in `assessment-service/src/main/resources/application.yml`
are indicative placeholders for the owner to confirm; they must match the rates the seed amounts were converted at, or the
documented USD figures drift.

## Reasoning

Six markets in six currencies cannot be summed as one number, so a manager's exposure figure needs an explicit, visible
conversion, and the per-market figures in local currency need to stay exact next to it. Keeping the rate card in the service
that reports (not in the claims) means a rate change moves a report, never a claim. Stating what is approximate (the rates),
what is simplified (one currency per market, yen with two decimals) and what is not done (live rates) is deliberate, so the limits
are known before an interviewer asks.

---

# 23. Roles Renamed: Claimant, Claims Staff, Manager

## Prompt

> we have 3 roles manager, claimest, claims staff

There was no instruction in the message. The code and docs used CLAIMANT, OFFICER and ADMIN, so it was read as a correction of the
role names. Because "rename" can mean three very different amounts of work, the owner was asked how far to go (names only, role
codes too, or everything including URLs) and chose **everything**.

## Accepted

**Role codes.** `OFFICER` became `CLAIMS_STAFF` and `ADMIN` became `MANAGER` (`CLAIMANT` is unchanged) in the JWT, the login
response, the auth database (the `users.role` constraint and the seed), Spring Security's rules in all three services, claim history
(`performed_by_role`, including the seeded rows) and every test.

**URLs and routes.** `/api/officer/**` became `/api/staff/**`; the Angular routes `/officer/*` and `/admin/*` became `/staff/*` and
`/manager/*`. The role-named classes and folders followed: `StaffClaimController`, `StaffApi`, `features/staff`, `features/manager`.
`/api/dashboard/**` is unchanged.

**Words people see.** Login buttons ("Claimant", "Claims staff", "Manager"), navigation, headings ("Claims staff workload"),
the history label ("Claims staff"), Swagger text, error messages and notifications ("assigned to a claims staff member", in the
code and in the seeded rows), the README, `architecture.md`, the API guide, the front-end README and `CLAUDE.md` (its roles,
endpoint list and module list, at the owner's choice).

**Demo logins.** `officer@chubb.com / Officer@123` became `staff@chubb.com / Staff@123` and `admin@chubb.com / Admin@123` became
`manager@chubb.com / Manager@123`; the other four claims staff share the new staff password. The six BCrypt hashes in the auth seed
were regenerated and checked against Spring's own encoder by the existing seed test.

**Deliberately not renamed (at the time; renamed in section 24).** Data names that describe a claim rather than a role: `assignedOfficerId`, `officerId`, `officerName`,
the `officers` list, `OfficerWorkload`, the `officer_id` columns and the `/api/dashboard/officer-workload` endpoint. Renaming
them would change the three database schemas and every API example. The README and `architecture.md` say so.

**How it was done.** The Angular half (49 files, folder moves, new guard tests) went to a forked sub-agent against a written spec,
in parallel with the backend, which was a scripted rename with the test suites as the safety net, then a docs-aware script that
changes roles, URLs, logins and names everywhere but leaves JSON keys and identifiers alone. The sub-agent's report was
re-checked here by re-running its tests and build.

Verification performed on 2026-10-23:

- Backend: 467 tests (auth 79, claim 225, assessment 163), 0 failures. The first run found two genuine failures in the auth seed test:
  an assertion that depended on the alphabetical order of the old role names, and the new hashes carrying the `$2b$` prefix where
  the project uses `$2a$` (the same algorithm for ordinary passwords). Both were fixed and the suite re-run.
- Front end: 276 tests in 23 files (19 more than before, including guards that now refuse a token carrying `OFFICER` or `ADMIN` and
  routes under `/officer` and `/admin` that now show "Page not found"). The production build passes at 501.15 kB.
- A 24-step script ran the real services: the three logins and their role codes in the response and in the JWT, the old logins
  refused, who may call what at the new URLs (including a 404 for the old `/api/officer` path), the whole claim journey, the history
  and notification wording in new and seeded data, and the dashboard still reading 2,674,303.58 USD.
- All eleven screenshots were retaken and six renamed (for example `02-manager-dashboard.png`, `07-staff-work-queue.png`).

## Rejected

Keeping the old logins and URLs as aliases. It would keep "officer" alive in the contract, and there are no outside clients to protect.

Renaming the data names listed above. Left as a possible next step rather than done half-way. (The owner asked for it next; see section 24.)

Rewriting this journal's history. Sections 1 to 22 (including the owner's own text) use the old role names, as they were at the
time; this section records the change.

Stopping the owner's running services. The checks ran on spare ports (18082 to 18084) with a second dev server on 4300.

## Manual Changes

None recorded. The owner needs to restart all three services from IntelliJ (new role values, seed and URLs) and `ng serve` (folders
moved), and anyone still signed in must sign in again: a token issued before the change carries `OFFICER` or `ADMIN`, which the
services and the app now refuse.

## Reasoning

The three roles are the business's own words for its three kinds of user (claimants, claims staff, managers), and a take-home that
names them differently in the brief, the API, the database and the screens reads as unfinished. Doing it fully, with the tests and
a live walk-through as evidence, is more convincing than a label change. Keeping the data names is the line between "what a person
is called" and "what a claim records", and it is stated so nobody has to discover it.

---

# 24. Officer Data Names Renamed to Staff

## Prompt

> rename the officer data names too

This is the follow-up that section 23 offered: the names that describe a claim's data and had kept the older word "officer".

## Accepted

**What was renamed.** Every identifier, JSON key, column and URL that still said "officer", to "staff":

| Before | After |
|---|---|
| `assignedOfficerId` (claim responses and the search filter) | `assignedStaffId` |
| `officerId`, `officerName` (assessments, notes, dashboard rows) | `staffId`, `staffName` |
| `officers` (list in the workload report) | `staff` |
| `byOfficer` (claim-service statistics) | `byStaff` |
| `/api/dashboard/officer-workload` | `/api/dashboard/staff-workload` (the old URL is a 404) |
| `OfficerWorkload`, `OfficerWorkloadResponse`, `OfficerStatusTotal`, `RemoteOfficerStatusTotal`, `NotAssignedOfficerException` | `StaffWorkload`, `StaffWorkloadResponse`, `StaffStatusTotal`, `RemoteStaffStatusTotal`, `NotAssignedStaffException` |
| columns `assigned_officer_id`, `officer_id`, `officer_name` and their indexes | `assigned_staff_id`, `staff_id`, `staff_name` |
| Angular `OfficerWorkload`, `API.dashboard.officerWorkload`, `DashboardApi.officerWorkload()` | `StaffWorkload`, `API.dashboard.staffWorkload`, `DashboardApi.staffWorkload()` |

Local variables and parameters that were called `officer` became `staffMember`. Eighty files changed, five Java files were renamed
with `git mv`, and the claim-service, assessment-service and Angular code, the claim-service and assessment-service `V1`/`V2` migrations (auth-service has no such columns), the tests, the
README, `architecture.md`, the API guide (its sample responses, so they match the new keys) and `CLAUDE.md` (its endpoint list) were updated.

**How it was done.** One scripted rename, written to be applied in a fixed order (the hyphenated URL first, then plurals, then a lone
`officer`, then the pieces of compound names), after backing up every file it would touch, with the existing tests as the safety net. Three Angular specs were left alone on purpose, because they prove that a token carrying the retired role `OFFICER` and the
old `/officer` routes are refused.

**Edited in place, again.** As with the market and currency columns, `V1` and `V2` of claim-service and assessment-service were changed rather than adding
`V3` files that rename columns. That is safe only because the databases are in memory and rebuilt on every start; the README and
`architecture.md` now say what an existing database would need instead.

Verification performed on 2026-10-23:

- Backend: 467 tests (auth 79, claim 225, assessment 163), 0 failures.
- Front end: 276 tests in 23 files; the production build passes at 501.15 kB.
- A 21-step script ran the real services on spare ports, started fresh: the new keys on claims, assessments, notes and the
  workload report, the old URL refused, the whole claim journey, and the dashboards still reading 2,674,303.58 USD. The 24-step script from
  section 23 (logins, role codes, who may call what) was re-run on fresh services as well and passed. The first draft of the new script
  wrongly expected `assignedStaffId` to be a sortable field; it never was (the sortable fields are unchanged), so that check was
  removed rather than the code changed.
- In a real browser (headless Chrome against the running services) the manager dashboard listed all five claims staff with their
  figures, and the staff "My claims" tab showed the signed-in staff member's own claims, which use `assignedStaffId` as the filter. The
  eleven README screenshots were not retaken: no screen text changed.
- The word "officer" now appears nowhere in the code, migrations, tests, README, `architecture.md`, API guide or `CLAUDE.md`, except in the
  three guard specs above. In this journal it appears in the sections written before the rename, as history.

## Rejected

Keeping the old JSON keys or URL as aliases. The same reasoning as section 23: there are no outside clients, and an alias keeps the
old word in the contract.

Adding migrations instead of editing `V1`/`V2`. It would be the right move for a database that exists somewhere, and not here.

Renaming the `officer`/`admin` wording in sections 1 to 23 of this journal. They record what was said and done at the time.

## Manual Changes

None recorded. The services must be restarted (new column names, URL and JSON keys), and a browser tab left open on the manager
dashboard should be reloaded: an old tab would ask for the retired `/officer-workload` URL.

## Reasoning

Section 23 drew a line between a role's name and a claim's data, and kept the second as it was to limit the change. The owner
preferred one vocabulary everywhere, which also removes the question an interviewer would otherwise ask: why does "claims staff" own a
column called `officer_id`? The cost is large but mechanical, so it was done by script and then checked by the full test suites and a
live walk-through rather than by reading eighty files.

---

# 25. README Header With Badges

## Prompt

> in README.md file can you add like this

followed by a pasted README header: a centred title, a one-line tagline, a short description, two rows of shield badges (the stack,
then build and test results) and a row of links to the main sections and documents, closed by a horizontal rule.

## Accepted

The same layout at the top of `README.md`, filled with this project's facts instead of the pasted ones:

- **Title and tagline.** "APAC Claims Platform" and the tagline "Motor & property claims · Explicit workflows · Traceable decisions",
  kept word for word because it describes this project as well (the claim lifecycle is an explicit state machine and every change
  is written to the claim history).
- **Stack badges.** Java 27, Spring Boot 3.5.16, Angular 22, H2 (in memory) and Flyway, read from `backend/pom.xml` and
  `frontend/claims-portal/package.json`. The pasted header showed Java 17, PostgreSQL, Kafka and Docker. They were not copied: `CLAUDE.md`
  rules out PostgreSQL, Docker and Kafka, and the build uses JDK 27, so those badges would have been wrong.
- **Result badges.** Local build passed, backend tests 467 passed, front-end tests 276 passed, and a "last verified 2026-10-23" badge.
  These are fixed text, not a live build status: nothing runs them on a schedule, so the date badge says when they were true. The
  pasted "PostgreSQL test skipped" badge has no equivalent here (no test is skipped) and was dropped.
- **Links.** Quick start, Build & test (the Tests section), API guide (`docs/api-guide.md`), Requirements (the coverage table),
  Architecture (`architecture.md`) and the AI journal, all checked by the link checker.
- **Stale counts fixed while there.** The README, `architecture.md` and the front-end README still said 257 front-end tests in 22
  files and 724 in total, left over from before the role rename. They now say 276 in 23 files and 743 in total, and the "Latest
  results" note was rewritten to say what the last run showed and keep the earlier failures on the record.

Not verified: whether the badge images render. They are fetched from shields.io when GitHub shows the page, and this machine's
network inspects TLS, so the requests could not be made here. The addresses use the same static-badge format as the pasted ones; the
Flyway logo name is the one most likely to be wrong, and a missing logo only means the badge shows without its icon.

## Rejected

Copying the pasted header unchanged. Three of its badges would have claimed technology this project does not use.

Wiring the result badges to a CI service. There is no pipeline, and adding one was not asked for.

## Manual Changes

None recorded.

## Reasoning

A README is read before anything else, so a first screen that states the stack, the verified state and where to go next is worth the
few lines. The risk with badges is that they assert things; every one here comes from a file or a run that can be pointed to, and the
one thing not checked is said so.

---

# Key Decisions

1. Microservices over Monolith
2. JWT Authentication over Session Authentication
3. H2 Database over PostgreSQL for local execution
4. Flyway over Hibernate Schema Generation
5. Event Publisher Pattern instead of direct Kafka
6. Audit Trail support through ClaimHistory
7. Notification module included for claimant visibility
8. Backend-first implementation strategy
9. Feature-based Angular architecture
10. Dashboard metrics aligned with business objectives
11. Claim Service as the single owner of claim status and the audit trail; Assessment Service calls it
12. The caller's JWT forwarded between services instead of separate service credentials
13. Relative `/api` URLs and a dev-server proxy instead of CORS configuration
14. Officers take claims themselves (self-assign), because there is no officer directory
15. Dashboards computed live from claim-service aggregates, with the liability formula applied literally
16. Auth Service on port 8084 instead of 8081
17. Market as a required, claimant-chosen field on every claim, not derived from the policy number
18. Currency derived from the market and stored on each claim; dashboards convert to USD at fixed, configured, indicative rates
19. Roles named Claimant, Claims staff and Manager throughout (codes, URLs, routes, wording)
20. The claim data uses the same word: `assignedStaffId`, `staffId`, `staffName`, `staff_id` columns and `/api/dashboard/staff-workload`; no "officer" remains in the code

The reasoning behind 11 to 20 is in `architecture.md`, sections 4 and 14.

---

# Future Enhancements

The following items were intentionally deferred:

- A settle endpoint and a `CLAIM_SETTLED` event (nothing moves a claim to SETTLED yet)
- An officer directory, so managers can assign claims and names replace "Officer #n"
- Refresh tokens and asymmetric JWT signing
- Component tests for the officer and admin pages, and a committed end-to-end suite
- Coverage tooling (JaCoCo, Vitest coverage) in the build, with thresholds, and a fix for the flaky test
- Live, dated exchange rates from a rates service (the dashboards use fixed indicative ones)
- Claims in a currency other than their market's, and currency-aware validation of amounts (yen have no decimals)
- Checking the chosen market against the policy number
- Email and SMS notifications (in-app only today)
- Kafka integration
- API Gateway
- Service Discovery
- Email Notifications
- SMS Notifications
- File Upload Storage
- Redis Caching
- Elasticsearch
- Distributed Tracing
- Centralized Logging
- CI/CD Pipeline
- Cloud Deployment

These enhancements can be introduced without major architectural changes due to the current service boundaries and event-driven design.