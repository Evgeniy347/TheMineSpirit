package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.HerdJob;
import com.theminespirit.core.husbandry.PenBounds;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;

public final class HerdSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        FIND,
        PICK,
        APPROACH,
        ACT,
        COLLECT,
        LURE
    }

    enum Act {
        BREED_FIRST,
        BREED_SECOND,
        FEED_ONE,
        SHEAR_ONE,
        MILK_ONE,
        CULL_ONE,
        HUNT_ONE
    }

    static final double APPROACH_ARRIVAL = 2.0;
    static final double REACH = 3.0;
    static final int COLLECT_RADIUS = 3;
    final SpiritEntity spirit;
    final HerdJob job;
    final List<Species> wanted;
    final int searchRadius;
    final int goal;
    final boolean babiesFirst;
    final BlockCoord home;
    final PenBounds pen;
    final PenBounds homePen;
    final HerdActions actions;
    final Approach approach;

    State state = State.FIND;
    Outcome outcome = Outcome.RUNNING;
    String failureReason;
    List<AnimalRef> herd = List.of();
    final Map<String, Animal> live = new HashMap<>();
    int lastRefresh = -CatalogLimits.HERD_REFRESH_TICKS - 1;
    Animal current;
    String currentId;
    Animal firstParent;
    Act act;
    String pendingSecondId;
    DropCollector collector;
    Animal lureTarget;
    String lureId;
    int lureTicks;
    boolean lureSwept;
    String preferredTarget;
    SwordTier swordTier;
    int adultsAtStart = -1;
    final Set<String> handledIds = new HashSet<>();
    final Set<String> lureSkipped = new HashSet<>();

    int done;
    int fed;
    int babiesFed;
    int born;
    int sheared;
    int milked;
    int eggsPicked;
    int culled;
    int escapedReturned;
    int skipped;
    int planCalls;
    int entityQueries;
    int ticks;
    int actionTicks;
    int maxEntityQueriesPerTick;
    int actionTick = -1;
    int actionsThisTick;
    int maxActionsPerTick;

    final HerdFlock flock;
    final HerdBreedFeed breedFeed;
    final HerdProduce produce;
    final HerdCullHunt cullHunt;
    final HerdLure lure;
    final HerdPickup pickup;
    private final HerdFlow flow;

    private HerdSession(
            SpiritEntity spirit, HerdJob job, List<Species> wanted, int searchRadius, int goal, boolean babiesFirst) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому пасти стадо");
        this.job = Objects.requireNonNull(job, "Работа не может быть null: без неё неизвестно, что делать со стадом");
        this.wanted = List.copyOf(Objects.requireNonNull(wanted, "Виды не могут быть null: пасут один набор видов"));
        this.searchRadius = searchRadius;
        this.goal = goal;
        this.babiesFirst = babiesFirst;
        BlockPos start = spirit.blockPosition();
        this.home = new BlockCoord(start.getX(), start.getY(), start.getZ());
        int penRadius = Math.clamp(searchRadius, CatalogLimits.HERD_PEN_RADIUS_MIN, CatalogLimits.HERD_PEN_RADIUS_MAX);
        this.pen = new PenBounds(home, penRadius);
        this.homePen = new PenBounds(home, CatalogLimits.HERD_HOME_RADIUS);
        this.actions = new HerdActions(spirit);
        this.approach = new Approach(spirit);
        this.flock = new HerdFlock(this);
        this.breedFeed = new HerdBreedFeed(this, flock);
        this.produce = new HerdProduce(this, flock);
        this.cullHunt = new HerdCullHunt(this, flock);
        this.lure = new HerdLure(this, flock);
        this.pickup = new HerdPickup(this);
        this.flow = new HerdFlow(this);
    }

    public static HerdSession breed(SpiritEntity spirit, Species species, int radius, int pairs) {
        return new HerdSession(spirit, HerdJob.BREED, List.of(species), radius, pairs, false);
    }

    public static HerdSession feed(SpiritEntity spirit, Species species, int radius, int count, boolean babiesFirst) {
        return new HerdSession(spirit, HerdJob.FEED, List.of(species), radius, count, babiesFirst);
    }

    public static HerdSession shear(SpiritEntity spirit, int radius, int count) {
        return new HerdSession(spirit, HerdJob.SHEAR, List.of(Species.SHEEP), radius, count, false);
    }

    public static HerdSession milk(SpiritEntity spirit, int radius, int count) {
        return new HerdSession(spirit, HerdJob.MILK, List.of(Species.COW), radius, count, false);
    }

    public static HerdSession collectEggs(SpiritEntity spirit, int radius) {
        return new HerdSession(spirit, HerdJob.COLLECT_EGGS, List.of(Species.CHICKEN), radius, 0, false);
    }

    public static HerdSession cull(SpiritEntity spirit, Species species, int keep, int radius) {
        return new HerdSession(spirit, HerdJob.CULL, List.of(species), radius, keep, false);
    }

    public static HerdSession herdCheck(SpiritEntity spirit, List<Species> species, int radius) {
        return new HerdSession(spirit, HerdJob.HERD_CHECK, species, radius, 0, false);
    }

    public static HerdSession hunt(SpiritEntity spirit, Species species, int radius, int count) {
        return new HerdSession(spirit, HerdJob.HUNT, List.of(species), radius, count, false);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время работы со стадом");
        }
        ticks++;
        switch (state) {
            case FIND -> flow.find();
            case PICK -> flow.pick();
            case APPROACH -> flow.approachTick();
            case ACT -> flow.act();
            case COLLECT -> pickup.collectTick();
            case LURE -> lure.lureTick();
        }
        return outcome;
    }

    public void suspend() {
        approach.suspend();
        if (collector != null) {
            collector.suspend();
        }
        if (lureTarget != null) {
            actions.stopLeading(lureTarget);
        }
    }

    public void cancel() {
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (lureTarget != null) {
            actions.stopLeading(lureTarget);
            lureTarget = null;
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int done() {
        return done;
    }

    public Map<String, Object> saveState() {
        return HerdSave.save(this);
    }

    public void restoreState(Map<String, Object> saved) {
        HerdSave.restore(this, saved);
    }

    public int planCalls() {
        return planCalls;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int maxEntityQueriesPerTick() {
        return maxEntityQueriesPerTick;
    }

    public int actionTicks() {
        return actionTicks;
    }

    public int maxActionsPerTick() {
        return maxActionsPerTick;
    }

    void beginApproach(Animal animal) {
        approach.begin(animal, APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    void finishWithTally() {
        flow.finishWithTally();
    }

    void startCollect(IdFilter filter, BlockPos at, int radius) {
        collector = new DropCollector(
                spirit,
                filter,
                new BlockCoord(at.getX(), at.getY(), at.getZ()),
                radius,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
        state = State.COLLECT;
    }

    void recordAction() {
        int now = spirit.tickCount;
        actionsThisTick = now == actionTick ? actionsThisTick + 1 : 1;
        actionTick = now;
        maxActionsPerTick = Math.max(maxActionsPerTick, actionsThisTick);
        actionTicks++;
    }

    Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }
}
