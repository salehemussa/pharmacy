package com.pharmacy.prescription;

import com.pharmacy.common.IdentifiedEntity;
import com.pharmacy.user.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "dispensings")
public class Dispensing extends IdentifiedEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispensed_by", nullable = false)
    private UserAccount dispensedBy;

    @Column(name = "dispensed_at", nullable = false)
    private Instant dispensedAt;

    @Column(length = 1000)
    private String notes;

    @Column(name = "expired_authorized", nullable = false)
    private boolean expiredAuthorized;

    @Column(name = "expired_reason", length = 500)
    private String expiredReason;

    @OneToMany(mappedBy = "dispensing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DispensingItem> items = new ArrayList<>();
}
