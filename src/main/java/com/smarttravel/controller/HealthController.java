package com.smarttravel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "message", "SmartTravel Backend đang chạy!",
                "version", "1.0.0",
                "endpoints", Map.of(
                        "auth",     "/api/auth/register, /api/auth/login",
                        "tours",    "/api/tours",
                        "bookings", "/api/bookings",
                        "reviews",  "/api/reviews/tour/{id}"
                )
        ));
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message != null ? message : "Không hợp lệ");
        });
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "Dữ liệu không hợp lệ");
        body.put("errors",  errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "Bạn không có quyền thực hiện thao tác này!");
        return ResponseEntity.status(403).body(body);
    }
}