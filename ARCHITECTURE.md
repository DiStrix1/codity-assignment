# Architecture Details

This document outlines the architectural components, data flow, and concurrency model of the Distributed Job Scheduler.

## Components

The system is designed as a single Spring Boot application that can be run in two distinct modes using Spring Profiles:
1. **API Service (`api` profile)**: Exposes the HTTP REST API, handles user authentication, schedules jobs, manages queues, and streams real-time updates over WebSockets.
2. **Worker Pool (`worker` profile)**: A background execution engine that polls the database for eligible jobs, claims them atomically, and executes them concurrently using Java 17 thread pools.

In development, both profiles are active by default. In production, you can scale them independently:
- Scale HTTP traffic: Spin up multiple API instances behind a load balancer.
- Scale throughput: Spin up multiple Worker instances to process jobs concurrently.

```
                  ┌───────────────────────────────┐
                  │        Load Balancer          │
                  └──────────────┬────────────────┘
                                 │
          ┌──────────────────────┴──────────────────────┐
          ▼                                             ▼
┌──────────────────┐                           ┌──────────────────┐
│   API Instance   │                           │   API Instance   │
│ (Spring Boot api)│                           │ (Spring Boot api)│
└─────────┬────────┘                           └─────────┬────────┘
          │                                              │
          │             ┌──────────────────┐             │
          ├────────────►│  PostgreSQL DB   │◄────────────┤
          │             │  (Shared State)  │             │
          │             └──────────▲───────┘             │
          ▼                        │                     ▼
┌──────────────────┐               │           ┌──────────────────┐
│  Worker Instance │───────────────┤           │  Worker Instance │
│(Spring Boot wrkr)│               │           │(Spring Boot wrkr)│
└──────────────────┘               │           └──────────────────┘
                                   │
                         ┌─────────┴─────────┐
                         │   Cron Scheduler  │
                         │(Spring Boot wrkr) │
                         └───────────────────┘
```

---

## Data Flow & Job Lifecycle

Every job created in the system transitions through a defined lifecycle. 

```
                                  +---------+
                                  | BATCH / |
                                  | IMMEDIATE
                                  +----+----+
                                       |
                                       ▼
+-----------+    cron due         +----+----+
| SCHEDULED | ------------------► | QUEUED  | ◄------------------+
+-----+-----+                     +----+----+                    |
      ▲                                |                         |
      | delayed                        | claim (SKIP LOCKED)     | retry
      |                                ▼                         |
+-----+-----+                     +----+----+                    |
| DELAYED/  |                     | CLAIMED |                    |
| SCHEDULED |                     +----+----+                    |
+-----------+                          |                         |
                                       ▼                         |
                                  +----+----+                    |
                                  | RUNNING |                    |
                                  +----+----+                    |
                                       |                         |
                    +------------------+------------------+      |
                    | success                             | fail |
                    ▼                                     ▼      |
              +-----+-----+                         +-----+-----+|
              | COMPLETED |                         |  FAILED   |+
              +-----------+                         +-----+-----+
                                                          |
                                                          | max attempts
                                                          | exhausted
                                                          ▼
                                                    +-----+-----+
                                                    |DEAD_LETTER|
                                                    +-----------+
```

1. **Submission**:
   - Immediate & Batch jobs enter `QUEUED` status immediately.
   - Delayed & Scheduled jobs enter `SCHEDULED` status and are promoted to `QUEUED` by the API service once their trigger time passes.
   - Recurring jobs have a metadata definition in the `scheduled_jobs` table. The `CronSchedulerService` periodically monitors due definitions, enqueues a regular job in `QUEUED` status, and computes the next trigger time.

2. **Claiming**:
   - Active worker instances poll the `queues` table ordered by queue priority.
   - For each queue, the worker checks its local semaphore to ensure it does not exceed the queue's `maxConcurrency` limit.
   - It issues an atomic DB claim query using `SELECT ... FOR UPDATE SKIP LOCKED` to transition the highest-priority job to `CLAIMED` and update the `claimed_by_worker` field.

3. **Execution**:
   - The worker shifts the job to `RUNNING` and spawns a thread to run the job's business logic.
   - Throughout execution, logs are written to the `job_logs` table linked to the current `job_execution` attempt.

4. **Completion / Failure**:
   - On success, the job moves to `COMPLETED`.
   - On failure, the retry policy determines if the job should be rescheduled back to `QUEUED` with a calculated backoff delay or transitioned to `DEAD_LETTER` if max attempts are reached.

---

## Concurrency and Distributed Locking

### Lock-Free Scaling with SKIP LOCKED
Instead of using a distributed lock manager (like Redis/Redisson) to coordinate which worker handles which job, the system leverages PostgreSQL's native lock concurrency:
```sql
SELECT j.id FROM jobs j
WHERE j.queue_id = :queueId AND j.status = 'QUEUED'
ORDER BY j.priority DESC, j.created_at ASC
LIMIT 1
FOR UPDATE SKIP LOCKED
```
This guarantees:
- **Zero Lock Contention**: Workers checking the database simultaneously will skip rows locked by other transactions, preventing serialization failures.
- **Transactional Atomicity**: The lock is acquired and the status is updated to `CLAIMED` in the same transaction, making it impossible for two workers to claim the same job.

### Cron Coordination
To prevent multiple worker instances from triggering the same recurring cron definition at the exact same moment, the `CronSchedulerService` uses JPA's pessimistic write locking (`LockModeType.PESSIMISTIC_WRITE`) when fetching due schedules:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ScheduledJob s WHERE s.enabled = true AND s.nextFireAt <= :now ORDER BY s.nextFireAt ASC")
List<ScheduledJob> findDueScheduledJobsForUpdate(Instant now);
```
The first worker instance to run the scheduler locks the due rows, processes them, updates their `nextFireAt`, and releases the lock. Subsequent workers find no due schedules and skip execution safely.
