package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.CrossingSpan;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SwimScript implements ScriptInstance {
    private static final int SURFACE_AIR_THRESHOLD = 120;
    private static final double SWIM_SPEED = 1.0;
    private static final double ARRIVAL = CatalogLimits.NAV_ARRIVAL_TOLERANCE;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private final @Nullable BlockCoord dry;
    private final @Nullable BlockCoord wet;
    private boolean swam;
    private boolean zoneChecked;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public SwimScript(SpiritEntity spirit, SwimParams params) {
        this(spirit, params, null, null);
    }

    public SwimScript(SpiritEntity spirit, SwimParams params, CrossingSpan span) {
        this(spirit, params, span.dry(), span.wet());
    }

    private SwimScript(SpiritEntity spirit, SwimParams params, @Nullable BlockCoord dry, @Nullable BlockCoord wet) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
        this.approach = new Approach(spirit);
        this.dry = dry;
        this.wet = wet;
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
            swam = true;
            return swimTowards();
        }
        if (swam || dry == null || wet == null) {
            return walkAshore();
        }
        return walkToWater();
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

    private ScriptStatus walkToWater() {
        @Nullable BlockCoord entryDry = dry;
        @Nullable BlockCoord entryWet = wet;
        if (entryWet == null) {
            return walkAshore();
        }
        if (entryDry != null && spirit.position().distanceTo(center(entryDry)) > ARRIVAL + 0.75) {
            Vec3 shore = center(entryDry);
            if (!approach.isActive()) {
                approach.begin(shore, ARRIVAL, PathMode.SWIM);
            }
            return switch (approach.tick()) {
                case ARRIVED, MOVING -> ScriptStatus.RUNNING;
                case TIMEOUT ->
                    fail("не дошёл до берега (x=" + shore.x + ", y=" + shore.y + ", z=" + shore.z + ") за "
                            + approach.timeoutTicks() + " тиков");
                case STUCK ->
                    fail("не дошёл до берега (x=" + shore.x + ", y=" + shore.y + ", z=" + shore.z + "): путь "
                            + (approach.refusal() == null ? "перекрыт" : approach.refusal()));
            };
        }
        approach.cancel();
        Vec3 water = center(entryWet);
        spirit.getMoveControl().setWantedPosition(water.x, water.y, water.z, SWIM_SPEED);
        return ScriptStatus.RUNNING;
    }

    private static Vec3 center(BlockCoord cell) {
        return new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
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
