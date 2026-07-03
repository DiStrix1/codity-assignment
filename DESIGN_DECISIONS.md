# Design Decisions

## Why PostgreSQL as the Queue (SKIP LOCKED) over Kafka/RabbitMQ

**Decision**: Use PostgreSQL with `SELECT ... FOR UPDATE SKIP LOCKED` for job distribution.

**Rationale**:
- **Single source of truth**: Job metadata, state, and queue are in the same database. No eventual consistency issues between a message broker and the database.
- **Transactional consistency**: Claiming a job + updating its state happens in a single transaction. With a message broker, you'd need distributed transactions or saga patterns.
- **Reduced infrastructure**: No need to deploy, configure, and monitor Kafka/RabbitMQ. PostgreSQL is already required for the application data.
- **Simpler failure recovery**: If a worker dies mid-execution, the claimed job is still in the database with a clear `CLAIMED` status and timestamp. We detect stale claims and re-queue automatically. With a message broker, you'd need complex acknowledgment/retry logic.
- **SKIP LOCKED is purpose-built**: PostgreSQL's `SKIP LOCKED` advisory lock mode is specifically designed for work queue patterns. It provides no-wait, no-block concurrent access — exactly what multiple workers need.

**Trade-off**: Throughput ceiling. PostgreSQL can handle ~10K-50K claims/sec on a single instance. For > 100K jobs/sec, you'd need queue sharding or a dedicated message broker. This system is designed for the 95% use case.

## Virtual Threads over Thread Pools

**Decision**: Use Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for job execution.

**Rationale**:
- **Scalability**: Virtual threads are lightweight (~1KB vs ~1MB for platform threads). A worker can execute hundreds of concurrent jobs without OS thread exhaustion.
- **Simplicity**: No need to tune thread pool sizes. Virtual threads scale automatically.
- **Blocking I/O friendly**: Job handlers may make HTTP calls, DB queries, etc. Virtual threads automatically yield during blocking operations without wasting resources.

**Trade-off**: Virtual threads are not suitable for CPU-bound work (they share carrier threads). For compute-intensive jobs, you'd pin to platform threads. Our simulated handler is I/O-bound (sleep), so virtual threads are ideal.

## Custom Cron Parser over Quartz

**Decision**: Use `cron-utils` library for cron parsing instead of Quartz Scheduler.

**Rationale**:
- **No hidden tables**: Quartz adds 11 internal tables to your database with its own cluster coordination. We keep all state in our own schema.
- **Simpler model**: Our `scheduled_jobs` table + `CronSchedulerService` is ~100 lines. Quartz would add thousands of lines of configuration and behavior.
- **Full control**: We handle misfires our way (gap detection on startup), not Quartz's misfire policy matrix.

**Trade-off**: We own the scheduling correctness. Quartz handles edge cases (DST transitions, cluster coordination) that we'd need to implement ourselves. For this system, the simplicity win outweighs the edge case coverage.

## Retry Backoff Defaults

**Default policy**: Exponential, 3 attempts, 1s initial delay, 2x multiplier, 5min max delay.

| Attempt | Delay |
|---------|-------|
| 1 | 1s |
| 2 | 2s |
| 3 | 4s |

**Rationale**: Exponential backoff is the industry standard for transient failures (network timeouts, temporary service unavailability). 3 attempts balances between giving jobs a fair chance and not wasting resources on permanently broken jobs.

## Consistency over Availability (CP System)

**Decision**: Favor consistency in all design choices.

- **Atomic claims**: `SELECT FOR UPDATE SKIP LOCKED` guarantees exactly-once claiming, even if it means a slight throughput reduction.
- **Synchronous state transitions**: Job status changes are transactional, not eventually consistent.
- **Idempotency keys**: Prevent duplicate job creation at the API level.
- **Execution idempotency keys**: `job_id + attempt_number` prevents double side-effects on retries.

**Trade-off**: A purely available system (AP) using optimistic concurrency could have higher throughput but would require reconciliation logic for duplicate executions. For a job scheduler, correctness (no duplicate execution) is non-negotiable.

## UUID Primary Keys

**Decision**: Use UUID v4 for all entity IDs, generated in Java.

**Rationale**:
- **Distributed-safe**: Multiple application instances can generate IDs without coordination.
- **No sequential guessing**: UUIDs don't leak information about entity count or creation order.
- **Merge-safe**: When aggregating data from multiple instances, UUIDs don't conflict.

**Trade-off**: UUIDs are 16 bytes vs 8 bytes for BIGSERIAL, and B-tree index performance is slightly worse due to randomness. For this system's scale, the difference is negligible.

## Single JAR with Profile Activation

**Decision**: One Spring Boot application, toggled by profiles (`api`, `worker`).

**Rationale**:
- **Shared codebase**: Worker and API share entities, repositories, and services. Separate JARs would mean duplicating or creating a shared library.
- **Simple development**: `./mvnw spring-boot:run` gives you everything. No multi-module build complexity.
- **Horizontal scaling**: Deploy N worker instances with `--spring.profiles.active=worker`. Deploy M API instances with `--spring.profiles.active=api`. Or run both in dev.

**Trade-off**: Slightly larger JAR size since both API and worker code are always included. Could be separated into modules if the codebase grows significantly.
