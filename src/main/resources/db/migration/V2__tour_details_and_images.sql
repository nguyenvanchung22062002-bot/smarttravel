-- MySQL migration: add detailed itinerary + gallery images for tours

ALTER TABLE tours
    ADD COLUMN IF NOT EXISTS description_long LONGTEXT NULL;

CREATE TABLE IF NOT EXISTS tour_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tour_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_tour_images_tour
        FOREIGN KEY (tour_id) REFERENCES tours(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_tour_images_tour_id_sort ON tour_images (tour_id, sort_order);

-- Update detailed Vietnamese itineraries for key Hanoi tours
UPDATE tours
SET description_long = 'Điểm nhấn hành trình:\n- 07:30: Đón khách tại khu vực phố cổ và di chuyển qua các tuyến phố di sản.\n- 08:00: Tham quan Hồ Hoàn Kiếm, đền Ngọc Sơn và cầu Thê Húc.\n- 09:30: Đi bộ khám phá 36 phố phường, check-in chợ Đồng Xuân và các ngõ cổ.\n- 11:00: Thưởng thức cà phê trứng, bánh cốm và đặc sản Hà Nội.\n- 14:00: Trải nghiệm xích lô quanh phố cổ, nghe kể chuyện về thương cảng Thăng Long.\n- 16:30: Tự do mua sắm đồ thủ công và quà lưu niệm.\n- 18:00: Kết thúc chương trình.\n\nLandmark nổi bật:\n• Hồ Hoàn Kiếm và Tháp Rùa.\n• Đền Ngọc Sơn.\n• Chợ Đồng Xuân.\n• Các tuyến phố Hàng Đào - Hàng Ngang - Hàng Mã.'
WHERE LOWER(name) LIKE '%phố cổ%' OR LOWER(name) LIKE '%pho co%';

UPDATE tours
SET description_long = 'Điểm nhấn hành trình:\n- 08:00: Đón khách, giới thiệu lịch sử Quốc Tử Giám.\n- 08:45: Tham quan cổng Văn Miếu và hồ Văn.\n- 09:30: Khám phá Khuê Văn Các, giếng Thiên Quang, bia Tiến sĩ.\n- 10:45: Trải nghiệm nghi thức xin chữ đầu năm, nghe thuyết minh về Nho học.\n- 12:00: Dùng bữa trưa phong vị Bắc Bộ.\n- 14:00: Tiếp tục tham quan khu Đại Thành và không gian trưng bày cổ vật.\n- 16:00: Chụp ảnh concept áo dài tại sân Văn Miếu.\n- 17:00: Kết thúc hành trình.\n\nLandmark nổi bật:\n• Cổng Văn Miếu.\n• Khuê Văn Các.\n• Giếng Thiên Quang.\n• Nhà Thái Học.'
WHERE LOWER(name) LIKE '%văn miếu%' OR LOWER(name) LIKE '%van mieu%';

UPDATE tours
SET description_long = 'Điểm nhấn hành trình:\n- 07:45: Đón khách tại trung tâm Hà Nội, di chuyển đến làng gốm Bát Tràng.\n- 09:00: Tham quan đình làng, chợ gốm và các xưởng gốm thủ công lâu đời.\n- 10:00: Trải nghiệm tự tay nặn gốm, tạo hình và vẽ men.\n- 12:00: Dùng bữa trưa với ẩm thực đồng bằng Bắc Bộ.\n- 13:30: Tham quan nhà cổ Vạn Vân và bảo tàng gốm.\n- 15:00: Mua sắm đồ gốm trang trí, quà lưu niệm cao cấp.\n- 16:30: Trở về trung tâm thành phố.\n\nLandmark nổi bật:\n• Chợ gốm Bát Tràng.\n• Xưởng gốm thủ công.\n• Nhà cổ Vạn Vân.\n• Không gian trải nghiệm workshop gốm.'
WHERE LOWER(name) LIKE '%bát tràng%' OR LOWER(name) LIKE '%bat trang%';

-- Replace gallery images for these tours
DELETE ti
FROM tour_images ti
JOIN tours t ON t.id = ti.tour_id
WHERE LOWER(t.name) LIKE '%phố cổ%'
   OR LOWER(t.name) LIKE '%pho co%'
   OR LOWER(t.name) LIKE '%văn miếu%'
   OR LOWER(t.name) LIKE '%van mieu%'
   OR LOWER(t.name) LIKE '%bát tràng%'
   OR LOWER(t.name) LIKE '%bat trang%';

INSERT INTO tour_images (tour_id, image_url, sort_order)
SELECT t.id, x.image_url, x.sort_order
FROM tours t
JOIN (
    SELECT 'pho_co' AS code, 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1400&q=80' AS image_url, 0 AS sort_order
    UNION ALL SELECT 'pho_co', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=1400&q=80', 1
    UNION ALL SELECT 'pho_co', 'https://images.unsplash.com/photo-1545569341-9eb8b30979d9?auto=format&fit=crop&w=1400&q=80', 2
    UNION ALL SELECT 'van_mieu', 'https://images.unsplash.com/photo-1483817101829-339b08e8d83f?auto=format&fit=crop&w=1400&q=80', 0
    UNION ALL SELECT 'van_mieu', 'https://images.unsplash.com/photo-1539650116574-75c0c6d73f0e?auto=format&fit=crop&w=1400&q=80', 1
    UNION ALL SELECT 'van_mieu', 'https://images.unsplash.com/photo-1518509562904-e7ef99cdcc86?auto=format&fit=crop&w=1400&q=80', 2
    UNION ALL SELECT 'bat_trang', 'https://images.unsplash.com/photo-1610701596007-11502861dcfa?auto=format&fit=crop&w=1400&q=80', 0
    UNION ALL SELECT 'bat_trang', 'https://images.unsplash.com/photo-1601055903647-ddf1ee9701b5?auto=format&fit=crop&w=1400&q=80', 1
    UNION ALL SELECT 'bat_trang', 'https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?auto=format&fit=crop&w=1400&q=80', 2
) x
ON (
       (x.code = 'pho_co' AND (LOWER(t.name) LIKE '%phố cổ%' OR LOWER(t.name) LIKE '%pho co%'))
    OR (x.code = 'van_mieu' AND (LOWER(t.name) LIKE '%văn miếu%' OR LOWER(t.name) LIKE '%van mieu%'))
    OR (x.code = 'bat_trang' AND (LOWER(t.name) LIKE '%bát tràng%' OR LOWER(t.name) LIKE '%bat trang%'))
);
