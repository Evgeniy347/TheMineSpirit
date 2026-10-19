package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.RoutePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.PatrolParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.phys.Vec3;

public final class PatrolScript implements ScriptInstance {
    private static final String INDEX_KEY = "index";
    private static final String SKIPPED_KEY = "skipped";
    private static final String LAPS_KEY = "laps";
    private static final String VISITED_KEY = "visited";
    private static final String TURNED_KEY = "turned";

    private final SpiritEntity spirit;
    private final RoutePlan plan;
    private final int loops;
    private final Approach approach;
    private final Set<Integer> skipped = new TreeSet<>();
    private int index;
    private int laps;
    private int visited;
    private int refused;
    private boolean turned;
    private boolean walking;
    private String failureReason;

    public PatrolScript(SpiritEntity spirit, PatrolParams params) {
        this.spirit = spirit;
        this.plan = new RoutePlan(params.points(), params.mode());
        this.loops = params.loops();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез во время патруля: " + summary());
        }
        if (!walking) {
            ScriptStatus status = beginPoint();
            if (status != null) {
                return status;
            }
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                visited++;
                yield advance();
            }
            case STUCK, TIMEOUT -> {
                skipped.add(index);
                yield advance();
            }
        };
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
        saved.put(INDEX_KEY, index);
        saved.put(SKIPPED_KEY, new ArrayList<>(skipped));
        saved.put(LAPS_KEY, laps);
        saved.put(VISITED_KEY, visited);
        saved.put(TURNED_KEY, turned);
        return saved;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        index = clampIndex(intOf(state.get(INDEX_KEY)));
        laps = Math.max(0, intOf(state.get(LAPS_KEY)));
        visited = Math.max(0, intOf(state.get(VISITED_KEY)));
        turned = Boolean.TRUE.equals(state.get(TURNED_KEY));
        skipped.clear();
        if (state.get(SKIPPED_KEY) instanceof List<?> saved) {
            for (Object value : saved) {
                skipped.add(clampIndex(intOf(value)));
            }
        }
        if (turned) {
            plan.next(plan.points().size() - 1, skipped);
        }
    }

    private ScriptStatus beginPoint() {
        while (true) {
            BlockCoord point = plan.points().get(index);
            String refusal = skipped.contains(index) ? "пропущена" : TravelCheck.refusal(spirit, point);
            if (refusal == null) {
                approach.begin(
                        new Vec3(point.x() + 0.5, point.y(), point.z() + 0.5), CatalogLimits.NAV_ARRIVAL_TOLERANCE);
                walking = true;
                return null;
            }
            if (!skipped.contains(index)) {
                refused++;
                skipped.add(index);
            }
            ScriptStatus status = advance();
            if (status != ScriptStatus.RUNNING) {
                return status;
            }
        }
    }

    private ScriptStatus advance() {
        walking = false;
        Optional<Integer> next = plan.next(index, skipped);
        if (next.isEmpty()) {
            return fail("патруль не дошёл ни до одной точки: " + summary());
        }
        countLap(next.get());
        index = next.get();
        if (laps >= loops) {
            return ScriptStatus.SUCCEEDED;
        }
        return ScriptStatus.RUNNING;
    }

    private void countLap(int next) {
        if (plan.mode() == RoutePlan.Mode.LOOP) {
            if (next <= index) {
                laps++;
            }
            return;
        }
        if (next < index) {
            turned = true;
        } else if (next > index && turned) {
            turned = false;
            laps++;
        } else if (next == index) {
            laps++;
        }
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private String summary() {
        int total = plan.points().size();
        return "прошёл " + visited + " из " + total + " точек; недостижимо: " + skipped.size()
                + (refused > 0 ? ", из них отказано зоной: " + refused : "");
    }

    private int clampIndex(int value) {
        return Math.max(0, Math.min(plan.points().size() - 1, value));
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
