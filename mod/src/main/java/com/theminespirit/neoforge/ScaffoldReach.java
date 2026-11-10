package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class ScaffoldReach {
    enum Result {
        RUNNING,
        DONE,
        FAILED
    }

    private static final int LANDING_TICKS = 40;
    private static final String DIRT = "minecraft:dirt";

    private final SpiritEntity spirit;
    private final String what;
    private @Nullable TaskRunner acquire;
    private int acquireRise;
    private @Nullable ScaffoldClimb climb;
    private @Nullable LeaseRun lease;
    private List<BlockCoord> scaffold = List.of();
    private boolean climbed;
    private @Nullable PillarDismantler step;
    private @Nullable BlockCoord stepCell;
    private int landingTicks;
    private @Nullable PillarDismantler descend;
    private boolean descended;
    private String failureReason;

    ScaffoldReach(SpiritEntity spirit, String what) {
        this.spirit = spirit;
        this.what = what;
    }

    Result climb(int rise) {
        if (rise < 1) {
            return failed("подъём высотой " + rise + ": столб строят хотя бы на один блок");
        }
        acquireRise = rise;
        if (PillarColumn.count(spirit, DIRT) >= rise) {
            return raise(rise);
        }
        acquire = TaskRunner.start(
                new ScriptCall("acquire", Map.of("item", DIRT, "count", rise)),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
        return Result.RUNNING;
    }

    Result stepDown() {
        if (!PillarDownScript.ownBelow(spirit)) {
            return failed("под духом нет своих подмостей: спуск шагом невозможен");
        }
        BlockCoord cell = new BlockCoord(
                spirit.blockPosition().below().getX(),
                spirit.blockPosition().below().getY(),
                spirit.blockPosition().below().getZ());
        stepCell = cell;
        landingTicks = 0;
        step = new PillarDismantler(spirit, List.of(cell), lease, true);
        return Result.RUNNING;
    }

    Result descend() {
        if (!climbed || descended) {
            return Result.DONE;
        }
        descended = true;
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

    boolean busy() {
        return acquire != null || climb != null || step != null || descend != null;
    }

    boolean climbed() {
        return climbed;
    }

    @Nullable
    LeaseRun lease() {
        return lease;
    }

    List<BlockCoord> cells() {
        return List.copyOf(scaffold);
    }

    String failureReason() {
        return failureReason == null ? "" : failureReason;
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
        return Result.DONE;
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
        if (lease != null) {
            lease.orphan();
        }
    }

    private Result raise(int rise) {
        if (PillarColumn.cheapBlock(spirit) == null) {
            return failed("нет материала для подмостей — нужны земля или булыжник");
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
            return failed("материал для подмостей не собран — "
                    + runner.failureReason().orElse("причина неизвестна"));
        }
        return raise(acquireRise);
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
                yield Result.DONE;
            }
            case FAILED -> failed("столб не построен — " + runner.failureReason());
        };
    }

    private Result tickStep() {
        PillarDismantler runner = step;
        if (runner == null) {
            return Result.RUNNING;
        }
        return switch (runner.tick()) {
            case RUNNING -> Result.RUNNING;
            case DONE -> landed();
            case FAILED -> failed("спуск по своим подмостям: " + runner.failureReason());
        };
    }

    private Result landed() {
        BlockCoord cell = stepCell;
        if (cell != null && (spirit.getBlockY() > cell.y() || !spirit.onGround())) {
            if (++landingTicks > LANDING_TICKS) {
                return failed("дух не встал на " + cell + " за " + LANDING_TICKS + " тиков (стоит на "
                        + spirit.blockPosition() + ")");
            }
            return Result.RUNNING;
        }
        step = null;
        stepCell = null;
        landingTicks = 0;
        return Result.DONE;
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
            return failed("разбор своих подмостей: " + runner.failureReason());
        }
        if (lease != null) {
            if (lease.cells().isEmpty()) {
                lease.commit();
            } else {
                lease.orphan();
            }
        }
        return Result.DONE;
    }

    private Result failed(String reason) {
        failureReason = what + ": " + reason;
        cancel();
        return Result.FAILED;
    }
}
