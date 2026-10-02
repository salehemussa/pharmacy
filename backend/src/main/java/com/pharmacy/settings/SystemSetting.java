package com.pharmacy.settings;

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

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSetting extends IdentifiedEntity {

    @Column(name = "setting_key", nullable = false, unique = true, length = 80)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserAccount updatedBy;
}
