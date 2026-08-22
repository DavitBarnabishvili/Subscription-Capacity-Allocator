package com.arcticblu.subscriptioncapacityallocator.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalScaler {

    private DecimalScaler() {}

    public static long toLong(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    public static BigDecimal toBigDecimal(long value, int scale) {
        return BigDecimal.valueOf(value, scale);
    }
}
