# ADR 001: a Java domain core with two HTTP adapters

Status: accepted for the initial implementation.

## Context

The projects need substantial Java behavior, a practical Spring deployment, and an executable development path that does not depend on a package registry being reachable. A framework-only implementation would make offline verification impossible; a separate mock server would create conflicting business logic.

## Decision

Keep domain rules, validation, persistence contracts, job scheduling, and route handlers in a Java 21 module without third-party dependencies. Provide a JDK HTTP adapter for local execution and a Spring MVC adapter for deployment. Both invoke the same gateway/router/services.

## Consequences

The core can be compiled and tested on an unmodified JDK. Transport behavior is testable independently. Spring-specific features remain available without moving domain logic into controllers.

There is an intentional cost: a small custom router and JSON codec must be maintained and tested. This is not a claim that building those utilities is preferable in every production system. A future Spring-only product could replace them with typed controllers/Jackson while preserving the tested domain invariants.

Native success does not prove Spring wiring or dependency compatibility. The Maven transport tests exist precisely to make that separate verification explicit.
