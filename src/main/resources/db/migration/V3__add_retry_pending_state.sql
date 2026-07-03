-- =============================================================================
-- V3: Add RETRY_PENDING Job Status State
-- =============================================================================

-- Drop the existing anonymous status check constraint
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;

-- Re-create the constraint including RETRY_PENDING
ALTER TABLE jobs ADD CONSTRAINT jobs_status_check 
    CHECK (status IN ('QUEUED', 'SCHEDULED', 'CLAIMED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTER', 'RETRY_PENDING'));
