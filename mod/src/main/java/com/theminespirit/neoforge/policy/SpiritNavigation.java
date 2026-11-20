package com.theminespirit.neoforge.policy;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.api.PathReuse;
import com.theminespirit.core.policy.WaterTasks;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SpiritNavigation extends GroundPathNavigation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NO_PATH_LOG_TICKS = 100;
    private @Nullable BlockPos requestedTarget;
    private long noPathLogTick = Long.MIN_VALUE;

    public SpiritNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new SpiritNodeEvaluator(this::waterAllowed);
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        return reuseOrRebuild(x, y, z, 0, speed);
    }

    @Override
    public boolean moveTo(double x, double y, double z, int floorLevel, double speed) {
        return reuseOrRebuild(x, y, z, floorLevel, speed);
    }

    private boolean reuseOrRebuild(double x, double y, double z, int floorLevel, double speed) {
        BlockPos want = BlockPos.containing(x, y, z);
        Path path = getPath();
        BlockPos last = requestedTarget;
        if (PathReuse.keep(
                last != null,
                last == null ? 0 : last.getX(),
                last == null ? 0 : last.getY(),
                last == null ? 0 : last.getZ(),
                want.getX(),
                want.getY(),
                want.getZ(),
                path != null && !path.isDone())) {
            return true;
        }
        requestedTarget = want;
        Path built = createPath(x, y, z, floorLevel);
        if (built == null) {
            logNoPath(want, floorLevel);
            return false;
        }
        return moveTo(built, speed);
    }

    private void logNoPath(BlockPos want, int floorLevel) {
        long now = mob.level().getGameTime();
        if (now - noPathLogTick < NO_PATH_LOG_TICKS) {
            return;
        }
        noPathLogTick = now;
        BlockPos feet = mob.blockPosition();
        LOGGER.info(
                "[nav] дух {}: пути к {} (уровень {}) нет из {}, цель навигации {}",
                mob.getName().getString(),
                want,
                floorLevel,
                feet.toShortString(),
                requestedTarget == null ? "-" : requestedTarget.toShortString());
    }

    public PathType classify(PathfindingContext context, BlockPos pos) {
        return nodeEvaluator.getPathType(context, pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean waterAllowed() {
        return mob instanceof SpiritEntity spirit
                && spirit.brainIfPresent()
                        .flatMap(SpiritBrain::currentScript)
                        .map(WaterTasks::allowsSwimming)
                        .orElse(false);
    }
}
