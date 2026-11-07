package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.StillWatch;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

final class DigOutAction {
    private static final long PAUSE_TICKS = 1200;
    private static final String DIG_OUT = "dig_out";

    private final StillWatch still = new StillWatch();
    private final Map<UUID, Long> pausedUntil = new HashMap<>();

    void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        BlockCoord settlement = SettlementHome.of(spirits);
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            if (service.board()
                    .statusOf("rescue:" + spirit.getUUID())
                    .filter(status -> status == BoardStatus.PENDING
                            || status == BoardStatus.ASSIGNED
                            || status == BoardStatus.WAITING_RESOURCES)
                    .isPresent()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            boolean digging = DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
            boolean paused = now < pausedUntil.getOrDefault(spirit.getUUID(), 0L);
            if (digging || paused) {
                continue;
            }
            if (DigOutScript.embedded(spirit)) {
                pausedUntil.put(spirit.getUUID(), now + PAUSE_TICKS);
                start(service, spirit, SurfacePoint.of(level, settlement));
                continue;
            }
            Optional<PitExitPlanner.Plan> pit = PitRescue.trapped(spirit);
            if (pit.isPresent()) {
                if (pit.orElseThrow().possible() && service.idleOrActivity(spirit)) {
                    pausedUntil.put(spirit.getUUID(), now + PAUSE_TICKS);
                    service.detachForAction(spirit);
                    spirit.startTask(PitRescue.plan(spirit, pit.orElseThrow()));
                }
                continue;
            }
            if (DigOutScript.open(level, feet)) {
                continue;
            }
            boolean stillInWater = inWater(level, feet) && still.still(spirit.getUUID(), coord(feet), now, PAUSE_TICKS);
            if (DigOutScript.sealed(level, feet) || stillInWater) {
                pausedUntil.put(spirit.getUUID(), now + PAUSE_TICKS);
                start(service, spirit, SurfacePoint.of(level, settlement));
            }
        }
    }

    private static void start(BoardService service, SpiritEntity spirit, BlockCoord goal) {
        service.detachForAction(spirit);
        TaskNode escape = new SequenceNode(List.of(
                new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z())),
                new ScriptCall("walk_careful", Map.of("x", goal.x(), "y", goal.y(), "z", goal.z()))));
        spirit.startTask(escape);
    }

    private static boolean inWater(Level level, BlockPos feet) {
        return level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(feet.above()).is(FluidTags.WATER);
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
