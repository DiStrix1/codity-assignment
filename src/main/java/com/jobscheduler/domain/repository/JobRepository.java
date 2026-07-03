package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.Job;
import com.jobscheduler.domain.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

  // ─── THE CRITICAL ATOMIC CLAIM QUERY ─────────────────────────────────────
  /**
   * Atomically claims the next eligible job from a specific queue.
   * 
   * Uses SELECT ... FOR UPDATE SKIP LOCKED to guarantee:
   * 1. No two workers ever claim the same job
   * 2. Workers don't block each other (SKIP LOCKED skips already-locked rows)
   * 3. The claim is atomic within a single transaction
   *
   * The subselect finds the highest-priority, oldest eligible job in the queue,
   * locks it, and the outer UPDATE atomically transitions it to CLAIMED status.
   */
  @Modifying
  @Query(value = """
      UPDATE jobs SET
          status = 'CLAIMED',
          claimed_at = NOW(),
          claimed_by_worker = :workerId,
          attempt_count = attempt_count + 1,
          updated_at = NOW()
      WHERE id = (
          SELECT j.id FROM jobs j
          WHERE j.queue_id = :queueId
            AND j.status = 'QUEUED'
            AND (j.scheduled_at IS NULL OR j.scheduled_at <= NOW())
          ORDER BY j.priority DESC, j.created_at ASC
          LIMIT 1
          FOR UPDATE SKIP LOCKED
      )
      """, nativeQuery = true)
  int claimNextJob(@Param("queueId") UUID queueId, @Param("workerId") UUID workerId);

  /**
   * After claimNextJob() succeeds (returns 1), fetch the claimed job.
   */
  @Query("SELECT j FROM Job j WHERE j.claimedByWorker = :workerId AND j.status = 'CLAIMED' ORDER BY j.claimedAt DESC")
  List<Job> findClaimedByWorker(@Param("workerId") UUID workerId);

  // ─── Status Queries ──────────────────────────────────────────────────────

  Page<Job> findByQueueId(UUID queueId, Pageable pageable);

  Page<Job> findByStatus(JobStatus status, Pageable pageable);

  Page<Job> findByQueueIdAndStatus(UUID queueId, JobStatus status, Pageable pageable);

  @Query("SELECT j FROM Job j WHERE j.batchId = :batchId")
  List<Job> findByBatchId(@Param("batchId") UUID batchId);

  // ─── Statistics ──────────────────────────────────────────────────────────

  @Query("SELECT COUNT(j) FROM Job j WHERE j.queue.id = :queueId AND j.status = :status")
  long countByQueueIdAndStatus(@Param("queueId") UUID queueId, @Param("status") JobStatus status);

  @Query("SELECT COUNT(j) FROM Job j WHERE j.queue.id = :queueId AND j.status = 'RUNNING'")
  int countRunningJobsInQueue(@Param("queueId") UUID queueId);

  // ─── Scheduled Job Promotion ─────────────────────────────────────────────

  /**
   * Promotes SCHEDULED jobs that are now due to QUEUED status.
   */
  @Modifying
  @Query(value = """
      UPDATE jobs SET status = 'QUEUED', updated_at = NOW()
      WHERE status = 'SCHEDULED'
        AND scheduled_at IS NOT NULL
        AND scheduled_at <= NOW()
      """, nativeQuery = true)
  int promoteScheduledJobs();

  /**
   * Promotes RETRY_PENDING jobs that have waited their backoff delay to QUEUED
   * status.
   */
  @Modifying
  @Query(value = """
      UPDATE jobs SET status = 'QUEUED', updated_at = NOW()
      WHERE status = 'RETRY_PENDING'
        AND scheduled_at IS NOT NULL
        AND scheduled_at <= NOW()
      """, nativeQuery = true)
  int promoteRetryingJobs();

  // ─── Stale Claim Recovery ────────────────────────────────────────────────

  /**
   * Finds jobs that were CLAIMED but the worker appears to have died
   * (claimed_at older than threshold). These need to be re-queued.
   */
  @Query("SELECT j FROM Job j WHERE j.status = com.jobscheduler.domain.enums.JobStatus.CLAIMED AND j.claimedAt < :threshold")
  List<Job> findStaleClaims(@Param("threshold") Instant threshold);

  /**
   * Re-queue stale claimed jobs so they can be picked up by active workers.
   */
  @Modifying
  @Query(value = """
      UPDATE jobs SET
          status = 'QUEUED',
          claimed_at = NULL,
          claimed_by_worker = NULL,
          updated_at = NOW()
      WHERE status = 'CLAIMED'
        AND claimed_at < :threshold
      """, nativeQuery = true)
  int requeueStaleClaims(@Param("threshold") Instant threshold);

  // ─── Metrics ─────────────────────────────────────────────────────────────

  @Query("SELECT COUNT(j) FROM Job j WHERE j.status = :status")
  long countByStatus(@Param("status") JobStatus status);

  @Query(value = """
      SELECT COUNT(*) FROM jobs
      WHERE status = 'COMPLETED'
        AND completed_at >= :since
      """, nativeQuery = true)
  long countCompletedSince(@Param("since") Instant since);

  @Query(value = """
      SELECT COUNT(*) FROM jobs
      WHERE status = 'FAILED'
        AND updated_at >= :since
      """, nativeQuery = true)
  long countFailedSince(@Param("since") Instant since);

  @Query(value = """
      SELECT COUNT(*) FROM jobs
      WHERE queue_id = :queueId
        AND status = 'COMPLETED'
        AND completed_at >= :since
      """, nativeQuery = true)
  long countCompletedInQueueSince(@Param("queueId") UUID queueId, @Param("since") Instant since);

  @Query(value = """
      SELECT COUNT(*) FROM jobs
      WHERE queue_id = :queueId
        AND status = 'FAILED'
        AND updated_at >= :since
      """, nativeQuery = true)
  long countFailedInQueueSince(@Param("queueId") UUID queueId, @Param("since") Instant since);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
