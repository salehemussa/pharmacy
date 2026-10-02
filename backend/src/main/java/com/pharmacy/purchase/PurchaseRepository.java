package com.pharmacy.purchase;

import com.pharmacy.common.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {

    long countBySupplier_IdAndStatusIn(Long supplierId, Collection<PurchaseStatus> statuses);
}
