package com.smarttravel.service;

import com.smarttravel.entity.Booking;

import java.util.Map;

public interface PaymentService {

    String generatePaymentUrl(Booking booking, String clientIp);

    PaymentResult processVnPayIpn(Map<String, String> params);

    PaymentResult processMoMoIpn(Map<String, Object> payload);

    record PaymentResult(String bookingCode,
                         boolean success,
                         String providerTxnId,
                         String responseCode,
                         String message) {
    }
}
