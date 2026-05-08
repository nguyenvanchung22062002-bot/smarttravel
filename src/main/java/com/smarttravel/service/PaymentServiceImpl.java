package com.smarttravel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttravel.entity.Booking;
import com.smarttravel.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final BookingRepository bookingRepository;

    // VNPay config
    @Value("${payment.vnpay.tmn-code:}")
    private String vnPayTmnCode;
    @Value("${payment.vnpay.hash-secret:}")
    private String vnPayHashSecret;
    @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnPayPayUrl;
    @Value("${payment.vnpay.return-url:http://localhost:8080/payment/vnpay/return}")
    private String vnPayReturnUrl;

    // MoMo config
    @Value("${payment.momo.partner-code:}")
    private String momoPartnerCode;
    @Value("${payment.momo.access-key:}")
    private String momoAccessKey;
    @Value("${payment.momo.secret-key:}")
    private String momoSecretKey;
    @Value("${payment.momo.pay-url:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String momoPayUrl;
    @Value("${payment.momo.return-url:http://localhost:8080/payment/momo/return}")
    private String momoReturnUrl;
    @Value("${payment.momo.ipn-url:http://localhost:8080/api/bookings/ipn/momo}")
    private String momoIpnUrl;

    // generatePaymentUrl
    @Override
    public String generatePaymentUrl(Booking booking, String clientIp) {
        if (booking.getPaymentMethod() == null) return null;
        return switch (booking.getPaymentMethod()) {
            case VNPAY -> createVnPayUrl(booking, clientIp);
            case MOMO  -> createMoMoUrl(booking);
            default    -> null;
        };
    }

    // VNPay IPN
    @Override
    public PaymentResult processVnPayIpn(Map<String, String> params) {
        String secureHash   = params.get("vnp_SecureHash");
        String bookingCode  = params.get("vnp_TxnRef");
        if (bookingCode == null || secureHash == null) {
            return new PaymentResult(null, false, null, "99", "Missing required VNPay params");
        }

        Map<String, String> signParams = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)) {
                signParams.put(key, entry.getValue());
            }
        }
        String expectedHash = hmacSha512(vnPayHashSecret, buildQuery(signParams, false));
        if (!expectedHash.equalsIgnoreCase(secureHash)) {
            return new PaymentResult(bookingCode, false, null, "97", "Invalid VNPay signature");
        }

        String responseCode    = params.getOrDefault("vnp_ResponseCode", "99");
        boolean success        = "00".equals(responseCode);
        String providerTxnId   = params.get("vnp_TransactionNo");

        Booking booking = bookingRepository.findByBookingCode(bookingCode).orElse(null);
        if (booking != null) {
            booking.setStatus(success ? Booking.Status.PAID : Booking.Status.FAILED);
            booking.setPaymentGateway("VNPAY");
            booking.setPaymentTxnRef(bookingCode);
            booking.setPaymentProviderTxnId(providerTxnId);
            booking.setPaymentResponseCode(responseCode);
            booking.setPaymentMessage(success ? "Payment successful" : "Payment failed");
            booking.setPaidAt(success ? LocalDateTime.now() : null);
            bookingRepository.save(booking);
        }
        return new PaymentResult(bookingCode, success, providerTxnId, responseCode,
                success ? "VNPay payment successful" : "VNPay payment failed");
    }

    // MoMo IPN
    @Override
    public PaymentResult processMoMoIpn(Map<String, Object> payload) {
        String signature    = toText(payload.get("signature"));
        String bookingCode  = toText(payload.get("orderId"));
        String resultCode   = toText(payload.get("resultCode"));
        String transId      = toText(payload.get("transId"));
        String message      = toText(payload.get("message"));

        if (bookingCode == null || signature == null) {
            return new PaymentResult(null, false, null, "99", "Missing required MoMo payload");
        }

        // Signature dùng đúng tên field MoMo v2 IPN
        String rawSignature = "accessKey="    + nullToEmpty(momoAccessKey)
                + "&amount="      + nullToEmpty(toText(payload.get("amount")))
                + "&extraData="   + nullToEmpty(toText(payload.get("extraData")))
                + "&message="     + nullToEmpty(message)
                + "&orderId="     + bookingCode
                + "&orderInfo="   + nullToEmpty(toText(payload.get("orderInfo")))
                + "&orderType="   + nullToEmpty(toText(payload.get("orderType")))
                + "&partnerCode=" + nullToEmpty(toText(payload.get("partnerCode")))
                + "&payType="     + nullToEmpty(toText(payload.get("payType")))
                + "&requestId="   + nullToEmpty(toText(payload.get("requestId")))
                + "&responseTime="+ nullToEmpty(toText(payload.get("responseTime")))
                + "&resultCode="  + nullToEmpty(resultCode)
                + "&transId="     + nullToEmpty(transId);

        String expected = hmacSha256(momoSecretKey, rawSignature);
        if (!expected.equalsIgnoreCase(signature)) {
            return new PaymentResult(bookingCode, false, transId, "98", "Invalid MoMo signature");
        }

        boolean success = "0".equals(resultCode);
        Booking booking = bookingRepository.findByBookingCode(bookingCode).orElse(null);
        if (booking != null) {
            booking.setStatus(success ? Booking.Status.PAID : Booking.Status.FAILED);
            booking.setPaymentGateway("MOMO");
            booking.setPaymentTxnRef(bookingCode);
            booking.setPaymentProviderTxnId(transId);
            booking.setPaymentResponseCode(resultCode);
            booking.setPaymentMessage(message);
            booking.setPaidAt(success ? LocalDateTime.now() : null);
            bookingRepository.save(booking);
        }
        return new PaymentResult(bookingCode, success, transId, resultCode,
                success ? "MoMo payment successful" : "MoMo payment failed");
    }

    // VNPay URL builder
    private String createVnPayUrl(Booking booking, String clientIp) {
        String txnRef = booking.getBookingCode();
        booking.setPaymentTxnRef(txnRef);
        booking.setPaymentGateway("VNPAY");
        bookingRepository.save(booking);

        ZoneId zoneId       = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now   = LocalDateTime.now(zoneId);
        String createDate   = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String expireDate   = now.plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String amount       = booking.getTotalPrice()
                .multiply(java.math.BigDecimal.valueOf(100)).toBigInteger().toString();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version",   "2.1.0");
        params.put("vnp_Command",   "pay");
        params.put("vnp_TmnCode",   vnPayTmnCode);
        params.put("vnp_Amount",    amount);
        params.put("vnp_CurrCode",  "VND");
        params.put("vnp_TxnRef",    txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don " + txnRef);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale",    "vn");
        params.put("vnp_ReturnUrl", vnPayReturnUrl);
        params.put("vnp_IpAddr",    clientIp == null ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        String secureHash = hmacSha512(vnPayHashSecret, buildQuery(params, false));
        return vnPayPayUrl + "?" + buildQuery(params, true) + "&vnp_SecureHash=" + secureHash;
    }

    // MoMo POST JSON → trả về payUrl
    private String createMoMoUrl(Booking booking) {
        String orderId    = booking.getBookingCode();
        String requestId  = orderId + "-" + System.currentTimeMillis();
        String amount     = booking.getTotalPrice().toBigInteger().toString();
        String orderInfo  = "Thanh toan don " + orderId;
        String requestType = "captureWallet";
        String extraData  = "";

        String rawSignature =
                "accessKey="    + nullToEmpty(momoAccessKey)
                        + "&amount="    + amount
                        + "&extraData=" + extraData
                        + "&ipnUrl="    + nullToEmpty(momoIpnUrl)
                        + "&orderId="   + orderId
                        + "&orderInfo=" + orderInfo
                        + "&partnerCode=" + nullToEmpty(momoPartnerCode)
                        + "&redirectUrl=" + nullToEmpty(momoReturnUrl)
                        + "&requestId=" + requestId
                        + "&requestType=" + requestType;

        String signature = hmacSha256(momoSecretKey, rawSignature);

        // Build JSON body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", nullToEmpty(momoPartnerCode));
        body.put("accessKey",   nullToEmpty(momoAccessKey));
        body.put("requestId",   requestId);
        body.put("amount",      amount);
        body.put("orderId",     orderId);
        body.put("orderInfo",   orderInfo);
        body.put("redirectUrl", nullToEmpty(momoReturnUrl));
        body.put("ipnUrl",      nullToEmpty(momoIpnUrl));
        body.put("extraData",   extraData);
        body.put("requestType", requestType);
        body.put("lang",        "vi");
        body.put("signature",   signature);

        try {
            ObjectMapper mapper   = new ObjectMapper();
            String jsonBody       = mapper.writeValueAsString(body);
            System.out.println("[MoMo] Request body: " + jsonBody);
            System.out.println("[MoMo] Raw signature: " + rawSignature);

            URL url               = new URL(momoPayUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody    = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            Map<String, Object> momoResp = mapper.readValue(responseBody, Map.class);

            Object resultCode = momoResp.get("resultCode");
            String payUrl     = toText(momoResp.get("payUrl"));

            if (!"0".equals(toText(resultCode)) || payUrl == null || payUrl.isBlank()) {
                String errMsg = toText(momoResp.get("message"));
                throw new RuntimeException("MoMo error " + resultCode + ": " + errMsg);
            }

            booking.setPaymentTxnRef(orderId);
            booking.setPaymentGateway("MOMO");
            bookingRepository.save(booking);

            return payUrl;

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Không thể kết nối MoMo: " + ex.getMessage(), ex);
        }
    }

    //Utilities
    private String buildQuery(Map<String, String> params, boolean encodeValues) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> encodeValues
                        ? e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII)
                        : e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String secret, String data) { return hmac(secret, data, "HmacSHA512"); }
    private String hmacSha256(String secret, String data) { return hmac(secret, data, "HmacSHA256"); }

    private String hmac(String secret, String data, String algorithm) {
        try {
            Mac hmac = Mac.getInstance(algorithm);
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Cannot sign payment payload", ex);
        }
    }

    private String toText(Object value)       { return value == null ? null : String.valueOf(value); }
    private String nullToEmpty(String value)  { return value == null ? "" : value; }
}