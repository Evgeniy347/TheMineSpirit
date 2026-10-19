package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildSquad;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.CellPartition;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildSchematicParams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Стройка по схеме с места: сначала очищается обязательный воздух и чужие блоки в клетках схемы,
 * потом блоки ставятся снизу вверх — всё, до чего дух дотягивается со стоянки ({@link ReachPlanner}), с темпом
 * {@link BuildPace} и взмахом руки; двери и калитки — последними и снаружи, чтобы дух не замуровал себя.
 * Конец — сверка с миром: SUCCEEDED только когда каждая клетка совпала со схемой.
 */
final class BuildSchematicScript implements ScriptInstance {
    private static final int MAX_FINISH_WAITS = 200;

    private final SpiritEntity spirit;
    private final Level level;
    private final SchematicSite site;
    private final BlockPlacer placer;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final BuildReservations reservations;
    private final BuildKey buildKey;
    private final BuildSquad squad;
    private final BuildResult tally = new BuildResult();
    private final Set<BlockCoord> excludedStands = new HashSet<>();
    private final Map<BlockCoord, String> blocked = new LinkedHashMap<>();
    private final Map<BlockCoord, Integer> refusals = new HashMap<>();

    private Set<BlockCoord> ownShare = Set.of();
    private long squadVersion = -1;
    private BlockCoord approachStand;
    private BlockCoord breaking;
    private long ticks;
    private long lastPlaceTick = Long.MIN_VALUE / 2;
    private int placed;
    private int cleared;
    private int failed;
    private int finishWaits;
    private boolean standsRetried;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private String lastRefusal = "";

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        BlockCoord origin = new BlockCoord(params.x(), params.y(), params.z());
        BuildRotation rotation = params.rotation();
        this.site = new SchematicSite(level, SchematicResources.load(params.file()), origin, rotation);
        this.placer = new BlockPlacer(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.buildKey = new BuildKey(params.file(), origin, rotation);
        this.squad = BuildSquadRegistry.join(level, buildKey, spirit.getUUID());
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки по схеме");
        }
        ticks++;
        refreshShare();
        if (breaking != null) {
            return breakTick();
        }
        if (approach.isActive()) {
            return approachTick();
        }
        return planTick();
    }

    private ScriptStatus approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
                return ScriptStatus.RUNNING;
            }
            case ARRIVED -> {
                excludedStands.add(approachStand);
            }
            case STUCK, TIMEOUT -> {
                excludedStands.add(approachStand);
                lastRefusal = "стоянка " + approachStand + " недостижима: "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal());
            }
        }
        approach.cancel();
        approachStand = null;
        return planTick();
    }

    private ScriptStatus breakTick() {
        BlockBreaker.Progress progress = breaker.tick();
        if (progress == BlockBreaker.Progress.BREAKING) {
            return ScriptStatus.RUNNING;
        }
        if (progress == BlockBreaker.Progress.BROKEN) {
            cleared++;
        }
        breaking = null;
        excludedStands.clear();
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus planTick() {
        List<BlockCoord> clear = new ArrayList<>(site.toClear());
        clear.removeIf(blocked::containsKey);
        if (!clear.isEmpty()) {
            return clearStep(clear);
        }
        if (ticks - lastPlaceTick < BuildPace.intervalTicks(spirit)) {
            return ScriptStatus.RUNNING;
        }
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        boolean waitingForeign = false;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (site.matches(entry) && site.secondPartMatches(entry)) {
                continue;
            }
            if (blocked.containsKey(entry.pos())) {
                continue;
            }
            if (reservations.foreign(entry.pos(), spirit.getUUID())) {
                waitingForeign = true;
                continue;
            }
            if (site.rightBlockWrongState(entry)) {
                site.repairState(entry);
                return ScriptStatus.RUNNING;
            }
            missing.add(entry);
        }
        if (missing.isEmpty()) {
            return waitingForeign && ++finishWaits <= MAX_FINISH_WAITS ? ScriptStatus.RUNNING : finish();
        }
        List<Schematic.PlacedEntry> main = new ArrayList<>();
        List<Schematic.PlacedEntry> entrances = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            (SchematicBlocks.isEntranceBlock(entry.block()) ? entrances : main).add(entry);
        }
        boolean lastStep = main.isEmpty();
        List<Schematic.PlacedEntry> targets = ownFirst(lastStep ? entrances : main);
        return placeStep(targets, lastStep);
    }

    private ScriptStatus clearStep(List<BlockCoord> clear) {
        SpiritReach reach = SpiritReach.current();
        ReachPlanner.World world = clearWorld();
        for (BlockCoord cell : ReachPlanner.workableNow(reach, feet(), clear, world)) {
            BlockBreaker.Begin begin = breaker.beginAnyDistance(SchematicSite.toPos(cell));
            switch (begin) {
                case BlockBreaker.Started ignored -> {
                    breaking = cell;
                    return ScriptStatus.RUNNING;
                }
                case BlockBreaker.Protected refused -> blocked.put(cell, "очистка запрещена: " + refused.reason());
                case BlockBreaker.Refused refused -> {
                    if (refused.reason() != BlockBreaker.RefuseReason.AIR
                            && refused.reason() != BlockBreaker.RefuseReason.OUT_OF_REACH) {
                        blocked.put(cell, "очистка не удалась: " + refused.message());
                    }
                }
            }
        }
        return moveTowards(clear, world, false);
    }

    private ScriptStatus placeStep(List<Schematic.PlacedEntry> targets, boolean lastStep) {
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : targets) {
            byCell.put(entry.pos(), entry);
        }
        List<BlockCoord> cells = List.copyOf(byCell.keySet());
        ReachPlanner.World world = placeWorld(byCell, lastStep);
        boolean mayPlaceHere = !lastStep || site.outsideFootprint(feet());
        if (mayPlaceHere) {
            for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), feet(), cells, world)) {
                Optional<ScriptStatus> placedNow = placeOne(byCell.get(cell));
                if (placedNow.isPresent()) {
                    return placedNow.get();
                }
            }
        }
        return moveTowards(cells, world, lastStep);
    }

    private Optional<ScriptStatus> placeOne(Schematic.PlacedEntry entry) {
        BlockCoord cell = entry.pos();
        reservations.reserve(cell, spirit.getUUID());
        BlockPlacer.Begin begin = placer.begin(SchematicSite.toPos(cell), site.wanted(entry), entry.block());
        reservations.release(cell, spirit.getUUID());
        switch (begin) {
            case BlockPlacer.Started started -> {
                if (!entry.text().isBlank()) {
                    SignTexts.write(level, started.pos(), entry.text());
                }
                placed++;
                tally.placed(entry.block());
                lastPlaceTick = ticks;
                refusals.remove(cell);
                excludedStands.clear();
                standsRetried = false;
                return Optional.of(ScriptStatus.RUNNING);
            }
            case BlockPlacer.Protected refused -> {
                blocked.put(cell, "запрещено: " + refused.reason());
                lastRefusal = "запрещено: " + refused.reason();
                return Optional.empty();
            }
            case BlockPlacer.Refused refused -> {
                lastRefusal = refused.reason() + ": " + refused.message();
                if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
                    failed++;
                    tally.failed();
                    int have = BlockItems.countIn(spirit.getInventory(), entry.block());
                    int needed = countMissing(entry.block());
                    tally.supplies(placed + have, Math.max(needed - have, 0));
                    return Optional.of(fail("не хватило материала '" + entry.block() + "': поставлено " + placed
                            + " из " + site.primaries().size() + "; нужно " + needed + ", есть " + have));
                }
                int attempts = refusals.merge(cell, 1, Integer::sum);
                if (attempts >= BuildTuning.MAX_CONSECUTIVE_SKIPS) {
                    blocked.put(cell, lastRefusal);
                }
                return Optional.empty();
            }
        }
    }

    private ScriptStatus moveTowards(List<BlockCoord> cells, ReachPlanner.World world, boolean outsideOnly) {
        Set<BlockCoord> occupied = new HashSet<>(site.schemeCells());
        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(SpiritReach.current(), feet(), cells, world, excludedStands, occupied);
        if (stand.isEmpty() && !standsRetried && !excludedStands.isEmpty()) {
            standsRetried = true;
            excludedStands.clear();
            stand = ReachPlanner.nextStand(SpiritReach.current(), feet(), cells, world, excludedStands, occupied);
        }
        if (stand.isEmpty()) {
            for (BlockCoord cell : cells) {
                blocked.putIfAbsent(
                        cell,
                        "ни с одной стоянки не дотянуться" + (outsideOnly ? " снаружи постройки" : "")
                                + (lastRefusal.isBlank() ? "" : "; " + lastRefusal));
            }
            return ScriptStatus.RUNNING;
        }
        approachStand = stand.get();
        approach.begin(
                new Vec3(approachStand.x() + 0.5, approachStand.y(), approachStand.z() + 0.5),
                BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    private ReachPlanner.World clearWorld() {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return site.standable(feet);
            }
        };
    }

    private ReachPlanner.World placeWorld(Map<BlockCoord, Schematic.PlacedEntry> byCell, boolean outsideOnly) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                return entry != null && site.freeForPlacing(entry) && site.supported(entry);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return site.standable(feet) && (!outsideOnly || site.outsideFootprint(feet));
            }
        };
    }

    private List<Schematic.PlacedEntry> ownFirst(List<Schematic.PlacedEntry> targets) {
        List<Schematic.PlacedEntry> own = new ArrayList<>();
        List<Schematic.PlacedEntry> others = new ArrayList<>();
        for (Schematic.PlacedEntry entry : targets) {
            (ownShare.contains(entry.pos()) ? own : others).add(entry);
        }
        own.addAll(others);
        return own;
    }

    private void refreshShare() {
        if (squad.slotOf(spirit.getUUID()) < 0) {
            squad.join(spirit.getUUID());
        }
        if (squad.version() == squadVersion) {
            return;
        }
        squadVersion = squad.version();
        Set<BlockCoord> share = new HashSet<>();
        for (Schematic.PlacedEntry entry :
                CellPartition.split(site.primaries(), squad.size()).get(squad.slotOf(spirit.getUUID()))) {
            share.add(entry.pos());
        }
        ownShare = Set.copyOf(share);
    }

    private ScriptStatus finish() {
        reservations.releaseAll(spirit.getUUID());
        List<Schematic.PlacedEntry> missing = site.missing();
        List<BlockCoord> clear = site.toClear();
        if (missing.isEmpty() && clear.isEmpty()) {
            leaveSquad();
            tally.supplies(placed, 0);
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        for (int i = 0; i < blocked.size(); i++) {
            tally.skipped();
        }
        return fail("не совпало со схемой " + missing.size() + " из "
                + site.entries().size() + " клеток"
                + (clear.isEmpty() ? "" : ", не очищено " + clear.size()) + ": " + sample(missing, clear)
                + "; дух " + feet() + "; поставлено " + placed + ", очищено " + cleared + "; причины: "
                + reasons());
    }

    private String reasons() {
        if (blocked.isEmpty()) {
            return lastRefusal.isBlank() ? "нет" : lastRefusal;
        }
        Map.Entry<BlockCoord, String> first = blocked.entrySet().iterator().next();
        return first.getKey() + " — " + first.getValue() + (blocked.size() > 1 ? " и ещё " + (blocked.size() - 1) : "");
    }

    private static String sample(List<Schematic.PlacedEntry> missing, List<BlockCoord> clear) {
        List<String> shown = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            if (shown.size() == 6) {
                shown.add("…");
                break;
            }
            shown.add(entry.block() + "@" + entry.pos());
        }
        for (BlockCoord cell : clear) {
            if (shown.size() >= 8) {
                break;
            }
            shown.add("воздух@" + cell);
        }
        return String.join(", ", shown);
    }

    private int countMissing(String blockId) {
        int total = 0;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (entry.block().equals(blockId) && !site.matches(entry)) {
                total++;
            }
        }
        return Math.max(total, 1);
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        cancel();
        return outcome;
    }

    private void leaveSquad() {
        BuildSquadRegistry.leave(level, buildKey, spirit.getUUID());
    }

    @Override
    public Optional<Progress> progress() {
        int total = Math.max(site.entries().size(), 1);
        return Optional.of(new Progress(site.entries().size() - site.missing().size(), total));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(tally.toData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
        breaker.cancel();
        breaking = null;
        reservations.releaseAll(spirit.getUUID());
        leaveSquad();
    }

    @Override
    public void suspend() {
        approach.suspend();
        breaker.cancel();
        breaking = null;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.copyOf(new BuildState(placed, 0, blocked.size(), failed, outcome == ScriptStatus.RUNNING).toMap());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        placed = Math.max(placed, snapshot.placed());
        failed = Math.max(failed, snapshot.failed());
        String kind = site.entries().isEmpty() ? "" : site.entries().get(0).block();
        for (int i = 0; i < snapshot.placed() && !kind.isEmpty(); i++) {
            tally.placed(kind);
        }
        for (int i = 0; i < snapshot.failed(); i++) {
            tally.failed();
        }
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
