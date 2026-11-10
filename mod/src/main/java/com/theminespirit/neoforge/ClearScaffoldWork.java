package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.mining.ClearScaffold;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class ClearScaffoldWork {
    enum Result {
        IDLE,
        RUNNING,
        FAILED,
        DONE
    }

    private static final int LANDING_TICKS = 40;
    private static final String DIRT = "minecraft:dirt";

    private final SpiritEntity spirit;
    private final ClearAreaSession session;
    private @Nullable TaskRunner acquire;
    private int acquireHeight;
    private @Nullable ScaffoldClimb climb;
    private @Nullable LeaseRun lease;
    private List<BlockCoord> scaffold = List.of();
    private boolean climbed;
    private @Nullable PillarDismantler step;
    private @Nullable BlockCoord stepCell;
    private int landingTicks;
    private @Nullable PillarDismantler descend;
    private boolean descendTried;

    ClearScaffoldWork(SpiritEntity spirit, ClearAreaSession session) {
        this.spirit = spirit;
        this.session = session;
    }

    Result tick() {
        if (acquire != null) {
            return tickAcquire();
        }
        if (climb != null) {
            return tickClimb();
        }
        if (step != null) {
            return tickStep();
        }
        if (descend != null) {
            return tickDescend();
        }
        if (session.outcome() == ClearAreaSession.Outcome.FAILED) {
            orphan();
            return Result.FAILED;
        }
        if (session.outcome() == ClearAreaSession.Outcome.SUCCEEDED) {
            return finish();
        }
        if (!session.picking()) {
            return Result.IDLE;
        }
        ClearScaffold.Decision decision = decide();
        return switch (decision.action()) {
            case CLIMB -> startClimb(decision.height());
            case DESCEND -> startStep() ? Result.RUNNING : Result.IDLE;
            default -> Result.IDLE;
        };
    }

    void cancel() {
        if (acquire != null) {
            acquire.cancel();
            acquire = null;
        }
        if (climb != null) {
            climb.cancel();
            climb = null;
        }
        step = null;
        descend = null;
        orphan();
    }

    private ClearScaffold.Decision decide() {
        BlockPos feet = spirit.blockPosition();
        ClearScaffold.Decision decision = ClearScaffold.decide(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                session.targets.highestWork(session.remaining),
                MiningTuning.MAX_STAND_RISE,
                PillarDownScript.ownBelow(spirit));
        if (decision.action() == ClearScaffold.Action.CLIMB && climbed) {
            return new ClearScaffold.Decision(ClearScaffold.Action.WORK, 0);
        }
        return decision;
    }

    private Result startClimb(int rise) {
        acquireHeight = rise;
        if (PillarColumn.count(spirit, DIRT) >= rise) {
            return raise(rise);
        }
        acquire = TaskRunner.start(
                new ScriptCall("acquire", Map.of("item", DIRT, "count", rise)),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
        return Result.RUNNING;
    }

    private Result raise(int rise) {
        if (PillarColumn.cheapBlock(spirit) == null) {
            session.fail("высокая клетка: нет материала для подмостей — нужны земля или булыжник");
            return Result.FAILED;
        }
        climb = new ScaffoldClimb(spirit, rise);
        return Result.RUNNING;
    }

    private Result tickAcquire() {
        TaskRunner runner = acquire;
        if (runner == null) {
            return Result.RUNNING;
        }
        if (runner.isSuspended()) {
            runner.resume();
        }
        if (runner.tick() == RunStatus.RUNNING) {
            return Result.RUNNING;
        }
        acquire = null;
        if (runner.status() != RunStatus.SUCCEEDED) {
            session.fail("высокая клетка: материал для подмостей не собран — "
                    + runner.failureReason().orElse("причина неизвестна"));
            return Result.FAILED;
        }
        return raise(acquireHeight);
    }

    private Result tickClimb() {
        ScaffoldClimb runner = climb;
        if (runner == null) {
            return Result.RUNNING;
        }
        return switch (runner.tick()) {
            case RUNNING -> Result.RUNNING;
            case BUILT -> {
                lease = runner.lease();
                scaffold = runner.cells();
                climbed = true;
                climb = null;
                yield Result.RUNNING;
            }
            case FAILED -> {
                session.fail("высокая клетка: столб не построен — " + runner.failureReason());
                yield Result.FAILED;
            }
        };
    }

    private boolean startStep() {
        BlockPos below = spirit.blockPosition().below();
        if (!PillarDownScript.ownBelow(spirit)) {
            return false;
        }
        BlockCoord cell = new BlockCoord(below.getX(), below.getY(), below.getZ());
        stepCell = cell;
        landingTicks = 0;
        step = new PillarDismantler(spirit, List.of(cell), lease, true);
        return true;
    }

    private Result tickStep() {
        PillarDismantler runner = step;
        if (runner == null) {
            return Result.RUNNING;
        }
        return switch (runner.tick()) {
            case RUNNING -> Result.RUNNING;
            case DONE -> landed();
            case FAILED -> {
                session.fail("спуск по своим подмостям: " + runner.failureReason());
                orphan();
                yield Result.FAILED;
            }
        };
    }

    private Result landed() {
        BlockCoord cell = stepCell;
        if (cell != null && (spirit.getBlockY() > cell.y() || !spirit.onGround())) {
            if (++landingTicks > LANDING_TICKS) {
                session.fail("спуск по своим подмостям: дух не встал на " + cell + " за " + LANDING_TICKS
                        + " тиков (стоит на " + spirit.blockPosition() + ")");
                orphan();
                return Result.FAILED;
            }
            return Result.RUNNING;
        }
        step = null;
        stepCell = null;
        landingTicks = 0;
        return Result.RUNNING;
    }

    private Result finish() {
        if (!climbed || descendTried) {
            return Result.DONE;
        }
        descendTried = true;
        if (lease == null) {
            return Result.DONE;
        }
        if (lease.cells().isEmpty()) {
            lease.commit();
            return Result.DONE;
        }
        List<BlockCoord> order = new ArrayList<>(scaffold);
        Collections.reverse(order);
        descend = new PillarDismantler(spirit, order, lease);
        return tickDescend();
    }

    private Result tickDescend() {
        PillarDismantler runner = descend;
        if (runner == null) {
            return Result.DONE;
        }
        PillarDismantler.Outcome result = runner.tick();
        if (result == PillarDismantler.Outcome.RUNNING) {
            return Result.RUNNING;
        }
        descend = null;
        if (result == PillarDismantler.Outcome.FAILED) {
            session.fail("разбор своих подмостей: " + runner.failureReason());
            orphan();
            return Result.FAILED;
        }
        return Result.DONE;
    }

    private void orphan() {
        if (lease != null) {
            lease.orphan();
        }
    }
}
