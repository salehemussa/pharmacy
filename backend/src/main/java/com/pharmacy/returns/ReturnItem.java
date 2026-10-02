package com.pharmacy.returns;

import com.pharmacy.common.IdentifiedEntity;
import com.pharmacy.inventory.Batch;
import com.pharmacy.sale.SaleItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "return_items")
public class ReturnItem extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;
}
