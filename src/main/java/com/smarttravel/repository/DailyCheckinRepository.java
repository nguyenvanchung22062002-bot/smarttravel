package com.smarttravel.repository;

import com.smarttravel.entity.DailyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {

    // Tìm check-in của user vào ngày cụ thể
    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate date);

    // Lịch sử check-in gần nhất (cho frontend hiển thị calendar)
    List<DailyCheckin> findByUserIdOrderByCheckinDateDesc(Long userId);
}