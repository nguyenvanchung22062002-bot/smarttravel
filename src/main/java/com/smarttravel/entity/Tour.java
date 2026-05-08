package com.smarttravel.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tours")
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "LONGTEXT")
    private String descriptionLong;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Integer durationNights;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer availableSlots;

    private String badge;

    // Đánh giá trung bình
    @Column(precision = 3, scale = 1)
    private BigDecimal avgRating = BigDecimal.valueOf(5.0);

    private Integer totalReviews = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL)
    private List<Booking> bookings;

    @JsonIgnore
    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<TourImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
