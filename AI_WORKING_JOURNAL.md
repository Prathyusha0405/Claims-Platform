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

http://localhost:8081/swagger-ui/index.html

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

---

# Future Enhancements

The following items were intentionally deferred:

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