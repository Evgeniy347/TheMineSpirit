package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.HerdJob;
import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.husbandry.HerdTally;
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
import java.util.Optional;
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
    private static final String VERSION_KEY = "v";
    private static final int VERSION = 1;
    private static final String DONE_KEY = "done";
    private static final String TARGET_KEY = "target";

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

    private final HerdFlock flock;
    private final HerdBreedFeed breedFeed;
    private final HerdProduce produce;
    private final HerdCullHunt cullHunt;
    private final HerdLure lure;
    private final HerdPickup pickup;

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
            case FIND -> find();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case ACT -> act();
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
        Map<String, Object> saved = new HashMap<>();
        saved.put(VERSION_KEY, VERSION);
        saved.put(DONE_KEY, done);
        saved.put("fed", fed);
        saved.put("babiesFed", babiesFed);
        saved.put("born", born);
        saved.put("sheared", sheared);
        saved.put("milked", milked);
        saved.put("eggsPicked", eggsPicked);
        saved.put("culled", culled);
        saved.put("escapedReturned", escapedReturned);
        saved.put("skipped", skipped);
        if (currentId != null && (state == State.APPROACH || state == State.ACT || state == State.COLLECT)) {
            saved.put(TARGET_KEY, currentId);
        }
        if (!handledIds.isEmpty()) {
            saved.put("handled", List.copyOf(handledIds));
        }
        return Map.copyOf(saved);
    }

    public void restoreState(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Состояние не может быть null: пустое задают пустой картой");
        if (saved.get(VERSION_KEY) instanceof Number version && version.intValue() != VERSION) {
            throw new IllegalArgumentException("Версия состояния " + version + " не поддерживается: сессия стада "
                    + "читает только версию " + VERSION);
        }
        done = Math.max(done, number(saved, DONE_KEY));
        fed = Math.max(fed, number(saved, "fed"));
        babiesFed = Math.max(babiesFed, number(saved, "babiesFed"));
        born = Math.max(born, number(saved, "born"));
        sheared = Math.max(sheared, number(saved, "sheared"));
        milked = Math.max(milked, number(saved, "milked"));
        eggsPicked = Math.max(eggsPicked, number(saved, "eggsPicked"));
        culled = Math.max(culled, number(saved, "culled"));
        escapedReturned = Math.max(escapedReturned, number(saved, "escapedReturned"));
        skipped = Math.max(skipped, number(saved, "skipped"));
        if (saved.get(TARGET_KEY) instanceof String target && !target.isBlank()) {
            preferredTarget = target;
        }
        if (saved.get("handled") instanceof List<?> handled) {
            for (Object id : handled) {
                if (id instanceof String text && !text.isBlank()) {
                    handledIds.add(text);
                }
            }
        }
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

    void find() {
        flock.refreshSnapshot(false);
        if (job == HerdJob.HERD_CHECK) {
            finishCheck();
            return;
        }
        if (job == HerdJob.COLLECT_EGGS) {
            pickup.findEggs();
            return;
        }
        state = State.PICK;
    }

    void pick() {
        flock.refreshSnapshot(false);
        switch (job) {
            case BREED -> breedFeed.pickBreed();
            case FEED -> breedFeed.pickFeed();
            case SHEAR -> produce.pickShear();
            case MILK -> produce.pickMilk();
            case CULL -> cullHunt.pickCull();
            case HUNT -> cullHunt.pickHunt();
            default -> fail("работа " + job + " не выбирает цели: снимок уже собран, а дальше идти некуда");
        }
    }

    void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> state = State.ACT;
            case STUCK, TIMEOUT -> {
                if (current != null && !current.isRemoved() && inReach(current)) {
                    state = State.ACT;
                } else {
                    skipped++;
                    handledIds.add(currentId);
                    current = null;
                    currentId = null;
                    state = State.PICK;
                }
            }
        }
    }

    void act() {
        if (current == null || current.isRemoved() || !current.isAlive()) {
            current = null;
            currentId = null;
            state = State.PICK;
            return;
        }
        Optional<String> danger = HerdSafety.dangerReason(
                new HerdSafety.Hazards(current.isInLava(), current.isOnFire(), current.isInWater()));
        if (danger.isPresent()) {
            skipped++;
            handledIds.add(currentId);
            current = null;
            currentId = null;
            firstParent = null;
            pendingSecondId = null;
            state = State.PICK;
            return;
        }
        if (!inReach(current)) {
            beginApproach(current);
            return;
        }
        switch (act) {
            case BREED_FIRST -> breedFeed.breedFirst();
            case BREED_SECOND -> breedFeed.breedSecond();
            case FEED_ONE -> breedFeed.feedOne();
            case SHEAR_ONE -> produce.shearOne();
            case MILK_ONE -> produce.milkOne();
            case CULL_ONE, HUNT_ONE -> cullHunt.strikeOne();
        }
    }

    void finishWithTally() {
        int kept = 0;
        if (job == HerdJob.CULL && adultsAtStart >= 0) {
            kept = Math.max(0, adultsAtStart - culled);
        }
        if (job == HerdJob.HERD_CHECK) {
            kept = flock.censusTotal();
        }
        HerdTally tally = new HerdTally(
                fed, babiesFed, born, sheared, milked, eggsPicked, culled, kept, escapedReturned, skipped, goal);
        if (tally.outcome(job) instanceof HerdTally.Failed failed) {
            fail(failed.text());
        } else {
            outcome = Outcome.SUCCEEDED;
            cancel();
        }
    }

    void finishCheck() {
        finishWithTally();
    }

    void beginApproach(Animal animal) {
        approach.begin(animal, APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    boolean inReach(Animal animal) {
        return spirit.getEyePosition().distanceTo(animal.position()) <= REACH;
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

    private static int number(Map<String, Object> saved, String key) {
        return saved.get(key) instanceof Number value ? Math.max(0, value.intValue()) : 0;
    }
}
