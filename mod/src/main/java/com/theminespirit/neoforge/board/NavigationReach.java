package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.DispatchReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.LevelNavWorld;
import com.theminespirit.neoforge.movement.RescueReach;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

final class NavigationReach implements DispatchReach.Cache {
    private final RescueReach.Cache cache;
    private final LevelNavWorld world;
    private final Predicate<BlockCoord> water;
    private final long now;

    private NavigationReach(ServerLevel level, RescueReach.Cache cache, long now) {
        this.cache = cache;
        this.now = now;
        this.world = new LevelNavWorld(level);
        this.water = cell ->
                level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z())).is(FluidTags.WATER);
    }

    static DispatchReach.Cache create(ServerLevel level, RescueReach.Cache cache, long now) {
        return new NavigationReach(level, cache, now);
    }

    @Override
    public Optional<DispatchReach.Verdict> verdict(UUID spirit, BlockCoord from, BlockCoord target) {
        RescueReach.Verdict verdict = cache.verdict(spirit, from, target, now);
        if (verdict != null) {
            return Optional.of(new DispatchReach.Verdict(verdict.reachable(), verdict.why()));
        }
        return Optional.empty();
    }

    @Override
    public void request(UUID spirit, BlockCoord from, BlockCoord target) {
        cache.request(world, water, spirit, from, target, now);
    }
}
