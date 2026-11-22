package com.theminespirit.neoforge;

import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class SweepPillar {
    enum Step {
        RUNNING,
        PLACED,
        FAILED
    }

    private static final double PILLAR_CLEARANCE = 1.0;
    private static final int MAX_STALL_TICKS = 200;

    private final SpiritEntity spirit;
    private final LeaseRun lease;
    private final String blockId;
    private final BlockState state;
    private final BlockPlacer placer;
    private final int x;
    private final int z;
    private final List<BlockCoord> cells = new ArrayList<>();
    private int targetY;
    private int stallTicks;
    private String failureReason;
    private FailureFact failureFact;

    private SweepPillar(SpiritEntity spirit, LeaseRun lease, String blockId) {
        this.spirit = spirit;
        this.lease = lease;
        this.blockId = blockId;
        this.state = BlockStates.stateFor(blockId);
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.TEMPORARY_MOVEMENT);
        BlockPos feet = spirit.blockPosition();
        this.x = feet.getX();
        this.z = feet.getZ();
        this.targetY = feet.getY();
    }

    static boolean hasMaterial(SpiritEntity spirit) {
        return PillarBlocks.cheapBlock(spirit) != null;
    }

    static SweepPillar start(SpiritEntity spirit) {
        String block = PillarBlocks.cheapBlock(spirit);
        if (block == null) {
            throw new IllegalStateException("нет материала для столба: нужны земля или булыжник");
        }
        return new SweepPillar(spirit, LeaseRun.open(spirit, "scaffold"), block);
    }

    Step tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время подъёма по столбу", FailureFact.NO_PATH);
        }
        Level level = spirit.level();
        if (targetY > CatalogLimits.WORLD_Y_MAX) {
            return fail("достигнут потолок мира на y=" + targetY + ": столб дальше не строится", FailureFact.NO_PATH);
        }
        BlockPos target = new BlockPos(x, targetY, z);
        BlockState current = level.getBlockState(target);
        if (!current.isAir() && !current.canBeReplaced()) {
            return fail("клетка " + target.toShortString() + " занята: потолок или помеха", FailureFact.NO_PATH);
        }
        BlockPos head = target.above();
        BlockState above = level.getBlockState(head);
        if (!above.isAir() && !above.canBeReplaced()) {
            return fail(
                    "над клеткой " + target.toShortString() + " блок " + BlockStates.idOf(above)
                            + ": потолок, дух не поднимется",
                    FailureFact.NO_PATH);
        }
        if (PillarColumn.count(spirit, blockId) < 1) {
            return fail(
                    "нет материала '" + blockId + "': закончился на " + cells.size() + "-м блоке столба",
                    FailureFact.NO_MATERIAL);
        }
        if (spirit.getY() >= targetY + PILLAR_CLEARANCE) {
            BlockPlacer.Begin begin = placer.begin(target, state, blockId);
            if (begin instanceof BlockPlacer.Started) {
                cells.add(new BlockCoord(target.getX(), target.getY(), target.getZ()));
                lease.placed(target, blockId);
                targetY++;
                stallTicks = 0;
                return Step.PLACED;
            }
            if (begin instanceof BlockPlacer.Protected protectedBlock) {
                return fail("ставить запрещено: " + protectedBlock.reason(), FailureFact.NO_PATH);
            }
            BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
            return switch (refused.reason()) {
                case NO_MATERIAL -> fail(refused.message(), FailureFact.NO_MATERIAL);
                default -> fail(refused.message(), FailureFact.NO_PATH);
            };
        }
        if (++stallTicks > MAX_STALL_TICKS) {
            return fail(
                    "дух не поднялся на " + (targetY - cells.size()) + "-й блок за " + MAX_STALL_TICKS
                            + " тиков (дух на " + spirit.blockPosition().toShortString() + "): вероятно, потолок",
                    FailureFact.NO_PATH);
        }
        if (spirit.onGround() || spirit.verticalCollisionBelow) {
            spirit.getJumpControl().jump();
        }
        return Step.RUNNING;
    }

    LeaseRun lease() {
        return lease;
    }

    List<BlockCoord> cells() {
        return List.copyOf(cells);
    }

    String failureReason() {
        return failureReason == null ? "" : failureReason;
    }

    FailureFact failureFact() {
        return failureFact;
    }

    private Step fail(String reason, FailureFact fact) {
        failureReason = reason;
        failureFact = fact;
        lease.orphan();
        return Step.FAILED;
    }
}
