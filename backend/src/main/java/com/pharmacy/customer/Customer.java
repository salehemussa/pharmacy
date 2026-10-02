package com.pharmacy.customer;

import com.pharmacy.common.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends TimestampedEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
