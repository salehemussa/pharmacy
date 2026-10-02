package com.pharmacy.sale;

import com.pharmacy.common.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    long countByCustomer_Id(Long customerId);

    @Query("""
            select coalesce(sum(sale.totalAmount), 0) from Sale sale
            where sale.status = :status and sale.createdAt >= :from and sale.createdAt < :to
            """)
    BigDecimal sumTotal(@Param("status") SaleStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(sale.totalAmount), 0) from Sale sale
            where sale.status = :status
            """)
    BigDecimal sumAll(@Param("status") SaleStatus status);
}
