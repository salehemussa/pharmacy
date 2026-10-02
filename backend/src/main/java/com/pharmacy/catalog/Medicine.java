package com.pharmacy.catalog;

import com.pharmacy.common.TimestampedEntity;
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
@Table(name = "medicines")
public class Medicine extends TimestampedEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "generic_name", nullable = false, length = 200)
    private String genericName;

    @Column(name = "brand_name", length = 200)
    private String brandName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "dosage_form", nullable = false, length = 50)
    private String dosageForm;

    @Column(nullable = false, length = 50)
    private String strength;

    @Column(nullable = false, length = 30)
    private String unit;

    @Column(length = 150)
    private String manufacturer;

    @Column(unique = true, length = 80)
    private String barcode;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "purchase_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellingPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StorageLocation location;

    @Column(nullable = false)
    private boolean active = true;
}
