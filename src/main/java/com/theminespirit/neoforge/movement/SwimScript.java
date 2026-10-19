package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.phys.Vec3;

public final class SwimScript implements ScriptInstance {
    private static final int SURFACE_AIR_THRESHOLD = 120;
    private static final double SWIM_SPEED = 1.0;
    private static final double ARRIVAL = CatalogLimits.NAV_ARRIVAL_TOLERANCE;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private boolean zoneChecked;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public SwimScript(SpiritEntity spirit, SwimParams params) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время переправы");
        }
        if (!zoneChecked) {
            zoneChecked = true;
            BlockCoord goal =
                    new BlockCoord((int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z));
            String refusal = TravelCheck.refusal(spirit, goal);
            if (refusal != null) {
                return fail(refusal);
            }
        }
        surfaceForAir();
        if (spirit.isInWater()) {
            return swimTowards();
        }
        return walkAshore();
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

    private ScriptStatus swimTowards() {
        if (spirit.position().distanceTo(target) <= ARRIVAL) {
            approach.cancel();
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        spirit.getMoveControl().setWantedPosition(target.x, target.y, target.z, SWIM_SPEED);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus walkAshore() {
        if (!approach.isActive()) {
            approach.begin(target, ARRIVAL, PathMode.SWIM);
        }
        return switch (approach.tick()) {
            case ARRIVED -> {
                outcome = ScriptStatus.SUCCEEDED;
                yield outcome;
            }
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT ->
                fail("не доплыл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + ") за "
                        + approach.timeoutTicks() + " тиков");
            case STUCK ->
                fail("не доплыл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + "): путь "
                        + (approach.refusal() == null ? "перекрыт" : approach.refusal()));
        };
    }

    private void surfaceForAir() {
        boolean againstBank = spirit.isInWater() && spirit.horizontalCollision;
        if (againstBank
                || spirit.isUnderWater()
                || (spirit.isInWater() && spirit.getAirSupply() < SURFACE_AIR_THRESHOLD)) {
            spirit.getJumpControl().jump();
        }
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
    }
}
