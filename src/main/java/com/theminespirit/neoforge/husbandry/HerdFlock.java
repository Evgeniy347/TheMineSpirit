package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.HerdCensus;
import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;

final class HerdFlock {
    private final HerdSession session;

    HerdFlock(HerdSession session) {
        this.session = session;
    }

    void refreshSnapshot(boolean event) {
        if (!event
                && session.ticks - session.lastRefresh < CatalogLimits.HERD_REFRESH_TICKS
                && !session.herd.isEmpty()) {
            return;
        }
        List<Animal> animals = HerdSurvey.query(
                session.spirit.level(),
                session.spirit.position(),
                session.searchRadius + CatalogLimits.HERD_SIGHT_MARGIN);
        List<AnimalRef> refs = new ArrayList<>(animals.size());
        session.live.clear();
        for (Animal animal : animals) {
            session.live.put(animal.getStringUUID(), animal);
            HerdSurvey.map(animal, session.pen).ifPresent(refs::add);
        }
        session.herd = List.copyOf(refs);
        session.entityQueries++;
        session.maxEntityQueriesPerTick = Math.max(session.maxEntityQueriesPerTick, 1);
        session.lastRefresh = session.ticks;
    }

    List<AnimalRef> inPenOf(Species species) {
        List<AnimalRef> inside = new ArrayList<>();
        for (AnimalRef animal : session.herd) {
            if (animal.species() == species && animal.inPen()) {
                inside.add(animal);
            }
        }
        return inside;
    }

    List<AnimalRef> escapeesOf(Species species) {
        List<AnimalRef> escapees = new ArrayList<>();
        for (AnimalRef animal : session.herd) {
            if (animal.species() == species
                    && !animal.inPen()
                    && session.live.containsKey(animal.id())
                    && !session.lureSkipped.contains(animal.id())) {
                escapees.add(animal);
            }
        }
        escapees.sort(this::byDistanceToHome);
        return escapees;
    }

    List<AnimalRef> unhandled(List<AnimalRef> refs) {
        List<AnimalRef> fresh = new ArrayList<>(refs.size());
        for (AnimalRef animal : refs) {
            if (!session.handledIds.contains(animal.id())) {
                fresh.add(animal);
            }
        }
        return fresh;
    }

    AnimalRef nextFeedTarget(Species species) {
        List<AnimalRef> babies = new ArrayList<>();
        List<AnimalRef> grown = new ArrayList<>();
        for (AnimalRef animal : session.herd) {
            if (animal.species() != species
                    || !animal.inPen()
                    || !session.live.containsKey(animal.id())
                    || session.handledIds.contains(animal.id())) {
                continue;
            }
            (animal.baby() ? babies : grown).add(animal);
        }
        babies.sort(this::byDistanceToHome);
        grown.sort(this::byDistanceToHome);
        if (session.babiesFirst && !babies.isEmpty()) {
            return applyPreferred(babies);
        }
        if (!grown.isEmpty()) {
            return applyPreferred(grown);
        }
        if (!session.babiesFirst && !babies.isEmpty()) {
            return applyPreferred(babies);
        }
        if (!babies.isEmpty()) {
            return applyPreferred(babies);
        }
        return null;
    }

    AnimalRef nearestOf(Species species, boolean adultsOnly, boolean woolOnly) {
        List<AnimalRef> candidates = new ArrayList<>();
        for (AnimalRef animal : session.herd) {
            if (animal.species() != species
                    || !animal.inPen()
                    || !session.live.containsKey(animal.id())
                    || session.handledIds.contains(animal.id())) {
                continue;
            }
            if (adultsOnly && animal.baby()) {
                continue;
            }
            if (woolOnly && !animal.woolReady()) {
                continue;
            }
            if (session.preferredTarget != null && !session.preferredTarget.equals(animal.id())) {
                continue;
            }
            candidates.add(animal);
        }
        session.preferredTarget = null;
        candidates.sort(this::byDistanceToHome);
        for (AnimalRef candidate : candidates) {
            if (deferEndangered(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    AnimalRef refOf(String id) {
        for (AnimalRef animal : session.herd) {
            if (animal.id().equals(id)) {
                return animal;
            }
        }
        return null;
    }

    boolean deferEndangeredId(String id) {
        AnimalRef ref = refOf(id);
        return ref != null && deferEndangered(ref);
    }

    boolean deferEndangered(AnimalRef ref) {
        Animal animal = session.live.get(ref.id());
        if (animal == null || animal.isRemoved()) {
            return true;
        }
        Optional<String> danger = HerdSafety.dangerReason(
                new HerdSafety.Hazards(animal.isInLava(), animal.isOnFire(), animal.isInWater()));
        if (danger.isPresent()) {
            session.skipped++;
            session.handledIds.add(ref.id());
            return true;
        }
        return false;
    }

    AnimalRef applyPreferred(List<AnimalRef> ordered) {
        if (session.preferredTarget != null) {
            for (AnimalRef animal : ordered) {
                if (animal.id().equals(session.preferredTarget)) {
                    session.preferredTarget = null;
                    return deferEndangered(animal) ? null : animal;
                }
            }
            session.preferredTarget = null;
        }
        for (AnimalRef animal : ordered) {
            if (!deferEndangered(animal)) {
                return animal;
            }
        }
        return null;
    }

    List<String> preferTargetFirst(List<String> ids) {
        if (session.preferredTarget != null && ids.contains(session.preferredTarget)) {
            List<String> ordered = new ArrayList<>(ids);
            ordered.remove(session.preferredTarget);
            ordered.add(0, session.preferredTarget);
            session.preferredTarget = null;
            return ordered;
        }
        session.preferredTarget = null;
        return ids;
    }

    List<AnimalRef> preferRefTargetFirst(List<AnimalRef> refs) {
        if (session.preferredTarget != null) {
            for (AnimalRef animal : refs) {
                if (animal.id().equals(session.preferredTarget)) {
                    List<AnimalRef> ordered = new ArrayList<>(refs);
                    ordered.remove(animal);
                    ordered.add(0, animal);
                    session.preferredTarget = null;
                    return ordered;
                }
            }
            session.preferredTarget = null;
        }
        return refs;
    }

    Map<String, Integer> feedCounts(Species species) {
        Map<String, Integer> counts = new HashMap<>();
        for (String feedId : FeedTable.feedFor(species)) {
            counts.put(feedId, session.actions.count(HerdActions.registryItem(feedId)));
        }
        return counts;
    }

    Item feedItem(Species species) {
        return HerdActions.registryItem(FeedTable.feedFor(species).get(0));
    }

    boolean hasFeed(Species species) {
        for (String feedId : FeedTable.feedFor(species)) {
            if (session.actions.count(HerdActions.registryItem(feedId)) > 0) {
                return true;
            }
        }
        return false;
    }

    BlockCoord spiritCoord() {
        BlockPos pos = session.spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    int censusTotal() {
        int total = 0;
        HerdCensus.Census census = HerdCensus.summarize(session.herd);
        for (Species species : session.wanted) {
            total += census.of(species).total();
        }
        return total;
    }

    int byDistanceToHome(AnimalRef left, AnimalRef right) {
        int byDistance =
                Long.compare(distanceSquared(session.home, left.pos()), distanceSquared(session.home, right.pos()));
        return byDistance != 0 ? byDistance : left.id().compareTo(right.id());
    }

    static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    static int adults(List<AnimalRef> refs) {
        int count = 0;
        for (AnimalRef animal : refs) {
            if (!animal.baby()) {
                count++;
            }
        }
        return count;
    }
}
