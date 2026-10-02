package com.pharmacy.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
