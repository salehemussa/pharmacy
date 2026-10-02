package com.pharmacy.prescription;

import com.pharmacy.catalog.Medicine;
import com.pharmacy.common.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "prescription_items")
public class PrescriptionItem extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Column(nullable = false, length = 80)
    private String dosage;

    @Column(nullable = false, length = 80)
    private String frequency;

    @Column(nullable = false, length = 80)
    private String duration;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 500)
    private String instructions;

    @Column(name = "quantity_dispensed", nullable = false)
    private int quantityDispensed;
}
