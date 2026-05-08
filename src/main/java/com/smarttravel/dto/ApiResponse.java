package com.smarttravel.dto;

import com.smarttravel.entity.Booking;
import com.smarttravel.entity.User;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class RegisterRequest {
    @NotBlank(message = "Họ tên không được để trống")
    public String fullName;

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    public String email;

    @Size(min = 6, message = "Mật khẩu phải ít nhất 6 ký tự")
    public String password;

    public String phone;
}

class LoginRequest {
    @Email
    @NotBlank
    public String email;

    @NotBlank
    public String password;
}

class AuthResponse {
    public String token;       // JWT token
    public String type = "Bearer";
    public Long id;
    public String fullName;
    public String email;
    public String role;

    public AuthResponse(String token, Long id, String fullName, String email, String role) {
        this.token = token;
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }
}

class TourResponse {
    public Long id;
    public String name;
    public String description;
    public String destination;
    public Integer durationDays;
    public Integer durationNights;
    public BigDecimal price;
    public Integer availableSlots;
    public String badge;
    public BigDecimal avgRating;
    public Integer totalReviews;
}

class TourRequest {
    @NotBlank
    public String name;

    public String description;

    @NotBlank
    public String destination;

    @Min(1)
    public Integer durationDays;

    @Min(0)
    public Integer durationNights;

    @DecimalMin("0.0")
    public BigDecimal price;

    @Min(0)
    public Integer availableSlots;

    public String badge;
}

class BookingRequest {
    @NotNull
    public Long tourId;

    @NotNull
    @Future(message = "Ngày khởi hành phải trong tương lai")
    public LocalDate departureDate;

    @Min(value = 1, message = "Phải có ít nhất 1 người lớn")
    public Integer numAdults;

    @Min(0)
    public Integer numChildren = 0;

    public String pickupLocation;
    public String note;
    public String paymentMethod; // "VNPAY", "MOMO", "BANK_TRANSFER", "CASH"
}

/** Trả về thông tin booking */
class BookingResponse {
    public Long id;
    public String bookingCode;
    public String tourName;
    public String destination;
    public LocalDate departureDate;
    public Integer numAdults;
    public Integer numChildren;
    public BigDecimal totalPrice;
    public String status;
    public String paymentMethod;
    public LocalDateTime createdAt;
}


class ReviewRequest {
    @NotNull
    public Long tourId;

    @Min(1) @Max(5)
    public Integer rating;

    @NotBlank
    @Size(max = 200)
    public String title;

    @NotBlank
    @Size(min = 20, max = 2000)
    public String body;
}

/** Trả về thông tin review */
class ReviewResponse {
    public Long id;
    public String userName;
    public Integer rating;
    public String title;
    public String body;
    public Integer helpfulCount;
    public LocalDateTime createdAt;
    public Boolean verified; // Đã đặt tour này chưa
}


@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }
}
