package com.smarttravel.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtils — Unit Tests")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private static final String TEST_EMAIL = "hoanganh@smarttravel.vn";
    private static final String SECRET = "SmartTravelTestSecretKeyMinimum32CharsLong!!";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 86_400_000L); // 24h
    }

    @Test
    @DisplayName("generateToken: trả về token không null và không rỗng")
    void generateToken_notNullOrEmpty() {
        String token = jwtUtils.generateToken(TEST_EMAIL);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("generateToken: token đúng định dạng JWT (3 phần cách nhau bởi dấu chấm)")
    void generateToken_hasThreeParts() {
        String token = jwtUtils.generateToken(TEST_EMAIL);
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("getEmailFromToken: trích xuất đúng email từ token hợp lệ")
    void getEmailFromToken_validToken_returnsEmail() {
        String token = jwtUtils.generateToken(TEST_EMAIL);
        String extractedEmail = jwtUtils.getEmailFromToken(token);
        assertThat(extractedEmail).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("isTokenValid: token mới tạo → valid = true")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtUtils.generateToken(TEST_EMAIL);
        assertThat(jwtUtils.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: token đã hết hạn → valid = false")
    void isTokenValid_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1L);
        String expiredToken = jwtUtils.generateToken(TEST_EMAIL);
        assertThat(jwtUtils.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: token bị giả mạo (sửa payload) → valid = false")
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtUtils.generateToken(TEST_EMAIL);
        // Sửa 1 ký tự ở phần payload (giữa hai dấu chấm)
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "TAMPERED" + "." + parts[2];
        assertThat(jwtUtils.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: chuỗi rác → valid = false, không throw exception")
    void isTokenValid_garbageString_returnsFalse() {
        assertThat(jwtUtils.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: chuỗi rỗng → valid = false, không throw exception")
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtUtils.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("generateToken: hai lần gọi cùng email → token khác nhau (iat khác nhau)")
    void generateToken_calledTwice_returnsDifferentTokens() throws InterruptedException {
        String token1 = jwtUtils.generateToken(TEST_EMAIL);
        Thread.sleep(10);
        String token2 = jwtUtils.generateToken(TEST_EMAIL);
        // Tokens sẽ khác nhau vì iat (issued-at) khác nhau
        assertThat(token1).isNotEqualTo(token2);
        // Nhưng cả hai đều hợp lệ
        assertThat(jwtUtils.isTokenValid(token1)).isTrue();
        assertThat(jwtUtils.isTokenValid(token2)).isTrue();
    }
}
