package com.theminespirit.core.pickup;

public enum OverflowMode {
    FAIL,
    BY_VALUE;

    public static OverflowMode parse(String text) {
        if (text == null) {
            return BY_VALUE;
        }
        return switch (text) {
            case "fail" -> FAIL;
            case "by_value" -> BY_VALUE;
            default -> throw new IllegalArgumentException("Режим переполнения '" + text + "' неизвестен: "
                    + "допустимы 'fail' (прежний отказ при полном инвентаре) и 'by_value' "
                    + "(взять ценное, выбросив дешёвое)");
        };
    }
}
