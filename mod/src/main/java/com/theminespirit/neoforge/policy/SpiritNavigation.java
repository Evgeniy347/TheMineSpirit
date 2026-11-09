package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.WaterTasks;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

public final class SpiritNavigation extends GroundPathNavigation {
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
