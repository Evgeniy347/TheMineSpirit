package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.CullPolicy;
import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;

final class HerdCullHunt {
    private final HerdSession session;
    private final HerdFlock flock;

    HerdCullHunt(HerdSession session, HerdFlock flock) {
        this.session = session;
        this.flock = flock;
    }

    void pickCull() {
        if (!gateSword()) {
            return;
        }
        Species species = session.wanted.get(0);
        List<AnimalRef> candidates = flock.inPenOf(species);
        if (session.adultsAtStart < 0) {
            session.adultsAtStart = HerdFlock.adults(candidates);
        }
        if (liveAdults(candidates) <= session.goal) {
            session.finishWithTally();
            return;
        }
        session.planCalls++;
        CullPolicy.Outcome result = CullPolicy.pick(candidates, session.home, species, session.goal,
                session.swordTier);
        if (result instanceof CullPolicy.Refused refused) {
            session.fail(refused.reason() + ": забито " + session.culled);
            return;
        }
        AnimalRef victim = selectVictim(candidates, ((CullPolicy.Picked) result).ids());
        if (victim == null) {
            session.finishWithTally();
            return;
        }
        Animal animal = session.live.get(victim.id());
        if (animal == null || animal.isRemoved()) {
            flock.refreshSnapshot(true);
            return;
        }
        session.current = animal;
        session.currentId = victim.id();
        session.act = HerdSession.Act.CULL_ONE;
        session.beginApproach(animal);
    }

    void pickHunt() {
        if (session.done >= session.goal) {
            session.finishWithTally();
            return;
        }
        if (!gateSword()) {
            return;
        }
        Species species = session.wanted.get(0);
        List<AnimalRef> wild = new ArrayList<>();
        for (AnimalRef animal : session.herd) {
            if (animal.species() == species && !session.homePen.inside(animal.pos())
                    && !session.handledIds.contains(animal.id())) {
                wild.add(animal);
            }
        }
        wild.sort(flock::byDistanceToHome);
        AnimalRef victim = firstStrikableIds(flock.preferRefTargetFirst(wild));
        if (victim == null) {
            session.finishWithTally();
            return;
        }
        Animal animal = session.live.get(victim.id());
        if (animal == null || animal.isRemoved()) {
            flock.refreshSnapshot(true);
            return;
        }
        session.current = animal;
        session.currentId = victim.id();
        session.act = HerdSession.Act.HUNT_ONE;
        session.beginApproach(animal);
    }

    void strikeOne() {
        session.recordAction();
        if (session.actions.strike(session.current, session.swordTier)) {
            session.culled++;
            session.done++;
            BlockPos at = session.current.blockPosition();
            session.current = null;
            session.currentId = null;
            flock.refreshSnapshot(true);
            session.startCollect(IdFilter.matchAll(), at, HerdSession.COLLECT_RADIUS);
        } else {
            session.beginApproach(session.current);
        }
    }

    boolean gateSword() {
        HerdActions.SwordSurvey survey = session.actions.surveySwords();
        if (!survey.unknown().isEmpty()
                && (survey.best() == null || !survey.best().atLeast(SwordTier.IRON))) {
            session.fail("неизвестный меч " + survey.unknown().get(0) + ": тир считается по таблице "
                    + SwordTier.RESOURCE + ", а не по урону, животное не бьют зря");
            return false;
        }
        session.swordTier = survey.best() == null ? SwordTier.HAND : survey.best();
        return true;
    }

    private AnimalRef selectVictim(List<AnimalRef> candidates, List<String> preferred) {
        List<AnimalRef> ordered = new ArrayList<>();
        for (String id : flock.preferTargetFirst(preferred)) {
            AnimalRef ref = flock.refOf(id);
            if (ref != null) {
                ordered.add(ref);
            }
        }
        List<AnimalRef> rest = new ArrayList<>(candidates);
        rest.sort(flock::byDistanceToHome);
        rest = rest.reversed();
        for (AnimalRef animal : rest) {
            if (!animal.baby() && ordered.stream().noneMatch(placed -> placed.id().equals(animal.id()))) {
                ordered.add(animal);
            }
        }
        for (AnimalRef ref : ordered) {
            if (!session.live.containsKey(ref.id()) || session.handledIds.contains(ref.id())) {
                continue;
            }
            if (HerdSafety.watchedByBaby(ref, session.herd)) {
                session.skipped++;
                session.handledIds.add(ref.id());
                continue;
            }
            if (flock.deferEndangered(ref)) {
                continue;
            }
            return ref;
        }
        return null;
    }

    private int liveAdults(List<AnimalRef> candidates) {
        int count = 0;
        for (AnimalRef animal : candidates) {
            Animal liveAnimal = session.live.get(animal.id());
            if (!animal.baby() && liveAnimal != null && !liveAnimal.isRemoved() && liveAnimal.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private AnimalRef firstStrikableIds(List<AnimalRef> refs) {
        for (AnimalRef ref : refs) {
            if (!session.live.containsKey(ref.id()) || flock.deferEndangered(ref)) {
                continue;
            }
            if (HerdSafety.watchedByBaby(ref, session.herd)) {
                session.skipped++;
                session.handledIds.add(ref.id());
                continue;
            }
            return ref;
        }
        return null;
    }
}
