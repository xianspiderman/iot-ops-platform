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

## v0.2.0 — correctness and bulk processing

### Case 1: device import

**Business problem -> first candidate.** Onboarding a workbook requires row-specific feedback without allowing one bad row to hide valid devices. The smallest implementation would loop over rows, query project/product/SN, and call one insert per valid device. It is easy to read, but query and network round trips grow with row count and the transaction becomes harder to reason about.

**Exposed issue -> candidates.** The candidate performs repeated lookups for the same project/product and cannot identify file-internal duplicates without database traffic. Three options were compared: retain row-by-row access; stage every row in a temporary table; or collect business keys in memory and issue bounded batch queries. A staging table is useful for very large or asynchronous jobs but adds cleanup and state transitions that a configured 10,000-row synchronous limit does not yet justify.

**Choice -> implementation.** EasyExcel keeps the original sheet row number. The service normalizes keys, collects project codes, product codes and SNs, loads maps/sets in batches, then performs required/length/format, file duplicate, existing database, project and product checks. Valid `Device` objects and `DeviceImportError` objects are separated. MyBatis XML writes valid devices in configurable 200-row statements inside one transaction; errors and final task counters join that transaction. A system exception rolls back every business write, after which a separate transaction marks the task failed. The database unique key remains the final defense against races after pre-query.

**Evidence -> tradeoff -> next condition.** MySQL tests prove all-valid success, mixed row errors with correct source row numbers, file/database duplicates, and a unique failure in batch two rolling back batch one. This removes per-row database access, but retains all normalized rows and keys in memory. Asynchronous processing or staging will be reconsidered only when accepted file sizes, request duration or measured heap use exceed this bounded model.

### Case 2: state reads versus conditional updates

**Business problem -> risk.** Two operators can accept the same waiting order. A separate `SELECT status` followed by an unconditional update creates a time-of-check/time-of-use race. The v0.1.0 acceptance path therefore shipped with an atomic `UPDATE ... WHERE status = 'WAITING'` rather than deliberately publishing the unsafe candidate.

**Generalization in v0.2.0.** The same pattern now covers submit, verify, cancel and transfer. The affected-row count is the concurrency decision: exactly one means the transition owns the change; zero becomes a business conflict and no track is written. The state-machine graph documents legal transitions, while SQL conditions enforce the state observed by MySQL. Main-row change and track insertion share a local transaction.

**Evidence and cost.** Two executor threads were released by the same latch against MySQL; exactly one acceptance and one acceptance track remained. A MySQL trigger then injected a track failure after a submit update, and the test observed the main row still in `PROCESSING` with no solution. The cost is explicit SQL per business transition, accepted in exchange for readable invariants and no distributed lock.

### Case 3: one-to-many paging

**Business problem -> first view.** A work-order list needs device IDs. A direct join is an attractive query shape, but paging joined rows limits relation rows rather than parent work orders, so one order with several devices can consume a page or appear on multiple pages. v0.1.0 avoided publishing that incorrect result by returning only main rows.

**Choice and flow.** v0.2.0 pages `work_order` first, collects only that page's IDs, performs one relation query, groups by order ID, and assembles views in page order. A nested query per order was rejected because it becomes N+1 database access. A single aggregated SQL query remains possible, but is database-specific and makes filtering/count behavior less transparent.

**Evidence and boundary.** With two orders and unequal relation counts, MySQL tests request page size one and observe a total of two distinct parents, one parent on each page, and complete device IDs for both. The approach costs one additional query per page; it remains stable and predictable until measured latency or exceptionally wide pages justify an aggregate alternative.
