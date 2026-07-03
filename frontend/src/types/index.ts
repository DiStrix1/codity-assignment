export interface Organization {
  id: string;
  name: string;
  slug: string;
}

export interface Project {
  id: string;
  name: string;
  slug: string;
  organizationId: string;
  createdAt: string;
  updatedAt: string;
}

export interface RetryPolicy {
  id: string;
  name: string;
  strategy: 'FIXED' | 'LINEAR' | 'EXPONENTIAL';
  maxAttempts: number;
  initialDelayMs: number;
  multiplier: number;
  maxDelayMs: number;
  createdAt: string;
}

export interface Queue {
  id: string;
  name: string;
  slug: string;
  projectId: string;
  projectName: string;
  priority: number;
  maxConcurrency: number;
  retryPolicyId?: string;
  retryPolicyName?: string;
  paused: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface QueueStats {
  queueId: string;
  queueName: string;
  pending: number;
  running: number;
  completed: number;
  failed: number;
  deadLetter: number;
  scheduled: number;
  paused: boolean;
}

export type JobStatus = 'QUEUED' | 'SCHEDULED' | 'CLAIMED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'DEAD_LETTER' | 'RETRY_PENDING';
export type JobType = 'IMMEDIATE' | 'DELAYED' | 'SCHEDULED' | 'RECURRING' | 'BATCH';

export interface Job {
  id: string;
  queueId: string;
  queueName: string;
  type: JobType;
  status: JobStatus;
  payload: Record<string, unknown>;
  idempotencyKey?: string;
  batchId?: string;
  priority: number;
  attemptCount: number;
  maxAttempts: number;
  scheduledAt?: string;
  claimedAt?: string;
  claimedByWorker?: string;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface JobExecution {
  id: string;
  attemptNumber: number;
  workerId?: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt: string;
  endedAt?: string;
  durationMs?: number;
  errorMessage?: string;
  stackTrace?: string;
}

export interface JobLog {
  id: number;
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
  message: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export interface JobDetail {
  job: Job;
  executions: JobExecution[];
  recentLogs: JobLog[];
}

export interface Worker {
  id: string;
  hostname: string;
  status: 'ACTIVE' | 'DRAINING' | 'OFFLINE';
  maxConcurrentJobs: number;
  currentJobCount: number;
  registeredAt: string;
  lastHeartbeatAt: string;
}

export interface DeadLetterEntry {
  id: string;
  originalJobId: string;
  queueId: string;
  queueName: string;
  payload: Record<string, unknown>;
  totalAttempts: number;
  finalError?: string;
  finalStackTrace?: string;
  failedAt: string;
  createdAt: string;
}

export interface Metrics {
  totalJobs: number;
  pendingJobs: number;
  runningJobs: number;
  completedJobs: number;
  failedJobs: number;
  deadLetterJobs: number;
  completedLastHour: number;
  failedLastHour: number;
  avgExecutionTimeMs?: number;
  successRate: number;
  activeWorkers: number;
}

export interface AuthResponse {
  token: string;
  email: string;
  fullName: string;
  role: string;
  organizationId: string;
  organizationName: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
