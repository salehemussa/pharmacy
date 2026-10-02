package com.pharmacy.common;

import java.time.LocalDate;

public final class BatchDates {

    private BatchDates() {
    }

    public static void validate(LocalDate manufacturingDate, LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            throw BusinessException.badRequest("EXPIRY_REQUIRED", "Expiry date is required.");
        }
        if (manufacturingDate != null) {
            if (today != null && manufacturingDate.isAfter(today)) {
                throw BusinessException.badRequest("INVALID_MANUFACTURING_DATE", "Manufacturing date cannot be in the future.");
            }
            if (manufacturingDate.isAfter(expiryDate)) {
                throw BusinessException.badRequest("INVALID_BATCH_DATES", "Manufacturing date cannot be after the expiry date.");
            }
        }
    }
}
