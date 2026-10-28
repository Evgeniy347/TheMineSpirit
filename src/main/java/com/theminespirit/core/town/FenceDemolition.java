package com.theminespirit.core.town;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class FenceDemolition {
    private static final IdFilter FENCES = IdFilter.of(List.of("*_fence", "*_fence_gate"));

    private FenceDemolition() {}

    public static boolean allowed(String blockId, ColumnMask strip, int x, int z) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: исключение защиты относится к виду блока");
        Objects.requireNonNull(strip, "Полоса ограды не может быть null: вне неё защита забора прежняя");
        return strip.contains(x, z) && FENCES.matches(blockId);
    }
}
