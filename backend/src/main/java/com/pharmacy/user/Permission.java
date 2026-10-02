package com.pharmacy.user;

import com.pharmacy.common.IdentifiedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends IdentifiedEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, length = 50)
    private String module;
}
