# CHUBB APAC CLAIMS MANAGEMENT PLATFORM

## Architecture

This solution follows a Microservices Architecture.

Services:

1. Auth Service
2. Claim Service
3. Assessment Service
4. Angular Frontend

---

# Technology Stack

## Backend

- Java 27 (the JDK the project is built and tested on)
- Spring Boot 3.5
- Spring Security
- JWT Authentication
- Spring Data JPA
- H2 Database
- Flyway
- OpenAPI / Swagger
- Validation API
- Spring Boot Actuator (health endpoints only)
- JUnit 5
- Mockito

## Frontend

- Angular 22
- Angular Material 22
- Standalone components and signals
- Reactive Forms
- Route Guards
- Vitest (run with `ng test`)

---

# Important Constraints

DO NOT USE

- Lombok
- PostgreSQL
- MySQL

USE

- Constructor Injection
- Java Records for DTOs
- H2 Database
- Flyway
- Event Publisher Pattern

Docker and Kafka were on the original "do not use" list and were added later at the owner's request. They are optional:

- Docker is for packaging and running only (a Dockerfile per part and one `../docker-compose.yml`). The databases stay in-memory H2. Nothing in the code needs Docker; `node scripts/dev.mjs` still starts everything without it.
- Kafka is opt-in (`app.events.publisher=kafka`). The default is still `LocalClaimEventPublisher`, so the services and their tests need no broker.

---

# User Roles

## CLAIMANT

Permissions

- Register
- Login
- Create Claim
- View Own Claims
- Track Claims
- Answer a Request for Additional Information
- View Notifications

---

## CLAIMS_STAFF

Permissions

- View Work Queue
- Assign Claims (a staff member takes an unassigned claim for themselves)
- Review Claims
- Approve Claims
- Reject Claims
- Request Additional Information
- Settle Approved or Rejected Claims
- Add Notes
- View Notifications

---

## MANAGER

Permissions

- Dashboard Access
- Liability Exposure Dashboard
- Claims Staff Workload Dashboard
- Filter the dashboards by market, claim type and submission dates

Self-registration always creates a CLAIMANT. Claims staff and manager accounts come from the seeded demo data.

---

# Microservice 1: Auth Service

Responsibilities

- User Registration
- Login
- JWT Token Generation
- JWT Validation
- User Management

Port

8084

Database Tables

users

Endpoints

POST /api/auth/register

POST /api/auth/login

GET /api/users/me

---

# Microservice 2: Claim Service

Responsibilities

- Create Claim
- Track Claim
- Search Claims
- Claim History
- Notifications
- Owns the claim lifecycle: every status change is made here and checked against the transition table in `ClaimStatus`

Every claim is filed in a market (AU, HK, JP, MY, NZ, SG). The market fixes the claim's currency (AUD, HKD, JPY, MYR, NZD, SGD).

Port

8082

Database Tables

claims
claim_history
notifications

Endpoints

POST /api/claims

GET /api/claims/{claimNumber}

GET /api/claims/my-claims

GET /api/claims/search

POST /api/claims/{id}/additional-info

GET /api/notifications

GET /api/notifications/unread-count

POST /api/notifications/{id}/read

POST /api/notifications/read-all

Internal endpoints, called only by the Assessment Service

/api/internal/claims/** (statistics, trend, get, assign, approve, reject, settle, request-info)

---

# Microservice 3: Assessment Service

Responsibilities

- Assign Claims
- Approve Claims
- Reject Claims
- Settle Claims
- Staff Notes
- Dashboard Metrics
- Liability Exposure

It does not read the claims table. It calls the Claim Service's internal endpoints and forwards the caller's JWT.

Port

8083

Database Tables

claim_assessments
claim_notes

Endpoints

GET /api/staff/claims/unassigned

POST /api/staff/claims/{id}/assign

POST /api/staff/claims/{id}/approve

POST /api/staff/claims/{id}/reject

POST /api/staff/claims/{id}/settle

POST /api/staff/claims/{id}/request-info

GET /api/staff/claims/{id}/notes

POST /api/staff/claims/{id}/notes

GET /api/dashboard/status-summary

GET /api/dashboard/staff-workload

GET /api/dashboard/liability-exposure

GET /api/dashboard/claims-over-time

The dashboard endpoints accept the optional filters market, claimType, fromDate and toDate.

---

# Claim Lifecycle

SUBMITTED

↓

UNDER_REVIEW

↓

MORE_INFO_REQUIRED

↓

UNDER_REVIEW

↓

APPROVED

OR

REJECTED

↓

SETTLED

Only valid status transitions allowed.

SETTLED is reached only from APPROVED or REJECTED, by the claims staff member the claim is assigned to. It is the end of the lifecycle.

---

# Event Publisher Architecture

Event Driven Design is implemented using abstraction.

Interface:

ClaimEventPublisher

Events:

CLAIM_CREATED

CLAIM_ASSIGNED

CLAIM_APPROVED

CLAIM_REJECTED

ADDITIONAL_INFO_REQUESTED

ADDITIONAL_INFO_RECEIVED

CLAIM_SETTLED

Implementations (chosen with `app.events.publisher`, environment variable `EVENTS_PUBLISHER`):

LocalClaimEventPublisher (`local`, the default)

KafkaClaimEventPublisher (`kafka`)

Actions (both, through one `ClaimEventHandler`):

- Logging
- Audit History
- Notifications

Local handles the event in-process, inside the claim's own transaction.

Kafka saves the event to an outbox table in that transaction (so it exists exactly when the claim change does), a relay sends it to the `claim-events` topic, and a listener records it from the topic. Delivery is at least once and the listener ignores a duplicate. The audit row and notifications therefore appear shortly after the change instead of with it. An event that cannot be recorded is parked on `claim-events.DLT`.

No service layer changes were needed: `ClaimService` still only calls `ClaimEventPublisher.publish`.

---

# Security

JWT Authentication

Roles

- CLAIMANT
- CLAIMS_STAFF
- MANAGER

Use Spring Security.

Use Bearer Tokens.

---

# Flyway

All schema creation must be done through Flyway.

Never use automatic schema generation.

Migration Folder:

src/main/resources/db/migration

---

# Dashboard Requirements

Metrics:

- Total Claims
- Pending Claims
- Approved Claims
- Rejected Claims
- Settled Claims
- Claims Staff Workload
- Liability Exposure
- Claims Over Time (submitted per week or month, and where they stand now)

Liability Exposure Formula

SUM(claimAmount)

WHERE status != SETTLED

Claims are in their own market's currency. The dashboards add them up in one reporting currency, USD, using fixed indicative rates held in the Assessment Service's `application.yml`. They are not live market rates.

Filters: market, claim type, submission date range.

---

# Search Features

Search By

- Claim Number
- Policy Number
- Status
- Market
- Date Range
- Assigned Staff Member

Support

- Pagination
- Sorting

---

# Testing

Generate

- Service Tests
- Controller Tests
- Security Tests
- Dashboard Tests

Use

- JUnit 5
- Mockito
- MockMvc
- Vitest (frontend)

---

# Angular Frontend

Modules

Auth

Claimant

Staff

Manager

Shared

Core

Pages

Login

Register

Create Claim

My Claims

Claim Details

Notifications

Work Queue

Claim Review

Dashboard

---

# Coding Standards

Use

- Constructor Injection
- SOLID Principles
- Clean Architecture
- DTO Pattern

Avoid

- Field Injection
- Large Controllers
- Business Logic in Controller Layer

---

# Documentation

Generate

README.md

architecture.md

AI_WORKING_JOURNAL.md

frontend/claims-portal/README.md

Keep documentation updated as development progresses.

---

# Operations

Health

- Every service exposes `/actuator/health`, with liveness and readiness probes under `/actuator/health/**`. They are public and show no details.
- The Assessment Service's readiness also checks that the Claim Service is reachable.

Request tracing

- Every service reads or creates an `X-Request-Id` header, puts it in the log pattern, and returns it on the response.
- The Assessment Service forwards it on its calls to the Claim Service, so one request can be followed across services.

Running

- `node scripts/dev.mjs` starts the three services and the Angular dev server together.
- `docker compose up --build` starts a single-node Kafka broker, the three services (claim-service with `EVENTS_PUBLISHER=kafka`) and the Angular app behind nginx on http://localhost:4200. `../backend/Dockerfile` builds any one service (`--build-arg SERVICE=<module>`); `frontend/claims-portal/Dockerfile` builds the app.
- `../.github/workflows/ci.yml` builds and tests the backend and the frontend.

---

# Differences from the Original Brief

The code goes beyond the first version of this file in these places.

| Area | Original brief | Code |
|---|---|---|
| Java | 21 | 27 |
| Angular | 19 | 22 |
| Docker | not to be used | optional: Dockerfiles and `../docker-compose.yml` |
| Kafka | not to be used; `KafkaEventPublisher` as a future step | `KafkaClaimEventPublisher`, opt-in, with an outbox, a relay and a listener |
| Event publisher | `LocalEventPublisher` | `LocalClaimEventPublisher` |
| Events | five | plus `ADDITIONAL_INFO_RECEIVED` and `CLAIM_SETTLED` |
| Settling | `SETTLED` in the lifecycle only | `POST /api/staff/claims/{id}/settle` |
| Markets | none | six markets, each with one currency; a market filter on search and the dashboards |
| Dashboard | three endpoints | plus `claims-over-time`; amounts in USD; filters |
| Notifications | listed as a responsibility | `/api/notifications` endpoints |
| Staff notes | `POST` only | plus `GET /api/staff/claims/{id}/notes` |
| Operations | none | health, request ids, one-command start |

# Priority Order

1. Auth Service
2. Claim Service
3. Assessment Service
4. Angular Frontend
5. Unit Tests
6. Documentation