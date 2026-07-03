# Entity-Relationship Diagram

This document contains the visual representation and column details of the database schema.

## Mermaid ER Diagram

```mermaid
erDiagram
    ORGANIZATIONS {
        uuid id PK
        varchar name
        varchar slug UK
        timestamp created_at
    }
    
    USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role "ADMIN | MEMBER"
        uuid organization_id FK
        timestamp created_at
        timestamp updated_at
    }
    
    PROJECTS {
        uuid id PK
        varchar name
        varchar slug
        uuid organization_id FK
        timestamp created_at
        timestamp updated_at
    }
    
    RETRY_POLICIES {
        uuid id PK
        varchar name
        varchar strategy "FIXED | LINEAR | EXPONENTIAL"
        int max_attempts
        int initial_delay_ms
        double multiplier
        int max_delay_ms
        uuid organization_id FK
        timestamp created_at
    }
    
    QUEUES {
        uuid id PK
        varchar name
        varchar slug
        uuid project_id FK
        int priority
        int max_concurrency
        uuid retry_policy_id FK
        boolean paused
        timestamp created_at
        timestamp updated_at
    }
    
    JOBS {
        uuid id PK
        uuid queue_id FK
        varchar type "IMMEDIATE | DELAYED | SCHEDULED | RECURRING | BATCH"
        varchar status "QUEUED | SCHEDULED | CLAIMED | RUNNING | COMPLETED | FAILED | DEAD_LETTER"
        jsonb payload
        varchar idempotency_key UK
        uuid batch_id
        int priority
        int attempt_count
        int max_attempts
        timestamp scheduled_at
        timestamp claimed_at
        uuid claimed_by_worker
        timestamp started_at
        timestamp completed_at
        text error_message
        timestamp created_at
        timestamp updated_at
    }
    
    JOB_EXECUTIONS {
        uuid id PK
        uuid job_id FK
        int attempt_number
        uuid worker_id FK
        varchar status "RUNNING | COMPLETED | FAILED"
        timestamp started_at
        timestamp ended_at
        bigint duration_ms
        text error_message
        text stack_trace
        varchar idempotency_key
        timestamp created_at
    }
    
    JOB_LOGS {
        bigserial id PK
        uuid job_id FK
        uuid execution_id FK
        varchar level "DEBUG | INFO | WARN | ERROR"
        text message
        jsonb metadata
        timestamp created_at
    }
    
    SCHEDULED_JOBS {
        uuid id PK
        uuid queue_id FK
        varchar cron_expression
        jsonb payload
        boolean enabled
        timestamp last_triggered_at
        timestamp next_fire_at
        timestamp created_at
        timestamp updated_at
    }
    
    WORKERS {
        uuid id PK
        varchar hostname
        varchar status "ACTIVE | DRAINING | OFFLINE"
        int max_concurrent_jobs
        int current_job_count
        timestamp registered_at
        timestamp last_heartbeat_at
    }
    
    WORKER_HEARTBEATS {
        bigserial id PK
        uuid worker_id FK
        int active_jobs
        bigint memory_used_mb
        double cpu_load
        timestamp created_at
    }
    
    DEAD_LETTER_QUEUE {
        uuid id PK
        uuid original_job_id FK
        uuid queue_id FK
        jsonb payload
        int total_attempts
        text final_error
        text final_stack_trace
        timestamp failed_at
        timestamp created_at
    }

    ORGANIZATIONS ||--o{ USERS : "contains"
    ORGANIZATIONS ||--o{ PROJECTS : "owns"
    ORGANIZATIONS ||--o{ RETRY_POLICIES : "defines"
    PROJECTS ||--o{ QUEUES : "contains"
    QUEUES ||--o{ JOBS : "contains"
    QUEUES }o--|| RETRY_POLICIES : "applies"
    QUEUES ||--o{ SCHEDULED_JOBS : "triggers"
    JOBS ||--o{ JOB_EXECUTIONS : "generates"
    JOBS ||--o{ JOB_LOGS : "writes"
    JOB_EXECUTIONS ||--o{ JOB_LOGS : "logs"
    WORKERS ||--o{ WORKER_HEARTBEATS : "emits"
    WORKERS ||--o{ JOBS : "executes"
    JOBS ||--o| DEAD_LETTER_QUEUE : "moves to"
```

## Critical Database Indexes

The schema contains the following specialized indexes to ensure high performance on hot execution and query paths:

1. **`idx_jobs_claimable`**:
   - Query: Polling for jobs where `status = 'QUEUED'`.
   - Index: `CREATE INDEX idx_jobs_claimable ON jobs (queue_id, priority DESC, created_at ASC) WHERE status = 'QUEUED';`
2. **`idx_jobs_scheduled`**:
   - Query: Promoting due scheduled jobs where `status = 'SCHEDULED'`.
   - Index: `CREATE INDEX idx_jobs_scheduled ON jobs (scheduled_at) WHERE status = 'SCHEDULED' AND scheduled_at IS NOT NULL;`
3. **`idx_scheduled_jobs_fire`**:
   - Query: Polling for due cron schedules.
   - Index: `CREATE INDEX idx_scheduled_jobs_fire ON scheduled_jobs (next_fire_at) WHERE enabled = TRUE;`
4. **`idx_workers_status`**:
   - Query: Heartbeat checks for active workers.
   - Index: `CREATE INDEX idx_workers_status ON workers (status, last_heartbeat_at) WHERE status = 'ACTIVE';`
