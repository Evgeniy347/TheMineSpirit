package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

final class HerdLure {
    private static final double LURE_SPEED = 1.0;
    private static final int LURE_TICKS_PER_ESCAPEE = 150;
    // Возврат ведём не на кромку загона, а на два блока внутрь: вернувшаяся на кромку овца выходит обратно
    // первым же шагом RandomStroll, и приманка крутится, пока не упрётся в лимит тиков.
    private static final int LURE_INSET = 2;

    private final HerdSession session;
    private final HerdFlock flock;

    HerdLure(HerdSession session, HerdFlock flock) {
        this.session = session;
        this.flock = flock;
    }

    void lureTick() {
        if (session.lureTarget == null || session.lureTarget.isRemoved() || !session.lureTarget.isAlive()) {
            nextEscapee();
            return;
        }
        Optional<String> danger = HerdSafety.dangerReason(session.lureTarget.isInLava(),
                session.lureTarget.isOnFire(), session.lureTarget.isInWater());
        if (danger.isPresent()) {
            session.skipped++;
            session.lureSkipped.add(session.lureId);
            session.lureTarget = null;
            session.lureId = null;
            session.state = HerdSession.State.PICK;
            return;
        }
        BlockCoord escapeePos = new BlockCoord(session.lureTarget.blockPosition().getX(),
                session.lureTarget.blockPosition().getY(), session.lureTarget.blockPosition().getZ());
        if (session.pen.inside(escapeePos)) {
            session.actions.stopLeading(session.lureTarget);
            session.lureTarget = null;
            session.lureId = null;
            session.escapedReturned++;
            flock.refreshSnapshot(true);
            session.state = HerdSession.State.PICK;
            return;
        }
        if (++session.lureTicks > LURE_TICKS_PER_ESCAPEE) {
            session.actions.stopLeading(session.lureTarget);
            session.fail("не удалось вернуть " + session.lureId + " в загон за " + LURE_TICKS_PER_ESCAPEE
                    + " тиков: животное не идёт за кормом");
            return;
        }
        BlockCoord returnPoint = session.pen.nearestInside(escapeePos, LURE_INSET);
        if (!session.approach.isActive()) {
            session.approach.begin(new Vec3(returnPoint.x() + 0.5, returnPoint.y(), returnPoint.z() + 0.5), 1.5);
        }
        session.approach.tick();
        if (session.lureTarget.getNavigation().isDone()) {
            session.actions.directTowards(session.lureTarget, session.spirit.position(), LURE_SPEED);
        }
    }

    private void nextEscapee() {
        session.lureTarget = null;
        session.lureId = null;
        session.state = HerdSession.State.PICK;
    }
}
