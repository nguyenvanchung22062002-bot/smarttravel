package com.smarttravel.service;

import com.smarttravel.entity.Booking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BookingService {

    BookingCreationResult createBooking(String authHeader,
                                        Long tourId,
                                        String departureDate,
                                        int numAdults,
                                        int numChildren,
                                        String pickupLocation,
                                        String note,
                                        String paymentMethod,
                                        String clientIp);

    BookingCreationResult createBooking(String authHeader,
                                        Long tourId,
                                        String departureDate,
                                        int numAdults,
                                        int numChildren,
                                        String pickupLocation,
                                        String note,
                                        String paymentMethod,
                                        String clientIp,
                                        String voucherCode);

    List<Map<String, Object>> getMyBookings(String authHeader);

    void cancelBooking(Long bookingId, String authHeader);

    void updateStatusByAdmin(Long bookingId, String status);

    List<Booking> getAllBookings();

    PaymentService.PaymentResult handleVnPayIpn(Map<String, String> params);

    PaymentService.PaymentResult handleMoMoIpn(Map<String, Object> payload);

    record BookingCreationResult(String bookingCode,
                                 String tourName,
                                 BigDecimal totalPrice,
                                 String status,
                                 String paymentUrl) {}
}
