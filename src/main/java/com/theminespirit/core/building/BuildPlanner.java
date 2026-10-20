package com.theminespirit.core.building;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * План стройки с заходами к сундуку (решение владельца 20.10): схема, поворот, сундук с материалами схемы и пустой
 * инвентарь → детерминированная последовательность заходов (что взято) и стоянок (что поставлено с каждой) при
 * идеальном движении: стоянка годится, если до неё есть пеший путь по уже поставленному. Повторяет решения стройки
 * духа (полосы по 4 слоя, порции, двери последним шагом снаружи, правило отхода), но без физики и поиска пути игры —
 * поэтому план не плавает и снимается снапшотом.
 */
public final class BuildPlanner {
    public static final int SLOTS = 36;
    static final int RING = 6;
    static final int MAX_TRIPS = 32;
    // Ниже — пол первого этажа, площади и крыльцо: поверхность, но не крыша.
    static final int ROOF_MIN_HEIGHT = 3;
    private static final int MAX_ROUNDS = 20_000;
    private static final int STACK = 64;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // Что брать в заход: ALL — недостающее всей постройки по порядку схемы (стройка до 20.10), PHASE — только клетки
    // текущего этапа, FILL — черновые текущей и следующих полос по порядку схемы (снизу вверх), декор и отделка — на
    // этапе отделки.
    public enum Portion {
        ALL,
        PHASE,
        FILL
    }

    public record Taken(String item, int count) {}

    public sealed interface Step permits Trip, Work, Stuck {}

    // slotsNow — слоты с материалом текущего этапа после захода, slotsLater — с материалом будущих этапов.
    public record Trip(int number, List<Taken> taken, int slotsNow, int slotsLater) implements Step {}

    public record Work(BlockCoord stand, String floor, int placed, int retreatRefusals, boolean onRoof)
            implements Step {}

    public record Stuck(int cells, BlockCoord sample, String reason) implements Step {}

    public record Plan(List<Step> steps) {
        public Plan {
            steps = List.copyOf(steps);
        }

        public long trips() {
            return steps.stream().filter(Trip.class::isInstance).count();
        }

        public long stands() {
            return steps.stream().filter(Work.class::isInstance).count();
        }

        public long roofStands() {
            return steps.stream()
                    .filter(step -> step instanceof Work work && work.onRoof())
                    .count();
        }

        public int retreatRefusals() {
            return steps.stream()
                    .filter(Work.class::isInstance)
                    .mapToInt(step -> ((Work) step).retreatRefusals())
                    .sum();
        }

        public int stuck() {
            return steps.stream()
                    .filter(Stuck.class::isInstance)
                    .mapToInt(step -> ((Stuck) step).cells())
                    .sum();
        }

        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            for (Step step : steps) {
                lines.add(
                        switch (step) {
                            case Trip trip ->
                                "заход " + trip.number() + ": " + describe(trip.taken()) + " | слоты: этап "
                                        + trip.slotsNow() + ", позже " + trip.slotsLater();
                            case Work work ->
                                "стоянка " + coord(work.stand()) + " на " + work.floor() + ": поставлено "
                                        + work.placed()
                                        + (work.retreatRefusals() > 0
                                                ? ", отказов отхода " + work.retreatRefusals()
                                                : "")
                                        + (work.onRoof() ? ", НА КРЫШЕ" : "");
                            case Stuck stuck ->
                                "не поставлено " + stuck.cells() + " (" + stuck.reason() + "), например "
                                        + coord(stuck.sample());
                        });
            }
            lines.add("итог: заходов " + trips() + ", стоянок " + stands() + ", на крыше " + roofStands()
                    + ", отказов отхода " + retreatRefusals() + ", не поставлено " + stuck());
            return List.copyOf(lines);
        }

        private static String describe(List<Taken> taken) {
            List<String> parts = new ArrayList<>();
            for (Taken item : taken) {
                parts.add(SchematicBlocks.path(item.item()) + "×" + item.count());
            }
            return String.join(", ", parts);
        }

        private static String coord(BlockCoord cell) {
            return cell.x() + "," + cell.y() + "," + cell.z();
        }
    }

    private final Schematic schematic;
    private final SpiritReach reach;
    private final Portion portion;
    private final int sizeX;
    private final int sizeZ;
    private final List<Schematic.PlacedEntry> primaries = new ArrayList<>();
    private final Map<BlockCoord, List<Schematic.PlacedEntry>> secondaries = new HashMap<>();
    private final Set<BlockCoord> schemeCells = new HashSet<>();
    private final Set<BlockCoord> standBlockers = new HashSet<>();
    private final Set<Long> coveredColumns = new HashSet<>();
    private final Map<BlockCoord, String> grid = new HashMap<>();
    private final Set<Schematic.PlacedEntry> placed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Schematic.PlacedEntry> blocked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> inventory = new TreeMap<>();
    private final Map<String, Integer> chest = new TreeMap<>();
    private final Map<String, Boolean> rough = new HashMap<>();
    private final Set<BlockCoord> touched = new HashSet<>();
    private final Map<String, Boolean> passable = new HashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private final SafetyView view = this::cellAt;
    private @Nullable Set<BlockCoord> reachable;
    private @Nullable Set<BlockCoord> retreatPath;
    private final BlockCoord supply;
    private BlockCoord feet;
    private int trips;
    private final Set<BlockCoord> excluded = new HashSet<>();
    private @Nullable Work current;
    private int highestBand = -1;

    private BuildPlanner(
            Schematic schematic, BuildRotation rotation, BlockCoord supply, SpiritReach reach, Portion portion) {
        this.schematic = schematic;
        this.reach = reach;
        this.portion = portion;
        this.sizeX = rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ());
        this.sizeZ = rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ());
        this.supply = supply;
        this.feet = supply;
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new HashMap<>();
        List<Schematic.PlacedEntry> seconds = new ArrayList<>();
        for (Schematic.PlacedEntry entry : schematic.entries(new BlockCoord(0, 0, 0), rotation)) {
            schemeCells.add(entry.pos());
            byCell.put(entry.pos(), entry);
            if (SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                seconds.add(entry);
            } else {
                primaries.add(entry);
                chest.merge(item(entry.block()), 1, Integer::sum);
            }
            if (!SchematicWalk.passable(entry.block())) {
                coveredColumns.add(column(entry.pos().x(), entry.pos().z()));
                standBlockers.add(entry.pos());
            }
        }
        for (Schematic.PlacedEntry second : seconds) {
            BlockCoord below = new BlockCoord(
                    second.pos().x(), second.pos().y() - 1, second.pos().z());
            Schematic.PlacedEntry first = byCell.get(below);
            if (first == null || !first.block().equals(second.block())) {
                for (int[] side : SIDES) {
                    Schematic.PlacedEntry near = byCell.get(new BlockCoord(
                            second.pos().x() + side[0],
                            second.pos().y(),
                            second.pos().z() + side[1]));
                    if (near != null && near.block().equals(second.block()) && !seconds.contains(near)) {
                        first = near;
                        break;
                    }
                }
            }
            if (first != null) {
                secondaries
                        .computeIfAbsent(first.pos(), key -> new ArrayList<>())
                        .add(second);
            }
        }
    }

    public static Plan plan(
            Schematic schematic, BuildRotation rotation, BlockCoord supply, SpiritReach reach, Portion portion) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё нечего планировать");
        Objects.requireNonNull(rotation, "Поворот не может быть null: от него зависят клетки и стоянки");
        Objects.requireNonNull(supply, "Сундук не может быть null: от него начинаются заходы");
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё стоянку не выбрать");
        Objects.requireNonNull(portion, "Правило порции не может быть null: без него неизвестно, что брать");
        BuildPlanner planner = new BuildPlanner(schematic, rotation, supply, reach, portion);
        planner.run();
        return new Plan(planner.steps);
    }

    private void run() {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (!round()) {
                return;
            }
        }
        throw new IllegalStateException("план стройки не сошёлся за " + MAX_ROUNDS + " шагов: зацикливание стоянок");
    }

    // Один шаг стройки, как planTick духа; false — стройка кончилась (всё стоит или дальше не продвинуться).
    private boolean round() {
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        List<Schematic.PlacedEntry> unsupplied = new ArrayList<>();
        for (Schematic.PlacedEntry entry : primaries) {
            if (!placed.contains(entry) && !blocked.contains(entry)) {
                (inventory.getOrDefault(item(entry.block()), 0) > 0 ? missing : unsupplied).add(entry);
            }
        }
        Set<Schematic.PlacedEntry> now = currentPhase(missing, unsupplied);
        missing.removeIf(entry -> !now.contains(entry));
        unsupplied.removeIf(entry -> !now.contains(entry));
        if (!unsupplied.isEmpty()) {
            missing.removeIf(entry -> !supported(entry.pos()));
        }
        List<Schematic.PlacedEntry> main = new ArrayList<>();
        List<Schematic.PlacedEntry> entrances = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            (SchematicBlocks.isEntranceBlock(entry.block()) ? entrances : main).add(entry);
        }
        boolean lastStep = main.isEmpty();
        boolean needTrip = missing.isEmpty()
                ? !unsupplied.isEmpty()
                : lastStep && unsupplied.stream().anyMatch(e -> !SchematicBlocks.isEntranceBlock(e.block()));
        if (needTrip) {
            current = null;
            if (!trip(now)) {
                stuck(unsupplied, "материала не взять");
                return false;
            }
            return true;
        }
        if (missing.isEmpty()) {
            return false;
        }
        List<Schematic.PlacedEntry> targets = lastStep ? entrances : main;
        if (placeFromHere(targets, lastStep) == 0) {
            moveOn(targets, lastStep);
        }
        return true;
    }

    // Полоса выше прежних — заново и отложенные клетки нижних (как на стройке): отказ мог быть временным.
    private Set<Schematic.PlacedEntry> currentPhase(
            List<Schematic.PlacedEntry> missing, List<Schematic.PlacedEntry> unsupplied) {
        List<Schematic.PlacedEntry> now = phase(missing, unsupplied);
        int band = now.isEmpty() || !rough(now.get(0).block())
                ? Integer.MAX_VALUE
                : BuildPhase.band(now.get(0).pos().y());
        if (band > highestBand) {
            highestBand = band;
            blocked.clear();
        }
        Set<Schematic.PlacedEntry> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(now);
        return set;
    }

    private int placeFromHere(List<Schematic.PlacedEntry> targets, boolean lastStep) {
        int refusals = 0;
        int done = 0;
        if (!lastStep || outside(feet)) {
            Map<BlockCoord, Schematic.PlacedEntry> byCell = new LinkedHashMap<>();
            targets.forEach(entry -> byCell.put(entry.pos(), entry));
            for (BlockCoord cell : ReachPlanner.workableNow(reach, feet, List.copyOf(byCell.keySet()), world(false))) {
                Schematic.PlacedEntry entry = Objects.requireNonNull(byCell.get(cell), "клетка вне целей");
                if (!keepsRetreat(cell, entry.block())) {
                    refusals++;
                    continue;
                }
                place(entry);
                done++;
            }
        }
        if (done > 0 || refusals > 0) {
            recordWork(done, refusals);
        }
        if (done > 0) {
            excluded.clear();
        }
        return done;
    }

    private void recordWork(int done, int refusals) {
        Work last = current;
        if (last != null && last.stand().equals(feet)) {
            steps.set(
                    steps.size() - 1,
                    new Work(
                            feet,
                            last.floor(),
                            last.placed() + done,
                            last.retreatRefusals() + refusals,
                            last.onRoof()));
        } else {
            steps.add(new Work(feet, floorAt(feet), done, refusals, onRoof(feet)));
        }
        current = (Work) steps.get(steps.size() - 1);
    }

    private void moveOn(List<Schematic.PlacedEntry> targets, boolean lastStep) {
        excluded.add(feet);
        List<BlockCoord> cells =
                targets.stream().map(Schematic.PlacedEntry::pos).toList();
        ReachPlanner.World world = world(lastStep);
        Optional<BlockCoord> stand = lastStep
                ? ReachPlanner.nextStandFromAfar(reach, feet, cells, world, excluded, schemeCells)
                : ReachPlanner.nextStand(reach, feet, cells, world, excluded, schemeCells);
        if (stand.isEmpty() && !lastStep) {
            stand = ReachPlanner.nextStandFromAfar(reach, feet, cells, world, excluded, schemeCells);
        }
        // Вокруг клетки только будущие ковры и пороги (устланный зал, чулан усадьбы) — стоянка и в них.
        if (stand.isEmpty()) {
            stand = ReachPlanner.nextStandFromAfar(reach, feet, cells, world, excluded, standBlockers);
        }
        current = null;
        if (stand.isEmpty()) {
            stuck(targets, "нет стоянки");
            blocked.addAll(targets);
            return;
        }
        feet = stand.get();
        retreatPath = null;
    }

    private List<Schematic.PlacedEntry> phase(List<Schematic.PlacedEntry> missing, List<Schematic.PlacedEntry> rest) {
        List<Schematic.PlacedEntry> unfinished = new ArrayList<>(missing);
        unfinished.addAll(rest);
        return BuildPhase.current(
                unfinished, entry -> entry.pos().y(), entry -> rough(entry.block()) && supported(entry.pos()));
    }

    private boolean trip(Set<Schematic.PlacedEntry> now) {
        if (trips >= MAX_TRIPS) {
            return false;
        }
        int band = Integer.MAX_VALUE;
        boolean finishing = false;
        for (Schematic.PlacedEntry entry : now) {
            finishing |= !rough(entry.block());
            band = Math.min(band, BuildPhase.band(entry.pos().y()));
        }
        Map<String, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : primaries) {
            if (!placed.contains(entry) && !blocked.contains(entry) && inPortion(entry, now, band, finishing)) {
                need.merge(item(entry.block()), 1, Integer::sum);
            }
        }
        inventory.forEach(
                (item, have) -> need.computeIfPresent(item, (key, count) -> count > have ? count - have : null));
        int free = SLOTS - slots(inventory);
        List<Taken> taken = new ArrayList<>();
        for (Map.Entry<String, Integer> wanted : need.entrySet()) {
            int have = chest.getOrDefault(wanted.getKey(), 0);
            int fits = free * STACK;
            int count = Math.min(Math.min(wanted.getValue(), have), fits);
            if (count <= 0) {
                continue;
            }
            taken.add(new Taken(wanted.getKey(), count));
            chest.merge(wanted.getKey(), -count, Integer::sum);
            inventory.merge(wanted.getKey(), count, Integer::sum);
            free = SLOTS - slots(inventory);
            if (free <= 0) {
                break;
            }
        }
        if (taken.isEmpty()) {
            return false;
        }
        trips++;
        Set<String> nowItems = new HashSet<>();
        now.forEach(entry -> nowItems.add(item(entry.block())));
        int slotsNow = 0;
        int slotsLater = 0;
        for (Map.Entry<String, Integer> held : inventory.entrySet()) {
            int stacks = (held.getValue() + STACK - 1) / STACK;
            if (nowItems.contains(held.getKey())) {
                slotsNow += stacks;
            } else {
                slotsLater += stacks;
            }
        }
        steps.add(new Trip(trips, taken, slotsNow, slotsLater));
        feet = supplyFeet();
        reachable = null;
        retreatPath = null;
        return true;
    }

    private boolean inPortion(
            Schematic.PlacedEntry entry, Set<Schematic.PlacedEntry> now, int band, boolean finishing) {
        return switch (portion) {
            case ALL -> true;
            case PHASE -> now.contains(entry);
            case FILL ->
                finishing
                        || (rough(entry.block()) && BuildPhase.band(entry.pos().y()) >= band);
        };
    }

    private boolean rough(String block) {
        return rough.computeIfAbsent(block, BuildPhase::rough);
    }

    private BlockCoord supplyFeet() {
        return supply;
    }

    private void place(Schematic.PlacedEntry entry) {
        placed.add(entry);
        grid.put(entry.pos(), entry.block());
        touch(entry.pos());
        for (Schematic.PlacedEntry second : secondaries.getOrDefault(entry.pos(), List.of())) {
            grid.put(second.pos(), second.block());
            touch(second.pos());
        }
        inventory.computeIfPresent(item(entry.block()), (key, count) -> count > 1 ? count - 1 : null);
        reachable = null;
        if (retreatPath != null && retreatPath.contains(entry.pos())) {
            retreatPath = null;
        }
    }

    // Клетки у поставленного блока получают опору: блок ставится к любой грани соседа.
    private void touch(BlockCoord cell) {
        for (int[] offset : new int[][] {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
            touched.add(new BlockCoord(cell.x() + offset[0], cell.y() + offset[1], cell.z() + offset[2]));
        }
    }

    private void stuck(List<Schematic.PlacedEntry> cells, String reason) {
        steps.add(new Stuck(cells.size(), cells.get(0).pos(), reason));
    }

    private ReachPlanner.World world(boolean outsideOnly) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return !grid.containsKey(cell) && supported(cell);
            }

            @Override
            public boolean standable(BlockCoord stand) {
                return canStand(stand) && reachable().contains(stand) && (!outsideOnly || outside(stand));
            }
        };
    }

    // Пеший обход от ног по поставленному: шаг тела как у духа (вверх на 1, спуск до 3) в габарите с кольцом.
    private Set<BlockCoord> reachable() {
        Set<BlockCoord> cached = reachable;
        if (cached != null) {
            return cached;
        }
        Set<BlockCoord> seen = new HashSet<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        seen.add(feet);
        queue.add(feet);
        while (!queue.isEmpty()) {
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1]);
                if (next != null && withinRing(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        reachable = seen;
        return seen;
    }

    // Блок вне одного пути отхода его не перекрывает — проверка правилом отхода только для клеток на этом пути.
    private boolean keepsRetreat(BlockCoord cell, String block) {
        if (SchematicWalk.passable(block) || outside(feet)) {
            return true;
        }
        Set<BlockCoord> path = retreatPath;
        if (path == null) {
            path = exitPath();
            retreatPath = path;
        }
        if (!path.isEmpty()
                && !path.contains(cell)
                && !path.contains(new BlockCoord(cell.x(), cell.y() - 1, cell.z()))) {
            return true;
        }
        retreatPath = null;
        return RetreatPath.allows(feet, Set.of(cell), view, this::outside);
    }

    // Клетки ног, головы и над головой (шаг вверх, спуск) одного кратчайшего пути наружу; пусто — пути нет или обход
    // велик (тогда — полное правило).
    private Set<BlockCoord> exitPath() {
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        parent.put(feet, feet);
        queue.add(feet);
        while (!queue.isEmpty() && parent.size() < RetreatPath.MAX_NODES) {
            BlockCoord from = queue.poll();
            if (outside(from)) {
                Set<BlockCoord> cells = new HashSet<>();
                for (BlockCoord c = from; !c.equals(feet); c = Objects.requireNonNull(parent.get(c), "обход")) {
                    for (int dy = 0; dy <= 2; dy++) {
                        cells.add(new BlockCoord(c.x(), c.y() + dy, c.z()));
                    }
                }
                for (int dy = 0; dy <= 2; dy++) {
                    cells.add(new BlockCoord(feet.x(), feet.y() + dy, feet.z()));
                }
                return cells;
            }
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1]);
                if (next != null && parent.putIfAbsent(next, from) == null) {
                    queue.add(next);
                }
            }
        }
        return Set.of();
    }

    private boolean supported(BlockCoord cell) {
        return cell.y() == 0 || touched.contains(cell);
    }

    private Cell cellAt(BlockCoord cell) {
        if (cell.y() < 0) {
            return Cell.SOLID;
        }
        String block = grid.get(cell);
        return block == null || passable(block) ? Cell.PASSABLE : Cell.SOLID;
    }

    // Шаг тела, как BodyStep, но без проверки опасных соседей: в плане стройки опасностей нет, а проверка соседей
    // втрое удорожает обход.
    private @Nullable BlockCoord step(BlockCoord from, int dx, int dz) {
        int x = from.x() + dx;
        int z = from.z() + dz;
        BlockCoord level = new BlockCoord(x, from.y(), z);
        if (canStand(level)) {
            return level;
        }
        BlockCoord up = new BlockCoord(x, from.y() + 1, z);
        if (open(from.x(), from.y() + 2, from.z()) && canStand(up)) {
            return up;
        }
        for (int drop = 1; drop <= BodyStep.MAX_DROP; drop++) {
            if (!open(x, from.y() + 1, z) || !open(x, from.y() - drop + 1, z)) {
                return null;
            }
            BlockCoord down = new BlockCoord(x, from.y() - drop, z);
            if (canStand(down)) {
                return down;
            }
        }
        return null;
    }

    private boolean canStand(BlockCoord feet) {
        return open(feet.x(), feet.y(), feet.z())
                && open(feet.x(), feet.y() + 1, feet.z())
                && !open(feet.x(), feet.y() - 1, feet.z());
    }

    private boolean open(int x, int y, int z) {
        return cellAt(new BlockCoord(x, y, z)) == Cell.PASSABLE;
    }

    private boolean passable(String block) {
        return passable.computeIfAbsent(block, SchematicWalk::passable);
    }

    private boolean outside(BlockCoord cell) {
        return cell.x() < 0 || cell.x() >= sizeX || cell.z() < 0 || cell.z() >= sizeZ;
    }

    private boolean withinRing(BlockCoord cell) {
        return cell.x() >= -RING
                && cell.x() < sizeX + RING
                && cell.z() >= -RING
                && cell.z() < sizeZ + RING
                && cell.y() >= 0
                && cell.y() <= schematic.sizeY() + 2;
    }

    // На крыше — стоянка внутри габарита, над которой по схеме ничего нет: наружная поверхность постройки.
    private boolean onRoof(BlockCoord stand) {
        if (outside(stand) || stand.y() < ROOF_MIN_HEIGHT) {
            return false;
        }
        for (int y = stand.y() + ReachPlanner.BODY_HEIGHT; y < schematic.sizeY(); y++) {
            if (schemeCells.contains(new BlockCoord(stand.x(), y, stand.z()))
                    && !SchematicWalk.passable(grid.getOrDefault(new BlockCoord(stand.x(), y, stand.z()), "air"))) {
                return false;
            }
        }
        return coveredColumns.contains(column(stand.x(), stand.z()));
    }

    private String floorAt(BlockCoord stand) {
        if (stand.y() == 0) {
            return "земле";
        }
        return SchematicBlocks.path(grid.getOrDefault(new BlockCoord(stand.x(), stand.y() - 1, stand.z()), "?"))
                + " (высота " + stand.y() + ")";
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int slots(Map<String, Integer> items) {
        int slots = 0;
        for (int count : items.values()) {
            slots += (count + STACK - 1) / STACK;
        }
        return slots;
    }

    static String item(String block) {
        String path = SchematicBlocks.path(block);
        String namespace = block.contains(":") ? block.substring(0, block.indexOf(':') + 1) : "minecraft:";
        return namespace
                + path.replace("wall_torch", "torch")
                        .replace("_wall_sign", "_sign")
                        .replace("_wall_banner", "_banner");
    }
}
