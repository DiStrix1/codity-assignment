package com.jobscheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.api.dto.AuthDto;
import com.jobscheduler.domain.entity.Organization;
import com.jobscheduler.domain.repository.OrganizationRepository;
import com.jobscheduler.domain.repository.UserRepository;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("api")
class AuthControllerTest {

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
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository orgRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        orgRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElseGet(() -> orgRepository.save(Organization.builder()
                        .name("Default Organization")
                        .slug("default-org")
                        .build()));
    }

    @Test
    @DisplayName("Successfully register a new user and login")
    void testRegisterAndLogin() throws Exception {
        String email = "testuser@example.com";
        String password = "securepassword123";
        String fullName = "Test User";

        // 1. Register Request
        AuthDto.RegisterRequest regRequest = AuthDto.RegisterRequest.builder()
                .email(email)
                .password(password)
                .fullName(fullName)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value(fullName));

        // 2. Login Request
        AuthDto.LoginRequest loginRequest = AuthDto.LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("Register user with invalid email returns 400 Bad Request")
    void testRegisterInvalidEmail() throws Exception {
        AuthDto.RegisterRequest regRequest = AuthDto.RegisterRequest.builder()
                .email("invalid-email")
                .password("short")
                .fullName("Test")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
