package com.smarttravel.repository;

import com.smarttravel.entity.Tour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TourRepository extends JpaRepository<Tour, Long> {

    // Chỉ lấy tour đang active
    List<Tour> findByActiveTrue();

    @Query("""
           SELECT DISTINCT t FROM Tour t
           LEFT JOIN FETCH t.images i
           WHERE t.active = true
           ORDER BY t.id DESC
           """)
    List<Tour> findActiveToursWithImages();

    @Query("""
           SELECT DISTINCT t FROM Tour t
           LEFT JOIN FETCH t.images i
           WHERE t.id = :id
           """)
    Optional<Tour> findByIdWithImages(@Param("id") Long id);

    // Tìm tour theo điểm đến
    List<Tour> findByDestinationContainingIgnoreCaseAndActiveTrue(String destination);

    // Tìm tour theo khoảng giá
    List<Tour> findByPriceBetweenAndActiveTrue(BigDecimal minPrice, BigDecimal maxPrice);

    // Tìm tour theo tên hoặc điểm đến
    @Query("SELECT t FROM Tour t WHERE t.active = true AND " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(t.destination) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Tour> searchByKeyword(@Param("keyword") String keyword);

    @Query("""
           SELECT DISTINCT t FROM Tour t
           LEFT JOIN FETCH t.images i
           WHERE t.active = true
             AND (:destination IS NULL OR LOWER(t.destination) LIKE LOWER(CONCAT('%', :destination, '%')))
             AND (:minPrice IS NULL OR t.price >= :minPrice)
             AND (:maxPrice IS NULL OR t.price <= :maxPrice)
             AND (:minDurationDays IS NULL OR t.durationDays >= :minDurationDays)
             AND (:maxDurationDays IS NULL OR t.durationDays <= :maxDurationDays)
           """)
    List<Tour> searchWithFilters(@Param("destination") String destination,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("minDurationDays") Integer minDurationDays,
                                 @Param("maxDurationDays") Integer maxDurationDays);
}
