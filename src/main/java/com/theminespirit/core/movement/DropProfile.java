package com.theminespirit.core.movement;

import java.util.List;
import java.util.Objects;

public final class DropProfile {
    public static final int MAX_SAFE_DROP = 2;
    public static final int LOOKAHEAD_CELLS = 8;

    private DropProfile() {
    }

    public static boolean blocksCarefulStep(List<Integer> floorHeights) {
        Objects.requireNonNull(floorHeights,
                "Профиль высот не может быть null: без него неизвестно, есть ли обрыв впереди");
        for (int i = 0; i + 1 < floorHeights.size(); i++) {
            if (floorHeights.get(i + 1) - floorHeights.get(i) < -MAX_SAFE_DROP) {
                return true;
            }
        }
        return false;
    }
}
