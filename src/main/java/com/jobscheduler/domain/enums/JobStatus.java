package com.jobscheduler.domain.enums;

public enum JobStatus {
    QUEUED,
    SCHEDULED,
    CLAIMED,
    RUNNING,
    COMPLETED,
    FAILED,
    DEAD_LETTER,
    RETRY_PENDING
}
