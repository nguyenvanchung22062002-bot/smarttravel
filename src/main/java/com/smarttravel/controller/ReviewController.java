package com.smarttravel.controller;

import com.smarttravel.entity.Review;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smarttravel.entity.Tour;
import com.smarttravel.entity.User;
import com.smarttravel.repository.BookingRepository;
import com.smarttravel.repository.ReviewRepository;
import com.smarttravel.repository.TourRepository;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Tag(name = "Reviews", description = "Gửi và xem đánh giá tour")
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired private ReviewRepository  reviewRepository;
    @Autowired private TourRepository    tourRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JwtUtils          jwtUtils;

    @Data
    static class ReviewRequest {
        @NotNull(message = "Vui lòng chọn tour")
        Long tourId;

        @NotNull @Min(1) @Max(5)
        Integer rating;

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200)
        String title;

        @NotBlank(message = "Vui lòng nhập nội dung")
        @Size(min = 20, message = "Nội dung phải ít nhất 20 ký tự")
        String body;
    }

    @GetMapping("/tour/{tourId}")
    public ResponseEntity<?> getReviewsByTour(
            @PathVariable Long tourId,
            @RequestParam(required = false) Integer rating) {

        List<Review> reviews;
        if (rating != null) {
            reviews = reviewRepository.findByTourIdAndRating(tourId, rating);
        } else {
            reviews = reviewRepository.findByTourIdOrderByCreatedAtDesc(tourId);
        }

        // Tính thống kê
        Double avg   = reviewRepository.getAvgRatingByTourId(tourId);
        long   total = reviewRepository.countByTourId(tourId);
        long[] counts = new long[6]; // index 1-5
        for (Review r : reviewRepository.findByTourIdOrderByCreatedAtDesc(tourId)) {
            counts[r.getRating()]++;
        }

        // Chuyển thành dạng đơn giản
        List<Map<String, Object>> list = reviews.stream().map(r -> Map.<String, Object>of(
                "id",           r.getId(),
                "userName",     r.getUser().getFullName(),
                "rating",       r.getRating(),
                "title",        r.getTitle(),
                "body",         r.getBody(),
                "helpfulCount", r.getHelpfulCount(),
                "createdAt",    r.getCreatedAt().toString(),
                "verified",     hasBookedTour(r.getUser().getId(), tourId)
        )).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", list,
                "stats", Map.of(
                        "avgRating",   avg != null ? Math.round(avg * 10.0) / 10.0 : 0,
                        "totalReviews", total,
                        "5star", counts[5],
                        "4star", counts[4],
                        "3star", counts[3],
                        "2star", counts[2],
                        "1star", counts[1]
                )
        ));
    }

    @GetMapping
    public ResponseEntity<?> getAllReviews(@RequestParam(required = false) Integer limit) {
        List<Review> reviews = reviewRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        if (limit != null && limit > 0) {
            reviews = reviews.stream().limit(limit).toList();
        }
        List<Map<String, Object>> list = reviews.stream().map(r -> Map.<String, Object>of(
                "id",         r.getId(),
                "tourId",     r.getTour().getId(),
                "tourName",   r.getTour().getName(),
                "userName",   r.getUser().getFullName(),
                "userEmail",  r.getUser().getEmail(),
                "rating",    r.getRating(),
                "title",     r.getTitle(),
                "body",      r.getBody(),
                "helpfulCount", r.getHelpfulCount(),
                "createdAt",  r.getCreatedAt().toString()
        )).toList();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", list,
                "total", list.size()
        ));
    }


    @PostMapping
    public ResponseEntity<?> createReview(
            @Valid @RequestBody ReviewRequest req,
            @RequestHeader("Authorization") String authHeader) {

        String email = jwtUtils.getEmailFromToken(authHeader.substring(7));
        User user = userRepository.findByEmail(email).orElseThrow();

        Tour tour = tourRepository.findById(req.tourId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tour"));

        // Tạo review
        Review review = new Review();
        review.setUser(user);
        review.setTour(tour);
        review.setRating(req.rating);
        review.setTitle(req.title);
        review.setBody(req.body);
        review.setHelpfulCount(0);
        reviewRepository.save(review);

        // Cập nhật điểm trung bình của tour
        updateTourRating(tour);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cảm ơn bạn đã đánh giá!"
        ));
    }


    @PutMapping("/{id}/helpful")
    public ResponseEntity<?> markHelpful(@PathVariable Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy review"));
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        reviewRepository.save(review);
        return ResponseEntity.ok(Map.of("success", true, "helpfulCount", review.getHelpfulCount()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        String email = jwtUtils.getEmailFromToken(authHeader.substring(7));
        User user = userRepository.findByEmail(email).orElseThrow();

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy review"));

        // Chỉ chủ sở hữu mới xóa được
        if (!review.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Không có quyền xóa review này!"));
        }

        Tour tour = review.getTour();
        reviewRepository.delete(review);
        updateTourRating(tour);

        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa đánh giá!"));
    }

    // Cập nhật điểm trung bình tour sau mỗi thao tác review
    private void updateTourRating(Tour tour) {
        Double avg   = reviewRepository.getAvgRatingByTourId(tour.getId());
        long   total = reviewRepository.countByTourId(tour.getId());
        if (avg != null) {
            tour.setAvgRating(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        }
        tour.setTotalReviews((int) total);
        tourRepository.save(tour);
    }

    // Kiểm tra user đã từng đặt tour này chưa
    private boolean hasBookedTour(Long userId, Long tourId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .anyMatch(b -> b.getTour().getId().equals(tourId));
    }
}
