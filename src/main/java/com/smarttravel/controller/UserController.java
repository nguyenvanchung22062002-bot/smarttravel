package com.smarttravel.controller;

import com.smarttravel.entity.User;
import com.smarttravel.repository.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Users", description = "Quản lý người dùng (Admin only)")
@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(Map.of("success", true, "data", users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(Map.of("success", true, "data", user)))
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/users/{id} — update fullName + role
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            try {
                if (body.containsKey("fullName") && body.get("fullName") != null) {
                    user.setFullName(body.get("fullName").trim());
                }
                if (body.containsKey("role") && body.get("role") != null) {
                    user.setRole(User.Role.valueOf(body.get("role").trim()));
                }
                if (body.containsKey("phone")) {
                    user.setPhone(body.get("phone"));
                }
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("success", true,
                        "message", "Cập nhật user thành công!",
                        "data", user));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Role không hợp lệ!"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa user!"));
    }
}