# Chubb APAC Claims Management Platform

A claims platform built as three Spring Boot microservices and an Angular single-page app. Claimants file and track
insurance claims, claims officers work a queue and decide them, and administrators see liability exposure and officer
workload.

| Role | Can do |
|---|---|
| **Claimant** | Register, sign in, file a claim, see and track their own claims, answer an officer's request for information, read notifications |
| **Officer** | See the work queue, take a claim, approve, reject or ask for more information, add internal notes, read notifications |
| **Admin** | Dashboard: totals by status, liability exposure, officer workload |

More detail: [architecture.md](architecture.md) (how it fits together and why) and
[AI_WORKING_JOURNAL.md](AI_WORKING_JOURNAL.md) (how AI assistance was used to build it).

## Stack

| | |
|---|---|
| Backend | Java (built on **JDK 27**, see the note below), Spring Boot 3.5, Spring Security with JWT (jjwt), Spring Data JPA, H2, Flyway, springdoc OpenAPI, Bean Validation, JUnit 5, Mockito, MockMvc |
| Frontend | **Angular 22**, Angular Material 3, reactive forms, signals, route guards, Vitest |
| Style | Constructor injection, Java records for DTOs, no Lombok, no Docker, no Kafka (events go through an abstraction) |

> **Differences from the brief.** `CLAUDE.md` asks for Java 21 and Angular 19. The backend is built and tested on
> JDK 27 (`java.version` in `backend/pom.xml`; not tried on 21) and the front end was scaffolded with Angular 22.

## Quick start

You need a JDK (the build uses 27), and Node.js with npm (developed on Node 26.3 / npm 11.16). Maven is **not**
needed; the repository carries the Maven wrapper.

Start the three services, each in its own terminal, from the `backend` folder (use `./mvnw` instead of `.\mvnw.cmd`
on macOS/Linux):

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run         # http://localhost:8081
.\mvnw.cmd -pl claim-service spring-boot:run        # http://localhost:8082
.\mvnw.cmd -pl assessment-service spring-boot:run   # http://localhost:8083
```

Each takes roughly 30 to 70 seconds to start (more on a slow disk). You can equally run the three `*Application` classes
from your IDE. Then, from `frontend/claims-portal`:

```powershell
npm install
npm start          # ng serve, http://localhost:4200
```

Open **http://localhost:4200**. The dev server proxies `/api/*` to the three services (`proxy.conf.json`), so there is
nothing else to configure and no CORS to worry about.

### Demo logins

The login page has quick-fill buttons for these in development builds.

| Role | Email | Password |
|---|---|---|
| Claimant | `weiling.tan@example.com` (any seeded claimant works) | `Claimant@123` |
| Officer | `officer@chubb.com` (Olivia Bennett; the other officers use the same password) | `Officer@123` |
| Admin | `admin@chubb.com` | `Admin@123` |

New accounts created through **Register** are always claimants.

### Seed data

On start every service loads demo data through Flyway (`V2__seed_data.sql`): 26 users (1 admin, 5 officers, 20
claimants), 250 claims, 500 history entries, 200 notifications, 150 assessments and 200 notes. The admin dashboard
should initially read:

| Total | Pending | Approved | Rejected | Settled | Liability exposure | Waiting in queue |
|---|---|---|---|---|---|---|
| 250 | 168 | 28 | 18 | 36 | 2,674,303.50 | 100 |

Databases are **in memory**, so every restart returns to exactly this state and anything you create is lost. To start
with an empty platform instead, set `FLYWAY_TARGET=1` before starting the services.

### Try it end to end

1. Sign in as the claimant, **New claim**, fill the form, submit. The claim starts as *Submitted*.
2. Sign out, sign in as the officer. The work queue lists oldest first, so click the **Submitted** column header once to
   put your new claim at the top, open it and **Take this claim**, add an internal note, then **Request information**.
3. Sign back in as the claimant. The claim shows the officer's question; reply. It returns to *Under review*.
4. As the officer, **Approve** (or **Reject** with a reason).
5. Sign in as admin: the dashboard totals and liability exposure have moved by that one claim.

The claimant's notification bell fills up along the way.

## Service endpoints

Interactive docs (Swagger UI, with a bearer-token "Authorize" button) and the in-memory databases:

| Service | Swagger UI | H2 console (JDBC URL, user `sa`, empty password) |
|---|---|---|
| auth-service :8081 | http://localhost:8081/swagger-ui.html | `jdbc:h2:mem:authdb` at `/h2-console` |
| claim-service :8082 | http://localhost:8082/swagger-ui.html | `jdbc:h2:mem:claimdb` at `/h2-console` |
| assessment-service :8083 | http://localhost:8083/swagger-ui.html | `jdbc:h2:mem:assessmentdb` at `/h2-console` |

Calls need `Authorization: Bearer <accessToken>` from `POST /api/auth/login`. Tokens last one hour.

**auth-service**

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | Create a claimant account |
| `POST /api/auth/login` | public | Returns `accessToken`, `tokenType`, `expiresIn`, `user` |
| `GET /api/users/me` | any signed-in user | The current user |

**claim-service**

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/claims` | claimant | File a claim (201, `Location` header) |
| `GET /api/claims/my-claims` | claimant | Own claims, paged and sortable |
| `GET /api/claims/search` | any role (claimants see only their own) | Filter by `claimNumber`, `policyNumber`, `status`, `fromDate`, `toDate`, `assignedOfficerId`; paged, sortable |
| `GET /api/claims/{claimNumber}` | any role (claimants: own) | The claim with its full history |
| `POST /api/claims/{id}/additional-info` | claimant (own claim, while *More info required*) | Answer the officer |
| `GET /api/notifications`, `GET /api/notifications/unread-count` | any role | Your own inbox and badge count |
| `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` | any role | Mark read |
| `/api/internal/claims/**` | officer / admin | Used by assessment-service; not meant for clients |

**assessment-service**

| Endpoint | Who | Purpose |
|---|---|---|
| `GET /api/officer/claims/unassigned` | officer | Work queue: submitted claims nobody holds |
| `POST /api/officer/claims/{id}/assign` | officer | Take a claim (becomes *Under review*) |
| `POST /api/officer/claims/{id}/approve` · `/reject` · `/request-info` | assigned officer | Decide, with a comment, a reason (required) or a question (required) |
| `GET` / `POST /api/officer/claims/{id}/notes` | officer | Read and add internal notes |
| `GET /api/dashboard/status-summary` · `/officer-workload` · `/liability-exposure` | admin | Dashboard figures, computed live |

Paging is zero-based (`page`, `size`, default 20, max 100) and sorting is `sort=field,asc|desc` over a fixed list of
fields. Every error has the same JSON shape (`timestamp`, `status`, `error`, `message`, `path`, optional
`fieldErrors`). Claim lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ MORE_INFO_REQUIRED`, then `APPROVED` or `REJECTED`, then
`SETTLED`; only these transitions are allowed.

## Configuration

Everything has a development default; override with environment variables.

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | a development-only value | HS256 signing secret, at least 32 bytes. **Must be identical in all three services.** |
| `FLYWAY_TARGET` | `latest` | `1` = schema only, no demo data |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | in-memory H2, `sa`, empty | Per-service database |
| `CLAIM_SERVICE_URL` | `http://localhost:8082` | Where assessment-service finds claim-service |
| `app.seed.demo-users` (auth) | `true` | Ensures the demo officer and admin exist; turn off outside development |

## Tests

```powershell
# Backend: 422 tests (auth 79, claim 212, assessment 131)
cd backend
.\mvnw.cmd test

# Frontend: 234 tests in 19 files (Vitest + jsdom)
cd frontend\claims-portal
npx ng test --watch=false
```

Backend: service and lifecycle rules, controllers (MockMvc), security (role matrix, forged/expired tokens), dashboards and
liability maths, repositories and search, and integration tests on H2 + Flyway including the seed data. Frontend: guards,
interceptors, stores, API clients, routes, the auth and claimant pages and the utilities. Stop running services before
`mvnw test` if they were started from the same folder, since both write to `target/`.

### Latest results (2026-10-22, JDK 27)

| Suite | Tests | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| auth-service | 79 | 79 | 0 | 0 |
| claim-service | 212 | 212 | 0 | 0 |
| assessment-service | 131 | 131 | 0 | 0 |
| **Backend total** | **422** | **422** | **0** | **0** |
| Frontend (19 files) | 234 | 234 | 0 | 0 |
| **All** | **656** | **656** | **0** | **0** |

The backend figures come from two runs, not one: in the first full run auth (79) and claim (212) passed and assessment had
1 failure out of 131; the assessment suite was then run again on its own and all 131 passed. The failure is a flaky test, not
a defect in the service (see *Known limitations*).

### Coverage (same day)

Backend, measured with JaCoCo 0.8.15 (classes with no executable code, such as records and interfaces, are not counted):

| Service | Instructions | Branches | Lines | Methods |
|---|---:|---:|---:|---:|
| auth-service | 93.0% (1002 / 1077) | 61.8% (21 / 34) | 93.4% (227 / 243) | 93.9% (93 / 99) |
| claim-service | 99.1% (2608 / 2631) | 91.4% (85 / 93) | 98.8% (514 / 520) | 98.4% (186 / 189) |
| assessment-service | 97.4% (2597 / 2667) | 78.7% (74 / 94) | 97.4% (493 / 506) | 97.8% (177 / 181) |
| **Backend total** | **97.4%** (6207 / 6375) | **81.4%** (180 / 221) | **97.2%** (1234 / 1269) | **97.2%** (456 / 469) |

The uncovered backend lines are mostly the three `GlobalExceptionHandler` classes (auth and assessment each miss 10 lines),
the `main` methods of the three application classes, and a handful of single lines elsewhere.

Frontend, measured with Vitest's V8 provider (includes the shared test helpers in `src/testing`):

| Statements | Branches | Functions | Lines |
|---:|---:|---:|---:|
| 67.3% (1656 / 2460) | 73.9% (636 / 861) | 69.6% (275 / 395) | 69.7% (1102 / 1581) |

The front-end figure is held down by the officer and admin pages and a few shared components that have no spec of their
own. Share of lines covered: officer **claim-review** 1%, **my-assigned-claims** 5%, **unassigned-queue** 52%, admin
**dashboard** 35%, the **bar-chart** component 1%, the **text-prompt dialog** 7% and the **notifications page** 49%. The
core services, interceptors, login, registration, create-claim and my-claims pages are at 92-98%, and claim-details at 89%.
(Vitest's text report leaves out files that are 100% covered, so the three guards do not appear in it; they are 100%
covered and counted in the totals.)

To reproduce the coverage figures. Neither tool is wired into the build: the JaCoCo goals are run from the command line, and
the Vitest coverage package is not in `package.json`.

```powershell
# Backend: HTML reports at <service>\target\site\jacoco\index.html
cd backend
.\mvnw.cmd org.jacoco:jacoco-maven-plugin:0.8.15:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.15:report

# Frontend: install the provider once (matches vitest 5.0.5), then run with --coverage
cd frontend\claims-portal
npm install --no-save @vitest/coverage-v8@5.0.5
npx ng test --watch=false --coverage
```

Production build: `npx ng build` from `frontend/claims-portal` (output in `dist/`).

## Repository layout

```
backend/
  pom.xml                     parent POM (modules: auth, claim, assessment)
  auth-service/               users, registration, login, JWT
  claim-service/              claims, lifecycle, history, notifications, search, event publisher
  assessment-service/         officer actions, notes, dashboards; calls claim-service
    src/main/resources/db/migration/   V1 schema + V2 seed (one pair per service)
frontend/claims-portal/
  src/app/core/               guards, interceptors, shell layout, API clients, auth store
  src/app/shared/             reusable components, models, utilities
  src/app/features/           auth, claimant, officer, admin pages (lazy-loaded)
  proxy.conf.json             /api/* → the three services
CLAUDE.md                     the project brief
architecture.md · AI_WORKING_JOURNAL.md
```

## Known limitations

* **Nothing settles a claim.** There is no settle endpoint, so *Settled* only exists in the seed data and liability
  exposure can never decrease.
* Per the brief's formula, **rejected claims count toward liability exposure**; only settled ones are excluded.
* Officers can only take claims for themselves, and an assignee is shown as "Officer #n" because there is no officer
  directory.
* **One flaky backend test.** `OfficerWorkflowIntegrationTest.anOfficerTakesAClaimAsksForInformationAndApprovesIt`
  (assessment-service) mints one JWT at the start and later compares it with the token its request helper mints again. A JWT
  carries its issue time to the second, so the two differ whenever the clock ticks over in between. It failed once in the
  2026-10-22 run (under the coverage agent) and passed on re-run. The fix is to capture the token the request actually used.
* Not production-ready by design: in-memory databases, an open H2 console and Swagger UI, a shared HS256 secret, no
  refresh tokens or rate limiting.
* The officer and admin pages have no component specs (they were checked by walking the running app).

More in [architecture.md](architecture.md#15-known-gaps-and-next-steps).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Dashboard or work queue shows "service unavailable" (503) | claim-service (8082) is not running; assessment-service depends on it |
| "Port 808x was already in use" | Something else holds the port, for example the same service started from your IDE; stop one of them |
| Everything signs out after changing `JWT_SECRET` in one service | The secret must match in all three; restart them together |
| Startup error "required a bean of type PasswordEncoder", or `application.yml` / `db/migration` missing from `target/classes`, or "Failed to delete …\target\classes" | The repository is inside a OneDrive folder, which turns freshly built `target` folders into placeholders and breaks Maven and IDE builds. Delete the affected `target` folder (`Remove-Item -Recurse -Force <service>\target`) and rebuild. The lasting fix is to keep the repo outside OneDrive. |
| First page load after `npm start` is slow | The dev server compiles on demand; later loads are fast |
