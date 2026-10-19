package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.neoforge.movement.TravelCheck;
import net.minecraft.world.phys.Vec3;

public final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private final PathMode mode;
    private String failureReason;
    private boolean zoneChecked;

    public GotoScript(SpiritEntity spirit, GotoParams params) {
        this(spirit, params, PathMode.NORMAL);
    }

    private GotoScript(SpiritEntity spirit, GotoParams params, PathMode mode) {
        this.spirit = spirit;
        this.mode = mode;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
        this.approach = new Approach(spirit);
        this.approach.begin(target, ARRIVAL_TOLERANCE, mode);
    }

    public static GotoScript careful(SpiritEntity spirit, WalkCarefulParams params) {
        return new GotoScript(spirit, new GotoParams(params.x(), params.y(), params.z()), PathMode.CAREFUL);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            failureReason = "дух исчез до достижения цели";
            return ScriptStatus.FAILED;
        }
        if (!zoneChecked) {
            zoneChecked = true;
            BlockCoord goal =
                    new BlockCoord((int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z));
            String refusal = TravelCheck.refusal(spirit, goal);
            if (refusal != null) {
                approach.cancel();
                failureReason = refusal;
                return ScriptStatus.FAILED;
            }
        }
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT -> {
                failureReason = failedToArriveReason();
                yield ScriptStatus.FAILED;
            }
            case STUCK -> {
                failureReason = stuckReason();
                yield ScriptStatus.FAILED;
            }
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

    private String failedToArriveReason() {
        return "не дошёл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + ") за " + approach.timeoutTicks()
                + " тиков";
    }

    private String stuckReason() {
        if (mode == PathMode.CAREFUL && approach.refusal() != null) {
            return "недостижимо осторожным шагом до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + "): "
                    + approach.refusal();
        }
        return "застрял в (x=" + spirit.getX() + ", y=" + spirit.getY() + ", z=" + spirit.getZ() + ")";
    }
}
