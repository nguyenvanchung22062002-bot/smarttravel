package com.smarttravel.service;

import com.smarttravel.entity.Booking;
import com.smarttravel.entity.Tour;
import com.smarttravel.entity.User;
import com.smarttravel.repository.BookingRepository;
import com.smarttravel.repository.TourRepository;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TourRepository    tourRepository;
    private final UserRepository    userRepository;
    private final JwtUtils          jwtUtils;
    private final PaymentService    paymentService;
    private final PaymentUrlHelper  paymentUrlHelper;
    private final VoucherService    voucherService;
    private final CheckinService    checkinService;

    private static final double POINTS_PER_VND = 1.0 / 100_000.0;

    @Override
    @Transactional
    public BookingCreationResult createBooking(String authHeader,
                                               Long tourId,
                                               String departureDate,
                                               int numAdults,
                                               int numChildren,
                                               String pickupLocation,
                                               String note,
                                               String paymentMethod,
                                               String clientIp) {
        return createBooking(authHeader, tourId, departureDate, numAdults,
                numChildren, pickupLocation, note, paymentMethod, clientIp, null);
    }

    @Transactional
    public BookingCreationResult createBooking(String authHeader,
                                               Long tourId,
                                               String departureDate,
                                               int numAdults,
                                               int numChildren,
                                               String pickupLocation,
                                               String note,
                                               String paymentMethod,
                                               String clientIp,
                                               String voucherCode) {

        User user = getUserFromAuthHeader(authHeader);
        Tour tour = tourRepository.findById(tourId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tour ID: " + tourId));

        if (!Boolean.TRUE.equals(tour.getActive()))
            throw new IllegalArgumentException("Tour này hiện không hoạt động!");

        int requestedSlots = numAdults + numChildren;
        if (tour.getAvailableSlots() < requestedSlots)
            throw new IllegalArgumentException("Không đủ chỗ! Chỉ còn " + tour.getAvailableSlots() + " chỗ.");

        // Tính tổng tiền
        BigDecimal adultTotal = tour.getPrice().multiply(BigDecimal.valueOf(numAdults));
        BigDecimal childTotal = tour.getPrice()
                .multiply(BigDecimal.valueOf(0.7))
                .multiply(BigDecimal.valueOf(numChildren));
        BigDecimal subtotal = adultTotal.add(childTotal);

        String     appliedVoucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (voucherCode != null && !voucherCode.isBlank()) {
            BigDecimal afterDiscount = voucherService.applyVoucherToBooking(
                    voucherCode, subtotal, user);
            discountAmount = subtotal.subtract(afterDiscount);
            subtotal       = afterDiscount;
            appliedVoucher = voucherCode.toUpperCase();
        }

        BigDecimal totalPrice = subtotal.multiply(BigDecimal.valueOf(1.05))
                .setScale(0, RoundingMode.HALF_UP);

        Booking.PaymentMethod method = Booking.PaymentMethod.CASH;
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            try {
                method = Booking.PaymentMethod.valueOf(paymentMethod.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ: " + paymentMethod);
            }
        }

        // ── Tạo booking (status PENDING trước) ──────────────────────────
        String code = "BK" + System.currentTimeMillis() % 1_000_000;

        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setUser(user);
        booking.setTour(tour);
        booking.setDepartureDate(LocalDate.parse(departureDate));
        booking.setNumAdults(numAdults);
        booking.setNumChildren(numChildren);
        booking.setPickupLocation(pickupLocation);
        booking.setNote(note);
        booking.setTotalPrice(totalPrice);
        booking.setPaymentMethod(method);
        booking.setStatus(Booking.Status.PENDING);

        String paymentUrl = null;

        if (method == Booking.PaymentMethod.MOMO || method == Booking.PaymentMethod.VNPAY) {
            bookingRepository.saveAndFlush(booking);

            try {
                paymentUrl = paymentService.generatePaymentUrl(booking, clientIp);

                if (paymentUrl == null || paymentUrl.isBlank()) {
                    bookingRepository.delete(booking);
                    throw new RuntimeException(
                            "Không thể tạo link thanh toán " + method.name()
                                    + ". Vui lòng kiểm tra cấu hình hoặc chọn phương thức khác."
                    );
                }

            } catch (RuntimeException ex) {
                try { bookingRepository.delete(booking); } catch (Exception ignored) {}
                throw ex;
            } catch (Exception ex) {
                try { bookingRepository.delete(booking); } catch (Exception ignored) {}
                throw new RuntimeException("Lỗi khởi tạo thanh toán: " + ex.getMessage());
            }

        } else {
            BigDecimal currentBalance = user.getBalance() != null
                    ? user.getBalance() : BigDecimal.ZERO;

            if (currentBalance.compareTo(totalPrice) < 0) {
                throw new IllegalArgumentException(
                        "Số dư không đủ! Hiện có: "
                                + String.format("%,.0f", currentBalance) + "đ"
                                + " – Cần: "
                                + String.format("%,.0f", totalPrice) + "đ"
                                + ". Vui lòng chọn thanh toán MoMo hoặc VNPay."
                );
            }

            user.setBalance(currentBalance.subtract(totalPrice));
            userRepository.save(user);

            booking.setStatus(Booking.Status.PAID);
            booking.setPaymentGateway("BALANCE");
            booking.setPaymentTxnRef(code);
            booking.setPaymentResponseCode("00");
            booking.setPaymentMessage(
                    appliedVoucher != null
                            ? "Thanh toán số dư | Voucher: " + appliedVoucher
                            + " | Giảm: " + String.format("%,.0f", discountAmount) + "đ"
                            : "Thanh toán bằng số dư tài khoản"
            );
            booking.setPaidAt(LocalDateTime.now());
            bookingRepository.saveAndFlush(booking);

            // ── Giảm slot tour ───────────────────────────────────────────
            tour.setAvailableSlots(tour.getAvailableSlots() - requestedSlots);
            tourRepository.save(tour);

            // ── Award điểm thưởng ────────────────────────────────────────
            try {
                int earnedPoints = Math.max(1, (int)(totalPrice.doubleValue() * POINTS_PER_VND));
                checkinService.awardBookingPoints(user, earnedPoints);
            } catch (Exception ignored) {}
        }

        return new BookingCreationResult(
                code,
                tour.getName(),
                totalPrice,
                booking.getStatus().name(),
                paymentUrl
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyBookings(String authHeader) {
        User user = getUserFromAuthHeader(authHeader);
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",            b.getId());
                    m.put("bookingCode",   b.getBookingCode());
                    m.put("tourName",      b.getTour().getName());
                    m.put("departureDate", b.getDepartureDate().toString());
                    m.put("numAdults",     b.getNumAdults());
                    m.put("numChildren",   b.getNumChildren());
                    m.put("totalPrice",    b.getTotalPrice());
                    m.put("status",        b.getStatus().name());
                    m.put("paymentMethod", b.getPaymentMethod() == null ? null : b.getPaymentMethod().name());
                    m.put("createdAt",     b.getCreatedAt().toString());
                    return m;
                }).toList();
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId, String authHeader) {
        User user = getUserFromAuthHeader(authHeader);
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking"));

        if (!b.getUser().getId().equals(user.getId()))
            throw new SecurityException("Không có quyền hủy đơn này!");

        if (b.getStatus() == Booking.Status.COMPLETED)
            throw new IllegalArgumentException("Không thể hủy đơn đã hoàn thành!");

        // Hoàn tiền chỉ khi thanh toán qua số dư
        if ((b.getStatus() == Booking.Status.PAID || b.getStatus() == Booking.Status.CONFIRMED)
                && "BALANCE".equals(b.getPaymentGateway())) {
            BigDecimal refund     = b.getTotalPrice() != null ? b.getTotalPrice() : BigDecimal.ZERO;
            BigDecimal newBalance = (user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO).add(refund);
            user.setBalance(newBalance);
            userRepository.save(user);
        }

        b.setStatus(Booking.Status.CANCELLED);
        bookingRepository.save(b);

        Tour tour = b.getTour();
        tour.setAvailableSlots(tour.getAvailableSlots() + b.getNumAdults() + b.getNumChildren());
        tourRepository.save(tour);
    }

    @Override
    @Transactional
    public void updateStatusByAdmin(Long bookingId, String status) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy booking ID: " + bookingId));
        b.setStatus(Booking.Status.valueOf(status.toUpperCase()));
        if (b.getStatus() == Booking.Status.PAID) b.setPaidAt(LocalDateTime.now());
        bookingRepository.save(b);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Override
    @Transactional
    public PaymentService.PaymentResult handleVnPayIpn(Map<String, String> params) {
        return paymentService.processVnPayIpn(params);
    }

    @Override
    @Transactional
    public PaymentService.PaymentResult handleMoMoIpn(Map<String, Object> payload) {
        return paymentService.processMoMoIpn(payload);
    }

    private User getUserFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalArgumentException("Authorization header không hợp lệ");
        String email = jwtUtils.getEmailFromToken(authHeader.substring(7));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }
}