# ADR 0001: Service boundaries and data ownership

- Status: Accepted
- Date: 2026-10-23

## Context

The assignment requires a reactive microservices architecture in which users authenticate and can
access only their own tasks. The implementation must remain small enough to complete and explain
within the take-home timeline.

## Decision

The system has two independently deployable Spring Boot services:

- `auth-service` owns user registration, credentials, and RS256 token issuance.
- `task-service` owns task data and verifies tokens using the RSA public key published by the auth
  service at `/.well-known/jwks.json`.

The normal request path requires no synchronous call between the services. The task service will
read the authenticated user ID from the JWT `sub` claim and use it in every task query.

For local development, both services use one PostgreSQL instance with separate `auth_service` and
`task_service` schemas. Each service owns its schema and Liquibase changelog. There are no
cross-schema foreign keys or queries.

Runtime database access uses R2DBC. Liquibase uses JDBC during startup because schema migration is
a bounded startup operation rather than request processing.

## Consequences

- A task cannot be accessed without both its ID and authenticated owner ID matching.
- Task-service can validate tokens without auth-service being available for every request.
- Each schema can move to a separate database later without changing the service contract.
- Local development needs only one PostgreSQL container.
- JWT revocation is not immediate; short token lifetimes will limit that trade-off.
- Database migration remains a blocking startup concern, but request processing stays reactive.

## Alternatives considered

### Modular monolith

It would be simpler operationally, but it does not satisfy the explicit microservices requirement.

### Separate PostgreSQL containers

This gives stronger physical isolation but adds setup and resource cost without improving the
take-home demonstration. Separate schemas preserve the important ownership boundary for now.

### Task-service calling auth-service on every request

This would support centralized session checks but couples task availability and latency to the auth
service. Local JWT verification is smaller and matches the RS256 requirement.
