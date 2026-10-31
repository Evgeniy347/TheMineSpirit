package com.theminespirit.core.mining;

import java.util.Objects;

public final class MineAimFlow {
    public enum Next {
        READY,
        AIM,
        REFUSE
    }

    private MineAimFlow() {}

    public static Next next(MineCellPlan.Kind kind) {
        Objects.requireNonNull(kind, "Вид решения клетки не может быть null: без него неизвестно, что делать дальше");
        return switch (kind) {
            case READY -> Next.READY;
            case WAIT, APPROACH -> Next.AIM;
            case REFUSE -> Next.REFUSE;
        };
    }
}
