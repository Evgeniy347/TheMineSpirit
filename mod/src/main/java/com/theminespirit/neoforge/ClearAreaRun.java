package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.CaveStand;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class ClearAreaRun {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int PROTECTED_LOGGED = 10;
    private static final int COLLECT_RADIUS = 4;
    private static final int LONG_APPROACH = 60;

    private final ClearAreaSession session;
    private int approachFailed;
    private int approachSpent;
    private int approaches;
    private int fromPlace;
    private int vanished;
    private String pickedKind = "";
    private String brokenKind = "";
    private final Map<String, Integer> brokenKinds = new TreeMap<>();
    private int longApproaches;

    ClearAreaRun(ClearAreaSession session) {
        this.session = session;
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
        return vanished;
    }

    Map<String, Integer> brokenKinds() {
        return brokenKinds;
    }

    void pick() {
        session.targets.dropLeased(session.remaining);
        session.noteSkipped(session.felling.dropPartial(session.remaining));
        while (!session.remaining.isEmpty() && session.targets.clear(session.remaining.get(0))) {
            session.remaining.remove(0);
        }
        session.apply(session.blocked.dropExcluded(session.remaining));
        session.apply(session.blocked.dropPlanned(session.remaining));
        session.noteSkipped(session.felling.dropPartial(session.remaining));
        if (session.remaining.isEmpty()) {
            session.finish();
            return;
        }
        BlockPos feet = session.spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<BlockCoord> top = ClearOrder.topmost(session.remaining, cell -> !session.targets.clear(cell));
        BlockCoord near =
                ClearReach.near(session.spirit, session.level, top, feet, session.safety, session.reservations);
        if (near != null && !session.approachFailures.excludedStands().contains(from)) {
            session.remaining.remove(near);
            session.reservations.reserve(near, session.spirit.getUUID());
            session.currentTarget = near;
            session.currentStand = from;
            approachSpent = 0;
            fromPlace++;
            prepareBreak();
            return;
        }
        MiningPlanner.Outcome result = ClearPlanning.next(
                session.spirit, from, top, session.safety, session.approachFailures.excludedStands());
        if (result instanceof MiningPlanner.Exhausted) {
            List<BlockCoord> solid = session.remaining.stream()
                    .filter(cell -> !session.targets.clear(cell))
                    .toList();
            result = ClearPlanning.next(
                    session.spirit, from, solid, session.safety, session.approachFailures.excludedStands());
        }
        if (result instanceof MiningPlanner.Exhausted) {
            session.apply(session.blocked.exhausted(
                    session.remaining.stream()
                            .filter(cell -> !session.targets.clear(cell))
                            .toList(),
                    session.safety,
                    from));
            session.remaining.clear();
            session.finish();
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        session.remaining.remove(next.target());
        BlockPos standPos = ClearAreaSession.toPos(next.stand());
        if (CaveStand.cave(next.stand().y(), session.box.minY(), session.level.canSeeSky(standPos))) {
            session.unreachable++;
            session.tally.skipped();
            LOGGER.info(
                    "[clear] {}: клетка {} — стоянка {} ниже ящика (пещера под участком), пропуск",
                    session.spirit.getName().getString(),
                    next.target(),
                    next.stand());
            return;
        }
        if (session.reservations.foreign(next.target(), session.spirit.getUUID())) {
            session.foreignSkipped++;
            session.tally.skipped();
            return;
        }
        session.reservations.reserve(next.target(), session.spirit.getUUID());
        session.currentTarget = next.target();
        session.currentStand = next.stand();
        pickedKind = ClearBlocked.blockAt(session.level, session.currentTarget);
        session.approach.beginWithRoutes(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        approaches++;
        session.state = ClearAreaSession.State.APPROACH;
    }

    void approachTick() {
        approachSpent++;
        BlockPos feet = session.spirit.blockPosition();
        if (session.spirit.onGround()
                && feet.getX() == session.currentStand.x()
                && feet.getY() == session.currentStand.y()
                && feet.getZ() == session.currentStand.z()) {
            session.approach.cancel();
            arrived();
            return;
        }
        switch (session.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> arrived();
            case STUCK, TIMEOUT -> {
                approachSpent = 0;
                approachFailed++;
                if (approachFailed <= PROTECTED_LOGGED) {
                    BlockPos at = session.spirit.blockPosition();
                    LOGGER.info(
                            "[clear] {}: срыв подхода к стоянке {} для {} из {}, {}",
                            session.spirit.getName().getString(),
                            session.currentStand,
                            session.currentTarget,
                            at.toShortString(),
                            session.approach.refusal() == null ? "без причины" : session.approach.refusal());
                }
                if (session.approachFailures.recordFailure(session.currentStand, session.currentTarget)) {
                    session.apply(session.blocked.approachFailure(
                            session.currentTarget, session.currentStand, session.approach.refusal()));
                    session.releaseCurrent();
                } else {
                    BlockCoord retry = session.currentTarget;
                    session.releaseCurrent();
                    session.remaining.add(0, retry);
                }
                session.state = ClearAreaSession.State.PICK;
            }
        }
    }

    private void arrived() {
        if (approachSpent > LONG_APPROACH && longApproaches++ < PROTECTED_LOGGED) {
            LOGGER.info(
                    "[clear] {}: долгий подход {} тиков к стоянке {} для {} ({})",
                    session.spirit.getName().getString(),
                    approachSpent,
                    session.currentStand,
                    session.currentTarget,
                    ClearBlocked.blockAt(session.level, session.currentTarget));
        }
        approachSpent = 0;
        prepareBreak();
    }

    private void prepareBreak() {
        if (!session.demolish && PlanCells.keep(session.level, ClearAreaSession.toPos(session.currentTarget))) {
            session.protectedSkipped++;
            if (session.protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: {} — постройка плана поселения, не ломается",
                        session.spirit.getName().getString(),
                        session.currentTarget);
            }
            session.tally.skipped();
            session.releaseCurrent();
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        if (!(MiningSafety.canBreak(session.currentTarget, session.safety) instanceof BreakSafety.Safe)) {
            session.unsafe++;
            session.tally.skipped();
            session.releaseCurrent();
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        BlockBreaker.Begin begin =
                session.breaker.begin(ClearAreaSession.toPos(session.currentTarget), false, session.demolish);
        if (begin instanceof BlockBreaker.Protected kept) {
            session.protectedSkipped++;
            if (session.protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: защищено {} — {}: {} ({})",
                        session.spirit.getName().getString(),
                        session.currentTarget,
                        BuiltInRegistries.BLOCK.getKey(session.level
                                .getBlockState(ClearAreaSession.toPos(session.currentTarget))
                                .getBlock()),
                        kept.policy(),
                        kept.reason());
            }
            session.tally.skipped();
            session.releaseCurrent();
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
            session.fail(refused.message());
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.AIR) {
            vanished++;
            if (vanished <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: клетка {} ({} при выборе) пуста по приходу на {}",
                        session.spirit.getName().getString(),
                        session.currentTarget,
                        pickedKind,
                        session.currentStand);
            }
            session.releaseCurrent();
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
                session.fail(refused.message());
                return;
            }
            BreakSkipTally.Reason reason = BreakSkips.reason(refused.reason());
            session.breakSkips
                    .record(reason, session.currentTarget, session.currentStand)
                    .ifPresent(sample -> SkipLog.skipped(LOGGER, session.spirit, "clear", sample));
            session.tally.skipped();
            if (BreakSight.retryable(refused.reason()) && session.retryOtherStand()) {
                return;
            }
            session.releaseCurrent();
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        brokenKind = ClearBlocked.blockAt(session.level, session.currentTarget);
        session.state = ClearAreaSession.State.BREAK;
    }

    void breakTick() {
        switch (session.breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                session.removed++;
                session.felling.removed(session.currentTarget);
                session.blocked.reached(session.currentTarget);
                brokenKinds.merge(brokenKind, 1, Integer::sum);
                session.tally.removed();
                session.collector = new DropCollector(
                        session.spirit,
                        IdFilter.matchAll(),
                        session.currentTarget,
                        COLLECT_RADIUS,
                        PickupTuning.DEFAULT,
                        OverflowMode.RETURN_TO_BASE);
                session.state = ClearAreaSession.State.COLLECT;
            }
            case GONE -> {
                vanished++;
                session.releaseCurrent();
                session.state = ClearAreaSession.State.PICK;
            }
        }
    }
}
