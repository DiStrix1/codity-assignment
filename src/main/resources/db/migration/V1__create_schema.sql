-- =============================================================================
-- V1: Distributed Job Scheduler — Complete Schema
-- =============================================================================
-- Tables: organizations, users, projects, retry_policies, queues, jobs,
--         job_executions, job_logs, scheduled_jobs, workers, worker_heartbeats,
--         dead_letter_queue
-- =============================================================================

-- ─── Extensions ──────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── Organizations ───────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN', 'MEMBER')),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_org ON users (organization_id);
CREATE INDEX idx_users_email ON users (email);

-- ─── Projects ────────────────────────────────────────────────────────────────
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, slug)
);

CREATE INDEX idx_projects_org ON projects (organization_id);

-- ─── Retry Policies ─────────────────────────────────────────────────────────
CREATE TABLE retry_policies (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    strategy         VARCHAR(20)  NOT NULL DEFAULT 'EXPONENTIAL' CHECK (strategy IN ('FIXED', 'LINEAR', 'EXPONENTIAL')),
    max_attempts     INT          NOT NULL DEFAULT 3 CHECK (max_attempts >= 1 AND max_attempts <= 100),
    initial_delay_ms INT          NOT NULL DEFAULT 1000 CHECK (initial_delay_ms >= 0),
    multiplier       DOUBLE PRECISION NOT NULL DEFAULT 2.0 CHECK (multiplier >= 1.0),
    max_delay_ms     INT          NOT NULL DEFAULT 300000 CHECK (max_delay_ms >= 0),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_retry_policies_org ON retry_policies (organization_id);

-- ─── Queues ──────────────────────────────────────────────────────────────────
CREATE TABLE queues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    priority        INT  NOT NULL DEFAULT 0,
    max_concurrency INT  NOT NULL DEFAULT 5 CHECK (max_concurrency >= 1),
    retry_policy_id UUID REFERENCES retry_policies(id) ON DELETE SET NULL,
    paused          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, slug)
);

CREATE INDEX idx_queues_project ON queues (project_id);
CREATE INDEX idx_queues_priority ON queues (priority DESC);

-- ─── Jobs ────────────────────────────────────────────────────────────────────
CREATE TABLE jobs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id          UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    type              VARCHAR(20)  NOT NULL DEFAULT 'IMMEDIATE' CHECK (type IN ('IMMEDIATE', 'DELAYED', 'SCHEDULED', 'RECURRING', 'BATCH')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'SCHEDULED', 'CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER')),
    payload           JSONB        NOT NULL DEFAULT '{}',
    idempotency_key   VARCHAR(255) UNIQUE,
    batch_id          UUID,
    priority          INT          NOT NULL DEFAULT 0,
    attempt_count     INT          NOT NULL DEFAULT 0,
    max_attempts      INT          NOT NULL DEFAULT 3,
    scheduled_at      TIMESTAMP WITH TIME ZONE,
    claimed_at        TIMESTAMP WITH TIME ZONE,
    claimed_by_worker UUID,
    started_at        TIMESTAMP WITH TIME ZONE,
    completed_at      TIMESTAMP WITH TIME ZONE,
    error_message     TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- THE critical index for the hot-path polling query
-- Covers: status filter + queue routing + priority ordering + time ordering
CREATE INDEX idx_jobs_claimable
    ON jobs (queue_id, priority DESC, created_at ASC)
    WHERE status = 'QUEUED';

-- For finding delayed/scheduled jobs that are now due
CREATE INDEX idx_jobs_scheduled
    ON jobs (scheduled_at)
    WHERE status = 'SCHEDULED' AND scheduled_at IS NOT NULL;

-- Stale claim detection (claimed but worker died)
CREATE INDEX idx_jobs_claimed
    ON jobs (claimed_at)
    WHERE status = 'CLAIMED';

-- Running jobs per queue (for concurrency check)
CREATE INDEX idx_jobs_running_per_queue
    ON jobs (queue_id)
    WHERE status = 'RUNNING';

-- Batch lookup
CREATE INDEX idx_jobs_batch
    ON jobs (batch_id)
    WHERE batch_id IS NOT NULL;

-- Status filter for dashboard queries
CREATE INDEX idx_jobs_status ON jobs (status);

-- Queue + status for stats queries
CREATE INDEX idx_jobs_queue_status ON jobs (queue_id, status);

-- ─── Job Executions ──────────────────────────────────────────────────────────
CREATE TABLE job_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    attempt_number  INT  NOT NULL,
    worker_id       UUID,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMP WITH TIME ZONE,
    duration_ms     BIGINT,
    error_message   TEXT,
    stack_trace     TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, attempt_number)
);

CREATE INDEX idx_job_executions_job ON job_executions (job_id, attempt_number);
CREATE INDEX idx_job_executions_worker ON job_executions (worker_id);

-- ─── Job Logs ────────────────────────────────────────────────────────────────
CREATE TABLE job_logs (
    id           BIGSERIAL PRIMARY KEY,
    job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    execution_id UUID REFERENCES job_executions(id) ON DELETE CASCADE,
    level        VARCHAR(10) NOT NULL DEFAULT 'INFO' CHECK (level IN ('INFO', 'WARN', 'ERROR', 'DEBUG')),
    message      TEXT NOT NULL,
    metadata     JSONB,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_logs_job ON job_logs (job_id, created_at);
CREATE INDEX idx_job_logs_execution ON job_logs (execution_id, created_at);

-- ─── Scheduled Jobs (Recurring / Cron) ───────────────────────────────────────
CREATE TABLE scheduled_jobs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id         UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    cron_expression  VARCHAR(100) NOT NULL,
    payload          JSONB NOT NULL DEFAULT '{}',
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    next_fire_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_jobs_fire
    ON scheduled_jobs (next_fire_at)
    WHERE enabled = TRUE;

-- ─── Workers ─────────────────────────────────────────────────────────────────
CREATE TABLE workers (
    id                  UUID PRIMARY KEY,
    hostname            VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DRAINING', 'OFFLINE')),
    max_concurrent_jobs INT NOT NULL DEFAULT 10,
    current_job_count   INT NOT NULL DEFAULT 0,
    registered_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_heartbeat_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workers_status
    ON workers (status, last_heartbeat_at)
    WHERE status = 'ACTIVE';

-- ─── Worker Heartbeats ───────────────────────────────────────────────────────
CREATE TABLE worker_heartbeats (
    id             BIGSERIAL PRIMARY KEY,
    worker_id      UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    active_jobs    INT NOT NULL DEFAULT 0,
    memory_used_mb BIGINT,
    cpu_load       DOUBLE PRECISION,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_heartbeats_worker ON worker_heartbeats (worker_id, created_at DESC);

-- ─── Dead Letter Queue ───────────────────────────────────────────────────────
CREATE TABLE dead_letter_queue (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_job_id  UUID NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE CASCADE,
    queue_id         UUID NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    payload          JSONB NOT NULL DEFAULT '{}',
    total_attempts   INT NOT NULL,
    final_error      TEXT,
    final_stack_trace TEXT,
    failed_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dlq_queue ON dead_letter_queue (queue_id);
CREATE INDEX idx_dlq_failed ON dead_letter_queue (failed_at DESC);

-- ─── Trigger: Auto-update updated_at ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_projects_updated_at BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_queues_updated_at BEFORE UPDATE ON queues
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_jobs_updated_at BEFORE UPDATE ON jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scheduled_jobs_updated_at BEFORE UPDATE ON scheduled_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
