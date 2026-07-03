package com.jobscheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.api.dto.JobDto;
import com.jobscheduler.domain.entity.Organization;
import com.jobscheduler.domain.entity.Project;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.JobType;
import com.jobscheduler.domain.repository.JobRepository;
import com.jobscheduler.domain.repository.OrganizationRepository;
import com.jobscheduler.domain.repository.ProjectRepository;
import com.jobscheduler.domain.repository.QueueRepository;
import com.jobscheduler.domain.repository.UserRepository;
import com.jobscheduler.domain.entity.User;
import com.jobscheduler.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("api")
class JobControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobscheduler_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private OrganizationRepository orgRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private Queue testQueue;
    private String jwtToken;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        jobRepository.deleteAll();
        queueRepository.deleteAll();
        projectRepository.deleteAll();

        Organization org = orgRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElseGet(() -> orgRepository.save(Organization.builder()
                        .name("Test Org")
                        .slug("test-org-jobs-" + UUID.randomUUID().toString().substring(0, 8))
                        .build()));

        userRepository.save(User.builder()
                .email("admin@jobscheduler.com")
                .passwordHash("password")
                .fullName("Admin User")
                .role("ADMIN")
                .organization(org)
                .build());

        Project project = projectRepository.save(Project.builder()
                .name("Job Test Project")
                .slug("job-test-" + UUID.randomUUID().toString().substring(0, 8))
                .organization(org)
                .build());

        testQueue = queueRepository.save(Queue.builder()
                .name("Job Test Queue")
                .slug("job-queue-" + UUID.randomUUID().toString().substring(0, 8))
                .project(project)
                .priority(1)
                .maxConcurrency(5)
                .paused(false)
                .build());

        // Generate valid JWT token for api calls
        jwtToken = "Bearer " + tokenProvider.generateToken("admin@jobscheduler.com");
    }

    @Test
    @DisplayName("Create immediate job, then fetch it")
    void testCreateAndGetJob() throws Exception {
        JobDto.CreateRequest request = JobDto.CreateRequest.builder()
                .queueId(testQueue.getId())
                .type("IMMEDIATE")
                .payload(Map.of("action", "test"))
                .build();

        // 1. Create Job
        String responseContent = mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.queueId").value(testQueue.getId().toString()))
                .andExpect(jsonPath("$.status").value(JobStatus.QUEUED.name()))
                .andExpect(jsonPath("$.type").value(JobType.IMMEDIATE.name()))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> responseMap = objectMapper.readValue(responseContent, Map.class);
        String jobId = (String) responseMap.get("id");

        // 2. Fetch Job Details
        mockMvc.perform(get("/api/v1/jobs/" + jobId)
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.job.status").value(JobStatus.QUEUED.name()));
    }

    @Test
    @DisplayName("Unauthorized request is rejected")
    void testUnauthorizedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isForbidden());
    }
}
