package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PlanFilterGate;
import com.theminespirit.neoforge.board.PlotCellStructure;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ClearSweepRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int LONG_APPROACH = 60;
    private static final int LONG_LOGGED = 10;

    private final ClearAreaSession session;
    private final ClearBreaks breaks;
    private final Set<BlockCoord> triedAnchors = new HashSet<>();
    private ClearAnchor.@Nullable Stand anchor;
    private final ClearSweepScaffold scaffold;
    private final SweepUnload unload;
    private final PlanFilterGate planFilter = new PlanFilterGate();
    private final ClearPlanFilter filter = new ClearPlanFilter();
    private int approachSpent;
    private int approaches;
    private int approachFailed;
    private int fromPlace;
    private int longApproaches;

    ClearSweepRun(ClearAreaSession session) {
        this.session = session;
        this.breaks = new ClearBreaks(session);
        this.unload = new SweepUnload(session);
        this.scaffold = new ClearSweepScaffold(session, this::afterDismantle);
    }

    int approachFailed() {
        return approachFailed;
    }

    int approaches() {
        return approaches;
    }

    int fromPlace() {
        return fromPlace;
    }

    int vanished() {
        return breaks.vanished();
    }

    Map<String, Integer> brokenKinds() {
        return breaks.brokenKinds();
    }

    void filterTick() {
        if (filter.idle() && planFilter.due(PlotCellStructure.generation())) {
            filter.start(session.remaining);
        }
        filter.tick(session);
    }

    void pick() {
        session.targets.dropLeased(session.remaining);
        while (!session.remaining.isEmpty() && session.targets.clear(session.remaining.get(0))) {
            session.remaining.remove(0);
        }
        session.apply(session.blocked.dropExcluded(session.remaining));
        if (session.remaining.isEmpty()) {
            finish();
            return;
        }
        boolean onPillar = PillarDownScript.ownBelow(session.spirit);
        if (unload.tryStart()) {
            return;
        }
        BlockCoord from = feet();
        List<BlockCoord> nearby = ClearReach.inReach(session.remaining, session.spirit.getEyePosition());
        List<BlockCoord> top = ClearOrder.topmost(nearby, cell -> !session.targets.clear(cell));
        BlockCoord near = ClearReach.near(
                session.spirit,
                session.level,
                top,
                session.spirit.blockPosition(),
                session.safety,
                session.reservations);
        if (near != null) {
            session.remaining.remove(near);
            session.reservations.reserve(near, session.spirit.getUUID());
            session.currentTarget = near;
            session.currentStand = from;
            approachSpent = 0;
            fromPlace++;
            breaks.prepare();
            return;
        }
        if (onPillar) {
            if (scaffold.canRise() && riseAllowed()) {
                scaffold.startStep();
                return;
            }
            scaffold.startDescent();
            if (session.state != ClearAreaSession.State.DESCEND) {
                session.fail("стою на своих подмостях, но своих клеток для разбора нет: нужен откат аренды");
            }
            return;
        }
        if (!session.spirit.onGround()) {
            return;
        }
        ClearAnchor.Stand chosen = anchor();
        if (chosen == null) {
            session.unreachable += session.remaining.size();
            session.noteSkipped(session.remaining.size());
            session.remaining.clear();
            finish();
            return;
        }
        if (chosen.x() == from.x() && chosen.y() == from.y() && chosen.z() == from.z()) {
            if (scaffold.canRise() && riseAllowed()) {
                scaffold.startStep();
                return;
            }
            triedAnchors.add(chosen.coord());
            anchor = null;
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        session.approach.beginWithRoutes(new Vec3(chosen.x() + 0.5, chosen.y(), chosen.z() + 0.5), APPROACH_ARRIVAL);
        approaches++;
        approachSpent = 0;
        session.state = ClearAreaSession.State.APPROACH;
    }

    void approachTick() {
        approachSpent++;
        switch (session.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                session.approach.cancel();
                BlockPos feet = session.spirit.blockPosition();
                ClearAnchor.Stand chosen = anchor;
                if (chosen != null
                        && session.spirit.onGround()
                        && ClearArrival.reached(chosen, feet.getX(), feet.getY(), feet.getZ())) {
                    session.state = ClearAreaSession.State.PICK;
                    return;
                }
                if (approachSpent > LONG_APPROACH && longApproaches++ < LONG_LOGGED) {
                    LOGGER.info(
                            "[clear] {}: долгий подход к точке {} ({})",
                            session.spirit.getName().getString(),
                            chosen,
                            approachSpent);
                }
                approachFailed++;
                approachMiss();
            }
            default -> {
                approachFailed++;
                approachMiss();
            }
        }
    }

    void pillarTick() {
        scaffold.pillarTick();
    }

    void descentTick() {
        scaffold.descentTick();
    }

    void unloadTick() {
        unload.tick();
    }

    void breakTick() {
        if (breaks.tick() == ClearBreaks.Result.BROKEN) {
            anchor = null;
            unload.reset();
        }
    }

    private void finish() {
        if (scaffold.teardown()) {
            return;
        }
        session.finish();
    }

    private void afterDismantle() {
        anchor = null;
        triedAnchors.clear();
    }

    private boolean riseAllowed() {
        return ClearRise.allowed(session.level, feet().y());
    }

    private void approachMiss() {
        session.approach.cancel();
        ClearAnchor.Stand chosen = anchor;
        if (chosen != null) {
            triedAnchors.add(chosen.coord());
        }
        anchor = null;
        session.state = ClearAreaSession.State.PICK;
    }

    private ClearAnchor.@Nullable Stand anchor() {
        ClearAnchor.Stand cached = anchor;
        if (cached != null) {
            return cached;
        }
        ClearAnchor.Stand chosen = ClearStands.best(session, triedAnchors);
        anchor = chosen;
        return chosen;
    }

    private BlockCoord feet() {
        BlockPos feet = session.spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }
}
