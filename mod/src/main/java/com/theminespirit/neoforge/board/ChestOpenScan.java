package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BlockedCells;
import com.theminespirit.core.board.ChestOpenRule;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

final class ChestOpenScan {
    private static final int TTL_TICKS = 2400;
    private static final Map<ServerLevel, BlockedCells> SEEN = new WeakHashMap<>();

    private ChestOpenScan() {}

    static List<BlockedCells.Mark> marks(ServerLevel level) {
        BlockedCells seen = SEEN.computeIfAbsent(level, ignored -> new BlockedCells(TTL_TICKS));
        long now = level.getGameTime();
        Set<BlockCoord> found = new LinkedHashSet<>();
        for (BlockCoord pos : ChestSnapshots.positions(level)) {
            if (!ChestSnapshots.countable(level, pos)) {
                continue;
            }
            List<BlockPos> covers = covers(level, pos);
            if (covers.isEmpty()) {
                continue;
            }
            for (BlockPos cover : covers) {
                found.add(new BlockCoord(cover.getX(), cover.getY(), cover.getZ()));
            }
        }
        found.forEach(cell -> seen.note(cell, now));
        List<BlockedCells.Mark> marks = new ArrayList<>();
        for (BlockedCells.Mark mark : seen.live(now)) {
            if (found.contains(mark.cell())) {
                marks.add(mark);
            }
        }
        return List.copyOf(marks);
    }

    private static List<BlockPos> covers(ServerLevel level, BlockCoord pos) {
        BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return List.of();
        }
        List<BlockPos> halves = halves(at, state);
        List<Boolean> covered = new ArrayList<>(halves.size());
        for (BlockPos half : halves) {
            BlockPos above = half.above();
            covered.add(level.getBlockState(above).isRedstoneConductor(level, above));
        }
        if (!ChestOpenRule.blocked(covered)) {
            return List.of();
        }
        List<BlockPos> blocked = new ArrayList<>(halves.size());
        for (int index = 0; index < halves.size(); index++) {
            if (covered.get(index)) {
                blocked.add(halves.get(index).above());
            }
        }
        return blocked;
    }

    private static List<BlockPos> halves(BlockPos at, BlockState state) {
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return List.of(at);
        }
        return List.of(at, at.relative(ChestBlock.getConnectedDirection(state)));
    }
}
