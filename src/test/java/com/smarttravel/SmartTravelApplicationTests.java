package com.smarttravel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration smoke test — kiểm tra ApplicationContext load thành công.
 * Chạy với profile "test" (dùng H2 in-memory thay MySQL).
 */
@SpringBootTest
@ActiveProfiles("test")
class SmartTravelApplicationTests {

    @Test
    void contextLoads() {
        // Nếu test này pass → toàn bộ Spring context (Security, JPA, Beans) cấu hình đúng
    }
}
