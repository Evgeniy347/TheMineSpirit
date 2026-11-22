package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ProtectionMemo {
    private final Set<BlockCoord> unprotected = new HashSet<>();
    private long stamp = Long.MIN_VALUE;

    public void refresh(long next) {
        if (next != stamp) {
            stamp = next;
            unprotected.clear();
        }
    }

    public boolean certified(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка проверки не может быть null: без неё не видно, что уже проверено");
        return unprotected.contains(cell);
    }

    public void certify(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка без защиты не может быть null: её надо запомнить до смены состояния");
        unprotected.add(cell);
    }

    public int size() {
        return unprotected.size();
    }
}
