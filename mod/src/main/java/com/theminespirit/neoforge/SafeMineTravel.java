package com.theminespirit.neoforge;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafeMineStep;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SafeMineTravel {
    enum Step {
        RUNNING,
        DIG_BEGIN,
        APPROACH,
        COLLECT,
        TORCH,
        STEP_FINISHED,
        FAILED
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;
    private static final int FALL_SETTLE_TICKS = 8;
    private static final int FALL_WAIT_TICKS = 60;

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final Approach approach;

    private int fallWait;
    private boolean approachBegun;
    private DropCollector collector;
    private String failure;

    SafeMineTravel(SpiritEntity spirit, Level level, LevelSafetyView safety, Approach approach) {
        this.spirit = spirit;
        this.level = level;
        this.safety = safety;
        this.approach = approach;
    }

    void resetStep() {
        fallWait = 0;
        approachBegun = false;
    }

    void beginFall() {
        fallWait = 0;
    }

    void beginApproach() {
        approachBegun = false;
    }

    String failure() {
        return failure;
    }

    Step fallTick(SafeMineStep step) {
        fallWait++;
        if (fallWait < FALL_SETTLE_TICKS) {
            return Step.RUNNING;
        }
        for (BlockCoord cell : step.passage()) {
            if (!level.getBlockState(toPos(cell)).isAir()) {
                return Step.DIG_BEGIN;
            }
        }
        BlockCoord ceiling = new BlockCoord(
                step.stand().x(),
                step.floor() + step.passage().size() + 1,
                step.stand().z());
        if (safety.cellAt(ceiling) == Cell.FALLING || fallingEntityAbove(step)) {
            if (fallWait >= FALL_WAIT_TICKS) {
                failure = "сыпучий блок над проходом " + ceiling + " не осыпался за " + FALL_WAIT_TICKS
                        + " тиков: дух не лезет под него";
                return Step.FAILED;
            }
            return Step.RUNNING;
        }
        approachBegun = false;
        return Step.APPROACH;
    }

    Step approachTick(SafeMineStep step) {
        if (!approachBegun) {
            BlockCoord stand = step.stand();
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), APPROACH_ARRIVAL);
            approachBegun = true;
        }
        return switch (approach.tick()) {
            case MOVING -> Step.RUNNING;
            case ARRIVED -> {
                collector = new DropCollector(
                        spirit,
                        IdFilter.matchAll(),
                        step.stand(),
                        COLLECT_RADIUS,
                        PickupTuning.DEFAULT,
                        OverflowMode.BY_VALUE);
                yield Step.COLLECT;
            }
            case STUCK, TIMEOUT -> {
                String detail = approach.refusal() == null ? "стоянка недостижима" : approach.refusal();
                failure = "к ступени " + step.stand() + " не подойти: " + detail;
                yield Step.FAILED;
            }
        };
    }

    Step collectTick(SafeMineStep step) {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return Step.RUNNING;
        }
        collector = null;
        return step.torch() ? Step.TORCH : Step.STEP_FINISHED;
    }

    void cancel() {
        if (collector != null) {
            collector.cancel();
        }
    }

    void suspend() {
        if (collector != null) {
            collector.suspend();
        }
    }

    private boolean fallingEntityAbove(SafeMineStep step) {
        AABB column = new AABB(
                step.stand().x(),
                step.stand().y() - 1,
                step.stand().z(),
                step.stand().x() + 1,
                step.stand().y() + 4,
                step.stand().z() + 1);
        return !level.getEntitiesOfClass(FallingBlockEntity.class, column).isEmpty();
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
