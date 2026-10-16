package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * Источник доски «сломанный забор»: поломка блока-ограждения рядом с поселением ставит
 * задание {@code REPAIR_FENCE} с высшим приоритетом. Дальняя поломка природная — задание
 * не ставится. Слушатель — O(1) на поломку, мир не обходит.
 */
public final class FenceWatch {
    private FenceWatch() {
    }

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
        BoardService service = BoardRegistry.get(level);
        BlockCoord target = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        service.submit(new BoardTicket(fenceId(pos), BoardCase.REPAIR_FENCE, Optional.empty(),
                BoardRules.priorityOf(BoardCase.REPAIR_FENCE), level.getGameTime(),
                BoardTuning.DEFAULT_TTL_TICKS, List.of(), Map.of()),
                TicketTarget.at(target).withFenceBlock(blockId));
    }

    static boolean isFence(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock || block instanceof WallBlock || block instanceof FenceGateBlock
                || block instanceof IronBarsBlock;
    }

    /** Задание ремонта адресуется сломанной клеткой: повторная поломка той же клетки — дубль. */
    static String fenceId(BlockPos pos) {
        return "repair_fence:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
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
