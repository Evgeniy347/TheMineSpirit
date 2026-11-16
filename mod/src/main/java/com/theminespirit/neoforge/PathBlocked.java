package com.theminespirit.neoforge;

import com.theminespirit.core.board.BlockedCells;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class PathBlocked {
    public static final int TTL_TICKS = 1200;
    private static final Map<ServerLevel, BlockedCells> LEVELS = new WeakHashMap<>();

    private PathBlocked() {}

    public static void note(ServerLevel level, BlockCoord cell) {
        cells(level).note(cell, level.getGameTime());
    }

    public static List<BlockedCells.Mark> marks(ServerLevel level) {
        return cells(level).live(level.getGameTime());
    }

    private static BlockedCells cells(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new BlockedCells(TTL_TICKS));
    }
}
