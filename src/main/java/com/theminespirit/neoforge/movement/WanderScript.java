package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.WanderPolicy;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.WanderParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class WanderScript implements ScriptInstance {
    public static final int GROUND_SEARCH_DEPTH = 4;

    private static final String SEED_KEY = "seed";
    private static final String ATTEMPT_KEY = "attempt";
    private static final String ELAPSED_KEY = "elapsed";
    private static final String ORIGIN_KEY = "origin";

    private final SpiritEntity spirit;
    private final int radius;
    private final int durationTicks;
    private final Approach approach;
    private final LevelSafetyView safety;
    private long seed;
    private int attempt;
    private int elapsed;
    private BlockCoord origin;
    private boolean walking;
    private String failureReason;

    public WanderScript(SpiritEntity spirit, WanderParams params) {
        this.spirit = spirit;
        this.radius = params.radius();
        this.durationTicks = params.durationTicks();
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
        this.seed = spirit.getUUID().getLeastSignificantBits() ^ spirit.getUUID().getMostSignificantBits();
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            failureReason = "дух исчез во время блуждания";
            return ScriptStatus.FAILED;
        }
        if (origin == null) {
            BlockPos feet = spirit.blockPosition();
            origin = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        }
        if (elapsed++ >= durationTicks) {
            approach.cancel();
            return ScriptStatus.SUCCEEDED;
        }
        if (!walking) {
            walking = chooseNextPoint();
            return ScriptStatus.RUNNING;
        }
        if (approach.tick() != Approach.State.MOVING) {
            walking = false;
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        approach.cancel();
        walking = false;
    }

    @Override
    public void suspend() {
        approach.suspend();
        walking = false;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(SEED_KEY, seed);
        saved.put(ATTEMPT_KEY, attempt);
        saved.put(ELAPSED_KEY, elapsed);
        if (origin != null) {
            saved.put(ORIGIN_KEY, List.of(origin.x(), origin.y(), origin.z()));
        }
        return saved;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(SEED_KEY) instanceof Number number) {
            seed = number.longValue();
        }
        if (state.get(ATTEMPT_KEY) instanceof Number number) {
            attempt = Math.max(0, number.intValue());
        }
        if (state.get(ELAPSED_KEY) instanceof Number number) {
            elapsed = Math.max(0, number.intValue());
        }
        if (state.get(ORIGIN_KEY) instanceof List<?> list && list.size() == 3
                && list.get(0) instanceof Number x && list.get(1) instanceof Number y && list.get(2) instanceof Number z) {
            origin = new BlockCoord(x.intValue(), y.intValue(), z.intValue());
        }
    }

    private boolean chooseNextPoint() {
        BlockCoord drawn = WanderPolicy.next(seed, origin, radius, attempt++);
        for (int drop = 0; drop <= GROUND_SEARCH_DEPTH; drop++) {
            BlockCoord stand = new BlockCoord(drawn.x(), drawn.y() - drop, drawn.z());
            if (MiningSafety.canStand(stand, safety) && TravelCheck.refusal(spirit, stand) == null) {
                approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5),
                        CatalogLimits.NAV_ARRIVAL_TOLERANCE);
                return true;
            }
        }
        return false;
    }
}
