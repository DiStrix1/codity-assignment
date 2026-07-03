package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByOrganizationId(UUID organizationId, Pageable pageable);
    Optional<Project> findByOrganizationIdAndSlug(UUID organizationId, String slug);
    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);
}
