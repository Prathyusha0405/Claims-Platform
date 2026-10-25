<div align="center">

# APAC Claims Platform

**Motor & property claims · Explicit workflows · Traceable decisions**

A claims management platform assessment for **Chubb APAC**: three Spring Boot microservices and an Angular app.

![Java](https://img.shields.io/badge/Java-27-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.16-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-22-DD0031?style=flat-square&logo=angular&logoColor=white)
![H2](https://img.shields.io/badge/H2-in--memory-09476B?style=flat-square)
![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?style=flat-square&logo=flyway&logoColor=white)

![Local build](https://img.shields.io/badge/Local_Java_27_build-passed-2EA44F?style=flat-square)
![Backend tests](https://img.shields.io/badge/Backend_tests-669_passed-2EA44F?style=flat-square)
![Frontend tests](https://img.shields.io/badge/Frontend_tests-384_passed-2EA44F?style=flat-square)
![Last verified](https://img.shields.io/badge/Last_verified-2026--10--26-blue?style=flat-square)

[Quick start](#quick-start) · [Build & test](#tests) · [Requirements](#requirements-coverage) · [Architecture](architecture.md) 

</div>

---

A claims platform built as three Spring Boot microservices and an Angular single-page app. Claimants file and track
insurance claims, claims staff work a queue and decide them, and managers see liability exposure and claims staff
workload.

| Role | Code | Can do |
|---|---|---|
| **Claimant** | `CLAIMANT` | Register, sign in, file a claim, see and track their own claims, answer a claims staff member's request for information, read notifications |
| **Claims staff** | `CLAIMS_STAFF` | See the work queue, take a claim, approve, reject or ask for more information, settle a decided claim, add internal notes, read notifications (a new claim is waiting; a claimant has answered) |
| **Manager** | `MANAGER` | Dashboard: totals by status, liability exposure, and charts of claims over time, by market, by claim type and per claims staff member, in USD; filtered by market, claim type and submission dates |

![The manager dashboard](docs/screenshots/02-manager-dashboard.png)

## Contents

1. [Business context](#business-context)
2. [At a glance](#at-a-glance)
3. [Quick start](#quick-start) · [demo logins](#demo-logins) · [seed data](#seed-data) · [try it end to end](#try-it-end-to-end)
4. [A tour of the app](#a-tour-of-the-app)
5. [Requirements coverage](#requirements-coverage)
6. [API](#api) · [configuration](#configuration) · [operations](#operations)
7. [Tests](#tests)
8. [Repository layout](#repository-layout) · [documentation map](#documentation-map)
9. [Known limitations](#known-limitations) · [troubleshooting](#troubleshooting)

## Business context

Chubb APAC processes motor and property claims across six markets. The scenario this platform answers:

> Today the process is fragmented: claimants submit by phone or email and wait with no visibility into what is happening.
> Claims staff manage incoming work from shared inboxes and spreadsheets, with no consolidated view of their workload.
> Managers have no real-time picture of outstanding claims or liability exposure.

| Who | The problem today | What the platform provides |
|---|---|---|
| **Claimants** | Submit by phone or email, then wait with no visibility | Self-service portal: file a claim online, follow it on a progress tracker and a timeline, get an in-app notification at every step, and answer a claims staff member's request for information in the same place ([claimant screens](#a-tour-of-the-app)) |
| **Claims staff** | Work from shared inboxes and spreadsheets, with no consolidated view of their workload | One work queue of submitted claims nobody holds (oldest first), *Take claim* in one click, a tab for the claims they hold, search by claim number, policy number, status, market, date range and claims staff member, internal notes, an in-app alert whenever a new claim is waiting in the queue, and an audit trail on every claim |
| **Managers** | No real-time picture of outstanding claims or liability exposure | Manager dashboard, computed live on every request: totals by status, pending, liability exposure (`SUM(claimAmount) WHERE status != SETTLED`), claims and value by status, each market side by side in its own currency and in USD, and per-claims staff workload, with charts for claims over time, each market, each claim type and each staff member; every figure and chart can be narrowed by market, claim type and submission dates |

How far the demo goes toward the scenario, stated plainly:

* **Motor and property.** Claim types `AUTO` and `HOME` carry the seed data (146 and 104 of the 250 claims, with policy numbers
  starting `MTR-` and `PRP-`). The schema also accepts `HEALTH`, `TRAVEL`, `PERSONAL_ACCIDENT` and `OTHER`.
* **Six markets, six currencies.** Every claim carries a `market` (Australia, Hong Kong, Japan, Malaysia, New Zealand or
  Singapore) and is in that market's currency (AUD, HKD, JPY, MYR, NZD, SGD). The claimant chooses the market when filing,
  claims can be searched and sorted by it, and a claims staff member can narrow the work queue to one market. The manager dashboard can be
  read for every market or for one, with each market's exposure side by side in its own currency and in USD, so a manager can
  now answer "what is our exposure in Japan?". The same page can also be narrowed to a claim type and to a range of submission
  dates, and charts the claims over time. The USD figures use fixed, indicative exchange rates, not live ones. See
  [known limitations](#known-limitations).
* **Visibility without phone or email.** Claimants are told of progress inside the portal (in-app notifications), and claims
  staff are told when a new claim is waiting or a claimant has answered them. Email and SMS alerts, and taking claims by
  phone or email, are not built.

## At a glance

```mermaid
flowchart LR
    U["Browser<br/>Angular app, :4200"] -->|"/api/auth, /api/users"| A["auth-service :8084<br/>users, login, JWT"]
    U -->|"/api/claims, /api/notifications"| C["claim-service :8082<br/>claims, lifecycle, history, notifications"]
    U -->|"/api/staff, /api/dashboard"| S["assessment-service :8083<br/>claims staff actions, notes, dashboards"]
    S -->|"HTTP, caller's JWT forwarded"| C
```

| Service | Port | Owns (own H2 database, Flyway-managed) |
|---|---|---|
| auth-service | 8084 | `users` |
| claim-service | 8082 | `claims`, `claim_history`, `notifications` |
| assessment-service | 8083 | `claim_assessments`, `claim_notes` |
| Angular dev server | 4200 | proxies `/api/*` to the three services, so the browser sees one origin and no CORS is needed |

| | |
|---|---|
| Backend | Java (built on **JDK 27**, see the note below), Spring Boot 3.5, Spring Security with JWT (jjwt), Spring Data JPA, H2, Flyway, springdoc OpenAPI, Bean Validation, JUnit 5, Mockito, MockMvc |
| Frontend | **Angular 22**, Angular Material 3, reactive forms, signals, route guards, Vitest |
| Style | Constructor injection, Java records for DTOs, no Lombok. Events go through an abstraction: handled in-process by default, through Kafka when switched on ([Events](#events-in-process-or-kafka)) |
| Packaging | A Dockerfile for the services and one for the app, and a `docker-compose.yml` that runs them with a Kafka broker ([In Docker](#in-docker)). Optional: `node scripts/dev.mjs` still starts everything without Docker ([Quick start](#quick-start)) |


## Quick start

You need a JDK (the build uses 27), and Node.js with npm (developed on Node 26.3 / npm 11.16). Maven is **not**
needed; the repository carries the Maven wrapper. Ports **8082, 8083, 8084 and 4200** must be free.

**One command**, from the repository root:

```powershell
node scripts/dev.mjs
```

It builds the three services (tests skipped), starts them, waits until each reports ready on its health endpoint, starts the
Angular dev server and prints the addresses. Logs go to `.run/logs/` and **Ctrl+C** stops everything. If one of the four ports
is taken it refuses to start and names the port; set `AUTH_PORT`, `CLAIM_PORT`, `ASSESSMENT_PORT` or `FRONTEND_PORT` to use
others (the dev server is then given a matching proxy file). `--no-frontend` starts only the services, `--skip-build` reuses
the jars already built, `--backend-dir` and `--frontend-dir` use a copy of either folder (see [troubleshooting](#troubleshooting)), and `--help` lists the rest. It was run on Windows; macOS and Linux are untried
([limitations](#known-limitations)).

**Or by hand**, each service in its own terminal, from the `backend` folder (use `./mvnw` instead of `.\mvnw.cmd` on
macOS/Linux):

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run         # http://localhost:8084
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

### In Docker

With Docker (Compose v2) you need neither a JDK nor Node.js. From the repository root:

```powershell
docker compose up --build
```

Open **http://localhost:4200** and use the same [demo logins](#demo-logins). Five containers start: a single-node Kafka
broker, the three services, and the Angular app behind nginx, which also forwards `/api/*` to the services as the dev-server
proxy does. claim-service runs with `EVENTS_PUBLISHER=kafka`, so its claim events travel through Kafka
([Events](#events-in-process-or-kafka)); `EVENTS_PUBLISHER=local docker compose up --build` keeps them in-process. Each container
starts when what it depends on reports healthy, so after the images are built expect a minute or two before the app answers.
Ports 4200, 8082, 8083 and 8084 must be free. `docker compose down` removes everything; the databases are in-memory H2, so
every start has the seed data again. To replace the development JWT secret, set `JWT_SECRET` in the shell or in a `.env` file
next to `docker-compose.yml`; all three services receive the same value.

To watch the events go by, then file a claim in the app:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claim-events --from-beginning
```

**Not yet built or run.** The Dockerfiles and the compose file were written on a machine without Docker, so no image has been
built and no container started. What was checked instead:

* the compose file parses and agrees with the services: published ports, health probes, every environment variable it sets is
  one the service reads, the same default JWT secret, and nginx routes that match `proxy.conf.json`;
* the Dockerfile's build commands (`mvnw -pl <module> -am -DskipTests package`, then copying the jar) were run by hand for each
  service and produced the jars;
* those jars were started as separate processes in `kafka` mode, with the environment variables compose sets, against a real
  broker (see [Events](#events-in-process-or-kafka));
* the base images exist on Docker Hub: `eclipse-temurin:27-jdk` and `:27-jre`, `node:24-alpine`, `nginx:1.27-alpine` and
  `apache/kafka:3.9.1`.

Not checked: that `docker build` succeeds, the nginx configuration under nginx, the container health checks, the settings of
the Kafka image, and the front-end build under Node 24 (it was built locally on Node 26). If something fails, the output of
`docker compose up --build` and of `docker compose logs <service>` is what is needed to fix it.

### Demo logins

The login page has quick-fill buttons for these in development builds.

| Role | Email | Password |
|---|---|---|
| Claimant | `weiling.tan@example.com` (any seeded claimant works) | `Claimant@123` |
| Claims staff | `staff@chubb.com` (Olivia Bennett; the other claims staff use the same password) | `Staff@123` |
| Manager | `manager@chubb.com` | `Manager@123` |

New accounts created through **Register** are always claimants.

### Seed data

On start every service loads demo data through Flyway (`V2__seed_data.sql`): 26 users (1 manager, 5 claims staff, 20
claimants), 250 claims (Australia 70, Hong Kong 30, Japan 40, Malaysia 35, New Zealand 25, Singapore 50), 500 history entries, 200 notifications, 150 assessments and 200 notes. The manager dashboard
should initially read:

| Total | Pending | Approved | Rejected | Settled | Liability exposure | Waiting in queue |
|---|---|---|---|---|---|---|
| 250 | 168 | 28 | 18 | 36 | USD 2,674,303.58 | 100 |

Each claim is in its market's own currency (AUD, HKD, JPY, MYR, NZD, SGD); the dashboard converts them to USD at fixed,
indicative rates (`app.dashboard.rates`, below), which is why the figure is in USD.

Databases are **in memory**, so every restart returns to exactly this state and anything you create is lost. To start
with an empty platform instead, set `FLYWAY_TARGET=1` before starting the services.

### Try it end to end

1. Sign in as the claimant, **New claim**, fill the form, submit. The claim starts as *Submitted*.
2. Sign out, sign in as the claims staff member. The work queue lists oldest first, so click the **Submitted** column header once to
   put your new claim at the top, open it and **Take this claim**, add an internal note, then **Request information**.
3. Sign back in as the claimant. The claim shows the claims staff member's question; reply. It returns to *Under review*.
4. As the claims staff member, **Approve** (or **Reject** with a reason).
5. Then **Settle claim**, with a comment if you like: the claim closes, the claimant is told, and it stops counting towards
   liability exposure.
6. Sign in as the manager: the dashboard totals and liability exposure have moved by that one claim (up when it was filed,
   back down when it was settled).

The claimant's notification bell fills up along the way.

## A tour of the app

Screenshots of the running app on the seed data (development build, 1440 × 900).

| | |
|---|---|
| **Sign in.** One login for all roles, with quick-fill demo accounts in development builds. | ![Sign in](docs/screenshots/01-login.png) |
| **Claimant: my claims.** Search by claim or policy number, status, market and date range; sort (including by market) and page; each amount in the claim's own currency (here SGD); status chips; unread-notification dot on the bell. | ![My claims](docs/screenshots/03-claimant-my-claims.png) |
| **Claimant: file a claim.** Validated reactive form (policy number format, a required market, date not in the future, amount greater than zero). The amount's currency follows the market (here Japan, so JPY); the server sets it, the form never sends one. | ![New claim](docs/screenshots/04-claimant-new-claim.png) |
| **Claimant: claim details.** The claim's facts, including its market, and the audit timeline (scrolled down here; above it are a progress tracker and, when claims staff need something, the question with a reply box). | ![Claim details](docs/screenshots/05-claimant-claim-details.png) |
| **Claimant: notifications.** One entry per lifecycle event, unread highlighted, filter and mark-all-read. | ![Notifications](docs/screenshots/06-claimant-notifications.png) |
| **Claims staff: work queue.** Submitted claims nobody holds, oldest first, with a market column and filter and a one-click *Take claim*; a second tab lists the claims the staff member holds. | ![Work queue](docs/screenshots/07-staff-work-queue.png) |
| **Claims staff: work queue by market.** Choosing a market (here Japan) narrows the queue to the claims filed there: 19 of the 100 waiting in the seed data. | ![Work queue by market](docs/screenshots/09-staff-work-queue-by-market.png) |
| **Claims staff: claim review.** The decision actions (request information, reject, approve), the claim's facts and internal notes the claimant never sees. | ![Claim review](docs/screenshots/08-staff-claim-review.png) |
| **Claims staff: settle a decided claim.** Once a claim is approved or rejected, the staff member who holds it can settle it: an optional comment, the claimant is told, and it stops counting towards liability exposure. | ![Settle a claim](docs/screenshots/17-staff-settle-claim.png) |
| **Claims staff: a settled claim.** The history now ends with *Claim settled* and its comment, and no decision is left to make. | ![A settled claim](docs/screenshots/18-staff-claim-settled.png) |
| **Claims staff: notifications.** An alert for every new claim waiting in the queue, and one when a claimant answers a request for information; the bell checks every ten seconds. | ![Staff notifications](docs/screenshots/12-staff-notifications.png) |
| **Manager: dashboard.** One row of filters (market, period, claim type) above everything it scopes, then liability exposure in USD with its formula and the status totals. | ![Dashboard](docs/screenshots/02-manager-dashboard.png) |
| **Manager: claims over time.** Claims filed per month (or per week for a window of up to six months), split into pending (blue) and decided (gray); hover for the numbers, *View as table* for the same figures as text. | ![Claims over time](docs/screenshots/13-manager-claims-over-time.png) |
| **Manager: by market.** Claims by market (pending and decided) beside liability exposure by market, then the table with each market's exposure in its own currency and in USD. It stays in view whichever market is selected above, and follows the claim type and dates. | ![By market](docs/screenshots/10-manager-dashboard-by-market.png) |
| **Manager: claims by type.** How claims, and liability exposure, divide between claim types (Auto and Home in the seed data) as 100% bars. Each type keeps its own colour, and the chart stays whichever type is selected above. | ![Claims by type](docs/screenshots/14-manager-claims-by-type.png) |
| **Manager: claims staff workload.** Each staff member's claims by under review, awaiting information and decided, busiest first, with the table underneath. | ![Claims staff workload](docs/screenshots/15-manager-staff-workload.png) |
| **Manager: one market.** Choosing Japan narrows the headline figures, the charts and the claims staff workload to Japanese claims. | ![Dashboard for Japan](docs/screenshots/11-manager-dashboard-japan.png) |
| **Manager: three filters at once.** Singapore, Auto, last 90 days: every figure and chart follows, and the claims over time switch to weeks. | ![Filtered dashboard](docs/screenshots/16-manager-filtered.png) |

## Requirements coverage

How the brief in [CLAUDE.md](CLAUDE.md) maps to the code. "Verified" means checked in the repository or by running it.

| Requirement | Status | Where / notes |
|---|---|---|
| Three microservices (Auth, Claim, Assessment) and an Angular front end | Done | `backend/*-service`, `frontend/claims-portal` |
| Spring Security, JWT bearer tokens, roles CLAIMANT / CLAIMS_STAFF / MANAGER | Done | `security` and `config/SecurityConfig` in each service; role matrix tested |
| H2 + Flyway, never automatic schema generation | Done | `db/migration` in each service; `ddl-auto: validate` in all three |
| No Lombok, PostgreSQL or MySQL | Verified | no Lombok in any `pom.xml` or source file; no PostgreSQL or MySQL dependency or configuration |
| Docker and Kafka | Added later, as options | The brief ruled both out; they were added at the project owner's request and are off by default. Kafka: `kafka` publisher mode, [Events](#events-in-process-or-kafka). Docker: Dockerfiles and `docker-compose.yml`, [In Docker](#in-docker) (not yet built or run) |
| Constructor injection, Java records for DTOs | Verified | no `@Autowired` in production code (only in test classes); all 36 `dto` types are records |
| OpenAPI / Swagger | Done | Swagger UI on each service ([below](#api)) |
| Auth endpoints (register, login, `users/me`) | Done | [auth-service](#api) |
| Claim endpoints (create, get by number, my-claims, search, additional-info) | Done | plus notification endpoints the UI needs |
| Assessment endpoints (unassigned, assign, approve, reject, request-info, notes, dashboards) | Done | plus `POST .../settle`, `GET .../notes` and a fourth dashboard, `claims-over-time` |
| Claim lifecycle, only valid transitions | Done | transition table in `ClaimStatus`; the whole path to `SETTLED` can be driven through the API and the app |
| Event publisher pattern | Done | `ClaimEventPublisher` with `LocalClaimEventPublisher` (the default) and `KafkaClaimEventPublisher`, both ending in `ClaimEventHandler` (log, audit history, notifications); the brief's events plus `ADDITIONAL_INFO_RECEIVED` and `CLAIM_SETTLED`. The brief's "future `KafkaEventPublisher`, no service layer changes" is done: `ClaimService` was not touched |
| Dashboard: totals, pending, approved, rejected, claims staff workload, liability exposure | Done, by market and in USD | exposure is `SUM(claimAmount) WHERE status != SETTLED` converted to USD at fixed indicative rates, in one class; every dashboard takes an optional `market`, `claimType`, `fromDate` and `toDate`, and `byMarket` shows each market in its own currency and in USD |
| Manager visuals and filters | Done, beyond the brief | charts of claims over time, by market, by claim type and per claims staff member, each with a table view; filters for market, claim type and submission dates that every figure and chart follows ([API](#api), [architecture §9](architecture.md#9-dashboards)) |
| Business context: six markets, each in its own currency | Done | a `market` and a `currency` on every claim; market filter on search, the work queue and the dashboards ([business context](#business-context)) |
| Search by claim number, policy number, status, date range, assigned staff member; paging and sorting | Done, plus market | `GET /api/claims/search`; the work queue also filters by market |
| Tests: service, controller, security, dashboard (JUnit 5, Mockito, MockMvc) | Done | 669 backend tests, plus 384 front-end tests ([results](#tests)) |
| Operating it: health checks, request ids, one-command start, CI | Done, beyond the brief | [operations](#operations) |
| Angular: Auth, Claimant, Staff, Manager, Shared, Core; the nine pages; reactive forms; route guards | Done | feature folders of standalone, lazy-loaded components rather than `NgModule`s |
| README.md, architecture.md, AI_WORKING_JOURNAL.md | Done | [documentation map](#documentation-map) |

## API

Interactive docs (Swagger UI, with a bearer-token "Authorize" button) and the in-memory databases:

| Service | Swagger UI | H2 console (JDBC URL, user `sa`, empty password) |
|---|---|---|
| auth-service :8084 | http://localhost:8084/swagger-ui.html | `jdbc:h2:mem:authdb` at `/h2-console` |
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
| `POST /api/claims` | claimant | File a claim (201, `Location` header); `market` (AU, HK, JP, MY, NZ, SG) is required and sets the claim's currency |
| `GET /api/claims/my-claims` | claimant | Own claims, paged and sortable |
| `GET /api/claims/search` | any role (claimants see only their own) | Filter by `claimNumber`, `policyNumber`, `status`, `market`, `fromDate`, `toDate`, `assignedStaffId`; paged, sortable |
| `GET /api/claims/{claimNumber}` | any role (claimants: own) | The claim with its full history |
| `POST /api/claims/{id}/additional-info` | claimant (own claim, while *More info required*) | Answer the claims staff |
| `GET /api/notifications`, `GET /api/notifications/unread-count` | any role | Your own inbox and badge count |
| `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` | any role | Mark read |
| `/api/internal/claims/**` | claims staff / manager | Used by assessment-service; not meant for clients |

**assessment-service**

| Endpoint | Who | Purpose |
|---|---|---|
| `GET /api/staff/claims/unassigned` | claims staff | Work queue: submitted claims nobody holds; optional `market` |
| `POST /api/staff/claims/{id}/assign` | claims staff | Take a claim (becomes *Under review*) |
| `POST /api/staff/claims/{id}/approve` · `/reject` · `/request-info` | assigned staff member | Decide, with a comment, a reason (required) or a question (required) |
| `POST /api/staff/claims/{id}/settle` | assigned staff member | Close an approved or rejected claim as *Settled*, with an optional comment the claimant can read |
| `GET` / `POST /api/staff/claims/{id}/notes` | claims staff | Read and add internal notes |
| `GET /api/dashboard/status-summary` · `/staff-workload` · `/liability-exposure` · `/claims-over-time` | manager | Dashboard figures, computed live; optional `market`, `claimType`, `fromDate` and `toDate` (the claim's submission date, UTC, both ends inclusive); money in USD, converted at fixed indicative rates |

Paging is zero-based (`page`, `size`, default 20, capped at 100) and sorting is `sort=field,asc|desc` over a fixed list of
fields. Every error has the same JSON shape (`timestamp`, `status`, `error`, `message`, `path`, optional
`fieldErrors`). Claim lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ MORE_INFO_REQUIRED`, then `APPROVED` or `REJECTED`, then
`SETTLED` (by the staff member who holds the claim); only these transitions are allowed.

## Configuration

Everything has a development default; override with environment variables.

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | a development-only value | HS256 signing secret, at least 32 bytes. **Must be identical in all three services.** |
| `SERVER_PORT` | 8084 / 8082 / 8083 | Standard Spring Boot override of a service's port. If you change one, update `frontend/claims-portal/proxy.conf.json` and restart `npm start` |
| `AUTH_PORT`, `CLAIM_PORT`, `ASSESSMENT_PORT`, `FRONTEND_PORT` | 8084, 8082, 8083, 4200 | Read by `scripts/dev.mjs` only: it passes `--server.port` to each service, points assessment-service at the chosen claim-service port and gives the dev server a matching proxy file |
| `FLYWAY_TARGET` | `latest` | `1` = schema only, no demo data |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | in-memory H2, `sa`, empty | Per-service database |
| `CLAIM_SERVICE_URL` | `http://localhost:8082` | Where assessment-service finds claim-service |
| `EVENTS_PUBLISHER` (claim) | `local` (`kafka` in `docker-compose.yml`) | `app.events.publisher`: `local` handles claim events in-process; `kafka` stages them in an outbox table and sends them through Kafka. See [Events](#events-in-process-or-kafka) |
| `KAFKA_BOOTSTRAP_SERVERS` (claim) | `localhost:9092` | Where the broker is; read only in `kafka` mode (`kafka:9092` in Docker) |
| `app.events.kafka.*` (claim) | topic `claim-events`, 3 partitions, replication factor 1, batch 100, relay delay 500 ms, send timeout 5 s, retry every 1 s up to 3 times | Topic and tuning for `kafka` mode, in claim-service's `application.yml`. A bad value falls back to its default |
| `app.dashboard.reporting-currency`, `app.dashboard.rates` (assessment) | `USD`; per 1 USD: AUD 1.50, HKD 7.80, JPY 150, MYR 4.50, NZD 1.65, SGD 1.35 | The currency the dashboards report in, and the fixed, indicative rates used to convert each market's amounts into it. Not live rates. Keep them equal to the rates the seed amounts were converted at, or the documented USD figures drift |
| `app.seed.demo-users` (auth) | `true` | Ensures the demo claims staff and manager accounts exist; turn off outside development |

## Operations

| What | Detail |
|---|---|
| **Health checks** | Every service answers `GET /actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` without a token, with the status only (`UP` or `DOWN`, never component details). Nothing else of Actuator is exposed. assessment-service's readiness, and so its overall health, goes `DOWN` while claim-service cannot be reached, because every staff action and every dashboard figure comes from it; its liveness does not, so a claim-service outage is not a reason to restart it |
| **Request ids** | Every response carries an `X-Request-Id`. Send one of your own (letters, digits, `.`, `_`, `-`, up to 64 characters) and it is kept; anything else is replaced by a generated one, so a caller cannot forge log lines. Each log line shows the id in square brackets (`-` outside a request), and assessment-service passes it on to claim-service, so one staff action can be followed through both logs |
| **Logs** | Plain text on the console; `scripts/dev.mjs` also writes each service's to `.run/logs/<service>.log` |
| **One-command start** | `node scripts/dev.mjs`, described under [Quick start](#quick-start) |
| **CI** | `.github/workflows/ci.yml` runs every backend test (`mvnw verify`), every front-end test and a production build of the app on each push to `main` and each pull request. It has not been run yet: it needs a JDK 27 on the runner and was written from the commands in this README |

## Events: in-process or Kafka

Everything that happens to a claim (filed, taken, approved, rejected, information asked for or given, settled) is an event
handed to `ClaimEventPublisher`. Each one is logged, written to the claim's history (the timeline a claimant sees) and turned
into notifications. Two implementations do the handing, chosen with `EVENTS_PUBLISHER` on claim-service:

| | `local` (the default) | `kafka` |
|---|---|---|
| What happens | The event is handled in-process, inside the claim's own database transaction | The event is saved to the `claim_event_outbox` table in that transaction; a relay sends it to the Kafka topic `claim-events`; a listener records it |
| History and notifications | Written together with the claim change | Follow it by about a second (the relay polls every 500 ms; 1.2 s in the live check below). The claim itself is current at once |
| Needs | Nothing | A Kafka broker |
| Used by | `node scripts/dev.mjs`, an IDE run, `mvnw test` | `docker compose up` |

To run from source in Kafka mode, have a broker on `localhost:9092` (or point `KAFKA_BOOTSTRAP_SERVERS` at one) and set the
variable first: `$env:EVENTS_PUBLISHER='kafka'; node scripts/dev.mjs`. The script hands its environment to the services; that
exact command line was not run, but the service itself was (below).

What kafka mode guarantees:

* **A claim change and its event are one fact.** They are saved in the same transaction, so a rollback discards both, and a
  broker outage can neither block nor lose a claim change.
* **Delivery is at least once, and a duplicate is harmless.** Each event has a stable id, stored in `claim_history.event_id`
  (unique); the listener skips an id it has recorded.
* **One claim's events stay in order** (they are keyed by claim id).
* **Nothing is silently dropped.** A message that cannot be recorded is retried three times, a second apart, and then parked
  on the topic `claim-events.DLT`. Nothing replays that topic: it is there to be looked at.

If the broker is unavailable, claims can still be filed and decided; their events wait in `claim_event_outbox` and are sent,
oldest first, when it answers.

How it was checked:

* **Tests**, which need no Docker or install because Kafka 3.9 runs inside the test JVM: unit tests for the publisher, the
  relay (order, a rejected send, a timeout, an empty outbox), the listener, the handler and its duplicate rule, and the event id;
  and `KafkaEventPublishingIntegrationTest` on a real broker: the whole lifecycle recorded from the topic in order, an event
  delivered twice recorded once, an unreadable message and an unrecordable event parked on the dead-letter topic, a rolled-back
  claim change leaving the outbox empty, and the publisher refusing to run outside a transaction. That is 38 tests in 9
  classes; `DefaultEventPublisherTest` pins that with no configuration the local publisher is the only one and no Kafka bean exists.
* **Live, on 2026-10-26**: the three packaged service jars as separate processes, with the environment variables
  `docker-compose.yml` sets, and a real broker. A claim was driven through assessment-service from filing to settlement: the
  claim was current at once and its four history rows arrived in order (the first 1.2 s after the request). The broker
  process was then frozen, so it accepted and answered nothing: a claim could still be filed (201 in 20 ms) and taken, the claim
  was current, its history stayed empty, and claim-service logged that it could not reach Kafka. When the broker was resumed
  both history rows appeared in order, once each, and the listener had ignored one duplicate delivery on the way. A *killed*
  broker was not run live (the relay's handling of a failed send is unit-tested), and the script is not in the repository.

## Tests

```powershell
# Backend: 669 tests (auth 92, claim 355, assessment 222)
cd backend
.\mvnw.cmd test

# Frontend: 384 tests in 30 files (Vitest + jsdom)
cd frontend\claims-portal
npx ng test --watch=false
```

Backend: service and lifecycle rules, controllers (MockMvc), security (role matrix, forged/expired tokens), dashboards and
liability maths, repositories and search, integration tests on H2 + Flyway including the seed data, and the Kafka publisher
end to end on a broker started inside the test JVM (no Docker needed). Frontend: guards,
interceptors, stores, API clients, routes, the auth and claimant pages, the work queue, the claim review page, the prompt dialog, the manager dashboard with its
filters and its three chart components, the notification bell and the utilities. Stop running services before
`mvnw test` if they were started from the same folder, since both write to `target/`.

### Latest results (JDK 27)

| Suite | Run on | Tests | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| auth-service | 2026-10-26 | 92 | 92 | 0 | 0 |
| claim-service | 2026-10-26 | 355 | 355 | 0 | 0 |
| assessment-service | 2026-10-26 | 222 | 222 | 0 | 0 |
| **Backend total** | | **669** | **669** | **0** | **0** |
| Frontend (30 files) | 2026-10-25 | 384 | 384 | 0 | 0 |
| **All** | | **1,053** | **1,053** | **0** | **0** |

The backend was run in full on 2026-10-26, after the Kafka publisher was added (`mvnw verify`, all three services in one Maven
run in a copy of `backend/`, so running services were not disturbed). The 29 tests more than on 2026-10-25 are all in
claim-service: 38 tests for the event handler, id and Kafka classes, replacing 9 for the old local publisher. The front end was
not re-run on 2026-10-26 because nothing in it changed (the Docker files were added beside it); its last full run, with a
production build, was on 2026-10-25, after settling and the operations work were added. On that day the new behaviour was also run against
the real services started by `scripts/dev.mjs` (seed data, spare ports): 39
checks covering the health endpoints, a request id travelling from assessment-service into claim-service's log, and a claim taken
from filing through approval to settlement, with the dashboard's liability exposure and settled total moving by exactly the
claim's amount; and the settle screen was driven in a real browser (8 checks, including Cancel). That is not the whole history:
on 2026-10-23, before the renames, the first full backend run had failures (claim-service 1, a seed assertion that still expected
one statistics row per status, fixed since; assessment-service **1 out of 163**, the flaky test, which has failed under load
three times now, on 2026-10-22 and twice on 2026-10-23). It passed in every full run on 2026-10-25 and 2026-10-26. It has not been fixed.

### Coverage (measured 2026-10-22)

These figures were taken before the market field, currency and the dashboard changes were added. The new code is covered by
the new tests (included in the counts above), but the percentages were not re-measured.

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

The front-end figure is held down by the claims staff and manager pages and a few shared components that have no spec of their
own. Share of lines covered: claims staff **claim-review** 1%, **my-assigned-claims** 5%, **unassigned-queue** 52%, manager
**dashboard** 35%, the **bar-chart** component 1%, the **text-prompt dialog** 7% and the **notifications page** 49%. The
core services, interceptors, login, registration, create-claim and my-claims pages are at 92-98%, and claim-details at 89%. The work queue's `unassigned-queue`, the manager dashboard, `claim-review` and the text-prompt dialog have had specs since, which these figures
predate.
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
  claim-service/              claims, lifecycle, history, notifications, search, event publishers (local and Kafka)
  assessment-service/         claims staff actions, notes, dashboards; calls claim-service
    src/main/resources/db/migration/   V1 schema + V2 seed (one pair per service)
  Dockerfile                  builds any one service: --build-arg SERVICE=<module>
  .dockerignore
frontend/claims-portal/       the Angular app (its own README covers the front end in detail)
  src/app/core/               guards, interceptors, shell layout, API clients, auth store
  src/app/shared/             reusable components, models, utilities
  src/app/features/           auth, claimant, staff, manager pages (lazy-loaded)
  proxy.conf.json             /api/* → the three services (the dev server)
  Dockerfile, nginx.conf      the production bundle served by nginx, which forwards /api/* the same way
docker-compose.yml            Kafka, the three services and the app, as containers
scripts/
  dev.mjs                     one command starts the services and the dev server
.github/workflows/ci.yml      tests and a production build on every push and pull request
docs/
  screenshots/                the images used above
CLAUDE.md                     the project brief
README.md · architecture.md · AI_WORKING_JOURNAL.md
```

### Documentation map

| Document | Read it for |
|---|---|
| [README.md](README.md) (this file) | Running the platform, demo logins, what is covered, test results, limitations |
| [architecture.md](architecture.md) | Design: service boundaries, request flow, lifecycle, security, event pattern, data model, dashboards, front end, decisions and trade-offs, assumptions, known gaps |
| [frontend/claims-portal/README.md](frontend/claims-portal/README.md) | The Angular app: scripts, routes, source layout, how it works |
| [AI_WORKING_JOURNAL.md](AI_WORKING_JOURNAL.md) | How AI assistance was used: a dated, running record, one numbered entry per request. Each entry gives the task or prompt, what the AI recommended or did, what was accepted, what the AI challenged or corrected, manual human changes, how it was validated (and what was not) and the result |
| [CLAUDE.md](CLAUDE.md) | The original brief |

## Known limitations

* **Settling records the fact, not a payment.** The staff member who holds an approved or rejected claim can settle it, with an
  optional comment, and that closes it. There is no field for the amount paid, the payee or a payment reference, and nobody
  but that staff member can settle it (a manager cannot, and there is no reassignment). A rejected claim can be settled too,
  which is how the brief's lifecycle ends.
* Per the brief's formula, **rejected claims count toward liability exposure**; only settled ones are excluded.
* Claims staff can only take claims for themselves, and an assignee is shown as "Staff #n" because there is no staff
  directory.
* **New-claim alerts reach only the claims staff that claim-service can find.** Users live in auth-service and claim-service
  has no directory, so it takes the claims staff to be everyone who has already acted on a claim in that role (read from the
  claim history; `HistoryClaimsStaffDirectory`). All five seeded staff qualify. An account added after the seed is not
  alerted about new claims until it takes its first one, though it still sees the work queue. The bell checks for new
  alerts every ten seconds, so one can take up to that long to appear.
* **One flaky backend test.** `StaffWorkflowIntegrationTest.aStaffMemberTakesAClaimAsksForInformationAndApprovesIt`
  (assessment-service) mints one JWT at the start and later compares it with the token its request helper mints again. A JWT
  carries its issue time to the second, so the two differ whenever the clock ticks over in between. It failed once in the
  2026-10-22 run (under the coverage agent) and passed on re-run. The fix is to capture the token the request actually used.
* Not production-ready by design: in-memory databases, an open H2 console and Swagger UI, a shared HS256 secret, no
  refresh tokens or rate limiting.
* **Exchange rates are fixed, not live.** The dashboards convert every market's amounts to USD at indicative rates held in
  configuration, so the USD figures are for orientation (the per-market figures in local currency are exact) and move if the
  rates are changed.
* A claim is always in the currency of its market: there is no way to file, say, a USD-denominated claim in Singapore. Yen are
  stored with two decimals although the currency has none.
* The market is chosen by the claimant and is not checked against the policy number.
* **Claims over time is bucketed in memory.** Claim-service reads each claim in scope as two values (when it was submitted and
  its status) and puts it into its week or month in Java, in UTC. That is fine at this size; at volume the move is a
  database-side `GROUP BY` or a daily rollup. A window of up to 183 days is drawn in weeks and anything longer in months.
* **Period presets use the browser's date.** "Last 30 days", "Last 90 days" and "Year to date" are worked out from the browser's
  local date but read by the server as UTC dates, so a manager well east of UTC can find the range a day off at its edge around
  midnight.
* **Exposure by claim type is rounded per market and type**, like the other breakdowns, so its parts can differ from the headline
  by a few cents (on the seed data and the guide's one extra claim the two types add up to 2,676,155.42 against 2,676,155.44).
  The chart shows whole dollars.
* The colour of three of the five claim-type series (Health, Travel, Personal accident) is under 3:1 against the white cards, which
  the palette validator reports as a warning: the relief is the table view, the legend and the labels. The seed data only has
  Auto and Home, whose colours pass.
* **Renames edited the migrations in place.** The role codes (`CLAIMS_STAFF`, `MANAGER`) and the claims-staff data names
  (`assignedStaffId`, `staffId`, `staffName`, the `staff_id` columns, `/api/dashboard/staff-workload`) were changed by editing
  the Flyway `V1` and `V2` files, which works only because the databases are in memory and rebuilt on every start. Against a
  database that already holds data it would take new migrations that rename the columns and rewrite the stored role values.
* The my-assigned-claims page has no component spec (it was checked by walking the running app); the work queue, the claim
  review page, the manager dashboard and the shared prompt dialog have one each.
* **Kafka mode is a first version, and eventually consistent.** The audit trail and notifications follow a claim change by
  about a second instead of arriving with it. It assumes one relay instance (two would not lose events but could send some
  twice, which the listener ignores), nothing replays the dead-letter topic `claim-events.DLT`, and the broker in
  `docker-compose.yml` is one plaintext node with replication factor 1 and no volume. Details and the failure behaviour are in
  [Events](#events-in-process-or-kafka) and [architecture.md](architecture.md#7-event-publisher-pattern).
* **The Docker files have never been built or run** (no Docker on the machine they were written on). See [In Docker](#in-docker)
  for what was checked instead and what was not.
* **CI is written but has not run.** `.github/workflows/ci.yml` follows the commands in this README and needs a Temurin JDK 27
  on the runner; nothing here could execute it. It does not build the Docker images either.
* **The start script was run on Windows only.** `scripts/dev.mjs` was exercised on Windows 11 (a start, a refused start on a
  busy port, a service dying under it, the stop that follows). Its macOS and Linux branches (process groups, `sh mvnw`) have not
  been run, and its Ctrl+C handler was not exercised from a script.
* **No metrics or tracing.** The health endpoints and the request id are all there is for operating the services; logs are plain
  text.

More in [architecture.md](architecture.md#15-known-gaps-and-next-steps).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Dashboard or work queue shows "service unavailable" (503) | claim-service (8082) is not running; assessment-service depends on it |
| `node scripts/dev.mjs` says "Cannot start: port … is already in use" | Another copy, often one started from your IDE, holds the port. Stop it, or set `AUTH_PORT`, `CLAIM_PORT`, `ASSESSMENT_PORT` or `FRONTEND_PORT` to free ports |
| assessment-service reports `DOWN` on `/actuator/health/readiness` | It cannot reach claim-service: check that claim-service is up and that `CLAIM_SERVICE_URL` points at it |
| "Port 808x was already in use", or `ng serve` offers a different port | Something already holds the port, usually the same service started twice (an IDE run configuration and a terminal). Find it with `netstat -ano \| findstr :8084` then `tasklist /FI "PID eq <pid>"` (macOS/Linux: `lsof -i :8084`), and stop it |
| `docker compose up` waits, then reports a service `unhealthy` | Its health check calls its own `/actuator/health/readiness`. See why with `docker compose logs <service>`. claim-service starts only when the broker is healthy, assessment-service only when claim-service is, and the front end when all three are; a first start can take a couple of minutes |
| In `kafka` mode a claim is filed or decided, but its timeline and notifications stay empty | The events are waiting for Kafka or for the listener. They are not lost: they sit in the `claim_event_outbox` table and are sent when the broker answers (claim-service logs "Could not send claim events to Kafka" while it does not). One that cannot be recorded ends up on the `claim-events.DLT` topic |
| claim-service logs an error about configuring topics when it starts in `kafka` mode | The broker was not reachable at start-up, when the service creates its topics. Start the broker first (compose does) and restart claim-service |
| Sign-in fails or calls go to the wrong service after a port change | The dev-server proxy reads `proxy.conf.json` only when it starts: stop `npm start` and run it again |
| Everything signs out after changing `JWT_SECRET` in one service | The secret must match in all three; restart them together |
| Startup error "required a bean of type PasswordEncoder", or `application.yml` / `db/migration` missing from `target/classes`, or "Failed to delete …\target\classes" | The repository is inside a OneDrive folder, which turns freshly built `target` folders into placeholders and breaks Maven and IDE builds. Delete the affected `target` folder (`Remove-Item -Recurse -Force <service>\target`) and rebuild. The lasting fix is to keep the repo outside OneDrive. |
| First page load after `npm start` is slow | The dev server compiles on demand; later loads are fast |
| Approve, Reject, Request information and Settle do nothing, and the browser console shows `NG0201: No provider found for RendererFactory2` or `NG0200: Circular dependency … ViewportRuler` | The dev server's dependency cache was rewritten underneath it. Starting a second `ng serve` from the same `frontend/claims-portal` folder does that, because both share `.angular/cache`; the page then loads two copies of Angular, and the dialogs, being lazy-loaded, are what fail. Stop `npm start`, delete `frontend/claims-portal/.angular/cache` if it still misbehaves, start it again and hard-refresh (Ctrl+Shift+R). For a second dev server, serve a copy of the folder (`node scripts/dev.mjs --frontend-dir <copy>`; the copy needs its own `node_modules`, because a junction to this one does not build) |
