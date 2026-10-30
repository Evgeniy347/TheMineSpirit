package com.theminespirit.neoforge;

import com.theminespirit.core.mining.UnreachableCells;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

public final class UnreachableCellsStore {
    private static final Map<Level, UnreachableCells> BY_LEVEL = new WeakHashMap<>();

    private UnreachableCellsStore() {}

    public static UnreachableCells of(Level level) {
        synchronized (BY_LEVEL) {
            return BY_LEVEL.computeIfAbsent(level, ignored -> new UnreachableCells());
        }
    }
}
