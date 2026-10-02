package com.pharmacy.inventory;

import com.pharmacy.common.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    long countByMovementTypeIn(java.util.Collection<StockMovementType> types);
}
