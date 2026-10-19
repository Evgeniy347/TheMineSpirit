package com.theminespirit.core.pickup;

public enum OverflowMode {
    FAIL,
    BY_VALUE,
    RETURN_TO_BASE;

    public static OverflowMode forSpirit(boolean followsPlayer) {
        return followsPlayer ? BY_VALUE : RETURN_TO_BASE;
    }

    public static OverflowMode parse(String text) {
        if (text == null) {
            return BY_VALUE;
        }
        return switch (text) {
            case "fail" -> FAIL;
            case "by_value" -> BY_VALUE;
            case "return_to_base" -> RETURN_TO_BASE;
            default ->
                throw new IllegalArgumentException("Режим переполнения '" + text + "' неизвестен: "
                        + "допустимы 'fail' (отказ при полном инвентаре), 'by_value' "
                        + "(взять ценное, выбросив дешёвое) и 'return_to_base' "
                        + "(сдать лишнее на склад и продолжить)");
        };
    }
}
