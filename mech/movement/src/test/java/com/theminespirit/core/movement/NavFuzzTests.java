package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.theminespirit.core.navigation.NavPillarEscape;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RoutePlanner;
import com.theminespirit.core.route.RouteRules;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.core.route.RouteWorld;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class NavFuzzTests {
    private static final int WORLDS = 2_000;
    private static final int MAX_VISITS = 20_000;
    private static final RouteRules RULES = new RouteRules(1.0, 1.0, 1.0, 0.0, 1.0, 0.0, 1.0, 3, 12, 4, 1200, 1.0, 1);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Test
    void navFuzz_GeneratedWorlds_EveryPlanExecutableByBody() {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < WORLDS; seed++) {
            FuzzWorld world = FuzzWorld.generate(seed);
            checkSurfaceEscape(seed, world, failures);
            checkPitExit(seed, world, failures);
            checkRoutePlanner(seed, world, failures);
            checkSelfEscape(seed, world, failures);
            checkSealedSpace(seed, world, failures);
            checkPitWalkOut(seed, world, failures);
            checkNavPillar(seed, world, failures);
        }
        if (!failures.isEmpty()) {
            fail(report(failures));
        }
    }

    @Test
    void bodyMotion_StepUpWithoutHeadroom_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 1, 5);
        world.set(5, 3, 5, FuzzWorld.Cell.STONE);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 2, 5)),
                "Шаг на ступень под притолокой неисполним: тело ростом 1.4 поднимает голову в клетку над головой, "
                        + "а она занята. Планировщик, разрешивший такой подъём, оставит духа прыгать на месте");
    }

    @Test
    void bodyMotion_DropUnderLintel_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 4, 5);
        world.set(5, 3, 5, FuzzWorld.Cell.DIRT);
        world.set(6, 4, 5, FuzzWorld.Cell.STONE);
        world.set(6, 3, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.AIR);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "Спуск под притолоку неисполним: в целевой колонке занята клетка на исходной высоте, и голова "
                        + "духа не проходит. Путь «к небу» вниз под притолоку — та же ошибка");
    }

    @Test
    void bodyMotion_DropFourBlocksWithoutWater_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 6, 5);
        world.set(5, 5, 5, FuzzWorld.Cell.AIR);
        world.set(5, 4, 5, FuzzWorld.Cell.AIR);
        world.set(5, 3, 5, FuzzWorld.Cell.AIR);
        world.set(5, 2, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "Падение глубже трёх блоков на сушу калечит духа: тело не считает такой спуск безопасным");
    }

    @Test
    void bodyMotion_DropFourBlocksIntoWater_Allowed() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 6, 5);
        for (int y = 1; y <= 5; y++) {
            world.set(5, y, 5, FuzzWorld.Cell.AIR);
            world.set(6, y, 5, FuzzWorld.Cell.WATER);
        }

        assertTrue(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "В воду падать можно глубже трёх блоков: вода гасит удар, и дух выбирается на берег");
    }

    @Test
    void bodyMotion_OneHighSlit_NotFit() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord slit = new BlockCoord(6, 1, 5);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.STONE);

        assertFalse(
                BodyMotion.fits(world, slit),
                "Щель высотой 1 не проходит для тела ростом 1.4: планировщик, считающий её выходом, снова "
                        + "оставит духа замурованным");
    }

    @Test
    void bodyMotion_Ladder_ClimbsUpWithoutSupport() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 1, 5);
        world.set(5, 1, 5, FuzzWorld.Cell.LADDER);
        world.set(5, 2, 5, FuzzWorld.Cell.LADDER);
        world.set(5, 3, 5, FuzzWorld.Cell.AIR);

        assertTrue(
                BodyMotion.canMove(world, feet, new BlockCoord(5, 2, 5)),
                "По лестнице дух поднимается без опоры под ногами: лестница — проходимая клетка");
    }

    @Test
    void navPillarEscape_LandingUnderLintel_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        for (int y = 1; y <= 3; y++) {
            world.set(5, y, 5, FuzzWorld.Cell.DIRT);
        }
        for (int[] side : SIDES) {
            world.set(5 + side[0], 4, 5 + side[1], FuzzWorld.Cell.STONE);
        }
        Set<BlockCoord> plateau = NavPillarEscape.plateau(world.navWorld(), 5, 4, 5);
        assertEquals(1, plateau.size(), "Предусловие: дух стоит на одиночном столбе, а не на ровном полу");

        assertTrue(
                NavPillarEscape.landingFrom(world.navWorld(), plateau, 6).isEmpty(),
                "Спуск под притолоку неисполним: соседняя колонка занята на высоте ног, и тело туда не пройдёт. "
                        + "Планировщик, назвавший такой спуск, отправляет духа вниз сквозь блок");
    }

    @Test
    void navPillarEscape_LiveFixtures_LandingReachableByBody() throws IOException {
        LiveWorld pillar = LiveWorld.load("/live/observe25-pillar-1108.live");
        Set<BlockCoord> single = NavPillarEscape.plateau(pillar, -155, 72, -490);
        assertEquals(1, single.size(), "Живая клетка spirit-1 (-155,72,-490) — одиночная опора");
        int ground = NavPillarEscape.landingFrom(pillar, single, 12).orElse(-1);
        assertEquals(68, ground, "Под одиночной опорой spirit-1 площадка y=68");
        assertTrue(
                canDrop(pillar, single, ground, 12),
                "Тело духа обязано пройти с живой опоры (-155,72,-490) на площадку y=68: иначе модель проходимости "
                        + "расходится с observe25");

        LiveWorld tower = LiveWorld.load("/live/observe26-tower-1108.live");
        Set<BlockCoord> two = NavPillarEscape.plateau(tower, -142, 79, -504);
        assertEquals(2, two.size(), "Опора spirit-4 шириной 2×2 — всё ещё узкая опора");
        int yard = NavPillarEscape.landingFrom(tower, two, 12).orElse(-1);
        assertEquals(72, yard, "Предел спуска с башни spirit-4 — земля y=72");
        assertTrue(
                canDrop(tower, two, yard, 12),
                "Тело духа обязано сойти с башни spirit-4 на землю y=72: иначе модель не подтверждает observe26");
    }

    private static boolean canDrop(RouteWorld world, Set<BlockCoord> plateau, int landingY, int maxFall) {
        for (BlockCoord from : plateau) {
            for (int[] side : SIDES) {
                BlockCoord to = new BlockCoord(from.x() + side[0], landingY, from.z() + side[1]);
                if (BodyMotion.canMove(world, from, to, maxFall)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkSurfaceEscape(long seed, FuzzWorld world, List<String> failures) {
        Optional<List<BlockCoord>> found = SurfaceEscape.path(world.start(), world, world::sky);
        if (found.isPresent()) {
            List<BlockCoord> path = found.orElseThrow();
            if (!world.sky(path.get(path.size() - 1))) {
                failures.add(seed + " SurfaceEscape: путь кончается не под небом — " + path.get(path.size() - 1));
            }
            for (int index = 1; index < path.size(); index++) {
                BlockCoord from = path.get(index - 1);
                BlockCoord to = path.get(index);
                if (!BodyMotion.canMove(world, from, to)) {
                    failures.add(seed + " SurfaceEscape: шаг " + from + " → " + to + " неисполним телом");
                }
            }
        }
        if (!found.isPresent() && bodyWalkReachesSky(world, world.start())) {
            failures.add(seed + " SurfaceEscape: BodyMotion находит пеший выход к небу, а планировщик — нет");
        }
    }

    private static void checkPitExit(long seed, FuzzWorld world, List<String> failures) {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(world.start(), world.pitWorld(), new PitExitPlanner.Supplies(8, true), Set.of());
        for (BlockCoord cell : plan.dig()) {
            if (world.protectedAt(cell)) {
                failures.add(seed + " PitExitPlanner: план ломает защищённый блок " + cell);
            }
        }
        for (BlockCoord cell : plan.place()) {
            if (world.protectedAt(cell)) {
                failures.add(seed + " PitExitPlanner: план ставит блок в защищённую клетку " + cell);
            }
        }
        if (!plan.possible()) {
            if (world.expectPitEscape() && !world.physicallyImpossible()) {
                failures.add(seed + " PitExitPlanner: с киркой и блоками выхода нет, хотя он физически возможен");
            }
            return;
        }
        switch (plan.method()) {
            case WALK -> {
                if (!walkReaches(world, world.start(), plan.exit(), BodyMotion.MAX_FALL)) {
                    failures.add(seed + " PitExitPlanner WALK: выход " + plan.exit() + " недостижим телом");
                }
            }
            case PILLAR -> {
                if (!pillarExecutable(world, world.start(), plan.rise(), plan.exit())) {
                    failures.add(seed + " PitExitPlanner PILLAR: столб на " + plan.rise() + " неисполним телом");
                }
            }
            case STAIRS -> {
                Overlay overlay = new Overlay(world);
                for (BlockCoord cell : plan.dig()) {
                    if (world.cellAt(cell) == FuzzWorld.Cell.CHEST
                            || world.cellAt(cell) == FuzzWorld.Cell.DOOR
                            || world.cellAt(cell) == FuzzWorld.Cell.TRAPDOOR
                            || world.cellAt(cell) == FuzzWorld.Cell.BED) {
                        failures.add(seed + " PitExitPlanner STAIRS: ломает защищённый блок " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.AIR);
                }
                for (BlockCoord cell : plan.place()) {
                    overlay.set(cell, FuzzWorld.Cell.DIRT);
                }
                if (!walkReaches(overlay, world.start(), plan.exit(), BodyMotion.MAX_FALL)) {
                    failures.add(seed + " PitExitPlanner STAIRS: после ломки и постановки выход " + plan.exit()
                            + " недостижим телом; dig=" + plan.dig() + " place=" + plan.place());
                }
            }
            case IMPOSSIBLE -> {}
        }
    }

    private static void checkRoutePlanner(long seed, FuzzWorld world, List<String> failures) {
        Optional<BlockCoord> target = world.surfaceTarget();
        if (target.isEmpty()) {
            return;
        }
        Overlay overlay = new Overlay(world);
        Route route = RoutePlanner.plan(overlay, world.start(), new GoalBlock(target.orElseThrow()), RULES, 16);
        if (!route.usable()) {
            return;
        }
        BlockCoord cursor = world.start();
        for (RouteStep step : route.steps()) {
            BlockCoord cell = step.cell();
            switch (step.action()) {
                case BREAK -> {
                    if (world.protectedAt(cell)) {
                        failures.add(seed + " RoutePlanner: ломает защищённый блок " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.AIR);
                }
                case PLACE -> {
                    if (!BodyMotion.passable(overlay, cell)) {
                        failures.add(seed + " RoutePlanner: ставит блок в занятую клетку " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.DIRT);
                }
                case PILLAR -> {
                    if (!cell.equals(cursor)) {
                        failures.add(seed + " RoutePlanner: столб ставится в " + cell + ", а дух в " + cursor);
                        overlay.set(cell, FuzzWorld.Cell.DIRT);
                        cursor = BodyMotion.above(cursor);
                        continue;
                    }
                    overlay.set(cell, FuzzWorld.Cell.DIRT);
                    cursor = BodyMotion.above(cursor);
                    if (!BodyMotion.fits(overlay, cursor)) {
                        failures.add(seed + " RoutePlanner: после столба тело не помещается в " + cursor);
                    }
                }
                case WALK, SWIM -> {
                    if (!BodyMotion.canMove(overlay, cursor, cell)) {
                        failures.add(seed + " RoutePlanner: шаг " + cursor + " → " + cell + " неисполним телом");
                    }
                    cursor = cell;
                }
            }
        }
    }

    private static void checkSelfEscape(long seed, FuzzWorld world, List<String> failures) {
        BlockCoord goal = world.surfaceTarget().orElse(BodyMotion.above(world.start()));
        Optional<SelfEscape.Plan> plan =
                SelfEscape.plan(world.start(), goal, world.grid(world.start()), true, world.stepBlocks());
        if (plan.isEmpty()) {
            return;
        }
        SelfEscape.Plan escape = plan.orElseThrow();
        switch (escape.method()) {
            case STEP -> {
                if (!pillarExecutable(world, world.start(), escape.rise(), escape.target())) {
                    failures.add(seed + " SelfEscape STEP: столб на " + escape.rise() + " неисполним телом");
                }
            }
            case DIG -> {
                Overlay overlay = new Overlay(world);
                for (BlockCoord cell : escape.blocks()) {
                    if (world.protectedAt(cell)) {
                        failures.add(seed + " SelfEscape DIG: ломает защищённый блок " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.AIR);
                }
                if (escape.rise() == 0 && !BodyMotion.canMove(overlay, world.start(), escape.target())) {
                    failures.add(seed + " SelfEscape DIG: проход " + world.start() + " → " + escape.target()
                            + " после ломки неисполним телом");
                }
                if (!BodyMotion.fits(overlay, escape.target())) {
                    failures.add(seed + " SelfEscape DIG: цель " + escape.target() + " не помещает тело");
                }
            }
            case DESCEND, GOTO -> {
                if (!BodyMotion.fits(world, escape.target())) {
                    failures.add(seed + " SelfEscape " + escape.method() + ": цель " + escape.target()
                            + " не помещает тело");
                }
            }
        }
    }

    private static void checkSealedSpace(long seed, FuzzWorld world, List<String> failures) {
        if (SealedSpace.sealed(world.start(), world.sealedView()) && bodyWalkReachesSky(world, world.start())) {
            failures.add(seed + " SealedSpace: дух назван замурованным, хотя BodyMotion находит пеший выход к небу");
        }
    }

    private static void checkPitWalkOut(long seed, FuzzWorld world, List<String> failures) {
        boolean exists = PitWalkOut.exists(
                world.pitWalkWorld(),
                world.start().x(),
                world.start().z(),
                world.start().y());
        boolean body = bodyWalkReachesSky(world, world.start());
        if (exists && !body) {
            failures.add(seed + " PitWalkOut: пеший выход назван, а тело до неба не доходит");
        }
        if (body && !exists && world.expectPitWalkOut()) {
            failures.add(seed + " PitWalkOut: тело доходит до неба, а пешего выхода планировщик не видит");
        }
    }

    private static void checkNavPillar(long seed, FuzzWorld world, List<String> failures) {
        NavWorld nav = world.navWorld();
        Set<BlockCoord> plateau = NavPillarEscape.plateau(
                nav, world.start().x(), world.start().y(), world.start().z());
        if (plateau.isEmpty()) {
            return;
        }
        OptionalInt landing = NavPillarEscape.landingFrom(nav, plateau, 12);
        if (landing.isPresent() && !canDrop(world, plateau, landing.getAsInt(), 12)) {
            failures.add(seed + " NavPillarEscape: приземление y=" + landing.getAsInt() + " с узкой опоры " + plateau
                    + " неисполнимо телом");
        }
    }

    private static boolean pillarExecutable(FuzzWorld world, BlockCoord feet, int rise, BlockCoord exit) {
        Overlay overlay = new Overlay(world);
        BlockCoord at = feet;
        if (!BodyMotion.fits(overlay, at)) {
            return false;
        }
        for (int step = 0; step < rise; step++) {
            overlay.set(at, FuzzWorld.Cell.DIRT);
            at = BodyMotion.above(at);
            if (!BodyMotion.fits(overlay, at)) {
                return false;
            }
        }
        return BodyMotion.canMove(overlay, at, exit);
    }

    private static boolean walkReaches(FuzzWorld world, BlockCoord start, BlockCoord target, int maxFall) {
        return walkRoute(world, start, target::equals, maxFall).isPresent();
    }

    private static boolean walkReaches(Overlay world, BlockCoord start, BlockCoord target, int maxFall) {
        return walkRoute(world, start, target::equals, maxFall).isPresent();
    }

    private static boolean bodyWalkReachesSky(FuzzWorld world, BlockCoord start) {
        return walkRoute(world, start, world::sky, BodyMotion.MAX_FALL).isPresent();
    }

    private static Optional<List<BlockCoord>> walkRoute(
            RouteWorld world, BlockCoord start, Predicate<BlockCoord> target, int maxFall) {
        if (!BodyMotion.fits(world, start)) {
            return Optional.empty();
        }
        if (target.test(start) && BodyMotion.standable(world, start)) {
            return Optional.of(List.of(start));
        }
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int visits = 0;
        while (!queue.isEmpty() && visits++ < MAX_VISITS) {
            BlockCoord cell = queue.poll();
            for (BlockCoord next : BodyMotion.steps(world, cell, maxFall)) {
                if (!seen.add(next)) {
                    continue;
                }
                parent.put(next, cell);
                if (target.test(next)) {
                    List<BlockCoord> path = new ArrayList<>();
                    for (BlockCoord at = next; at != null; at = parent.get(at)) {
                        path.add(at);
                    }
                    Collections.reverse(path);
                    return Optional.of(path);
                }
                queue.add(next);
            }
        }
        return Optional.empty();
    }

    private static String report(List<String> failures) {
        StringBuilder message =
                new StringBuilder("Навигационный фаззинг: миров " + WORLDS + ", расхождений " + failures.size()
                        + ". План, который тело не исполняет, — сломанный план: чиню планировщик, а не тест.");
        int shown = 0;
        Set<Long> reportedSeeds = new HashSet<>();
        for (String failure : failures) {
            long seed = Long.parseLong(failure.substring(0, failure.indexOf(' ')));
            if (!reportedSeeds.add(seed)) {
                continue;
            }
            if (shown++ < 3) {
                message.append("\n\n")
                        .append(failure)
                        .append('\n')
                        .append(FuzzWorld.generate(seed).render());
            } else {
                message.append("\nсид ").append(seed).append(": ").append(failure);
            }
        }
        return message.toString();
    }

    private static final class Overlay implements RouteWorld {
        private final FuzzWorld base;
        private final Map<BlockCoord, FuzzWorld.Cell> changes = new HashMap<>();

        private Overlay(FuzzWorld base) {
            this.base = base;
        }

        void set(BlockCoord cell, FuzzWorld.Cell value) {
            changes.put(cell, value);
        }

        @Override
        public int cell(int x, int y, int z) {
            FuzzWorld.Cell change = changes.get(new BlockCoord(x, y, z));
            return (change == null ? base.cellAt(x, y, z) : change).route();
        }
    }

    static final class FuzzWorld implements RouteWorld {
        static final int SIZE = 10;
        static final int Y_MIN = -7;
        static final int Y_MAX = 9;
        static final int Y_COUNT = Y_MAX - Y_MIN + 1;

        enum Cell {
            AIR("."),
            WATER("~"),
            LAVA("!"),
            DIRT("d"),
            STONE("s"),
            LADDER("h"),
            CHEST("C"),
            DOOR("D"),
            TRAPDOOR("T"),
            BED("B");

            private final String mark;

            Cell(String mark) {
                this.mark = mark;
            }

            boolean blocksSky() {
                return this != AIR && this != LADDER;
            }

            boolean protectedBlock() {
                return this == CHEST || this == DOOR || this == TRAPDOOR || this == BED;
            }

            int route() {
                return switch (this) {
                    case AIR -> RouteCell.AIR;
                    case WATER -> RouteCell.WATER;
                    case LAVA -> RouteCell.LAVA;
                    case LADDER -> RouteCell.CLIMB;
                    case DIRT -> RouteCell.solid(8);
                    case STONE -> RouteCell.solid(20);
                    case CHEST, DOOR, TRAPDOOR, BED -> RouteCell.BLOCKED;
                };
            }

            PitExitPlanner.Kind pit() {
                return switch (this) {
                    case AIR, LADDER -> PitExitPlanner.Kind.AIR;
                    case WATER -> PitExitPlanner.Kind.WATER;
                    case LAVA -> PitExitPlanner.Kind.LAVA;
                    case DIRT -> PitExitPlanner.Kind.SOFT;
                    case STONE -> PitExitPlanner.Kind.HARD;
                    case CHEST, DOOR, TRAPDOOR, BED -> PitExitPlanner.Kind.BEDROCK;
                };
            }
        }

        private final Cell[] cells = new Cell[SIZE * SIZE * Y_COUNT];
        private final long seed;
        private final String type;
        private BlockCoord start;
        private boolean impossible;

        private FuzzWorld(long seed, String type, BlockCoord start, boolean impossible) {
            this.seed = seed;
            this.type = type;
            this.start = start;
            this.impossible = impossible;
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int y = Y_MIN; y <= Y_MAX; y++) {
                        set(x, y, z, y <= 0 ? Cell.DIRT : Cell.AIR);
                    }
                }
            }
        }

        static FuzzWorld flat() {
            return new FuzzWorld(0, "flat", new BlockCoord(5, 1, 5), false);
        }

        static FuzzWorld generate(long seed) {
            int type = Math.floorMod(seed, 13);
            int variant = Math.floorMod(seed / 13, 8);
            int depth = 1 + Math.floorMod(seed / 13, 6);
            FuzzWorld world = new FuzzWorld(seed, name(type), new BlockCoord(5, 1, 5), false);
            switch (type) {
                case 0 -> {
                    world.pit(5, 5, depth);
                    world.start = new BlockCoord(5, 1 - depth, 5);
                }
                case 1 -> {
                    int chestDepth = Math.max(3, depth);
                    world.pit(5, 5, chestDepth);
                    world.set(5, 1, 5, Cell.CHEST);
                    world.start = new BlockCoord(5, 1 - chestDepth, 5);
                }
                case 2 -> {
                    world.room(3 + variant % 3, true, variant % 3 == 0);
                    world.start = new BlockCoord(5, 1, 5);
                }
                case 3 -> {
                    world.room(2 + variant % 3, false, variant % 2 == 0);
                    world.start = new BlockCoord(5, 1, 5);
                }
                case 4 -> {
                    world.lintelRoom(variant);
                    world.start = new BlockCoord(5, 1, 5);
                }
                case 5 -> {
                    world.slit(variant);
                    world.start = new BlockCoord(5, 1, 2);
                }
                case 6 -> {
                    world.caveMouth(variant);
                    world.start = new BlockCoord(4, -1, 5);
                }
                case 7 -> {
                    int height = 1 + variant % 4;
                    world.platform(4, 4, 5, 5, height);
                    world.start = new BlockCoord(4, height + 1, 4);
                }
                case 8 -> {
                    int steps = 3 + variant % 4;
                    world.stairs(steps);
                    world.start = new BlockCoord(3, 2 - steps, 5);
                }
                case 9 -> {
                    int waterDepth = 3 + variant % 3;
                    world.waterPit(5, 5, waterDepth);
                    world.start = new BlockCoord(5, 1 - waterDepth, 5);
                }
                case 10 -> {
                    world.ledge(variant);
                    world.start = new BlockCoord(5, 3, 5);
                }
                case 11 -> {
                    int height = 2 + variant % 4;
                    world.platform(5, 5, 5, 5, height);
                    world.start = new BlockCoord(5, height + 1, 5);
                }
                default -> {
                    if (variant == 7) {
                        world.cage();
                        world.start = new BlockCoord(5, 2, 5);
                        world.impossible = true;
                    } else {
                        int pit = 4 + variant % 3;
                        world.pit(5, 5, pit);
                        world.set(5, 1, 5, Cell.CHEST);
                        world.set(5, 1 - pit, 5, Cell.WATER);
                        world.set(4, 3, 4, Cell.STONE);
                        world.set(4, 4, 4, Cell.STONE);
                        world.start = new BlockCoord(5, 1 - pit, 5);
                    }
                }
            }
            return world;
        }

        private static String name(int type) {
            return switch (type) {
                case 0 -> "pit";
                case 1 -> "pit-under-chest";
                case 2 -> "cell-with-roof";
                case 3 -> "cell-without-roof";
                case 4 -> "lintel";
                case 5 -> "slit-height-one";
                case 6 -> "cave-mouth";
                case 7 -> "pillar-2x2";
                case 8 -> "stairs";
                case 9 -> "water";
                case 10 -> "ledge";
                case 11 -> "narrow-support";
                default -> "mixed";
            };
        }

        BlockCoord start() {
            return start;
        }

        boolean physicallyImpossible() {
            return impossible;
        }

        boolean expectPitEscape() {
            return switch (type) {
                case "pit",
                        "pit-under-chest",
                        "cell-with-roof",
                        "cell-without-roof",
                        "lintel",
                        "slit-height-one",
                        "cave-mouth",
                        "stairs",
                        "water",
                        "mixed" -> true;
                default -> false;
            };
        }

        boolean expectPitWalkOut() {
            return expectPitEscape() && !"water".equals(type);
        }

        int stepBlocks() {
            return 8;
        }

        Cell cellAt(BlockCoord cell) {
            return cellAt(cell.x(), cell.y(), cell.z());
        }

        Cell cellAt(int x, int y, int z) {
            if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < Y_MIN) {
                return Cell.DIRT;
            }
            if (y > Y_MAX) {
                return Cell.AIR;
            }
            return cells[index(x, y, z)];
        }

        void set(int x, int y, int z, Cell value) {
            if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < Y_MIN || y > Y_MAX) {
                return;
            }
            cells[index(x, y, z)] = value;
        }

        private static int index(int x, int y, int z) {
            return (x * SIZE + z) * Y_COUNT + (y - Y_MIN);
        }

        @Override
        public int cell(int x, int y, int z) {
            return cellAt(x, y, z).route();
        }

        boolean protectedAt(BlockCoord cell) {
            return cellAt(cell).protectedBlock();
        }

        boolean sky(BlockCoord cell) {
            if (cell.y() < 1 || cell.y() > Y_MAX || !openAbove(cell.x(), cell.y(), cell.z())) {
                return false;
            }
            for (int[] side : SIDES) {
                if (height(cell.x() + side[0], cell.z() + side[1]) <= cell.y()) {
                    return true;
                }
            }
            return false;
        }

        private boolean openAbove(int x, int y, int z) {
            for (int level = y; level <= Y_MAX; level++) {
                if (cellAt(x, level, z).blocksSky()) {
                    return false;
                }
            }
            return true;
        }

        private int height(int x, int z) {
            for (int y = Y_MAX; y >= Y_MIN; y--) {
                if (cellAt(x, y, z).blocksSky()) {
                    return y + 1;
                }
            }
            return Y_MIN;
        }

        Optional<BlockCoord> surfaceTarget() {
            for (int x = 1; x < SIZE - 1; x++) {
                for (int z = 1; z < SIZE - 1; z++) {
                    BlockCoord cell = new BlockCoord(x, 1, z);
                    if (sky(cell) && BodyMotion.standable(this, cell)) {
                        return Optional.of(cell);
                    }
                }
            }
            return Optional.empty();
        }

        PitExitPlanner.World pitWorld() {
            return cell -> cellAt(cell).pit();
        }

        StuckSituation.Grid grid(BlockCoord feet) {
            return new StuckSituation.Grid() {
                @Override
                public boolean passable(BlockCoord cell) {
                    Cell value = cellAt(cell);
                    return value == Cell.AIR || value == Cell.LADDER;
                }

                @Override
                public boolean swimmable(BlockCoord cell) {
                    return cellAt(cell) == Cell.WATER;
                }

                @Override
                public boolean breakable(BlockCoord cell) {
                    Cell value = cellAt(cell);
                    return value == Cell.DIRT || value == Cell.STONE;
                }

                @Override
                public boolean clearColumn(BlockCoord feet, int rise) {
                    for (int dy = 0; dy <= rise + 2; dy++) {
                        Cell value = cellAt(feet.x(), feet.y() + dy, feet.z());
                        if (value != Cell.AIR && value != Cell.WATER && value != Cell.LADDER) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public int floorY(int x, int z) {
                    for (int y = Y_MIN + 2; y <= Y_MAX; y++) {
                        BlockCoord cell = new BlockCoord(x, y, z);
                        if (BodyMotion.standable(FuzzWorld.this, cell)) {
                            return y;
                        }
                    }
                    return feet.y();
                }
            };
        }

        SealedSpace.View sealedView() {
            return new SealedSpace.View() {
                @Override
                public boolean passable(BlockCoord cell) {
                    Cell value = cellAt(cell);
                    return value == Cell.AIR || value == Cell.LADDER;
                }

                @Override
                public boolean swimmable(BlockCoord cell) {
                    return cellAt(cell) == Cell.WATER;
                }

                @Override
                public boolean sky(BlockCoord cell) {
                    return FuzzWorld.this.sky(cell);
                }
            };
        }

        NavWorld navWorld() {
            return new NavWorld() {
                @Override
                public boolean available(int x, int z) {
                    return true;
                }

                @Override
                public boolean passable(int x, int y, int z) {
                    return BodyMotion.passable(FuzzWorld.this, new BlockCoord(x, y, z));
                }

                @Override
                public boolean walkable(int x, int y, int z) {
                    return BodyMotion.solid(FuzzWorld.this, new BlockCoord(x, y, z));
                }

                @Override
                public boolean water(int x, int y, int z) {
                    return cellAt(x, y, z) == Cell.WATER;
                }

                @Override
                public boolean climbable(int x, int y, int z) {
                    return cellAt(x, y, z) == Cell.LADDER;
                }

                @Override
                public double breakTicks(int x, int y, int z) {
                    Cell value = cellAt(x, y, z);
                    return value == Cell.CHEST || value == Cell.DOOR || value == Cell.TRAPDOOR || value == Cell.BED
                            ? Double.POSITIVE_INFINITY
                            : 10;
                }

                @Override
                public int minY() {
                    return Y_MIN;
                }

                @Override
                public int maxY() {
                    return Y_MAX;
                }
            };
        }

        PitWalkOut.World pitWalkWorld() {
            return new PitWalkOut.World() {
                @Override
                public OptionalInt groundY(int x, int z, int from) {
                    if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) {
                        return OptionalInt.of(1);
                    }
                    for (int y = Math.max(from, Y_MIN + 2); y <= Y_MAX; y++) {
                        BlockCoord cell = new BlockCoord(x, y, z);
                        if (BodyMotion.standable(FuzzWorld.this, cell)) {
                            return OptionalInt.of(y);
                        }
                    }
                    return OptionalInt.empty();
                }

                @Override
                public boolean clear(int x, int y, int z) {
                    Cell value = cellAt(x, y, z);
                    return value == Cell.AIR || value == Cell.WATER;
                }

                @Override
                public int surfaceY(int x, int z) {
                    if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) {
                        return 1;
                    }
                    for (int y = Y_MAX; y >= Y_MIN; y--) {
                        if (cellAt(x, y, z).blocksSky()) {
                            return y + 1;
                        }
                    }
                    return Y_MIN;
                }
            };
        }

        String render() {
            StringBuilder map = new StringBuilder();
            map.append("мир seed=")
                    .append(seed)
                    .append(" тип=")
                    .append(type)
                    .append(" старт=")
                    .append(start);
            for (int y = Y_MAX; y >= Y_MIN; y--) {
                map.append("\nслой y=").append(y).append('\n');
                for (int z = 0; z < SIZE; z++) {
                    for (int x = 0; x < SIZE; x++) {
                        map.append(cellAt(x, y, z).mark);
                    }
                    map.append('\n');
                }
            }
            return map.toString();
        }

        private void pit(int x, int z, int depth) {
            for (int y = 0; y >= 1 - depth; y--) {
                set(x, y, z, Cell.AIR);
            }
        }

        private void room(int height, boolean roof, boolean withDoor) {
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 6; z++) {
                    for (int y = 1; y <= height; y++) {
                        boolean wall = x == 4 || x == 6 || z == 4 || z == 6;
                        set(x, y, z, wall ? Cell.DIRT : Cell.AIR);
                    }
                }
            }
            if (roof) {
                for (int x = 3; x <= 7; x++) {
                    for (int z = 3; z <= 7; z++) {
                        set(x, height + 1, z, Cell.DIRT);
                    }
                }
            }
            if (withDoor) {
                set(5, 1, 4, Cell.DOOR);
                set(5, 2, 4, Cell.DOOR);
            }
        }

        private void lintelRoom(int variant) {
            room(3, true, false);
            set(5, 1, 4, Cell.AIR);
            set(5, 2, 4, Cell.DIRT);
            set(5, 3, 4, Cell.DIRT);
            if (variant % 2 == 0) {
                set(4, 1, 5, Cell.TRAPDOOR);
            } else {
                set(4, 1, 5, Cell.BED);
            }
        }

        private void slit(int variant) {
            for (int x = 2; x <= 8; x++) {
                for (int z = 1; z <= 4; z++) {
                    for (int y = 1; y <= 2; y++) {
                        boolean wall = x == 2 || x == 8 || z == 1 || z == 4;
                        set(x, y, z, wall ? Cell.STONE : Cell.AIR);
                    }
                }
            }
            for (int x = 1; x <= 9; x++) {
                for (int z = 0; z <= 5; z++) {
                    set(x, 3, z, Cell.DIRT);
                }
            }
            set(4, 1, 4, Cell.AIR);
            set(5, 1, 4, Cell.AIR);
            if (variant % 2 == 0) {
                set(4, 2, 4, Cell.STONE);
                set(5, 2, 4, Cell.STONE);
            }
        }

        private void caveMouth(int variant) {
            for (int x = 3; x <= 7; x++) {
                for (int z = 3; z <= 7; z++) {
                    for (int y = -1; y <= 1; y++) {
                        set(x, y, z, Cell.AIR);
                    }
                }
            }
            for (int x = 3; x <= 6; x++) {
                for (int z = 3; z <= 7; z++) {
                    set(x, 2, z, Cell.DIRT);
                }
            }
            set(7, 1, 2, Cell.AIR);
            set(7, 1, 3, Cell.AIR);
            set(7, 1, 4, Cell.AIR);
            set(7, 1, 5, Cell.AIR);
            set(7, 1, 6, Cell.AIR);
            set(7, 1, 7, Cell.AIR);
            set(7, 2, 2, Cell.AIR);
            set(7, 2, 3, Cell.AIR);
            set(7, 2, 4, Cell.AIR);
            set(7, 2, 5, Cell.AIR);
            set(7, 2, 6, Cell.AIR);
            set(7, 2, 7, Cell.AIR);
            if (variant % 2 == 0) {
                set(4, -1, 5, Cell.WATER);
            }
        }

        private void platform(int x0, int z0, int x1, int z1, int height) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = 1; y <= height; y++) {
                        set(x, y, z, Cell.DIRT);
                    }
                }
            }
        }

        private void stairs(int steps) {
            for (int step = 0; step < steps; step++) {
                int x = 3 + step;
                int floor = 1 - steps + step;
                for (int y = Y_MIN; y <= floor; y++) {
                    set(x, y, 5, Cell.DIRT);
                }
                for (int y = floor + 1; y <= Y_MAX; y++) {
                    set(x, y, 5, Cell.AIR);
                }
            }
            for (int x = 1; x < 3; x++) {
                for (int z = 4; z <= 6; z++) {
                    set(x, 1, z, Cell.AIR);
                    set(x, 2, z, Cell.AIR);
                }
            }
        }

        private void waterPit(int x, int z, int depth) {
            pit(x, z, depth);
            for (int y = 1 - depth; y <= 1; y++) {
                set(x, y, z, Cell.WATER);
            }
            set(x, -depth, z, Cell.DIRT);
        }

        private void ledge(int variant) {
            for (int x = 3; x <= 7; x++) {
                for (int z = 5; z <= 5; z++) {
                    set(x, 1, z, Cell.DIRT);
                    set(x, 2, z, Cell.DIRT);
                }
            }
            if (variant % 2 == 0) {
                set(5, 5, 5, Cell.DIRT);
                set(4, 5, 5, Cell.DIRT);
                set(6, 5, 5, Cell.DIRT);
            }
        }

        private void cage() {
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 6; z++) {
                    for (int y = 1; y <= 4; y++) {
                        boolean shell = x == 4 || x == 6 || z == 4 || z == 6 || y == 1 || y == 4;
                        set(x, y, z, shell ? Cell.CHEST : Cell.AIR);
                    }
                }
            }
        }
    }

    static final class LiveWorld implements RouteWorld, NavWorld {
        private final Map<BlockCoord, String> cells = new HashMap<>();

        static LiveWorld load(String resource) throws IOException {
            LiveWorld world = new LiveWorld();
            try (InputStream stream = NavFuzzTests.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new IllegalStateException("Нет живой фикстуры: " + resource);
                }
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                            continue;
                        }
                        StringTokenizer parts = new StringTokenizer(line);
                        if (parts.countTokens() != 4) {
                            throw new IllegalArgumentException("Строка живой фикстуры не из четырёх полей: " + line);
                        }
                        world.cells.put(
                                new BlockCoord(
                                        Integer.parseInt(parts.nextToken()),
                                        Integer.parseInt(parts.nextToken()),
                                        Integer.parseInt(parts.nextToken())),
                                parts.nextToken());
                    }
                }
            }
            return world;
        }

        private String kind(int x, int y, int z) {
            return cells.getOrDefault(new BlockCoord(x, y, z), "HARD");
        }

        @Override
        public int cell(int x, int y, int z) {
            return switch (kind(x, y, z)) {
                case "AIR" -> RouteCell.AIR;
                case "WATER" -> RouteCell.WATER;
                case "LAVA" -> RouteCell.LAVA;
                case "SOFT", "FALLING" -> RouteCell.solid(8);
                case "HARD" -> RouteCell.solid(20);
                default -> RouteCell.BLOCKED;
            };
        }

        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            String value = kind(x, y, z);
            return "AIR".equals(value) || "WATER".equals(value);
        }

        @Override
        public boolean walkable(int x, int y, int z) {
            String value = kind(x, y, z);
            return "SOFT".equals(value) || "HARD".equals(value) || "FALLING".equals(value) || "BEDROCK".equals(value);
        }

        @Override
        public boolean water(int x, int y, int z) {
            return "WATER".equals(kind(x, y, z));
        }

        @Override
        public boolean climbable(int x, int y, int z) {
            return false;
        }

        @Override
        public double breakTicks(int x, int y, int z) {
            return "BEDROCK".equals(kind(x, y, z)) ? Double.POSITIVE_INFINITY : 10;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }
}
