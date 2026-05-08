package com.smarttravel.repository;

import com.smarttravel.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Lịch sử booking của 1 user
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Tìm theo mã đơn
    Optional<Booking> findByBookingCode(String bookingCode);

    // Đếm số booking của 1 tour (admin)
    long countByTourId(Long tourId);
}
