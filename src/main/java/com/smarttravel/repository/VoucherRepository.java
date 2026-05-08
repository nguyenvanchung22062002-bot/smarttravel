package com.smarttravel.repository;

import com.smarttravel.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCode(String code);

    // Voucher public (không gắn owner) + còn ACTIVE
    @Query("SELECT v FROM Voucher v WHERE v.ownerUser IS NULL AND v.status = 'ACTIVE'")
    List<Voucher> findPublicActiveVouchers();

    // Voucher của user cụ thể (đổi từ điểm) + còn ACTIVE
    @Query("SELECT v FROM Voucher v WHERE v.ownerUser.id = :userId AND v.status = 'ACTIVE'")
    List<Voucher> findActiveVouchersByOwner(@Param("userId") Long userId);
}