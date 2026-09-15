# Evolution record

This document is updated at the moment each version is implemented and verified. It records why a change exists, not merely the final component list.

## v0.1.0 — runnable baseline

### Business problem

Device delivery and field operations need a shared record of projects, product models, installed devices and issues. A work order may affect several devices, and later investigation must retain who performed each state change.

### First design and rationale

The first version is a Spring Boot modular monolith with one MySQL transaction boundary. It deliberately keeps project, product, device and work-order capabilities in separate packages while producing one deployable artifact. Work-order core data, device relations and tracks are separate tables because their cardinality and retention rules differ.

Device serial number validation is enforced in the service and by `uk_device_sn` in MySQL. Work-order acceptance uses `UPDATE ... WHERE id = ? AND status = 'WAITING'` and checks `affectedRows`; this is already safe rather than introducing a knowingly broken read-then-write implementation.

### Alternatives considered

| Decision | Chosen now | Deferred alternative | Why deferred |
|---|---|---|---|
| Deployment boundary | Modular monolith | Microservices | No independent scaling or team-ownership evidence yet; would add network and consistency failure modes |
| Work-order/device query | Simple work-order page | Join page | Direct join pagination duplicates parent rows; a two-query view is introduced with its real tests in v0.2.0 |
| Device creation | Single API | Full import pipeline | The baseline stays small and executable; batch validation and XML insert are the explicit v0.2.0 change |
| Event handling | Synchronous domain calls | RocketMQ | Message retry and idempotency require failure storage and integration tests, delivered together in v0.3.0 |
| Permission lookup | Authenticated session | Redis RBAC cache | Cache invalidation and session effects must be implemented as one coherent v1.0.0 capability |

### Implementation and exception path

Creating a work order validates the project and all deduplicated device IDs before opening writes. The main row, relation rows and initial track share one transaction. A validation failure produces no write; an unexpected persistence exception rolls back all writes. Acceptance executes one conditional SQL statement, writes a track only when exactly one row changed, and returns a business conflict otherwise.

### Test evidence and boundary

On 2026-09-15, `mvnw.cmd test` compiled with JDK 25 targeting Java 21 and ran three JUnit 5/Mockito tests: deduplicated relations and track creation, cross-project rejection before writes, and no track after a failed conditional acceptance. These are unit-level tests; MySQL constraint, transaction and concurrency behavior is intentionally not claimed until the v0.2.0 integration suite runs against MySQL.

### Next evolution

v0.2.0 adds the complete state machine and real-MySQL evidence, then replaces per-item import access with business-key collection, database batch lookup, in-memory `Set`/`Map` classification and XML batch insertion.
