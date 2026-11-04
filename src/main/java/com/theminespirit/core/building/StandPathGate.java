package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StandPathGate {
    private StandPathGate() {}

    public static boolean usable(
            boolean standable, boolean near, Optional<Set<BlockCoord>> walkable, BlockCoord stand) {
        Objects.requireNonNull(walkable, "Обход стоянок не может быть null: пустой — Set.of()");
        Objects.requireNonNull(stand, "Стоянка не может быть null: без неё нечего проверять");
        if (!standable) {
            return false;
        }
        if (!near) {
            return true;
        }
        return walkable.map(cells -> cells.contains(stand)).orElse(true);
    }
}
