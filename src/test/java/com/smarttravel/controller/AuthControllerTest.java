package com.smarttravel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttravel.entity.User;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController — Web Layer Tests")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserRepository userRepository;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean AuthenticationManager authManager;
    @MockBean JwtUtils jwtUtils;

    // ──────────────────────────────────────────────────
    // POST /api/auth/register
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("register: email mới → 200 OK, success=true")
    void register_newEmail_returns200() throws Exception {
        given(userRepository.existsByEmail("new@test.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hashed");
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Nguyễn Văn A",
                        "email", "new@test.com",
                        "password", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("register: email đã tồn tại → 400 Bad Request, success=false")
    void register_duplicateEmail_returns400() throws Exception {
        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Test",
                        "email", "dup@test.com",
                        "password", "secret123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email đã được đăng ký!"));
    }

    @Test
    @DisplayName("register: mật khẩu < 6 ký tự → 400, validation error")
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Test",
                        "email", "test@test.com",
                        "password", "123"))))   // quá ngắn
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────
    // POST /api/auth/login
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("login: thông tin đúng → 200, trả về token và role")
    void login_validCredentials_returnsToken() throws Exception {
        User user = buildUser();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(jwtUtils.generateToken("user@test.com")).willReturn("jwt.mock.token");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@test.com",
                        "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt.mock.token"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.email").value("user@test.com"));
    }

    @Test
    @DisplayName("login: sai mật khẩu → 400, success=false")
    void login_wrongPassword_returns400() throws Exception {
        willThrow(new BadCredentialsException("bad credentials"))
                .given(authManager).authenticate(any());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@test.com",
                        "password", "wrongpass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("login: thiếu email → 400 validation error")
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────

    private User buildUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@test.com");
        u.setFullName("Test User");
        u.setPassword("hashed");
        u.setRole(User.Role.USER);
        return u;
    }
}
