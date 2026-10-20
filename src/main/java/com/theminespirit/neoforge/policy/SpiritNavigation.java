package com.theminespirit.neoforge.policy;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

public final class SpiritNavigation extends GroundPathNavigation {
    static final float WALK_RANGE = 96.0F;
    private static final float VISITED_NODES_MULTIPLIER = 4.0F;

    public SpiritNavigation(Mob mob, Level level) {
        super(mob, level);
        setMaxVisitedNodesMultiplier(VISITED_NODES_MULTIPLIER);
    }

    // mc: поиск пути игры обрывает маршрут длиннее дальности следования (FOLLOW_RANGE, 32 шага пути, а не прямой) и
    // ведёт к ближайшей по прямой клетке. Из глубины большого дома (второй этаж особняка, усадьбы) путь к сундуку у
    // входа — 40–60 шагов по лестнице и коридорам, и дух уходил на балкон над входом и вставал там (разбор 20.10,
    // HouseMovementGameTests). Длина пути — WALK_RANGE, узлов поиска — с запасом под неё.
    @Override
    protected Path createPath(
            Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, WALK_RANGE));
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new SpiritNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    public PathType classify(PathfindingContext context, BlockPos pos) {
        return nodeEvaluator.getPathType(context, pos.getX(), pos.getY(), pos.getZ());
    }
}
