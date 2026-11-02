// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/utils/pathing/PathingBlockType.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public enum NavBlockType {
    AIR(0),
    SOLID(1),
    WATER(2),
    AVOID(3);

    private static final NavBlockType[] VALUES = values();

    private final int bits;

    NavBlockType(int bits) {
        this.bits = bits;
    }

    public static NavBlockType fromBits(int bits) {
        for (NavBlockType type : VALUES) {
            if (type.bits == bits) {
                return type;
            }
        }
        throw new IllegalArgumentException("Тип блока " + bits + " вне 2-битного диапазона");
    }

    public int bits() {
        return bits;
    }
}
