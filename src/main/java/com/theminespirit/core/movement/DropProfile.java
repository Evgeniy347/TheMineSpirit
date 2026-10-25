package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class DropProfile {
    public static final int MAX_SAFE_DROP = 2;
    public static final int LOOKAHEAD_CELLS = 8;

    private DropProfile() {}

    public static boolean blocksCarefulStep(List<Integer> floorHeights) {
        Objects.requireNonNull(
                floorHeights, "Профиль высот не может быть null: без него неизвестно, есть ли обрыв впереди");
        for (int i = 0; i + 1 < floorHeights.size(); i++) {
            if (floorHeights.get(i + 1) - floorHeights.get(i) < -MAX_SAFE_DROP) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable String carefulRefusal(boolean hasPath, boolean reaches, List<Integer> heightsFromNext) {
        if (!hasPath) {
            return "пути к цели нет: осторожный шаг не строит обход через обрыв";
        }
        if (!reaches) {
            return "путь не доходит до цели: она за обрывом глубже " + MAX_SAFE_DROP + " блоков или за преградой";
        }
        List<Integer> window = heightsFromNext.subList(0, Math.min(heightsFromNext.size(), LOOKAHEAD_CELLS + 1));
        if (blocksCarefulStep(window)) {
            return "на пути обрыв глубже " + MAX_SAFE_DROP + " блоков в ближайших " + LOOKAHEAD_CELLS + " клетках";
        }
        return null;
    }
}
