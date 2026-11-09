package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.HerdJob;
import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.husbandry.HerdTally;
import java.util.Optional;
import net.minecraft.world.entity.animal.Animal;

final class HerdFlow {
    private final HerdSession session;

    HerdFlow(HerdSession session) {
        this.session = session;
    }

    void find() {
        session.flock.refreshSnapshot(false);
        if (session.job == HerdJob.HERD_CHECK) {
            finishCheck();
            return;
        }
        if (session.job == HerdJob.COLLECT_EGGS) {
            session.pickup.findEggs();
            return;
        }
        session.state = HerdSession.State.PICK;
    }

    void pick() {
        session.flock.refreshSnapshot(false);
        switch (session.job) {
            case BREED -> session.breedFeed.pickBreed();
            case FEED -> session.breedFeed.pickFeed();
            case SHEAR -> session.produce.pickShear();
            case MILK -> session.produce.pickMilk();
            case CULL -> session.cullHunt.pickCull();
            case HUNT -> session.cullHunt.pickHunt();
            default ->
                session.fail("работа " + session.job + " не выбирает цели: снимок уже собран, а дальше идти некуда");
        }
    }

    void approachTick() {
        switch (session.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> session.state = HerdSession.State.ACT;
            case STUCK, TIMEOUT -> {
                if (session.current != null && !session.current.isRemoved() && inReach(session.current)) {
                    session.state = HerdSession.State.ACT;
                } else {
                    session.skipped++;
                    session.handledIds.add(session.currentId);
                    session.current = null;
                    session.currentId = null;
                    session.state = HerdSession.State.PICK;
                }
            }
        }
    }

    void act() {
        if (session.current == null || session.current.isRemoved() || !session.current.isAlive()) {
            session.current = null;
            session.currentId = null;
            session.state = HerdSession.State.PICK;
            return;
        }
        Optional<String> danger = HerdSafety.dangerReason(new HerdSafety.Hazards(
                session.current.isInLava(), session.current.isOnFire(), session.current.isInWater()));
        if (danger.isPresent()) {
            session.skipped++;
            session.handledIds.add(session.currentId);
            session.current = null;
            session.currentId = null;
            session.firstParent = null;
            session.pendingSecondId = null;
            session.state = HerdSession.State.PICK;
            return;
        }
        if (!inReach(session.current)) {
            session.beginApproach(session.current);
            return;
        }
        switch (session.act) {
            case BREED_FIRST -> session.breedFeed.breedFirst();
            case BREED_SECOND -> session.breedFeed.breedSecond();
            case FEED_ONE -> session.breedFeed.feedOne();
            case SHEAR_ONE -> session.produce.shearOne();
            case MILK_ONE -> session.produce.milkOne();
            case CULL_ONE, HUNT_ONE -> session.cullHunt.strikeOne();
        }
    }

    void finishWithTally() {
        int kept = 0;
        if (session.job == HerdJob.CULL && session.adultsAtStart >= 0) {
            kept = Math.max(0, session.adultsAtStart - session.culled);
        }
        if (session.job == HerdJob.HERD_CHECK) {
            kept = session.flock.censusTotal();
        }
        HerdTally tally = new HerdTally(
                session.fed,
                session.babiesFed,
                session.born,
                session.sheared,
                session.milked,
                session.eggsPicked,
                session.culled,
                kept,
                session.escapedReturned,
                session.skipped,
                session.goal);
        if (tally.outcome(session.job) instanceof HerdTally.Failed failed) {
            session.fail(failed.text());
        } else {
            session.outcome = HerdSession.Outcome.SUCCEEDED;
            session.cancel();
        }
    }

    void finishCheck() {
        finishWithTally();
    }

    boolean inReach(Animal animal) {
        return session.spirit.getEyePosition().distanceTo(animal.position()) <= HerdSession.REACH;
    }
}
