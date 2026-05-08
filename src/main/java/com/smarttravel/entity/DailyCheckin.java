package com.smarttravel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "daily_checkins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_checkin_date",
                columnNames = {"user_id", "checkin_date"}
        ))
public class DailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(nullable = false)
    private Integer streakAtCheckin = 1;

    @Column(nullable = false)
    private Integer pointsAwarded = 0;

    @Column(length = 30)
    private String bonusType = "DAILY";

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.streakAtCheckin == null) this.streakAtCheckin = 1;
        if (this.pointsAwarded   == null) this.pointsAwarded   = 0;
        if (this.bonusType       == null) this.bonusType       = "DAILY";
    }
}