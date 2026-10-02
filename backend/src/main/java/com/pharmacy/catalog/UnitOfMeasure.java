package com.pharmacy.catalog;

import com.pharmacy.common.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "units_of_measure")
public class UnitOfMeasure extends IdentifiedEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
