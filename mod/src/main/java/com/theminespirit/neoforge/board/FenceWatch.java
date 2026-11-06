package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FenceBreaks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.FenceLedger;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashSet;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class FenceWatch {
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
        Set<String> live = new LinkedHashSet<>();
        liveBreaks(level).keySet().forEach(at -> live.add(FenceBreaks.cause(at)));
        return live;
    }

    static Map<BlockCoord, String> liveBreaks(ServerLevel level) {
        return FenceLedger.get(level).retainLive(at -> {
            BlockPos block = pos(at);
            return !level.isLoaded(block) || !isFence(level.getBlockState(block));
        });
    }

    static String fenceCause(BlockPos pos) {
        return "fence:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    static boolean isFence(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock
                || block instanceof WallBlock
                || block instanceof FenceGateBlock
                || block instanceof IronBarsBlock;
    }

    static String fenceId(BlockPos pos) {
        return "repair_fence:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static BlockPos pos(BlockCoord at) {
        return new BlockPos(at.x(), at.y(), at.z());
    }

    private static boolean nearSettlement(ServerLevel level, BlockPos pos) {
        long limit = (long) BoardTuning.FENCE_WATCH_RADIUS * BoardTuning.FENCE_WATCH_RADIUS;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof SpiritEntity spirit) || spirit.isRemoved()) {
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
