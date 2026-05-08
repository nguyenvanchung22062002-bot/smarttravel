package com.smarttravel.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã voucher — unique, UPPERCASE
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    // Loại: PERCENTAGE (%) hoặc FIXED (VND)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    // Giá trị giảm (VD: 10 = 10%, hoặc 50000 = 50.000đ)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    // Giảm tối đa bao nhiêu VND (null = không giới hạn)
    @Column(precision = 15, scale = 0)
    private BigDecimal maxDiscountAmount;

    // Ngày hết hạn
    @Column(nullable = false)
    private LocalDate expiryDate;

    // Gắn với user cụ thể (null = public — ai cũng dùng được)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    // Số lần tối đa được sử dụng (null = unlimited)
    private Integer maxUsage;

    // Đã sử dụng bao nhiêu lần
    @Column(nullable = false)
    private Integer usedCount = 0;

    // Nguồn gốc: ADMIN_CREATED / POINTS_REDEEM / SYSTEM
    @Column(length = 20)
    private String source = "ADMIN_CREATED";

    // Trạng thái: ACTIVE / USED / EXPIRED / DISABLED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.usedCount == null)  this.usedCount = 0;
        if (this.status    == null)  this.status    = VoucherStatus.ACTIVE;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Enums ──────────────────────────────────────────────────────────────
    public enum DiscountType  { PERCENTAGE, FIXED }
    public enum VoucherStatus { ACTIVE, USED, EXPIRED, DISABLED }

    // ── Helper: kiểm tra voucher còn dùng được không ──────────────────────
    public boolean isUsable() {
        if (status != VoucherStatus.ACTIVE)              return false;
        if (expiryDate.isBefore(LocalDate.now()))        return false;
        if (maxUsage != null && usedCount >= maxUsage)   return false;
        return true;
    }

    // ── Helper: tính số tiền được giảm ───────────────────────────────────
    public BigDecimal calcDiscount(BigDecimal originalPrice) {
        BigDecimal discount;
        if (discountType == DiscountType.PERCENTAGE) {
            // VD: discountValue = 10 → giảm 10%
            discount = originalPrice.multiply(discountValue).divide(BigDecimal.valueOf(100));
        } else {
            // FIXED: giảm đúng số tiền
            discount = discountValue;
        }
        // Không giảm quá maxDiscountAmount
        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            discount = maxDiscountAmount;
        }
        // Không giảm quá giá gốc
        if (discount.compareTo(originalPrice) > 0) {
            discount = originalPrice;
        }
        return discount;
    }
}