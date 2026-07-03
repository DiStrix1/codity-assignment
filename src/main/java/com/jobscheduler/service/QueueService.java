package com.jobscheduler.service;

import com.jobscheduler.api.dto.QueueDto;
import com.jobscheduler.api.exception.BadRequestException;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.Project;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.entity.RetryPolicy;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QueueService {

    private final QueueRepository queueRepository;
    private final ProjectRepository projectRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final JobRepository jobRepository;
    private final DeadLetterRepository deadLetterRepository;

    public QueueService(QueueRepository queueRepository,
                        ProjectRepository projectRepository,
                        RetryPolicyRepository retryPolicyRepository,
                        JobRepository jobRepository,
                        DeadLetterRepository deadLetterRepository) {
        this.queueRepository = queueRepository;
        this.projectRepository = projectRepository;
        this.retryPolicyRepository = retryPolicyRepository;
        this.jobRepository = jobRepository;
        this.deadLetterRepository = deadLetterRepository;
    }

    @Transactional
    public QueueDto.Response createQueue(QueueDto.CreateRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));

        String slug = request.getSlug() != null ? request.getSlug() : slugify(request.getName());

        if (queueRepository.existsByProjectIdAndSlug(project.getId(), slug)) {
            throw new BadRequestException("Queue with slug '" + slug + "' already exists in this project");
        }

        RetryPolicy retryPolicy = null;
        if (request.getRetryPolicyId() != null) {
            retryPolicy = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", request.getRetryPolicyId()));
        }

        Queue queue = Queue.builder()
                .name(request.getName())
                .slug(slug)
                .project(project)
                .priority(request.getPriority())
                .maxConcurrency(request.getMaxConcurrency() > 0 ? request.getMaxConcurrency() : 5)
                .retryPolicy(retryPolicy)
                .paused(false)
                .build();
        queue = queueRepository.save(queue);
        return toResponse(queue);
    }

    @Transactional(readOnly = true)
    public Page<QueueDto.Response> listQueues(UUID projectId, Pageable pageable) {
        Page<Queue> queues = (projectId != null)
                ? queueRepository.findByProjectId(projectId, pageable)
                : queueRepository.findAll(pageable);
        return queues.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public QueueDto.Response getQueue(UUID queueId) {
        Queue queue = findQueueOrThrow(queueId);
        return toResponse(queue);
    }

    @Transactional
    public QueueDto.Response updateQueue(UUID queueId, QueueDto.UpdateRequest request) {
        Queue queue = findQueueOrThrow(queueId);
        if (request.getName() != null) queue.setName(request.getName());
        if (request.getPriority() != null) queue.setPriority(request.getPriority());
        if (request.getMaxConcurrency() != null) queue.setMaxConcurrency(request.getMaxConcurrency());
        if (request.getRetryPolicyId() != null) {
            RetryPolicy rp = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", request.getRetryPolicyId()));
            queue.setRetryPolicy(rp);
        }
        queue = queueRepository.save(queue);
        return toResponse(queue);
    }

    @Transactional
    public void deleteQueue(UUID queueId) {
        Queue queue = findQueueOrThrow(queueId);
        queueRepository.delete(queue);
    }

    @Transactional
    public QueueDto.Response pauseQueue(UUID queueId) {
        Queue queue = findQueueOrThrow(queueId);
        queue.setPaused(true);
        return toResponse(queueRepository.save(queue));
    }

    @Transactional
    public QueueDto.Response resumeQueue(UUID queueId) {
        Queue queue = findQueueOrThrow(queueId);
        queue.setPaused(false);
        return toResponse(queueRepository.save(queue));
    }

    @Transactional(readOnly = true)
    public QueueDto.StatsResponse getQueueStats(UUID queueId) {
        Queue queue = findQueueOrThrow(queueId);
        return QueueDto.StatsResponse.builder()
                .queueId(queue.getId())
                .queueName(queue.getName())
                .pending(jobRepository.countByQueueIdAndStatus(queueId, JobStatus.QUEUED))
                .running(jobRepository.countByQueueIdAndStatus(queueId, JobStatus.RUNNING))
                .completed(jobRepository.countByQueueIdAndStatus(queueId, JobStatus.COMPLETED))
                .failed(jobRepository.countByQueueIdAndStatus(queueId, JobStatus.FAILED))
                .deadLetter(deadLetterRepository.countByQueueId(queueId))
                .scheduled(jobRepository.countByQueueIdAndStatus(queueId, JobStatus.SCHEDULED))
                .paused(queue.isPaused())
                .build();
    }

    private Queue findQueueOrThrow(UUID queueId) {
        return queueRepository.findById(queueId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue", queueId));
    }

    private QueueDto.Response toResponse(Queue queue) {
        return QueueDto.Response.builder()
                .id(queue.getId())
                .name(queue.getName())
                .slug(queue.getSlug())
                .projectId(queue.getProject().getId())
                .projectName(queue.getProject().getName())
                .priority(queue.getPriority())
                .maxConcurrency(queue.getMaxConcurrency())
                .retryPolicyId(queue.getRetryPolicy() != null ? queue.getRetryPolicy().getId() : null)
                .retryPolicyName(queue.getRetryPolicy() != null ? queue.getRetryPolicy().getName() : null)
                .paused(queue.isPaused())
                .createdAt(queue.getCreatedAt())
                .updatedAt(queue.getUpdatedAt())
                .build();
    }

    private String slugify(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
