package com.pharmacy.settings;

import com.pharmacy.audit.AuditService;
import com.pharmacy.common.BusinessException;
import com.pharmacy.common.SearchText;
import com.pharmacy.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingRepository repository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DisplayProfile display() {
        return new DisplayProfile(
                get("pharmacy.name"),
                get("pharmacy.currency"),
                get("pharmacy.timezone"),
                getInt("inventory.expiry_warning_days"),
                getBoolean("inventory.allow_authorized_expired_use"),
                getDecimal("sales.max_discount_percent")
        );
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return repository.findAll().stream()
                .sorted((left, right) -> left.getSettingKey().compareTo(right.getSettingKey()))
                .map(SettingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElseThrow(() -> new IllegalStateException("Required setting is missing: " + key));
    }

    @Transactional(readOnly = true)
    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Setting " + key + " is not a whole number.");
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key).trim());
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key) {
        try {
            return new BigDecimal(get(key).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Setting " + key + " is not a number.");
        }
    }

    @Transactional(readOnly = true)
    public ZoneId zone() {
        try {
            return ZoneId.of(get("pharmacy.timezone").trim());
        } catch (Exception exception) {
            throw new IllegalStateException("pharmacy.timezone is not a valid timezone.");
        }
    }

    @Transactional(readOnly = true)
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    @Transactional
    public List<SettingResponse> update(Map<String, String> changes) {
        if (changes == null || changes.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_UPDATE", "Provide at least one setting to update.");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            SystemSetting setting = repository.findBySettingKey(entry.getKey())
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_SETTING", "Unknown setting: " + entry.getKey()));
            String value = validate(entry.getKey(), entry.getValue());
            before.put(entry.getKey(), setting.getSettingValue());
            setting.setSettingValue(value);
            setting.setUpdatedAt(Instant.now());
            setting.setUpdatedBy(currentUserService.requireEntity());
            after.put(entry.getKey(), value);
        }
        auditService.record("SETTING_UPDATE", "SETTING", null, Map.of("before", before, "after", after));
        return list();
    }

    private String validate(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (key) {
            case "pharmacy.name" -> requireText(key, value, 1, 150);
            case "pharmacy.address" -> requireText(key, value, 0, 300);
            case "pharmacy.phone" -> requireText(key, value, 0, 30);
            case "pharmacy.currency" -> {
                if (!value.matches("^[A-Z]{3}$")) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Currency must be a 3-letter ISO code.");
                }
            }
            case "pharmacy.timezone" -> {
                try {
                    ZoneId.of(value);
                } catch (Exception exception) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Timezone is not a valid IANA timezone.");
                }
            }
            case "inventory.expiry_warning_days" -> {
                int days = parseInt(key, value);
                if (days < 1 || days > 3650) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Expiry warning days must be between 1 and 3650.");
                }
            }
            case "inventory.allow_authorized_expired_use" -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Expired-use setting must be true or false.");
                }
            }
            case "sales.max_discount_percent" -> {
                BigDecimal percent;
                try {
                    percent = new BigDecimal(value);
                } catch (NumberFormatException exception) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Maximum discount must be a number.");
                }
                if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Maximum discount must be between 0 and 100.");
                }
            }
            case "security.password_min_length" -> {
                int length = parseInt(key, value);
                if (length < 8 || length > 72) {
                    throw BusinessException.badRequest("INVALID_SETTING", "Minimum password length must be between 8 and 72.");
                }
            }
            case "receipt.footer" -> requireText(key, value, 0, 200);
            default -> throw BusinessException.badRequest("UNKNOWN_SETTING", "Unknown setting: " + key);
        }
        return value;
    }

    private void requireText(String key, String value, int min, int max) {
        if (value.length() < min || value.length() > max) {
            throw BusinessException.badRequest("INVALID_SETTING", key + " must be between " + min + " and " + max + " characters.");
        }
        if (min > 0 && SearchText.blank(value)) {
            throw BusinessException.badRequest("INVALID_SETTING", key + " is required.");
        }
    }

    private int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest("INVALID_SETTING", key + " must be a whole number.");
        }
    }

    public record DisplayProfile(
            String name,
            String currency,
            String timezone,
            int expiryWarningDays,
            boolean allowAuthorizedExpiredUse,
            BigDecimal maxDiscountPercent
    ) {
    }

    public record SettingResponse(String key, String value, String description) {
        static SettingResponse from(SystemSetting setting) {
            return new SettingResponse(setting.getSettingKey(), setting.getSettingValue(), setting.getDescription());
        }
    }
}
