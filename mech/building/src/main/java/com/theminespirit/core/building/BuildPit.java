package com.theminespirit.core.building;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class BuildPit {
    private BuildPit() {}

    public static boolean needsEscape(int feetY, int baseY, BooleanSupplier trapped) {
        Objects.requireNonNull(trapped, "Признак запертости не может быть null: без него яма — это любая низина");
        return feetY < baseY - 1 && trapped.getAsBoolean();
    }

    public static boolean trapped(boolean sealed, boolean embedded) {
        return sealed || embedded;
    }
}
