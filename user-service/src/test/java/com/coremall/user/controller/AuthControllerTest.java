package com.coremall.user.controller;

import com.coremall.user.config.SecurityConfig;
import com.coremall.user.dto.LoginRequest;
import com.coremall.user.dto.LoginResponse;
import com.coremall.user.dto.RegisterRequest;
import com.coremall.user.dto.RegisterResponse;
import com.coremall.user.exception.DuplicateEmailException;
import com.coremall.user.exception.InvalidCredentialsException;
import com.coremall.user.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController - POST /api/v1/auth")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /register 成功回傳 201 與 userId")
    void shouldReturn201WhenRegisterSucceeds() throws Exception {
        RegisterRequest request = new RegisterRequest("user@test.com", "password123");
        when(authService.register(any())).thenReturn(new RegisterResponse("uuid-123", "user@test.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("uuid-123"))
                .andExpect(jsonPath("$.data.email").value("user@test.com"));
    }

    @Test
    @DisplayName("POST /register email 重複回傳 409")
    void shouldReturn409WhenEmailDuplicate() throws Exception {
        RegisterRequest request = new RegisterRequest("dup@test.com", "password123");
        when(authService.register(any())).thenThrow(new DuplicateEmailException("dup@test.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATE"));
    }

    @Test
    @DisplayName("POST /register 參數驗證失敗回傳 400")
    void shouldReturn400WhenValidationFails() throws Exception {
        RegisterRequest invalid = new RegisterRequest("not-an-email", "pw");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /login 成功回傳 200 與 token")
    void shouldReturn200WhenLoginSucceeds() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(authService.login(any())).thenReturn(new LoginResponse("jwt.token.here", "uuid-123"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.userId").value("uuid-123"));
    }

    @Test
    @DisplayName("POST /login 帳密錯誤回傳 401")
    void shouldReturn401WhenCredentialsInvalid() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "wrongpass");
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
