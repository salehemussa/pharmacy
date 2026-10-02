package com.pharmacy.sale;

import com.pharmacy.common.IdentifiedEntity;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "tendered_amount", precision = 14, scale = 2)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Column(length = 100)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false)
    private UserAccount receivedBy;
}
