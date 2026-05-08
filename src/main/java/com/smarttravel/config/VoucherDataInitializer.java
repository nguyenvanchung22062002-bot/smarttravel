package com.smarttravel.config;

import com.smarttravel.entity.Voucher;
import com.smarttravel.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherDataInitializer implements ApplicationRunner {

    private final VoucherRepository voucherRepo;

    @Override
    public void run(ApplicationArguments args) {
        // maxUsage = null → unlimited (ai cũng dùng được nhiều lần)
        seedIfAbsent("SUMMER20",
                Voucher.DiscountType.PERCENTAGE, BigDecimal.valueOf(20),
                null,
                LocalDate.of(2026, 8, 30),
                null);   // ← unlimited

        seedIfAbsent("WEEKEND10",
                Voucher.DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                null,
                LocalDate.of(2026, 9, 15),
                null);   // ← unlimited

        // ⚠️ BUG CŨ: maxUsage=1 → chỉ 1 người dùng được, người thứ 2 bị lỗi
        // FIX: maxUsage=null → unlimited
        seedIfAbsent("NEWUSER15",
                Voucher.DiscountType.PERCENTAGE, BigDecimal.valueOf(15),
                BigDecimal.valueOf(500_000),
                LocalDate.of(2026, 12, 31),
                null);   // ← đã sửa từ 1 → null

        log.info("✅ Voucher seeding complete.");
    }

    private void seedIfAbsent(String code,
                              Voucher.DiscountType type,
                              BigDecimal value,
                              BigDecimal maxDiscount,
                              LocalDate expiry,
                              Integer maxUsage) {
        if (voucherRepo.findByCode(code).isPresent()) return;

        Voucher v = new Voucher();
        v.setCode(code);
        v.setDiscountType(type);
        v.setDiscountValue(value);
        v.setMaxDiscountAmount(maxDiscount);
        v.setExpiryDate(expiry);
        v.setMaxUsage(maxUsage);
        v.setUsedCount(0);
        v.setSource("ADMIN_CREATED");
        v.setStatus(Voucher.VoucherStatus.ACTIVE);
        voucherRepo.save(v);
        log.info("  Seeded voucher: {}", code);
    }
}