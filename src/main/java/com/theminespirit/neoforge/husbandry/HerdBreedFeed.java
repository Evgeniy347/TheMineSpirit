package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.BreedPlanner;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import java.util.List;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;

final class HerdBreedFeed {
    private final HerdSession session;
    private final HerdFlock flock;

    HerdBreedFeed(HerdSession session, HerdFlock flock) {
        this.session = session;
        this.flock = flock;
    }

    void pickBreed() {
        if (session.done >= session.goal) {
            session.finishWithTally();
            return;
        }
        Species species = session.wanted.get(0);
        session.planCalls++;
        BreedPlanner.Outcome result = BreedPlanner.next(
                flock.unhandled(flock.inPenOf(species)),
                flock.spiritCoord(),
                species,
                flock.feedCounts(species),
                false);
        if (result instanceof BreedPlanner.Feed feed) {
            Animal first = session.live.get(feed.targetId());
            Animal partner = session.live.get(feed.partnerId());
            if (first == null || partner == null || first.isRemoved() || partner.isRemoved()) {
                flock.refreshSnapshot(true);
                return;
            }
            if (flock.deferEndangeredId(feed.targetId()) || flock.deferEndangeredId(feed.partnerId())) {
                return;
            }
            session.current = first;
            session.currentId = feed.targetId();
            session.pendingSecondId = feed.partnerId();
            session.act = HerdSession.Act.BREED_FIRST;
            session.beginApproach(first);
            return;
        }
        if (result instanceof BreedPlanner.NeedsMoreFeed needs) {
            session.fail("нет корма: нужен " + needs.feedId() + ", не хватает " + needs.missing() + ": сделано пар "
                    + session.done + " из " + session.goal);
            return;
        }
        session.fail(
                ((BreedPlanner.NothingToDo) result).reason() + ": сделано пар " + session.done + " из " + session.goal);
    }

    void pickFeed() {
        if (session.lureSwept) {
            session.finishWithTally();
            return;
        }
        if (session.fed < session.goal) {
            Species species = session.wanted.get(0);
            AnimalRef next = flock.nextFeedTarget(species);
            if (next != null) {
                Animal animal = session.live.get(next.id());
                if (animal == null || animal.isRemoved()) {
                    flock.refreshSnapshot(true);
                    return;
                }
                session.current = animal;
                session.currentId = next.id();
                session.act = HerdSession.Act.FEED_ONE;
                session.beginApproach(animal);
                return;
            }
        }
        List<AnimalRef> escapees = flock.escapeesOf(session.wanted.get(0));
        if (escapees.isEmpty()) {
            session.lureSwept = true;
            session.finishWithTally();
            return;
        }
        if (!flock.hasFeed(session.wanted.get(0))) {
            session.fail("нет корма: нужен "
                    + FeedTable.feedFor(session.wanted.get(0)).get(0)
                    + ": без приманки отбившихся не вернуть, покормлено " + session.fed + " из " + session.goal);
            return;
        }
        session.lureTarget = session.live.get(escapees.get(0).id());
        session.lureId = escapees.get(0).id();
        session.lureTicks = 0;
        if (session.lureTarget == null || session.lureTarget.isRemoved()) {
            flock.refreshSnapshot(true);
            return;
        }
        session.state = HerdSession.State.LURE;
    }

    void breedFirst() {
        Item feed = flock.feedItem(session.wanted.get(0));
        if (!session.actions.feedAdult(session.current, feed)) {
            session.fail("корм закончился посреди разведения: нужен "
                    + FeedTable.feedFor(session.wanted.get(0)).get(0)
                    + ", сделано пар " + session.done + " из " + session.goal);
            return;
        }
        session.recordAction();
        session.handledIds.add(session.currentId);
        Animal partner = session.live.get(session.pendingSecondId);
        if (partner == null || partner.isRemoved() || !partner.isAlive()) {
            flock.refreshSnapshot(true);
            session.current = null;
            session.currentId = null;
            session.state = HerdSession.State.PICK;
            return;
        }
        session.firstParent = session.current;
        session.current = partner;
        session.currentId = session.pendingSecondId;
        session.act = HerdSession.Act.BREED_SECOND;
        session.beginApproach(partner);
    }

    void breedSecond() {
        Item feed = flock.feedItem(session.wanted.get(0));
        if (!session.actions.feedAdult(session.current, feed)) {
            session.fail("корм закончился посреди разведения: нужен "
                    + FeedTable.feedFor(session.wanted.get(0)).get(0)
                    + ": первая голова покормлена зря, сделано пар " + session.done + " из " + session.goal);
            return;
        }
        session.recordAction();
        session.handledIds.add(session.currentId);
        Animal parent = session.firstParent;
        if (parent == null || parent.isRemoved() || !parent.isAlive()) {
            session.firstParent = null;
            session.current = null;
            session.currentId = null;
            session.pendingSecondId = null;
            flock.refreshSnapshot(true);
            session.state = HerdSession.State.PICK;
            return;
        }
        session.actions.breedPair(parent, session.current);
        session.born++;
        session.done++;
        session.firstParent = null;
        session.current = null;
        session.currentId = null;
        session.pendingSecondId = null;
        flock.refreshSnapshot(true);
        session.state = HerdSession.State.PICK;
    }

    void feedOne() {
        Item feed = flock.feedItem(session.wanted.get(0));
        boolean baby = session.current != null && session.current.isBaby();
        boolean eaten;
        if (baby) {
            eaten = session.actions.feedBaby((AgeableMob) session.current, feed);
        } else {
            eaten = session.actions.feedAdult(session.current, feed);
        }
        if (!eaten) {
            session.fail("корм закончился: нужен "
                    + FeedTable.feedFor(session.wanted.get(0)).get(0) + ", покормлено " + session.fed + " из "
                    + session.goal);
            return;
        }
        session.recordAction();
        session.fed++;
        session.done++;
        session.handledIds.add(session.currentId);
        if (baby) {
            session.babiesFed++;
        }
        session.current = null;
        session.currentId = null;
        session.state = HerdSession.State.PICK;
    }
}
