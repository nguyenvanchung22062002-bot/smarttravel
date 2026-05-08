package com.smarttravel.service;

import com.smarttravel.entity.Booking;
import com.smarttravel.entity.Tour;
import com.smarttravel.entity.User;
import com.smarttravel.repository.BookingRepository;
import com.smarttravel.repository.TourRepository;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService — Unit Tests")
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock TourRepository tourRepository;
    @Mock UserRepository userRepository;
    @Mock JwtUtils jwtUtils;
    @Mock PaymentService paymentService;

    @InjectMocks BookingServiceImpl bookingService;

    private User mockUser;
    private Tour mockTour;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("user@test.com");
        mockUser.setFullName("Test User");
        mockUser.setRole(User.Role.USER);

        mockTour = new Tour();
        mockTour.setId(10L);
        mockTour.setName("Tour Hà Nội - Hạ Long 3N2Đ");
        mockTour.setPrice(new BigDecimal("3500000"));
        mockTour.setAvailableSlots(20);
        mockTour.setActive(true);
        mockTour.setCreatedAt(LocalDateTime.now());
    }

    // ──────────────────────────────────────────────────
    // createBooking — Happy path
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("createBooking: 2 người lớn + 1 trẻ em → tính giá đúng (giá gốc×2 + giá×70%×1 + phí 5%)")
    void createBooking_calcPrice_correctly() {
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));
        given(bookingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tourRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentService.generatePaymentUrl(any(), any())).willReturn(null);

        BookingService.BookingCreationResult result = bookingService.createBooking(
                "Bearer dummy.token.here",
                10L,
                LocalDate.now().plusDays(7).toString(),
                2,   // người lớn
                1,   // trẻ em
                "Hà Nội",
                null,
                null,
                "127.0.0.1"
        );

        // adult: 3_500_000 × 2 = 7_000_000
        // child: 3_500_000 × 0.7 × 1 = 2_450_000
        // subtotal = 9_450_000
        // service fee 5% = 472_500
        // total = 9_922_500
        assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("9922500"));
        assertThat(result.bookingCode()).startsWith("BK");
        assertThat(result.tourName()).isEqualTo("Tour Hà Nội - Hạ Long 3N2Đ");
        assertThat(result.paymentUrl()).isNull();
    }

    @Test
    @DisplayName("createBooking: chỉ 1 người lớn, không trẻ em → phí dịch vụ 5% đúng")
    void createBooking_oneAdultNoChild() {
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));
        given(bookingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tourRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentService.generatePaymentUrl(any(), any())).willReturn(null);

        BookingService.BookingCreationResult result = bookingService.createBooking(
                "Bearer dummy", 10L, LocalDate.now().plusDays(3).toString(),
                1, 0, null, null, null, "127.0.0.1");

        // 3_500_000 × 1.05 = 3_675_000
        assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("3675000"));
    }

    @Test
    @DisplayName("createBooking: phương thức VNPay → trả về paymentUrl không null")
    void createBooking_withVnPay_returnsPaymentUrl() {
        String expectedUrl = "https://sandbox.vnpayment.vn/?token=abc123";
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));
        given(bookingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tourRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentService.generatePaymentUrl(any(), any())).willReturn(expectedUrl);

        BookingService.BookingCreationResult result = bookingService.createBooking(
                "Bearer dummy", 10L, LocalDate.now().plusDays(5).toString(),
                1, 0, null, null, "VNPAY", "127.0.0.1");

        assertThat(result.paymentUrl()).isEqualTo(expectedUrl);
    }

    @Test
    @DisplayName("createBooking: số lượng chỗ trống giảm đúng sau khi đặt")
    void createBooking_decreasesAvailableSlots() {
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));
        given(bookingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tourRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(paymentService.generatePaymentUrl(any(), any())).willReturn(null);

        bookingService.createBooking("Bearer dummy", 10L, LocalDate.now().plusDays(7).toString(),
                2, 1, null, null, null, "127.0.0.1");

        // 20 - (2 adults + 1 child) = 17
        assertThat(mockTour.getAvailableSlots()).isEqualTo(17);
        then(tourRepository).should().save(mockTour);
    }

    // ──────────────────────────────────────────────────
    // createBooking — Validation errors
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("createBooking: tour inactive → throw IllegalArgumentException")
    void createBooking_inactiveTour_throws() {
        mockTour.setActive(false);
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));

        assertThatThrownBy(() -> bookingService.createBooking(
                "Bearer dummy", 10L, LocalDate.now().plusDays(1).toString(),
                1, 0, null, null, null, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hoạt động");
    }

    @Test
    @DisplayName("createBooking: yêu cầu nhiều chỗ hơn số slot còn lại → throw IllegalArgumentException")
    void createBooking_notEnoughSlots_throws() {
        mockTour.setAvailableSlots(2);
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(10L)).willReturn(Optional.of(mockTour));

        assertThatThrownBy(() -> bookingService.createBooking(
                "Bearer dummy", 10L, LocalDate.now().plusDays(1).toString(),
                3, 0, null, null, null, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không đủ chỗ");
    }

    @Test
    @DisplayName("createBooking: tour không tồn tại → throw RuntimeException")
    void createBooking_tourNotFound_throws() {
        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(tourRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(
                "Bearer dummy", 99L, LocalDate.now().plusDays(1).toString(),
                1, 0, null, null, null, "127.0.0.1"))
                .isInstanceOf(RuntimeException.class);
    }

    // ──────────────────────────────────────────────────
    // cancelBooking
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("cancelBooking: đơn PENDING → hủy thành công, hoàn trả slot")
    void cancelBooking_pendingBooking_success() {
        Booking booking = buildBooking(Booking.Status.PENDING, 1, 0);

        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(tourRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(1L, "Bearer dummy");

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
        assertThat(mockTour.getAvailableSlots()).isEqualTo(21); // 20 + 1
    }

    @Test
    @DisplayName("cancelBooking: đơn đã PAID → throw IllegalArgumentException")
    void cancelBooking_paidBooking_throws() {
        Booking booking = buildBooking(Booking.Status.PAID, 1, 0);

        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, "Bearer dummy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể hủy");
    }

    @Test
    @DisplayName("cancelBooking: user không phải chủ đơn → throw SecurityException")
    void cancelBooking_notOwner_throws() {
        User anotherUser = new User();
        anotherUser.setId(99L);

        Booking booking = buildBooking(Booking.Status.PENDING, 1, 0);
        booking.setUser(anotherUser);

        given(jwtUtils.getEmailFromToken(anyString())).willReturn("user@test.com");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(mockUser));
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, "Bearer dummy"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("quyền");
    }

    // ──────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────

    private Booking buildBooking(Booking.Status status, int adults, int children) {
        Booking b = new Booking();
        b.setId(1L);
        b.setBookingCode("BK123456");
        b.setUser(mockUser);
        b.setTour(mockTour);
        b.setNumAdults(adults);
        b.setNumChildren(children);
        b.setTotalPrice(new BigDecimal("3675000"));
        b.setStatus(status);
        b.setDepartureDate(LocalDate.now().plusDays(7));
        return b;
    }
}
