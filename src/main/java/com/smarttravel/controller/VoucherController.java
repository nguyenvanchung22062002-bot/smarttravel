package com.smarttravel.controller;

import com.smarttravel.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "Vouchers", description = "Danh sách, validate và đổi điểm lấy voucher")
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    @Autowired
    private VoucherService voucherService;

    /**
     * GET /api/vouchers/available
     * Lấy danh sách voucher để hiển thị trên home.
     * Public + của user nếu đã login.
     */
    @Operation(summary = "Lấy danh sách voucher khả dụng")
    @GetMapping("/available")
    public ResponseEntity<?> getAvailable(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data",    voucherService.getAvailableVouchers(authHeader)
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /**
     * POST /api/vouchers/validate
     * Kiểm tra voucher hợp lệ và tính số tiền được giảm.
     * Gọi từ FE khi user nhập mã trong form đặt tour.
     * Body: { "code": "SUMMER20", "originalPrice": 3500000 }
     */
    @Operation(summary = "Validate voucher và tính giá sau giảm",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String code    = ((String) body.get("code")).trim().toUpperCase();
            BigDecimal price = new BigDecimal(body.get("originalPrice").toString());

            Map<String, Object> result = voucherService.validateVoucher(code, price, authHeader);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /**
     * POST /api/vouchers/redeem
     * Đổi 300 điểm → voucher -10% cá nhân (hết hạn sau 3 tháng, dùng 1 lần).
     */
    @Operation(summary = "Đổi điểm lấy voucher cá nhân",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Map<String, Object> result = voucherService.redeemPoints(authHeader);
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