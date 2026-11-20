package com.theminespirit.neoforge.policy;

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

public final class SpiritNavigation extends GroundPathNavigation {
    private @Nullable BlockPos requestedTarget;

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
        return moveTo(createPath(x, y, z, 0), speed);
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
