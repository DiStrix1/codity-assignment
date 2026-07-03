package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.RetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RetryPolicyRepository extends JpaRepository<RetryPolicy, UUID> {
    List<RetryPolicy> findByOrganizationId(UUID organizationId);
}
