package com.smarttravel.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "bookings", "reviews", "password"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "images", "reviews"})
    private Tour tour;

    @Column(nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private Integer numAdults;

    @Column(nullable = false)
    private Integer numChildren = 0;

    private String pickupLocation;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(length = 20)
    private String paymentGateway;

    @Column(length = 50)
    private String paymentTxnRef;

    @Column(length = 100)
    private String paymentProviderTxnId;

    @Column(length = 30)
    private String paymentResponseCode;

    @Column(length = 255)
    private String paymentMessage;

    private LocalDateTime paidAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING,
        PAID,
        FAILED,
        CONFIRMED,
        CANCELLED,
        COMPLETED
    }

    public enum PaymentMethod {
        VNPAY, MOMO, BANK_TRANSFER, CASH, BALANCE
    }
}