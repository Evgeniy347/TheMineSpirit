package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.BreedPlanner;
import com.theminespirit.core.husbandry.CullPolicy;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.HerdCensus;
import com.theminespirit.core.husbandry.HerdJob;
import com.theminespirit.core.husbandry.HerdSafety;
import com.theminespirit.core.husbandry.HerdTally;
import com.theminespirit.core.husbandry.PenBounds;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HerdSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        FIND,
        PICK,
        APPROACH,
        ACT,
        COLLECT,
        LURE
    }

    private enum Act {
        BREED_FIRST,
        BREED_SECOND,
        FEED_ONE,
        SHEAR_ONE,
        MILK_ONE,
        CULL_ONE,
        HUNT_ONE
    }

    private static final double APPROACH_ARRIVAL = 2.0;
    private static final double REACH = 3.0;
    private static final double LURE_SPEED = 1.0;
    private static final int LURE_TICKS_PER_ESCAPEE = 150;
    private static final int COLLECT_RADIUS = 3;
    private static final String VERSION_KEY = "v";
    private static final int VERSION = 1;
    private static final String DONE_KEY = "done";
    private static final String TARGET_KEY = "target";

    private final SpiritEntity spirit;
    private final HerdJob job;
    private final List<Species> wanted;
    private final int searchRadius;
    private final int goal;
    private final boolean babiesFirst;
    private final BlockCoord home;
    private final PenBounds pen;
    private final PenBounds homePen;
    private final HerdActions actions;
    private final Approach approach;

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private List<AnimalRef> herd = List.of();
    private final Map<String, Animal> live = new HashMap<>();
    private int lastRefresh = -CatalogLimits.HERD_REFRESH_TICKS - 1;
    private Animal current;
    private String currentId;
    private Animal firstParent;
    private Act act;
    private String pendingSecondId;
    private DropCollector collector;
    private Animal lureTarget;
    private String lureId;
    private int lureTicks;
    private boolean lureSwept;
    private String preferredTarget;
    private SwordTier swordTier;
    private int adultsAtStart = -1;
    private final Set<String> handledIds = new HashSet<>();
    private final Set<String> lureSkipped = new HashSet<>();

    private int done;
    private int fed;
    private int babiesFed;
    private int born;
    private int sheared;
    private int milked;
    private int eggsPicked;
    private int culled;
    private int escapedReturned;
    private int skipped;
    private int planCalls;
    private int entityQueries;
    private int ticks;
    private int actionTicks;
    private int maxEntityQueriesPerTick;
    private int actionTick = -1;
    private int actionsThisTick;
    private int maxActionsPerTick;

    private HerdSession(SpiritEntity spirit, HerdJob job, List<Species> wanted, int searchRadius, int goal,
            boolean babiesFirst) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому пасти стадо");
        this.job = Objects.requireNonNull(job, "Работа не может быть null: без неё неизвестно, что делать со стадом");
        this.wanted = List.copyOf(Objects.requireNonNull(wanted, "Виды не могут быть null: пасут один набор видов"));
        this.searchRadius = searchRadius;
        this.goal = goal;
        this.babiesFirst = babiesFirst;
        BlockPos start = spirit.blockPosition();
        this.home = new BlockCoord(start.getX(), start.getY(), start.getZ());
        int penRadius = Math.clamp(searchRadius, CatalogLimits.HERD_PEN_RADIUS_MIN,
                CatalogLimits.HERD_PEN_RADIUS_MAX);
        this.pen = new PenBounds(home, penRadius);
        this.homePen = new PenBounds(home, CatalogLimits.HERD_HOME_RADIUS);
        this.actions = new HerdActions(spirit);
        this.approach = new Approach(spirit);
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
            case COLLECT -> collectTick();
            case LURE -> lureTick();
        }
        return outcome;
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

    public int blockReads() {
        return 0;
    }

    private void find() {
        refreshSnapshot(false);
        if (job == HerdJob.HERD_CHECK) {
            finishCheck();
            return;
        }
        if (job == HerdJob.COLLECT_EGGS) {
            findEggs();
            return;
        }
        state = State.PICK;
    }

    private void pick() {
        refreshSnapshot(false);
        switch (job) {
            case BREED -> pickBreed();
            case FEED -> pickFeed();
            case SHEAR -> pickShear();
            case MILK -> pickMilk();
            case CULL -> pickCull();
            case HUNT -> pickHunt();
            default -> fail("работа " + job + " не выбирает цели: снимок уже собран, а дальше идти некуда");
        }
    }

    private void pickBreed() {
        if (done >= goal) {
            finishWithTally();
            return;
        }
        Species species = wanted.get(0);
        planCalls++;
        BreedPlanner.Outcome result = BreedPlanner.next(unhandled(inPenOf(species)), spiritCoord(), species,
                feedCounts(species), false);
        if (result instanceof BreedPlanner.Feed feed) {
            Animal first = live.get(feed.targetId());
            Animal partner = live.get(feed.partnerId());
            if (first == null || partner == null || first.isRemoved() || partner.isRemoved()) {
                refreshSnapshot(true);
                return;
            }
            if (deferEndangeredId(feed.targetId()) | deferEndangeredId(feed.partnerId())) {
                return;
            }
            current = first;
            currentId = feed.targetId();
            pendingSecondId = feed.partnerId();
            act = Act.BREED_FIRST;
            beginApproach(first);
            return;
        }
        if (result instanceof BreedPlanner.NeedsMoreFeed needs) {
            fail("нет корма: нужен " + needs.feedId() + ", не хватает " + needs.missing()
                    + ": сделано пар " + done + " из " + goal);
            return;
        }
        fail(((BreedPlanner.NothingToDo) result).reason() + ": сделано пар " + done + " из " + goal);
    }

    private void pickFeed() {
        if (lureSwept) {
            finishWithTally();
            return;
        }
        if (fed < goal) {
            Species species = wanted.get(0);
            AnimalRef next = nextFeedTarget(species);
            if (next != null) {
                Animal animal = live.get(next.id());
                if (animal == null || animal.isRemoved()) {
                    refreshSnapshot(true);
                    return;
                }
                current = animal;
                currentId = next.id();
                act = Act.FEED_ONE;
                beginApproach(animal);
                return;
            }
        }
        List<AnimalRef> escapees = escapeesOf(wanted.get(0));
        if (escapees.isEmpty()) {
            lureSwept = true;
            finishWithTally();
            return;
        }
        if (!hasFeed(wanted.get(0))) {
            fail("нет корма: нужен " + FeedTable.feedFor(wanted.get(0)).get(0)
                    + ": без приманки отбившихся не вернуть, покормлено " + fed + " из " + goal);
            return;
        }
        lureTarget = live.get(escapees.get(0).id());
        lureId = escapees.get(0).id();
        lureTicks = 0;
        if (lureTarget == null || lureTarget.isRemoved()) {
            refreshSnapshot(true);
            return;
        }
        state = State.LURE;
    }

    private void pickShear() {
        if (done >= goal) {
            finishWithTally();
            return;
        }
        if (actions.shearsStack().isEmpty()) {
            fail("нет ножниц в инвентаре: стричь нечем, мир не изменён");
            return;
        }
        AnimalRef next = nearestOf(Species.SHEEP, true, true);
        if (next == null) {
            finishWithTally();
            return;
        }
        Animal animal = live.get(next.id());
        if (animal == null || animal.isRemoved()) {
            refreshSnapshot(true);
            return;
        }
        current = animal;
        currentId = next.id();
        act = Act.SHEAR_ONE;
        beginApproach(animal);
    }

    private void pickMilk() {
        if (done >= goal) {
            finishWithTally();
            return;
        }
        if (actions.count(Items.BUCKET) == 0) {
            fail("нет пустых вёдер в инвентаре: доить не во что, мир не изменён");
            return;
        }
        AnimalRef next = nearestOf(Species.COW, false, false);
        if (next == null) {
            finishWithTally();
            return;
        }
        Animal animal = live.get(next.id());
        if (animal == null || animal.isRemoved()) {
            refreshSnapshot(true);
            return;
        }
        current = animal;
        currentId = next.id();
        act = Act.MILK_ONE;
        beginApproach(animal);
    }

    private void pickCull() {
        if (!gateSword()) {
            return;
        }
        Species species = wanted.get(0);
        List<AnimalRef> candidates = inPenOf(species);
        if (adultsAtStart < 0) {
            adultsAtStart = adults(candidates);
        }
        if (liveAdults(candidates) <= goal) {
            finishWithTally();
            return;
        }
        planCalls++;
        CullPolicy.Outcome result = CullPolicy.pick(candidates, home, species, goal, swordTier);
        if (result instanceof CullPolicy.Refused refused) {
            fail(refused.reason() + ": забито " + culled);
            return;
        }
        AnimalRef victim = selectVictim(candidates, ((CullPolicy.Picked) result).ids());
        if (victim == null) {
            finishWithTally();
            return;
        }
        Animal animal = live.get(victim.id());
        if (animal == null || animal.isRemoved()) {
            refreshSnapshot(true);
            return;
        }
        current = animal;
        currentId = victim.id();
        act = Act.CULL_ONE;
        beginApproach(animal);
    }

    private void pickHunt() {
        if (done >= goal) {
            finishWithTally();
            return;
        }
        if (!gateSword()) {
            return;
        }
        Species species = wanted.get(0);
        List<AnimalRef> wild = new ArrayList<>();
        for (AnimalRef animal : herd) {
            if (animal.species() == species && !homePen.inside(animal.pos())
                    && !handledIds.contains(animal.id())) {
                wild.add(animal);
            }
        }
        wild.sort(this::byDistanceToHome);
        AnimalRef victim = firstStrikableIds(preferRefTargetFirst(wild));
        if (victim == null) {
            finishWithTally();
            return;
        }
        Animal animal = live.get(victim.id());
        if (animal == null || animal.isRemoved()) {
            refreshSnapshot(true);
            return;
        }
        current = animal;
        currentId = victim.id();
        act = Act.HUNT_ONE;
        beginApproach(animal);
    }

    private AnimalRef selectVictim(List<AnimalRef> candidates, List<String> preferred) {
        List<AnimalRef> ordered = new ArrayList<>();
        for (String id : preferTargetFirst(preferred)) {
            AnimalRef ref = refOf(id);
            if (ref != null) {
                ordered.add(ref);
            }
        }
        List<AnimalRef> rest = new ArrayList<>(candidates);
        rest.sort(this::byDistanceToHome);
        rest = rest.reversed();
        for (AnimalRef animal : rest) {
            if (!animal.baby() && ordered.stream().noneMatch(placed -> placed.id().equals(animal.id()))) {
                ordered.add(animal);
            }
        }
        for (AnimalRef ref : ordered) {
            if (!live.containsKey(ref.id()) || handledIds.contains(ref.id())) {
                continue;
            }
            if (HerdSafety.watchedByBaby(ref, herd)) {
                skipped++;
                handledIds.add(ref.id());
                continue;
            }
            if (deferEndangered(ref)) {
                continue;
            }
            return ref;
        }
        return null;
    }

    private int liveAdults(List<AnimalRef> candidates) {
        int count = 0;
        for (AnimalRef animal : candidates) {
            Animal liveAnimal = live.get(animal.id());
            if (!animal.baby() && liveAnimal != null && !liveAnimal.isRemoved() && liveAnimal.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private AnimalRef firstStrikableIds(List<AnimalRef> refs) {
        for (AnimalRef ref : refs) {
            if (!live.containsKey(ref.id()) || deferEndangered(ref)) {
                continue;
            }
            if (HerdSafety.watchedByBaby(ref, herd)) {
                skipped++;
                handledIds.add(ref.id());
                continue;
            }
            return ref;
        }
        return null;
    }

    private AnimalRef refOf(String id) {
        for (AnimalRef animal : herd) {
            if (animal.id().equals(id)) {
                return animal;
            }
        }
        return null;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
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

    private void act() {
        if (current == null || current.isRemoved() || !current.isAlive()) {
            current = null;
            currentId = null;
            state = State.PICK;
            return;
        }
        Optional<String> danger = HerdSafety.dangerReason(current.isInLava(), current.isOnFire(),
                current.isInWater());
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
            case BREED_FIRST -> breedFirst();
            case BREED_SECOND -> breedSecond();
            case FEED_ONE -> feedOne();
            case SHEAR_ONE -> shearOne();
            case MILK_ONE -> milkOne();
            case CULL_ONE, HUNT_ONE -> strikeOne();
        }
    }

    private void breedFirst() {
        Item feed = feedItem(wanted.get(0));
        if (!actions.feedAdult(current, feed)) {
            fail("корм закончился посреди разведения: нужен " + FeedTable.feedFor(wanted.get(0)).get(0)
                    + ", сделано пар " + done + " из " + goal);
            return;
        }
        recordAction();
        handledIds.add(currentId);
        Animal partner = live.get(pendingSecondId);
        if (partner == null || partner.isRemoved() || !partner.isAlive()) {
            refreshSnapshot(true);
            current = null;
            currentId = null;
            state = State.PICK;
            return;
        }
        firstParent = current;
        current = partner;
        currentId = pendingSecondId;
        act = Act.BREED_SECOND;
        beginApproach(partner);
    }

    private void breedSecond() {
        Item feed = feedItem(wanted.get(0));
        if (!actions.feedAdult(current, feed)) {
            fail("корм закончился посреди разведения: нужен " + FeedTable.feedFor(wanted.get(0)).get(0)
                    + ": первая голова покормлена зря, сделано пар " + done + " из " + goal);
            return;
        }
        recordAction();
        handledIds.add(currentId);
        Animal parent = firstParent;
        if (parent == null || parent.isRemoved() || !parent.isAlive()) {
            firstParent = null;
            current = null;
            currentId = null;
            pendingSecondId = null;
            refreshSnapshot(true);
            state = State.PICK;
            return;
        }
        actions.breedPair(parent, current);
        born++;
        done++;
        firstParent = null;
        current = null;
        currentId = null;
        pendingSecondId = null;
        refreshSnapshot(true);
        state = State.PICK;
    }

    private void feedOne() {
        Item feed = feedItem(wanted.get(0));
        boolean baby = current instanceof AgeableMob ageable && ageable.isBaby();
        boolean eaten;
        if (baby) {
            eaten = actions.feedBaby((AgeableMob) current, feed);
        } else {
            eaten = actions.feedAdult(current, feed);
        }
        if (!eaten) {
            fail("корм закончился: нужен " + FeedTable.feedFor(wanted.get(0)).get(0) + ", покормлено " + fed + " из "
                    + goal);
            return;
        }
        recordAction();
        fed++;
        done++;
        handledIds.add(currentId);
        if (baby) {
            babiesFed++;
        }
        current = null;
        currentId = null;
        state = State.PICK;
    }

    private void shearOne() {
        ItemStack shears = actions.shearsStack();
        if (shears.isEmpty()) {
            fail("ножницы сломались посреди стрижки: пострижено " + done + " из " + goal);
            return;
        }
        if (!(current instanceof Sheep sheep) || sheep.isSheared()) {
            current = null;
            currentId = null;
            state = State.PICK;
            return;
        }
        recordAction();
        actions.shearWool(sheep, shears);
        sheared++;
        done++;
        handledIds.add(currentId);
        String woolId = "minecraft:" + sheep.getColor().getName() + "_wool";
        startCollect(IdFilter.of(List.of(woolId)), current.blockPosition(), COLLECT_RADIUS);
    }

    private void milkOne() {
        Optional<String> failure = actions.milkIntoInventory();
        if (failure.isPresent()) {
            fail(failure.get() + ": подоено " + done + " из " + goal);
            return;
        }
        recordAction();
        milked++;
        done++;
        handledIds.add(currentId);
        current = null;
        currentId = null;
        state = State.PICK;
    }

    private void strikeOne() {
        recordAction();
        if (actions.strike(current, swordTier)) {
            culled++;
            done++;
            BlockPos at = current.blockPosition();
            current = null;
            currentId = null;
            refreshSnapshot(true);
            startCollect(IdFilter.matchAll(), at, COLLECT_RADIUS);
        } else {
            beginApproach(current);
        }
    }

    private void collectTick() {
        int before = collector.entityQueries();
        DropCollector.State collected = collector.tick();
        maxEntityQueriesPerTick = Math.max(maxEntityQueriesPerTick, collector.entityQueries() - before);
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        entityQueries += collector.entityQueries();
        if (collected == DropCollector.State.FAILED) {
            fail("не удалось подобрать рядом с " + currentId + ": " + collector.failureReason());
            return;
        }
        if (job == HerdJob.COLLECT_EGGS) {
            eggsPicked += collector.collected();
            done = eggsPicked;
            collector = null;
            current = null;
            currentId = null;
            finishWithTally();
            return;
        }
        collector = null;
        current = null;
        currentId = null;
        state = State.PICK;
    }

    private void lureTick() {
        if (lureTarget == null || lureTarget.isRemoved() || !lureTarget.isAlive()) {
            nextEscapee();
            return;
        }
        Optional<String> danger = HerdSafety.dangerReason(lureTarget.isInLava(), lureTarget.isOnFire(),
                lureTarget.isInWater());
        if (danger.isPresent()) {
            skipped++;
            lureSkipped.add(lureId);
            lureTarget = null;
            lureId = null;
            state = State.PICK;
            return;
        }
        BlockCoord escapeePos = new BlockCoord(lureTarget.blockPosition().getX(), lureTarget.blockPosition().getY(),
                lureTarget.blockPosition().getZ());
        if (pen.inside(escapeePos)) {
            actions.stopLeading(lureTarget);
            lureTarget = null;
            lureId = null;
            escapedReturned++;
            refreshSnapshot(true);
            state = State.PICK;
            return;
        }
        if (++lureTicks > LURE_TICKS_PER_ESCAPEE) {
            actions.stopLeading(lureTarget);
            fail("не удалось вернуть " + lureId + " в загон за " + LURE_TICKS_PER_ESCAPEE
                    + " тиков: животное не идёт за кормом");
            return;
        }
        BlockCoord returnPoint = pen.nearestInside(escapeePos);
        if (!approach.isActive()) {
            approach.begin(new Vec3(returnPoint.x() + 0.5, returnPoint.y(), returnPoint.z() + 0.5), 1.5);
        }
        approach.tick();
        if (lureTarget.getNavigation().isDone()) {
            actions.directTowards(lureTarget, spirit.position(), LURE_SPEED);
        }
    }

    private void findEggs() {
        List<ItemEntity> eggs = spirit.level().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(home.x() - searchRadius, home.y() - searchRadius,
                        home.z() - searchRadius, home.x() + searchRadius + 1.0, home.y() + searchRadius + 1.0,
                        home.z() + searchRadius + 1.0),
                drop -> !drop.isRemoved() && drop.getItem().is(Items.EGG)
                        && pen.inside(new BlockCoord(drop.blockPosition().getX(), drop.blockPosition().getY(),
                                drop.blockPosition().getZ())));
        entityQueries++;
        maxEntityQueriesPerTick = Math.max(maxEntityQueriesPerTick, 1);
        if (eggs.isEmpty()) {
            fail("яиц в загоне нет: куры несутся медленно, мир не изменён");
            return;
        }
        startCollect(IdFilter.of(List.of("minecraft:egg")), new BlockPos(home.x(), home.y(), home.z()), searchRadius);
    }

    private void finishCheck() {
        finishWithTally();
    }

    private void finishWithTally() {
        int kept = 0;
        if (job == HerdJob.CULL && adultsAtStart >= 0) {
            kept = Math.max(0, adultsAtStart - culled);
        }
        if (job == HerdJob.HERD_CHECK) {
            kept = censusTotal();
        }
        HerdTally tally = new HerdTally(fed, babiesFed, born, sheared, milked, eggsPicked, culled, kept,
                escapedReturned, skipped, goal);
        if (tally.outcome(job) instanceof HerdTally.Failed failed) {
            fail(failed.text());
        } else {
            outcome = Outcome.SUCCEEDED;
            cancel();
        }
    }

    private int censusTotal() {
        int total = 0;
        HerdCensus.Census census = HerdCensus.summarize(herd);
        for (Species species : wanted) {
            total += census.of(species).total();
        }
        return total;
    }

    private boolean gateSword() {
        HerdActions.SwordSurvey survey = actions.surveySwords();
        if (!survey.unknown().isEmpty() && (survey.best() == null || !survey.best().atLeast(SwordTier.IRON))) {
            fail("неизвестный меч " + survey.unknown().get(0) + ": тир считается по таблице " + SwordTier.RESOURCE
                    + ", а не по урону, животное не бьют зря");
            return false;
        }
        swordTier = survey.best() == null ? SwordTier.HAND : survey.best();
        return true;
    }

    private void beginApproach(Animal animal) {
        approach.begin(animal, APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private boolean inReach(Animal animal) {
        return spirit.getEyePosition().distanceTo(animal.position()) <= REACH;
    }

    private void startCollect(IdFilter filter, BlockPos at, int radius) {
        collector = new DropCollector(spirit, filter, new BlockCoord(at.getX(), at.getY(), at.getZ()), radius,
                PickupTuning.DEFAULT);
        state = State.COLLECT;
    }

    private void refreshSnapshot(boolean event) {
        if (!event && ticks - lastRefresh < CatalogLimits.HERD_REFRESH_TICKS && !herd.isEmpty()) {
            return;
        }
        List<Animal> animals = HerdSurvey.query(spirit.level(), spirit.position(),
                searchRadius + CatalogLimits.HERD_SIGHT_MARGIN);
        List<AnimalRef> refs = new ArrayList<>(animals.size());
        live.clear();
        for (Animal animal : animals) {
            live.put(animal.getStringUUID(), animal);
            HerdSurvey.map(animal, pen).ifPresent(refs::add);
        }
        herd = List.copyOf(refs);
        entityQueries++;
        maxEntityQueriesPerTick = Math.max(maxEntityQueriesPerTick, 1);
        lastRefresh = ticks;
    }

    private List<AnimalRef> inPenOf(Species species) {
        List<AnimalRef> inside = new ArrayList<>();
        for (AnimalRef animal : herd) {
            if (animal.species() == species && animal.inPen()) {
                inside.add(animal);
            }
        }
        return inside;
    }

    private List<AnimalRef> escapeesOf(Species species) {
        List<AnimalRef> escapees = new ArrayList<>();
        for (AnimalRef animal : herd) {
            if (animal.species() == species && !animal.inPen() && live.containsKey(animal.id())
                    && !lureSkipped.contains(animal.id())) {
                escapees.add(animal);
            }
        }
        escapees.sort(this::byDistanceToHome);
        return escapees;
    }

    private List<AnimalRef> unhandled(List<AnimalRef> refs) {
        List<AnimalRef> fresh = new ArrayList<>(refs.size());
        for (AnimalRef animal : refs) {
            if (!handledIds.contains(animal.id())) {
                fresh.add(animal);
            }
        }
        return fresh;
    }

    private AnimalRef nextFeedTarget(Species species) {
        List<AnimalRef> babies = new ArrayList<>();
        List<AnimalRef> grown = new ArrayList<>();
        for (AnimalRef animal : herd) {
            if (animal.species() != species || !animal.inPen() || !live.containsKey(animal.id())
                    || handledIds.contains(animal.id())) {
                continue;
            }
            (animal.baby() ? babies : grown).add(animal);
        }
        babies.sort(this::byDistanceToHome);
        grown.sort(this::byDistanceToHome);
        if (babiesFirst && !babies.isEmpty()) {
            return applyPreferred(babies);
        }
        if (!grown.isEmpty()) {
            return applyPreferred(grown);
        }
        if (!babiesFirst && !babies.isEmpty()) {
            return applyPreferred(babies);
        }
        if (!babies.isEmpty()) {
            return applyPreferred(babies);
        }
        return null;
    }

    private AnimalRef nearestOf(Species species, boolean adultsOnly, boolean woolOnly) {
        List<AnimalRef> candidates = new ArrayList<>();
        for (AnimalRef animal : herd) {
            if (animal.species() != species || !animal.inPen() || !live.containsKey(animal.id())
                    || handledIds.contains(animal.id())) {
                continue;
            }
            if (adultsOnly && animal.baby()) {
                continue;
            }
            if (woolOnly && !animal.woolReady()) {
                continue;
            }
            if (preferredTarget != null && !preferredTarget.equals(animal.id())) {
                continue;
            }
            candidates.add(animal);
        }
        preferredTarget = null;
        candidates.sort(this::byDistanceToHome);
        for (AnimalRef candidate : candidates) {
            if (deferEndangered(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean deferEndangeredId(String id) {
        AnimalRef ref = refOf(id);
        return ref != null && deferEndangered(ref);
    }

    private boolean deferEndangered(AnimalRef ref) {
        Animal animal = live.get(ref.id());
        if (animal == null || animal.isRemoved()) {
            return true;
        }
        Optional<String> danger = HerdSafety.dangerReason(animal.isInLava(), animal.isOnFire(),
                animal.isInWater());
        if (danger.isPresent()) {
            skipped++;
            handledIds.add(ref.id());
            return true;
        }
        return false;
    }

    private AnimalRef applyPreferred(List<AnimalRef> ordered) {
        if (preferredTarget != null) {
            for (AnimalRef animal : ordered) {
                if (animal.id().equals(preferredTarget)) {
                    preferredTarget = null;
                    return deferEndangered(animal) ? null : animal;
                }
            }
            preferredTarget = null;
        }
        for (AnimalRef animal : ordered) {
            if (!deferEndangered(animal)) {
                return animal;
            }
        }
        return null;
    }

    private List<String> preferTargetFirst(List<String> ids) {
        if (preferredTarget != null && ids.contains(preferredTarget)) {
            List<String> ordered = new ArrayList<>(ids);
            ordered.remove(preferredTarget);
            ordered.add(0, preferredTarget);
            preferredTarget = null;
            return ordered;
        }
        preferredTarget = null;
        return ids;
    }

    private List<AnimalRef> preferRefTargetFirst(List<AnimalRef> refs) {
        if (preferredTarget != null) {
            for (AnimalRef animal : refs) {
                if (animal.id().equals(preferredTarget)) {
                    List<AnimalRef> ordered = new ArrayList<>(refs);
                    ordered.remove(animal);
                    ordered.add(0, animal);
                    preferredTarget = null;
                    return ordered;
                }
            }
            preferredTarget = null;
        }
        return refs;
    }

    private Map<String, Integer> feedCounts(Species species) {
        Map<String, Integer> counts = new HashMap<>();
        for (String feedId : FeedTable.feedFor(species)) {
            counts.put(feedId, actions.count(HerdActions.registryItem(feedId)));
        }
        return counts;
    }

    private Item feedItem(Species species) {
        return HerdActions.registryItem(FeedTable.feedFor(species).get(0));
    }

    private boolean hasFeed(Species species) {
        for (String feedId : FeedTable.feedFor(species)) {
            if (actions.count(HerdActions.registryItem(feedId)) > 0) {
                return true;
            }
        }
        return false;
    }

    private void nextEscapee() {
        lureTarget = null;
        lureId = null;
        state = State.PICK;
    }

    private void recordAction() {
        int now = spirit.tickCount;
        actionsThisTick = now == actionTick ? actionsThisTick + 1 : 1;
        actionTick = now;
        maxActionsPerTick = Math.max(maxActionsPerTick, actionsThisTick);
        actionTicks++;
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private BlockCoord spiritCoord() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static int number(Map<String, Object> saved, String key) {
        return saved.get(key) instanceof Number value ? Math.max(0, value.intValue()) : 0;
    }

    private static int adults(List<AnimalRef> refs) {
        int count = 0;
        for (AnimalRef animal : refs) {
            if (!animal.baby()) {
                count++;
            }
        }
        return count;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private int byDistanceToHome(AnimalRef left, AnimalRef right) {
        int byDistance = Long.compare(distanceSquared(home, left.pos()), distanceSquared(home, right.pos()));
        return byDistance != 0 ? byDistance : left.id().compareTo(right.id());
    }
}
