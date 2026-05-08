package com.smarttravel.controller;

import com.smarttravel.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Check-in & Points", description = "Điểm thưởng và check-in hàng ngày")
@RestController
@RequestMapping("/api/checkin")
@SecurityRequirement(name = "bearerAuth")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    /**
     * GET /api/checkin/me
     * Lấy trạng thái điểm, streak, lịch sử check-in của user hiện tại.
     * Gọi khi load trang home để hiển thị lên Promotion Center.
     */
    @Operation(summary = "Lấy thông tin điểm và streak hiện tại")
    @GetMapping("/me")
    public ResponseEntity<?> getMyPoints(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> data = checkinService.getMyPoints(authHeader);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /**
     * POST /api/checkin
     * Thực hiện check-in hôm nay.
     * Idempotent: gọi 2 lần cùng ngày trả về alreadyChecked=true, không ghi trùng.
     */
    @Operation(summary = "Thực hiện check-in hàng ngày")
    @PostMapping
    public ResponseEntity<?> doCheckin(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> result = checkinService.doCheckin(authHeader);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }
}