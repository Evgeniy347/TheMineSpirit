package com.theminespirit.core.town;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class FenceDemolition {
    private static final IdFilter FENCES = IdFilter.of(List.of("*_fence", "*_fence_gate"));

    public record StubFacts(
            boolean planned, boolean inPlacement, boolean inOrchard, boolean inFenceStrip, boolean inRing) {}

    private FenceDemolition() {}

    public static boolean allowed(String blockId, ColumnMask strip, int x, int z) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: исключение защиты относится к виду блока");
        Objects.requireNonNull(strip, "Полоса ограды не может быть null: вне неё защита забора прежняя");
        return strip.contains(x, z) && FENCES.matches(blockId);
    }

    public static boolean stub(String blockId, StubFacts facts) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: снос заглушки решается по виду блока");
        Objects.requireNonNull(facts, "Признаки клетки не могут быть null: без них не отличить заглушку от плана");
        return FENCES.matches(blockId)
                && !facts.planned()
                && !facts.inFenceStrip()
                && !facts.inRing()
                && (!facts.inPlacement() || facts.inOrchard());
    }
}
