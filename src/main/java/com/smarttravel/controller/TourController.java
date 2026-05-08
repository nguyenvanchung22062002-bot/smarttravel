package com.smarttravel.controller;

import com.smarttravel.entity.Tour;
import com.smarttravel.entity.TourImage;
import com.smarttravel.repository.TourRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

@Tag(name = "Tours", description = "Danh sách, tìm kiếm và quản lý tour du lịch")
@RestController
@RequestMapping("/api/tours")
public class TourController {

    @Autowired
    private TourRepository tourRepository;

    @Value("${app.upload.dir:uploads/tours}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Data
    static class TourRequest {
        @NotBlank(message = "Tên tour không được để trống")
        String name;
        String description;
        String descriptionLong;
        @NotBlank(message = "Điểm đến không được để trống")
        String destination;
        @NotNull @Min(1)
        Integer durationDays;
        @NotNull @Min(0)
        Integer durationNights;
        @NotNull @DecimalMin("0.0")
        BigDecimal price;
        @NotNull @Min(0)
        Integer availableSlots;
        String badge;
        List<String> imageUrls;
    }

    // ── GET all tours
    @GetMapping
    public ResponseEntity<?> getAllTours() {
        List<Tour> tours = tourRepository.findActiveToursWithImages();
        return ResponseEntity.ok(Map.of("success", true, "data", tours, "total", tours.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTourById(@PathVariable Long id) {
        return tourRepository.findByIdWithImages(id)
                .map(tour -> ResponseEntity.ok(Map.of("success", true, "data", tour)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTours(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minDurationDays,
            @RequestParam(required = false) Integer maxDurationDays) {

        String dest = destination;
        if ((dest == null || dest.isBlank()) && keyword != null && !keyword.isBlank())
            dest = keyword.trim();

        List<Tour> result = tourRepository.searchWithFilters(
                dest == null ? null : dest.trim(), minPrice, maxPrice, minDurationDays, maxDurationDays);
        return ResponseEntity.ok(Map.of("success", true, "data", result, "total", result.size()));
    }

    // ── CREATE tour
    @Operation(summary = "Tạo tour mới (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTour(@Valid @RequestBody TourRequest req) {
        Tour tour = new Tour();
        mapRequestToTour(req, tour);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tạo tour thành công!",
                "data", tourRepository.save(tour)));
    }

    // ── UPDATE tour
    @Operation(summary = "Cập nhật tour (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTour(@PathVariable Long id,
                                        @Valid @RequestBody TourRequest req) {
        Tour tour = tourRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tour ID: " + id));
        mapRequestToTour(req, tour);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cập nhật tour thành công!",
                "data", tourRepository.save(tour)));
    }

    // ── DELETE tour (soft)
    @Operation(summary = "Ẩn tour (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTour(@PathVariable Long id) {
        Tour tour = tourRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tour ID: " + id));
        tour.setActive(false);
        tourRepository.save(tour);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã ẩn tour thành công!"));
    }

    // ── UPLOAD ảnh từ máy tính
    @Operation(summary = "Upload ảnh tour từ máy tính (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadImage(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Không có file nào được gửi lên!"));
        }
        if (files.length > 10) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Tối đa 10 ảnh mỗi lần upload!"));
        }

        List<String> uploadedUrls  = new ArrayList<>();
        List<String> failedFiles   = new ArrayList<>();
        Set<String>  allowedTypes  = Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/jpg");
        long         maxSizeBytes  = 10 * 1024 * 1024;

        try {
            // Tạo thư mục nếu chưa có
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(uploadPath);

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                // Kiểm tra loại file
                String contentType = file.getContentType();
                if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
                    failedFiles.add(file.getOriginalFilename() + " (không phải ảnh hợp lệ)");
                    continue;
                }

                // Kiểm tra kích thước
                if (file.getSize() > maxSizeBytes) {
                    failedFiles.add(file.getOriginalFilename() + " (vượt quá 10MB)");
                    continue;
                }

                String ext       = getExtension(file.getOriginalFilename(), contentType);
                String fileName  = "tour_" + UUID.randomUUID().toString().replace("-", "") + ext;
                Path   filePath  = uploadPath.resolve(fileName);

                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                String publicUrl = baseUrl + "/uploads/tours/" + fileName;
                uploadedUrls.add(publicUrl);
            }

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Lỗi lưu file: " + e.getMessage()));
        }

        if (uploadedUrls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Không có file nào được upload thành công!",
                    "failed", failedFiles
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Upload thành công " + uploadedUrls.size() + " ảnh!");
        result.put("urls",    uploadedUrls);
        if (!failedFiles.isEmpty()) result.put("failed", failedFiles);
        return ResponseEntity.ok(result);
    }

    // Helpers
    private void mapRequestToTour(TourRequest req, Tour tour) {
        tour.setName(req.name);
        tour.setDescription(req.description);
        tour.setDescriptionLong(req.descriptionLong);
        tour.setDestination(req.destination);
        tour.setDurationDays(req.durationDays);
        tour.setDurationNights(req.durationNights);
        tour.setPrice(req.price);
        tour.setAvailableSlots(req.availableSlots);
        tour.setBadge(req.badge);
        tour.setActive(true);
        mapTourImages(req.imageUrls, tour);
    }

    private void mapTourImages(List<String> imageUrls, Tour tour) {
        List<TourImage> list = new ArrayList<>();
        if (imageUrls != null) {
            int idx = 0;
            for (String url : imageUrls) {
                if (url == null || url.isBlank()) continue;
                TourImage img = new TourImage();
                img.setTour(tour);
                img.setImageUrl(url.trim());
                img.setSortOrder(idx++);
                list.add(img);
            }
        }
        if (tour.getImages() == null) tour.setImages(new ArrayList<>());
        else tour.getImages().clear();
        tour.getImages().addAll(list);
    }

    private String getExtension(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            if (Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(ext)) return ext;
        }
        return switch (contentType.toLowerCase()) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif"  -> ".gif";
            default           -> ".jpg";
        };
    }
}