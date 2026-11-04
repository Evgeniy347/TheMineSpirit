package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class SelfRescue {
    private SelfRescue() {}

    public static boolean mayBreakAtFeet(BlockCoord target, BlockCoord feet) {
        Objects.requireNonNull(target, "Цель ломки не может быть null: без неё неизвестно, что дух ломает");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, где дух стоит");
        return target.equals(feet);
    }
}
