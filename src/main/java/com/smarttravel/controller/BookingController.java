package com.smarttravel.controller;

import com.smarttravel.entity.Booking;
import com.smarttravel.repository.BookingRepository;
import com.smarttravel.service.BookingService;
import com.smarttravel.service.BookingServiceImpl;
import com.smarttravel.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Bookings", description = "Đặt tour, lịch sử đơn, hủy đơn và xử lý thanh toán")
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired private BookingService    bookingService;
    @Autowired private BookingRepository bookingRepository;

    @Data
    static class BookingRequest {
        @NotNull(message = "Vui lòng chọn tour")
        Long tourId;

        @NotNull(message = "Vui lòng chọn ngày khởi hành")
        String departureDate;

        @Min(value = 1, message = "Phải có ít nhất 1 người lớn")
        int numAdults = 1;

        @Min(0)
        int numChildren = 0;

        String pickupLocation;
        String note;
        String paymentMethod;
        String voucherCode;
    }

    @Operation(summary = "Tạo đơn đặt tour",
            description = "Tạo booking mới. Hỗ trợ voucherCode để giảm giá thật. Nếu chọn VNPay/MoMo, paymentUrl được trả về.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Đặt tour thành công"),
                    @ApiResponse(responseCode = "400", description = "Tour hết chỗ, voucher không hợp lệ, hoặc số dư không đủ")
            })
    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest req,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
        try {
            BookingService.BookingCreationResult result =
                    bookingService.createBooking(
                            authHeader,
                            req.tourId,
                            req.departureDate,
                            req.numAdults,
                            req.numChildren,
                            req.pickupLocation,
                            req.note,
                            req.paymentMethod,
                            extractClientIp(forwardedFor),
                            req.voucherCode
                    );

            Map<String, Object> data = new HashMap<>();
            data.put("bookingCode", result.bookingCode());
            data.put("tourName",    result.tourName());
            data.put("totalPrice",  result.totalPrice());
            data.put("status",      result.status());
            data.put("paymentUrl",  result.paymentUrl());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Đặt tour thành công!");
            response.put("data",    data);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", ex.getMessage() != null ? ex.getMessage() : "Dữ liệu không hợp lệ");
            return ResponseEntity.badRequest().body(err);
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống");
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @Operation(summary = "Lấy lịch sử đặt tour của tôi",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<?> getMyBookings(
            @RequestHeader("Authorization") String authHeader) {
        List<Map<String, Object>> result = bookingService.getMyBookings(authHeader);
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    @Operation(summary = "Hủy đơn đặt tour",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            bookingService.cancelBooking(id, authHeader);
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã hủy đơn thành công!"));
        } catch (SecurityException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @Operation(summary = "Xem tất cả đơn (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllBookings() {
        List<Booking> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(Map.of("success", true, "data", bookings, "total", bookings.size()));
    }

    @Operation(summary = "Cập nhật trạng thái đơn (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        bookingService.updateStatusByAdmin(id, body.get("status"));
        return ResponseEntity.ok(Map.of("success", true, "message", "Cập nhật trạng thái thành công!"));
    }

    @Operation(summary = "VNPay IPN Callback")
    @GetMapping("/ipn/vnpay")
    public ResponseEntity<?> vnPayIpn(@RequestParam Map<String, String> params) {
        PaymentService.PaymentResult result = bookingService.handleVnPayIpn(params);
        Map<String, Object> resp = new HashMap<>();
        resp.put("RspCode", result.success() ? "00" : "99");
        resp.put("Message", result.message() != null ? result.message() : "");
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "MoMo IPN Callback")
    @PostMapping("/ipn/momo")
    public ResponseEntity<?> momoIpn(@RequestBody Map<String, Object> payload) {
        PaymentService.PaymentResult result = bookingService.handleMoMoIpn(payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success",     result.success());
        resp.put("bookingCode", result.bookingCode()  != null ? result.bookingCode()  : "");
        resp.put("message",     result.message()      != null ? result.message()      : "");
        resp.put("resultCode",  result.responseCode() != null ? result.responseCode() : "");
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "MoMo Return Callback")
    @GetMapping("/payment/momo/return")
    public ResponseEntity<?> momoReturn(@RequestParam Map<String, String> params) {
        String resultCode = params.getOrDefault("resultCode", "99");
        String orderId    = params.get("orderId");
        boolean success   = "0".equals(resultCode);

        if (orderId != null) {
            bookingRepository.findByBookingCode(orderId).ifPresent(b -> {
                bookingService.updateStatusByAdmin(b.getId(), success ? "PAID" : "FAILED");
            });
        }

        String redirect = success
                ? "/?payment=success&code=" + orderId
                : "/?payment=failed&code="  + orderId;
        return ResponseEntity.status(302).header("Location", redirect).build();
    }

    private String extractClientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) return "127.0.0.1";
        return forwardedFor.split(",")[0].trim();
    }
}