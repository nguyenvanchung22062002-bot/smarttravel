package com.smarttravel.service;

import com.smarttravel.entity.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentUrlHelper {

    private final PaymentService paymentService;

    public String generateSafely(Booking booking, String clientIp) {
            if (booking.getPaymentMethod() == null
            || booking.getPaymentMethod() == Booking.PaymentMethod.CASH) {
                return null;
            }

            return paymentService.generatePaymentUrl(booking, clientIp);
    }
}