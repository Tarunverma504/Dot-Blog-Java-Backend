package com.dotblog.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v2/register returns verificationToken")
    void register_returnsVerificationToken() throws Exception {
        mockMvc.perform(post("/api/v2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"TestUser","email":"test@example.com","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").isString())
                .andExpect(jsonPath("$.verificationToken").value(not(emptyOrNullString())));
    }

    @Test
    @DisplayName("POST /api/v2/register with empty fields returns 401")
    void register_emptyFields_returns401() throws Exception {
        mockMvc.perform(post("/api/v2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","email":"","password":""}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please fill all the details"));
    }

    @Test
    @DisplayName("POST /api/v2/login returns authToken and user info")
    void login_returnsAuthTokenAndUser() throws Exception {
        // Register first
        mockMvc.perform(post("/api/v2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"LoginTest","email":"login@example.com","password":"pass456"}
                                """))
                .andExpect(status().isOk());

        // Then login
        mockMvc.perform(post("/api/v2/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"pass456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authToken").isString())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.name").value("LoginTest"));
    }

    @Test
    @DisplayName("POST /api/v2/login with wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"WrongPass","email":"wrong@example.com","password":"correct"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrong@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Email or Password"));
    }

    @Test
    @DisplayName("GET /api/v2/isAuthenticated with valid token returns user")
    void isAuthenticated_validToken_returnsUser() throws Exception {
        // Register and login to get token
        mockMvc.perform(post("/api/v2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"AuthUser","email":"auth@example.com","password":"secret"}
                                """))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/v2/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"auth@example.com","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(loginResponse, "$.authToken");

        mockMvc.perform(get("/api/v2/isAuthenticated")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AuthUser"))
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.authToken").value(token));
    }

    @Test
    @DisplayName("GET /api/v2/isAuthenticated without token returns 401")
    void isAuthenticated_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v2/isAuthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No User Loggged"));
    }
}
