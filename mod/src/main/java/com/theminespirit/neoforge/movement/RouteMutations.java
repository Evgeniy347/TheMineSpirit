package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.RemovalLog;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

final class RouteMutations {
    private final SpiritEntity spirit;
    private final RouteWalk walk;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final RemovalLog removals = new RemovalLog();
    private @Nullable LeaseRun lease;
    private boolean breaking;
    private boolean pillarPlaced;
    private String breakingId = "-";

    RouteMutations(SpiritEntity spirit, RouteWalk walk, boolean rescue) {
        this.spirit = spirit;
        this.walk = walk;
        this.breaker = rescue ? BlockBreaker.escaping(spirit, true) : new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.TEMPORARY_MOVEMENT);
    }

    boolean busy() {
        return breaking;
    }

    RemovalLog removals() {
        return removals;
    }

    Optional<String> breakCell(BlockPos cell) {
        Level level = spirit.level();
        boolean active = breaking;
        if (active) {
            BlockBreaker.Progress progress = breaker.tick();
            if (progress == BlockBreaker.Progress.BREAKING) {
                return Optional.empty();
            }
            active = false;
            if (progress == BlockBreaker.Progress.BROKEN) {
                removals.removed(breakingId, new BlockCoord(cell.getX(), cell.getY(), cell.getZ()));
                lease().broken(cell, breakingId);
            }
        }
        if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
            breaking = active;
            return walk.advanceStep();
        }
        Optional<String> result = switch (breaker.begin(cell)) {
            case BlockBreaker.Started ignored -> {
                active = true;
                breakingId = BlockStates.idOf(level.getBlockState(cell));
                yield Optional.empty();
            }
            case BlockBreaker.Refused refused -> Optional.of(refused.message());
            case BlockBreaker.Protected refused -> Optional.of(refused.reason());
        };
        breaking = active;
        return result;
    }

    Optional<String> placeCell(BlockPos cell) {
        Level level = spirit.level();
        if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
            return walk.advanceStep();
        }
        Optional<String> placed = place(cell);
        if (placed.isEmpty()) {
            return walk.advanceStep();
        }
        return placed;
    }

    Optional<String> pillar(BlockCoord cell) {
        BlockPos below = pos(cell);
        if (pillarPlaced) {
            if (spirit.onGround() && spirit.blockPosition().getY() == cell.y() + 1) {
                pillarPlaced = false;
                return walk.advanceStep();
            }
            return Optional.empty();
        }
        spirit.getNavigation().stop();
        if (spirit.onGround()) {
            spirit.getJumpControl().jump();
            return Optional.empty();
        }
        if (spirit.getY() > cell.y() + 1.0) {
            Optional<String> placed = place(below);
            pillarPlaced = placed.isEmpty();
            return placed;
        }
        return Optional.empty();
    }

    void cancel() {
        if (breaking) {
            breaker.cancel();
            breaking = false;
        }
        if (lease != null) {
            lease.orphan();
        }
    }

    void complete() {
        if (lease != null) {
            lease.commit();
        }
    }

    private LeaseRun lease() {
        if (lease == null) {
            lease = LeaseRun.open(spirit, "route");
        }
        return lease;
    }

    private Optional<String> place(BlockPos cell) {
        Optional<String> blockId = WaterCrossingPlanner.cheapestAvailable(CheapBlocks.counts(spirit));
        if (blockId.isEmpty()) {
            return Optional.of("в инвентаре нет дешёвых блоков для постановки");
        }
        String id = blockId.get();
        return switch (placer.beginWithoutSupport(cell, BlockStates.stateFor(id), id)) {
            case BlockPlacer.Started ignored -> {
                lease().placed(cell, id);
                yield Optional.empty();
            }
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
