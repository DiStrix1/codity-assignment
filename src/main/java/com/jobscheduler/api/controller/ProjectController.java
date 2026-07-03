package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.ProjectDto;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.Organization;
import com.jobscheduler.domain.entity.Project;
import com.jobscheduler.domain.repository.OrganizationRepository;
import com.jobscheduler.domain.repository.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Project management")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;

    public ProjectController(ProjectRepository projectRepository, OrganizationRepository organizationRepository) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping
    @Operation(summary = "Create a project")
    public ResponseEntity<ProjectDto.Response> createProject(
            @RequestParam UUID organizationId,
            @Valid @RequestBody ProjectDto.CreateRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

        String slug = request.getSlug() != null ? request.getSlug()
                : request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

        Project project = Project.builder()
                .name(request.getName())
                .slug(slug)
                .organization(org)
                .build();
        project = projectRepository.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(project));
    }

    @GetMapping
    @Operation(summary = "List projects")
    public ResponseEntity<Page<ProjectDto.Response>> listProjects(
            @RequestParam(required = false) UUID organizationId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Project> projects = (organizationId != null)
                ? projectRepository.findByOrganizationId(organizationId, pageable)
                : projectRepository.findAll(pageable);
        return ResponseEntity.ok(projects.map(this::toResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ProjectDto.Response> getProject(@PathVariable UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        return ResponseEntity.ok(toResponse(project));
    }

    private ProjectDto.Response toResponse(Project p) {
        return ProjectDto.Response.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .organizationId(p.getOrganization().getId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
