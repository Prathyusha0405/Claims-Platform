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

- Java 21
- Spring Boot 3
- Spring Security
- JWT Authentication
- Spring Data JPA
- H2 Database
- Flyway
- OpenAPI / Swagger
- Validation API
- JUnit 5
- Mockito

## Frontend

- Angular 19
- Angular Material
- Reactive Forms
- Route Guards

---

# Important Constraints

DO NOT USE

- Lombok
- PostgreSQL
- MySQL
- Docker
- Kafka Infrastructure

USE

- Constructor Injection
- Java Records for DTOs
- H2 Database
- Flyway
- Event Publisher Pattern

---

# User Roles

## CLAIMANT

Permissions

- Register
- Login
- Create Claim
- View Own Claims
- Track Claims
- View Notifications

---

## CLAIMS_STAFF

Permissions

- View Work Queue
- Assign Claims
- Review Claims
- Approve Claims
- Reject Claims
- Request Additional Information
- Add Notes

---

## MANAGER

Permissions

- Dashboard Access
- Liability Exposure Dashboard
- Claims Staff Workload Dashboard

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

---

# Microservice 3: Assessment Service

Responsibilities

- Assign Claims
- Approve Claims
- Reject Claims
- Staff Notes
- Dashboard Metrics
- Liability Exposure

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

POST /api/staff/claims/{id}/request-info

POST /api/staff/claims/{id}/notes

GET /api/dashboard/status-summary

GET /api/dashboard/staff-workload

GET /api/dashboard/liability-exposure

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

Current Implementation:

LocalEventPublisher

Actions:

- Logging
- Audit History
- Notifications

Future:

KafkaEventPublisher

No service layer changes required.

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
- Claims Staff Workload
- Liability Exposure

Liability Exposure Formula

SUM(claimAmount)

WHERE status != SETTLED

---

# Search Features

Search By

- Claim Number
- Policy Number
- Status
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

Keep documentation updated as development progresses.

---

# Priority Order

1. Auth Service
2. Claim Service
3. Assessment Service
4. Angular Frontend
5. Unit Tests
6. Documentation