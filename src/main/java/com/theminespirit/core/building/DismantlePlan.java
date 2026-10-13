package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class DismantlePlan {
    private DismantlePlan() {
    }

    public static List<BlockCoord> dismantle(List<BlockCoord> cells, Predicate<BlockCoord> own) {
        Objects.requireNonNull(cells, "Разбор без списка клеток не считается: без него неизвестно, что снимать");
        Objects.requireNonNull(own, "Разбор без проверки своих не считается: без неё дух тронет чужие блоки");
        Set<BlockCoord> topDown = new LinkedHashSet<>();
        for (int index = cells.size() - 1; index >= 0; index--) {
            BlockCoord cell = cells.get(index);
            if (own.test(cell)) {
                topDown.add(cell);
            }
        }
        return List.copyOf(new ArrayList<>(topDown));
    }
}
