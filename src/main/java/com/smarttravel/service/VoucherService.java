package com.smarttravel.service;

import com.smarttravel.entity.User;
import com.smarttravel.entity.Voucher;
import com.smarttravel.repository.UserRepository;
import com.smarttravel.repository.VoucherRepository;
import com.smarttravel.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final UserRepository    userRepo;
    private final JwtUtils          jwtUtils;
    private final CheckinService    checkinService;   // để deductPoints

    // Điểm cần để đổi 1 voucher
    private static final int    REDEEM_POINTS_COST     = 300;
    private static final int    REDEEM_DISCOUNT_PCT    = 10;
    private static final BigDecimal REDEEM_MAX_DISCOUNT = BigDecimal.valueOf(500_000);

    // ─────────────────────────────────────────────────────────────────────────
    // GET: danh sách voucher hiển thị trên home (public + của user)
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableVouchers(String authHeader) {
        List<Voucher> list = new ArrayList<>(voucherRepo.findPublicActiveVouchers());

        // Thêm voucher riêng của user nếu đã login
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                User user = resolveUser(authHeader);
                list.addAll(voucherRepo.findActiveVouchersByOwner(user.getId()));
            } catch (Exception ignored) { /* không login thì bỏ qua */ }
        }

        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /vouchers/validate — kiểm tra voucher trước khi đặt tour
    // Trả về discountAmount để FE hiển thị, KHÔNG đánh dấu đã dùng ở đây
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> validateVoucher(String code,
                                               BigDecimal originalPrice,
                                               String authHeader) {
        Voucher v = voucherRepo.findByCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Mã voucher không tồn tại!"));

        if (!v.isUsable())
            throw new IllegalArgumentException("Voucher đã hết hạn hoặc không còn hiệu lực!");

        // Nếu voucher gắn owner → chỉ chủ mới dùng được
        if (v.getOwnerUser() != null) {
            User user = resolveUser(authHeader);
            if (!v.getOwnerUser().getId().equals(user.getId()))
                throw new IllegalArgumentException("Voucher này không thuộc về bạn!");
        }

        BigDecimal discount     = v.calcDiscount(originalPrice);
        BigDecimal finalPrice   = originalPrice.subtract(discount);

        Map<String, Object> result = new HashMap<>();
        result.put("code",           v.getCode());
        result.put("discountType",   v.getDiscountType().name());
        result.put("discountValue",  v.getDiscountValue());
        result.put("discountAmount", discount);        // tiền được giảm (VND)
        result.put("finalPrice",     finalPrice);
        result.put("expiryDate",     v.getExpiryDate().toString());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dùng trong BookingService.createBooking() — atomic với booking
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public BigDecimal applyVoucherToBooking(String code,
                                            BigDecimal originalPrice,
                                            User user) {
        if (code == null || code.isBlank()) return originalPrice;

        Voucher v = voucherRepo.findByCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Mã voucher không tồn tại!"));

        if (!v.isUsable())
            throw new IllegalArgumentException("Voucher đã hết hạn hoặc đã được sử dụng!");

        // Kiểm tra owner
        if (v.getOwnerUser() != null && !v.getOwnerUser().getId().equals(user.getId()))
            throw new IllegalArgumentException("Voucher này không thuộc về bạn!");

        // Tính giá sau giảm
        BigDecimal discount   = v.calcDiscount(originalPrice);
        BigDecimal finalPrice = originalPrice.subtract(discount);

        // Đánh dấu đã dùng
        v.setUsedCount(v.getUsedCount() + 1);
        // Nếu single-use (maxUsage=1) hoặc đã đạt max → mark USED
        if (v.getMaxUsage() != null && v.getUsedCount() >= v.getMaxUsage()) {
            v.setStatus(Voucher.VoucherStatus.USED);
        }
        voucherRepo.save(v);

        return finalPrice.compareTo(BigDecimal.ZERO) > 0 ? finalPrice : BigDecimal.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /vouchers/redeem — đổi điểm lấy voucher cá nhân
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> redeemPoints(String authHeader) {
        User user = resolveUser(authHeader);

        // Trừ điểm (sẽ throw nếu không đủ điểm)
        checkinService.deductPoints(user, REDEEM_POINTS_COST);

        // Tạo voucher cá nhân
        String code = "POINT10-" + generateCode();
        Voucher v = new Voucher();
        v.setCode(code);
        v.setDiscountType(Voucher.DiscountType.PERCENTAGE);
        v.setDiscountValue(BigDecimal.valueOf(REDEEM_DISCOUNT_PCT));
        v.setMaxDiscountAmount(REDEEM_MAX_DISCOUNT);
        v.setExpiryDate(LocalDate.now().plusMonths(3));  // hết hạn sau 3 tháng
        v.setOwnerUser(user);
        v.setMaxUsage(1);                                // dùng 1 lần
        v.setSource("POINTS_REDEEM");
        v.setStatus(Voucher.VoucherStatus.ACTIVE);
        voucherRepo.save(v);

        Map<String, Object> result = new HashMap<>();
        result.put("code",        code);
        result.put("discount",    REDEEM_DISCOUNT_PCT);
        result.put("maxDiscount", REDEEM_MAX_DISCOUNT);
        result.put("expiry",      v.getExpiryDate().toString());
        result.put("message",     "Đổi điểm thành công! Mã: " + code + " (-" + REDEEM_DISCOUNT_PCT + "%)");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private Map<String, Object> toMap(Voucher v) {
        Map<String, Object> m = new HashMap<>();
        m.put("code",          v.getCode());
        m.put("discountType",  v.getDiscountType().name());
        m.put("discountValue", v.getDiscountValue());
        m.put("expiryDate",    v.getExpiryDate().toString());
        m.put("isPersonal",    v.getOwnerUser() != null);
        return m;
    }

    private String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private User resolveUser(String authHeader) {
        String email = jwtUtils.getEmailFromToken(authHeader.substring(7));
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));
    }
}