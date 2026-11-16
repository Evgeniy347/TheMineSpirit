package com.theminespirit.core.board;

import java.util.List;
import java.util.Objects;

public final class ChestOpenRule {
    private ChestOpenRule() {}

    public static boolean blocked(List<Boolean> covered) {
        Objects.requireNonNull(
                covered, "Крышки половин сундука не могут быть null: без них не видно, открыт ли сундук");
        if (covered.isEmpty()) {
            return false;
        }
        return covered.stream().allMatch(Boolean.TRUE::equals);
    }
}
