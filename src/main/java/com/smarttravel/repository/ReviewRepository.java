package com.smarttravel.repository;

import com.smarttravel.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Lấy tất cả review của 1 tour
    List<Review> findByTourIdOrderByCreatedAtDesc(Long tourId);

    // Lọc review theo số sao
    List<Review> findByTourIdAndRating(Long tourId, Integer rating);

    // Tính điểm đánh giá trung bình của 1 tour
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.tour.id = :tourId")
    Double getAvgRatingByTourId(@Param("tourId") Long tourId);

    // Đếm tổng số review của 1 tour
    long countByTourId(Long tourId);
}
