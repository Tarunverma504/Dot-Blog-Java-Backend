package com.dotblog.auth.service;

import com.dotblog.auth.domain.User;
import com.dotblog.auth.repository.UserRepository;
import com.dotblog.shared.contract.auth.AuthResponse;
import com.dotblog.shared.contract.auth.LoginRequest;
import com.dotblog.shared.contract.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock com.dotblog.auth.repository.ForgotPasswordRepository forgotPasswordRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EncryptionService encryptionService;
    @Mock MongoTemplate mongoTemplate;
    @Mock com.dotblog.auth.messaging.OtpEventPublisher otpEventPublisher;

    @InjectMocks AuthService authService;

    @Test
    @DisplayName("register saves new user and returns verificationToken")
    void register_newUser_returnsVerificationToken() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = new User();
        saved.setId("user-123");
        saved.setEmail("new@example.com");
        saved.setName("NewUser");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-123");
            return u;
        });
        when(jwtService.createToken("user-123")).thenReturn("jwt-token");

        AuthResponse res = authService.register(new RegisterRequest("NewUser", "new@example.com", "secret"));

        assertThat(res.verificationToken()).isEqualTo("jwt-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register with empty username throws 401")
    void register_emptyUsername_throws401() {
        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("", "a@b.com", "pass")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    @DisplayName("login returns auth response with token")
    void login_valid_returnsAuthResponse() {
        User user = new User();
        user.setId("id-1");
        user.setName("Test");
        user.setEmail("test@example.com");
        user.setPassword("hashed");
        user.setProfilePhoto("");
        user.setCoverPhoto("");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtService.createToken("id-1")).thenReturn("auth-token");

        AuthResponse res = authService.login(new LoginRequest("test@example.com", "pass"));

        assertThat(res.name()).isEqualTo("Test");
        assertThat(res.authToken()).isEqualTo("auth-token");
        assertThat(res.userId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("login wrong password throws 401")
    void login_wrongPassword_throws401() {
        User user = new User();
        user.setId("id-1");
        user.setPassword("hashed");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
    }
}
