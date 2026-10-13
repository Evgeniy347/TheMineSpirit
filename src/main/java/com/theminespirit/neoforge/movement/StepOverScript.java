package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.StepOverParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class StepOverScript implements ScriptInstance {
    public static final int MAX_STEP_UP = 1;
    public static final double STEP_ARRIVAL = 0.5;

    private final SpiritEntity spirit;
    private final int dx;
    private final int dz;
    private final Approach approach;
    private boolean started;
    private String failureReason;

    public StepOverScript(SpiritEntity spirit, StepOverParams params) {
        this.spirit = spirit;
        this.dx = params.dx();
        this.dz = params.dz();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез на полпути шага");
        }
        if (!started) {
            started = true;
            BlockCoord cell = steppableCell();
            if (cell == null) {
                return fail("шаг (dx=" + dx + ", dz=" + dz + ") невозможен: соседняя клетка на той же высоте или на "
                        + MAX_STEP_UP + " выше не годится для стоянки (нет пола, занята или рядом опасность)");
            }
            String refusal = TravelCheck.refusal(spirit, cell);
            if (refusal != null) {
                return fail(refusal);
            }
            approach.begin(new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5), STEP_ARRIVAL);
        }
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case STUCK -> fail("застрял, не дойдя до соседней клетки");
            case TIMEOUT -> fail("не дошёл до соседней клетки за " + approach.timeoutTicks() + " тиков");
        };
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private BlockCoord steppableCell() {
        BlockPos feet = spirit.blockPosition();
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        for (int up = 0; up <= MAX_STEP_UP; up++) {
            BlockCoord cell = new BlockCoord(feet.getX() + dx, feet.getY() + up, feet.getZ() + dz);
            if (MiningSafety.canStand(cell, safety)) {
                return cell;
            }
        }
        return null;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
