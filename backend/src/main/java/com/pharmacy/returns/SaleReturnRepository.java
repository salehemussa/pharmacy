package com.pharmacy.returns;

import com.pharmacy.common.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long>, JpaSpecificationExecutor<SaleReturn> {

    boolean existsBySale_IdAndStatusIn(Long saleId, Collection<ReturnStatus> statuses);

    @Query("""
            select coalesce(sum(item.quantity), 0) from ReturnItem item
            where item.saleItem.id = :saleItemId and item.saleReturn.status in :statuses
            """)
    int sumQuantityForSaleItem(@Param("saleItemId") Long saleItemId, @Param("statuses") Collection<ReturnStatus> statuses);
}
