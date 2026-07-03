package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.RetryPolicyDto;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.Organization;
import com.jobscheduler.domain.entity.RetryPolicy;
import com.jobscheduler.domain.repository.OrganizationRepository;
import com.jobscheduler.domain.repository.RetryPolicyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/retry-policies")
@Tag(name = "Retry Policies", description = "Retry policy management")
public class RetryPolicyController {

    private final RetryPolicyRepository retryPolicyRepository;
    private final OrganizationRepository organizationRepository;

    public RetryPolicyController(RetryPolicyRepository retryPolicyRepository, OrganizationRepository organizationRepository) {
        this.retryPolicyRepository = retryPolicyRepository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping
    @Operation(summary = "Create a retry policy")
    public ResponseEntity<RetryPolicyDto.Response> createPolicy(
            @RequestParam(required = false) UUID organizationId,
            @Valid @RequestBody RetryPolicyDto.CreateRequest request) {
        
        UUID orgId = organizationId != null ? organizationId : UUID.fromString("00000000-0000-0000-0000-000000000001");
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        RetryPolicy policy = RetryPolicy.builder()
                .name(request.getName())
                .strategy(request.getStrategy())
                .maxAttempts(request.getMaxAttempts())
                .initialDelayMs(request.getInitialDelayMs())
                .multiplier(request.getMultiplier())
                .maxDelayMs(request.getMaxDelayMs())
                .organization(org)
                .build();
        
        policy = retryPolicyRepository.save(policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(policy));
    }

    @GetMapping
    @Operation(summary = "List retry policies")
    public ResponseEntity<Page<RetryPolicyDto.Response>> listPolicies(
            @RequestParam(required = false) UUID organizationId,
            @PageableDefault(size = 100) Pageable pageable) {
        if (organizationId != null) {
            List<RetryPolicy> list = retryPolicyRepository.findByOrganizationId(organizationId);
            List<RetryPolicyDto.Response> dtoList = list.stream().map(this::toResponse).toList();
            return ResponseEntity.ok(new PageImpl<>(dtoList, pageable, dtoList.size()));
        }
        Page<RetryPolicy> policies = retryPolicyRepository.findAll(pageable);
        return ResponseEntity.ok(policies.map(this::toResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get retry policy by ID")
    public ResponseEntity<RetryPolicyDto.Response> getPolicy(@PathVariable UUID id) {
        RetryPolicy policy = retryPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", id));
        return ResponseEntity.ok(toResponse(policy));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update retry policy")
    public ResponseEntity<RetryPolicyDto.Response> updatePolicy(
            @PathVariable UUID id,
            @Valid @RequestBody RetryPolicyDto.CreateRequest request) {
        RetryPolicy policy = retryPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", id));

        policy.setName(request.getName());
        policy.setStrategy(request.getStrategy());
        policy.setMaxAttempts(request.getMaxAttempts());
        policy.setInitialDelayMs(request.getInitialDelayMs());
        policy.setMultiplier(request.getMultiplier());
        policy.setMaxDelayMs(request.getMaxDelayMs());

        policy = retryPolicyRepository.save(policy);
        return ResponseEntity.ok(toResponse(policy));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete retry policy")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        RetryPolicy policy = retryPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RetryPolicy", id));
        retryPolicyRepository.delete(policy);
        return ResponseEntity.noContent().build();
    }

    private RetryPolicyDto.Response toResponse(RetryPolicy p) {
        return RetryPolicyDto.Response.builder()
                .id(p.getId())
                .name(p.getName())
                .strategy(p.getStrategy())
                .maxAttempts(p.getMaxAttempts())
                .initialDelayMs(p.getInitialDelayMs())
                .multiplier(p.getMultiplier())
                .maxDelayMs(p.getMaxDelayMs())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
