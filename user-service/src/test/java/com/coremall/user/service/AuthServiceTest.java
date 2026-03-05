package com.coremall.user.service;

import com.coremall.sharedkernel.jwt.JwtTokenProvider;
import com.coremall.user.domain.User;
import com.coremall.user.dto.LoginRequest;
import com.coremall.user.dto.LoginResponse;
import com.coremall.user.dto.RegisterRequest;
import com.coremall.user.dto.RegisterResponse;
import com.coremall.user.exception.DuplicateEmailException;
import com.coremall.user.exception.InvalidCredentialsException;
import com.coremall.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - 註冊與登入")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("新 email 註冊成功，回傳 userId 與 email")
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest("new@test.com", "password123");
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        UUID savedId = UUID.randomUUID();
        User savedUser = User.of("new@test.com", "hashed");
        savedUser.setId(savedId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(savedId.toString());
        assertThat(response.email()).isEqualTo("new@test.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("重複 email 註冊時拋出 DuplicateEmailException（409）")
    void shouldThrowDuplicateEmailExceptionWhenEmailExists() {
        RegisterRequest request = new RegisterRequest("dup@test.com", "password123");
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("正確帳密登入成功，回傳 JWT token")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        UUID userId = UUID.randomUUID();
        User user = User.of("user@test.com", "hashed");
        user.setId(userId);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(userId.toString())).thenReturn("jwt.token.here");

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.here");
        assertThat(response.userId()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("密碼錯誤時拋出 InvalidCredentialsException（401）")
    void shouldThrowInvalidCredentialsWhenPasswordWrong() {
        LoginRequest request = new LoginRequest("user@test.com", "wrongpass");
        User user = User.of("user@test.com", "hashed");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("使用者不存在時拋出 InvalidCredentialsException（防止 user enumeration）")
    void shouldThrowInvalidCredentialsWhenUserNotFound() {
        LoginRequest request = new LoginRequest("ghost@test.com", "password");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
