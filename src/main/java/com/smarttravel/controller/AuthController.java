package com.smarttravel.controller;

import com.smarttravel.entity.User;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Authentication", description = "Đăng ký, đăng nhập và thông tin tài khoản")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthenticationManager authManager;
    @Autowired private JwtUtils jwtUtils;

    @Data
    static class RegisterRequest {
        @NotBlank(message = "Họ tên không được để trống")
        String fullName;

        @Email(message = "Email không hợp lệ")
        @NotBlank
        String email;

        @Size(min = 6, message = "Mật khẩu phải ít nhất 6 ký tự")
        String password;

        String phone;
    }

    @Data
    static class LoginRequest {
        @NotBlank @Email
        String email;
        @NotBlank
        String password;
    }

    @Operation(
        summary = "Đăng ký tài khoản mới",
        description = "Tạo tài khoản USER mới. Email phải chưa tồn tại trong hệ thống.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đăng ký thành công"),
            @ApiResponse(responseCode = "400", description = "Email đã tồn tại hoặc dữ liệu không hợp lệ")
        }
    )
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email đã được đăng ký!"));
        }

        User user = new User();
        user.setFullName(req.fullName);
        user.setEmail(req.email);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setPhone(req.phone);
        user.setRole(User.Role.USER);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng ký thành công! Vui lòng đăng nhập."
        ));
    }

    @Operation(
        summary = "Đăng nhập",
        description = "Trả về JWT Bearer token. Sử dụng token này cho tất cả các request cần xác thực.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về JWT token"),
            @ApiResponse(responseCode = "400", description = "Email hoặc mật khẩu không đúng")
        }
    )
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email, req.password));
        } catch (BadCredentialsException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Email hoặc mật khẩu không đúng!"));
        }

        User user = userRepository.findByEmail(req.email).orElseThrow();
        String token = jwtUtils.generateToken(user.getEmail());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng nhập thành công!",
                "data", Map.of(
                        "token", token,
                        "type", "Bearer",
                        "id", user.getId(),
                        "fullName", user.getFullName(),
                        "email", user.getEmail(),
                        "role", user.getRole().name()
                )
        ));
    }

    @Operation(
        summary = "Lấy thông tin tài khoản hiện tại",
        security = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @ApiResponse(responseCode = "200", description = "Thông tin user"),
            @ApiResponse(responseCode = "401", description = "Token không hợp lệ hoặc hết hạn")
        }
    )
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String email = jwtUtils.getEmailFromToken(token);
        User user = userRepository.findByEmail(email).orElseThrow();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "id",        user.getId(),
                        "fullName",  user.getFullName(),
                        "email",     user.getEmail(),
                        "phone",     user.getPhone() != null ? user.getPhone() : "",
                        "address",   user.getAddress() != null ? user.getAddress() : "",
                        "role",      user.getRole().name(),
                        "createdAt", user.getCreatedAt()
                )
        ));
    }
}
