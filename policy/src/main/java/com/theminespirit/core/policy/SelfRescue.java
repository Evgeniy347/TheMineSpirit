package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class SelfRescue {
    public static final int ESCAPE_HEIGHT = 2;

    private SelfRescue() {}

    public static boolean mayBreakAtFeet(BlockCoord target, BlockCoord feet) {
        Objects.requireNonNull(target, "Цель ломки не может быть null: без неё неизвестно, что дух ломает");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, где дух стоит");
        return target.equals(feet);
    }

    public static boolean mayBreakEscaping(BlockCoord target, BlockCoord feet) {
        Objects.requireNonNull(target, "Цель ломки не может быть null: без неё неизвестно, что дух ломает");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, где дух стоит");
        if (mayBreakAtFeet(target, feet)) {
            return true;
        }
        int dy = target.y() - feet.y();
        return dy >= 0
                && dy <= ESCAPE_HEIGHT + 1
                && Math.abs(target.x() - feet.x()) <= 1
                && Math.abs(target.z() - feet.z()) <= 1;
    }
}
