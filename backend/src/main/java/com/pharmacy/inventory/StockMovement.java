package com.pharmacy.inventory;

import com.pharmacy.catalog.Medicine;
import com.pharmacy.common.IdentifiedEntity;
import com.pharmacy.common.ReferenceType;
import com.pharmacy.common.StockDirection;
import com.pharmacy.common.StockMovementType;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "stock_movements")
public class StockMovement extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private StockDirection direction;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private UserAccount performedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
