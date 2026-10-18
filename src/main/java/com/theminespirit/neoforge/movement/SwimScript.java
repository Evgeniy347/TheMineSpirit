package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.phys.Vec3;

public final class SwimScript implements ScriptInstance {
    private static final int SURFACE_AIR_THRESHOLD = 120;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private boolean zoneChecked;
    private String failureReason;

    public SwimScript(SpiritEntity spirit, SwimParams params) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
        this.approach = new Approach(spirit);
        this.approach.begin(target, CatalogLimits.NAV_ARRIVAL_TOLERANCE);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            failureReason = "дух исчез во время переправы";
            return ScriptStatus.FAILED;
        }
        if (!zoneChecked) {
            zoneChecked = true;
            BlockCoord goal = new BlockCoord((int) Math.floor(target.x), (int) Math.floor(target.y),
                    (int) Math.floor(target.z));
            String refusal = TravelCheck.refusal(spirit, goal);
            if (refusal != null) {
                approach.cancel();
                failureReason = refusal;
                return ScriptStatus.FAILED;
            }
        }
        surfaceForAir();
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT -> fail("не доплыл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + ") за "
                    + approach.timeoutTicks() + " тиков");
            case STUCK -> fail(stuckReason());
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

    private void surfaceForAir() {
        if (spirit.isUnderWater() && spirit.getAirSupply() < SURFACE_AIR_THRESHOLD) {
            spirit.getJumpControl().jump();
        }
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private String stuckReason() {
        if (approach.refusal() != null) {
            return "не доплыл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + "): "
                    + approach.refusal();
        }
        return "не доплыл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + "): путь перекрыт";
    }
}
