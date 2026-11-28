package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FenceRepairs;
import com.theminespirit.core.log.Perf;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.FenceLedger;
import com.theminespirit.neoforge.SpiritConfig;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class FenceWatch {
    private static final int SCAN_BUDGET = 8;

    private FenceWatch() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель забора не встанет");
        }
        bus.addListener(FenceWatch::onBreak);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!SpiritConfig.boardEnabled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!isFence(state)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!nearSettlement(level, pos)) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        FenceLedger.get(level).remember(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()), blockId);
    }

    static Set<String> liveCauses(ServerLevel level) {
        return liveBreaks(level).isEmpty() ? Set.of() : Set.of(FenceRepairs.CAUSE);
    }

    static Map<BlockCoord, String> liveBreaks(ServerLevel level) {
        long started = System.nanoTime();
        FenceLedger ledger = FenceLedger.get(level);
        Perf.record("board.src.fence.ledger", System.nanoTime() - started);
        started = System.nanoTime();
        Map<BlockCoord, String> live = ledger.retainLive(
                at -> {
                    BlockPos block = pos(at);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4);
                    return chunk == null || !isFence(chunk.getBlockState(block));
                },
                SCAN_BUDGET);
        Perf.record("board.src.fence.scan", System.nanoTime() - started);
        return live;
    }

    public static boolean isFence(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock
                || block instanceof WallBlock
                || block instanceof FenceGateBlock
                || block instanceof IronBarsBlock;
    }

    static BlockPos pos(BlockCoord at) {
        return new BlockPos(at.x(), at.y(), at.z());
    }

    private static boolean nearSettlement(ServerLevel level, BlockPos pos) {
        long limit = (long) BoardTuning.fenceWatchRadius() * BoardTuning.fenceWatchRadius();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof HomedSpirit spirit) || spirit.isRemoved()) {
                continue;
            }
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                BlockCoord point = home.orElseThrow();
                long dx = (long) point.x() - pos.getX();
                long dz = (long) point.z() - pos.getZ();
                if (dx * dx + dz * dz <= limit) {
                    return true;
                }
            }
        }
        return false;
    }
}
