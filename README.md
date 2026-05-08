# SmartTravel

### Create/Update database
```sql
CREATE DATABASE smarttravel_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```properties
spring.datasource.username=root
spring.datasource.password=yourpass
```

Run:
```bash
mvn spring-boot:run
```

## Test

### Auth
| Method | URL | Mô tả | Auth |
|--------|-----|-------|------|
| POST | /api/auth/register | Đăng ký | Public |
| POST | /api/auth/login | Đăng nhập | Public |
| GET  | /api/auth/me | Thông tin tôi | Token |

### Tour
| Method | URL | Mô tả | Auth |
|--------|-----|-------|------|
| GET | /api/tours | Lấy tất cả tour | Public |
| GET | /api/tours/{id} | Chi tiết tour | Public |
| GET | /api/tours/search?keyword=đà nẵng | Tìm kiếm | Public |
| POST | /api/tours | Tạo tour mới | Admin |
| PUT | /api/tours/{id} | Sửa tour | Admin |
| DELETE | /api/tours/{id} | Ẩn tour | Admin |

### Booking
| Method | URL | Mô tả | Auth |
|--------|-----|-------|------|
| POST | /api/bookings | Đặt tour | Token |
| GET | /api/bookings/my | Lịch sử của tôi | Token |
| PUT | /api/bookings/{id}/cancel | Hủy đơn | Token |
| GET | /api/bookings/admin/all | Tất cả đơn | Admin |
| PUT | /api/bookings/admin/{id}/status | Đổi trạng thái | Admin |

### Review
| Method | URL | Mô tả | Auth |
|--------|-----|-------|------|
| GET | /api/reviews/tour/{tourId} | Xem review | Public |
| POST | /api/reviews | Viết review | Token |
| PUT | /api/reviews/{id}/helpful | Bấm hữu ích | Token |
| DELETE | /api/reviews/{id} | Xóa review | Token |
