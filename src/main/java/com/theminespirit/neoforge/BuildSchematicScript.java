package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
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
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
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
import org.slf4j.Logger;

/**
 * Стройка по схеме с места: сначала очищается обязательный воздух и чужие блоки в клетках схемы,
 * потом блоки ставятся снизу вверх — всё, до чего дух дотягивается со стоянки ({@link ReachPlanner}), с темпом
 * {@link BuildPace} и взмахом руки; двери и калитки — последними и снаружи, чтобы дух не замуровал себя.
 * Конец — сверка с миром: SUCCEEDED только когда каждая клетка совпала со схемой.
 */
final class BuildSchematicScript implements ScriptInstance {
    private static final int MAX_FINISH_WAITS = 200;
    // Шаг к середине клетки (тело шириной 0,6 тогда не выходит за её края): дольше — стоянка не годится.
    private static final int MAX_CENTRING_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();
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
    private TaskRunner supplyRunner;
    private int trips;
    private int centring;

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
        if (supplyRunner != null) {
            RunStatus supply = supplyRunner.tick();
            if (supply == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            if (supply != RunStatus.SUCCEEDED) {
                LOGGER.info(
                        "Стройка {}: заход {} за материалами закончился {}: {}; дух {}",
                        buildKey,
                        trips,
                        supply,
                        supplyRunner.failureReason().orElse("причина не названа"),
                        feet());
            }
            // После захода — заново: клетки, отложенные без опоры или без стоянки, теперь могут стать доступны.
            supplyRunner = null;
            blocked.clear();
            refusals.clear();
            standsRetried = false;
        }
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
        List<Schematic.PlacedEntry> unsupplied = new ArrayList<>();
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
            if (BlockItems.hasBlockItem(entry.block())
                    && BlockItems.countIn(spirit.getInventory(), entry.block()) == 0) {
                unsupplied.add(entry);
                continue;
            }
            missing.add(entry);
        }
        // Решение владельца 20.10: дух ставит всё, на что хватает взятого, и только потом идёт за следующей
        // порцией. Нехватка — отказ «не хватило материала», когда взятым больше нечего ставить.
        if (!unsupplied.isEmpty()) {
            // Пока часть материала не взята, цели — только клетки с опорой: висящие над невзятым ждут захода,
            // иначе их без стоянки отметили бы недоступными.
            missing.removeIf(entry -> !site.supported(entry));
        }
        if (missing.isEmpty() && !unsupplied.isEmpty()) {
            return noMaterial(unsupplied.get(0).block());
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
        // Двери — последним шагом, когда всё остальное стоит: без материала на стены сперва заход за ним.
        if (lastStep && unsupplied.stream().anyMatch(entry -> !SchematicBlocks.isEntranceBlock(entry.block()))) {
            return noMaterial(unsupplied.get(0).block());
        }
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
        boolean overlapped = false;
        if (mayPlaceHere) {
            for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), feet(), cells, world)) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                // Тело духа шире клетки ног, когда он стоит у края: блок в соседней клетке на уровне головы душил
                // его в стене (дом игрока 3, 20.10). Сперва — на середину своей клетки.
                if (site.overlaps(entry, spirit.getBoundingBox())) {
                    overlapped = true;
                    continue;
                }
                Optional<ScriptStatus> placedNow = placeOne(entry);
                if (placedNow.isPresent()) {
                    return placedNow.get();
                }
            }
        }
        if (overlapped) {
            if (++centring <= MAX_CENTRING_TICKS) {
                BlockCoord here = feet();
                spirit.getNavigation().stop();
                spirit.getMoveControl().setWantedPosition(here.x() + 0.5, spirit.getY(), here.z() + 0.5, 1.0);
                return ScriptStatus.RUNNING;
            }
            // К середине не встать (мебель, край): эта стоянка не годится, ставить отсюда — задушить себя.
            centring = 0;
            excludedStands.add(feet());
            return moveTowards(cells, world, lastStep);
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
                centring = 0;
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
                    return Optional.of(noMaterial(entry.block()));
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
        Optional<BlockCoord> stand = stand(cells, world, occupied, outsideOnly);
        if (stand.isEmpty() && !standsRetried && !excludedStands.isEmpty()) {
            standsRetried = true;
            excludedStands.clear();
            stand = stand(cells, world, occupied, outsideOnly);
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

    // Последний шаг снаружи (двери, ставни) — стоянки только на земле, верхние ставни ставятся издалека снизу.
    private Optional<BlockCoord> stand(
            List<BlockCoord> cells, ReachPlanner.World world, Set<BlockCoord> occupied, boolean outsideOnly) {
        return outsideOnly
                ? ReachPlanner.nextStandFromAfar(SpiritReach.current(), feet(), cells, world, excludedStands, occupied)
                : ReachPlanner.nextStand(SpiritReach.current(), feet(), cells, world, excludedStands, occupied);
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

    private ScriptStatus noMaterial(String blockId) {
        if (trips < BuildSupplyTrip.MAX_TRIPS) {
            List<ScriptCall> steps = BuildSupplyTrip.plan(spirit, site);
            if (!steps.isEmpty()) {
                trips++;
                LOGGER.info(
                        "Стройка {}: заход {} за материалами ({} шагов withdraw), поставлено {} из {}, не хватило '{}'",
                        buildKey,
                        trips,
                        steps.size(),
                        placed,
                        site.primaries().size(),
                        blockId);
                approach.cancel();
                approachStand = null;
                supplyRunner = TaskRunner.start(
                        new SequenceNode(new ArrayList<TaskNode>(steps)),
                        SpiritScripts.registryFor(spirit),
                        ConditionRegistry.of(Map.of()));
                return ScriptStatus.RUNNING;
            }
        }
        failed++;
        tally.failed();
        int have = BlockItems.countIn(spirit.getInventory(), blockId);
        int needed = countMissing(blockId);
        tally.supplies(placed + have, Math.max(needed - have, 0));
        return fail("не хватило материала '" + blockId + "': поставлено " + placed + " из "
                + site.primaries().size() + "; нужно " + needed + ", есть " + have);
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

    private void stopSupply() {
        if (supplyRunner != null) {
            supplyRunner.cancel();
            supplyRunner = null;
        }
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
        stopSupply();
        approach.cancel();
        breaker.cancel();
        breaking = null;
        reservations.releaseAll(spirit.getUUID());
        leaveSquad();
    }

    @Override
    public void suspend() {
        stopSupply();
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
