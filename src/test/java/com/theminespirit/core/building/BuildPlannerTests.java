package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class BuildPlannerTests {
    private static final PresetLibrary LIBRARY = BuildPlanSnapshotTests.library();

    private static BuildPlanner.Plan plan(String scheme, BuildPlanner.Portion portion) {
        return BuildPlanSnapshotTests.plan(LIBRARY.byId(scheme).orElseThrow(), BuildRotation.NONE, portion);
    }

    private static List<BuildPlanner.Trip> trips(BuildPlanner.Plan plan) {
        List<BuildPlanner.Trip> trips = new ArrayList<>();
        for (BuildPlanner.Step step : plan.steps()) {
            if (step instanceof BuildPlanner.Trip trip) {
                trips.add(trip);
            }
        }
        return trips;
    }

    // Гипотеза владельца 20.10: порция берёт всё подряд, включая декор, и инвентарь забит тем, что ставится последним.
    @Test
    void plan_FillPortion_TakesDecorOnlyOnLastTrip() {
        List<BuildPlanner.Trip> trips = trips(plan("family_house_3", BuildPlanner.Portion.FILL));
        for (BuildPlanner.Trip trip : trips.subList(0, trips.size() - 1)) {
            for (BuildPlanner.Taken taken : trip.taken()) {
                assertTrue(
                        BuildPhase.rough(taken.item()),
                        "заход " + trip.number() + " берёт '" + taken.item() + "' — декор и отделка берутся только на "
                                + "этапе отделки, иначе инвентарь занят тем, что ставится последним");
            }
        }
    }

    @Test
    void plan_AllPortion_FillsInventoryWithLaterMaterial() {
        int later = trips(plan("family_house_3", BuildPlanner.Portion.ALL)).stream()
                .mapToInt(BuildPlanner.Trip::slotsLater)
                .max()
                .orElse(0);
        assertTrue(
                later >= BuildPlanner.SLOTS / 2,
                "прежняя порция (всё недостающее по порядку схемы) держит в инвентаре больше половины слотов под "
                        + "будущие этапы — гипотеза владельца подтверждена замером на плане; слотов будущего " + later);
    }

    @Test
    void plan_PhaseOnlyPortion_NeedsManyMoreTripsThanFill() {
        long phase = plan("family_house_3", BuildPlanner.Portion.PHASE).trips();
        long fill = plan("family_house_3", BuildPlanner.Portion.FILL).trips();
        assertTrue(
                phase >= 5 * fill,
                "порция только текущей полосы — заходов " + phase + " против " + fill + " у порции «черновые от "
                        + "текущей полосы вверх»: строгое правило гоняет духа к сундуку почти за каждым слоем");
    }

    @Test
    void plans_EveryCatalogBuilding_PlaceEverythingFromInsideWithoutRoofStands() {
        List<String> problems = new ArrayList<>();
        for (String scheme : BuildPlanSnapshotTests.schemes()) {
            BuildPlanner.Plan plan = plan(scheme, BuildPlanner.Portion.FILL);
            boolean house = scheme.contains("house") || scheme.startsWith("storage");
            if (plan.stuck() > 0 || (house && plan.roofStands() > 0)) {
                problems.add(scheme + ": не поставлено " + plan.stuck() + ", стоянок на крыше " + plan.roofStands());
            }
        }
        assertEquals(
                List.of(),
                problems,
                "план каждой постройки ставит всё, а крышу дома — со стоянок верхнего этажа изнутри (решение "
                        + "владельца 20.10: стоянки на самой крыше не нужны)");
    }

    private static BuildPlanner.Plan planFrom(String scheme, BuildPlanner.Start start) {
        Preset preset = LIBRARY.byId(scheme).orElseThrow();
        return BuildPlanner.plan(
                preset.schematic(),
                BuildRotation.NONE,
                BuildPlanSnapshotTests.supplyFeet(preset, BuildRotation.NONE),
                new SpiritReach(3, 15, 3),
                BuildPlanner.Portion.FILL,
                start);
    }

    private static Map<String, Integer> materials(String scheme) {
        Map<String, Integer> all = new TreeMap<>();
        for (Schematic.PlacedEntry entry :
                LIBRARY.byId(scheme).orElseThrow().schematic().entries(new BlockCoord(0, 0, 0), BuildRotation.NONE)) {
            if (!SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                all.merge(BuildPlanner.item(entry.block()), 1, Integer::sum);
            }
        }
        return all;
    }

    // Исполнитель строит план от того, что есть: весь материал при себе — заходов нет, всё ставится.
    @Test
    void plan_AllMaterialInInventory_NeedsNoTrips() {
        BuildPlanner.Plan plan = planFrom(
                "family_house",
                new BuildPlanner.Start(
                        Set.of(), Set.of(), materials("family_house"), Map.of(), BuildPlanner.SLOTS, 0, Map.of()));
        assertEquals(0, plan.trips(), "материал при себе — к сундуку ходить незачем: " + plan.lines());
        assertEquals(0, plan.stuck(), "всё при себе — всё ставится: " + plan.lines());
    }

    // Пустой старт равен прежнему плану: снапшоты исполнителя и плана — одно и то же.
    @Test
    void plan_EmptyStart_EqualsDefaultPlan() {
        BuildPlanner.Plan plain = plan("family_house_3", BuildPlanner.Portion.FILL);
        BuildPlanner.Plan started = planFrom(
                "family_house_3",
                new BuildPlanner.Start(
                        Set.of(), Set.of(), Map.of(), materials("family_house_3"), BuildPlanner.SLOTS, 0, Map.of()));
        assertEquals(plain.lines(), started.lines(), "старт «пусто, всё в сундуке» обязан дать тот же план");
    }

    // Два свободных слота и лишнее в остальных: заход освобождает лишнее, когда свободных нет (QUESTIONS-3 п. 5 А).
    @Test
    void plan_JunkSlots_ReleasedWhenNoFreeSlots() {
        BuildPlanner.Plan plan = planFrom(
                "family_house_3",
                new BuildPlanner.Start(
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        materials("family_house_3"),
                        2,
                        BuildPlanner.SLOTS - 2,
                        Map.of()));
        assertEquals(0, plan.stuck(), "лишнее освобождает слоты — стройка доходит до конца: " + plan.lines());
    }

    // Уже стоящее не планируется: стоянки плана ставят только остальное, и ни одна клетка — дважды.
    @Test
    void plan_DoneCells_AreNotPlannedAgain() {
        BuildPlanner.Plan full = plan("family_house", BuildPlanner.Portion.FILL);
        Set<BlockCoord> done = new HashSet<>();
        for (BuildPlanner.Step step : full.steps().subList(0, full.steps().size() / 2)) {
            if (step instanceof BuildPlanner.Work work) {
                done.addAll(work.cells());
            }
        }
        BuildPlanner.Plan rest = planFrom(
                "family_house",
                new BuildPlanner.Start(
                        done, Set.of(), Map.of(), materials("family_house"), BuildPlanner.SLOTS, 0, Map.of()));
        Set<BlockCoord> seen = new HashSet<>();
        for (BuildPlanner.Step step : rest.steps()) {
            if (step instanceof BuildPlanner.Work work) {
                for (BlockCoord cell : work.cells()) {
                    assertTrue(!done.contains(cell) && seen.add(cell), "клетка " + cell + " запланирована дважды");
                }
            }
        }
    }

    // План и исполнитель решают «на чём стоять» одной функцией: каждая стоянка плана на полу постройки обязана
    // проходить правило исполнителя (MiningSafety.canStand). Площадь 1 стоит на гравии: пока исполнитель считал
    // сыпучий пол запретным, он искал стоянки заново — подходов 47–49 при плане 21.
    @Test
    void plan_PlazaStands_PassExecutorStandRule() {
        for (String scheme : List.of("plaza_1", "plaza_2")) {
            Preset preset = LIBRARY.byId(scheme).orElseThrow();
            for (BuildRotation rotation : BuildRotation.values()) {
                Map<BlockCoord, String> grid = new HashMap<>();
                for (Schematic.PlacedEntry entry : preset.schematic().entries(new BlockCoord(0, 0, 0), rotation)) {
                    grid.put(entry.pos(), entry.block());
                }
                BuildPlanner.Plan plan = BuildPlanSnapshotTests.plan(preset, rotation, BuildPlanner.Portion.FILL);
                for (BuildPlanner.Step step : plan.steps()) {
                    if (!(step instanceof BuildPlanner.Work work)
                            || work.stand().y() <= 0) {
                        continue;
                    }
                    BlockCoord stand = work.stand();
                    BlockCoord floor = new BlockCoord(stand.x(), stand.y() - 1, stand.z());
                    assertTrue(
                            MiningSafety.canStand(stand, standView(stand, floor, cellOf(grid.get(floor)))),
                            scheme + " " + rotation.id() + ": стоянка плана " + stand + " на полу '"
                                    + grid.get(floor) + "' не проходит правило исполнителя — он пойдёт искать "
                                    + "другую стоянку, и подходов станет больше плана");
                }
            }
        }
    }

    private static Cell cellOf(@Nullable String block) {
        if (block == null || SchematicWalk.passable(block)) {
            return Cell.PASSABLE;
        }
        if (SchematicBlocks.isFalling(block)) {
            return Cell.FALLING;
        }
        String path = SchematicBlocks.path(block);
        if ("water".equals(path)) {
            return Cell.WATER;
        }
        if ("lava".equals(path)) {
            return Cell.LAVA;
        }
        return Cell.SOLID;
    }

    private static SafetyView standView(BlockCoord stand, BlockCoord floor, Cell floorCell) {
        BlockCoord head = new BlockCoord(stand.x(), stand.y() + 1, stand.z());
        return cell -> {
            if (cell.equals(stand) || cell.equals(head)) {
                return Cell.PASSABLE;
            }
            if (cell.equals(floor)) {
                return floorCell;
            }
            return Cell.SOLID;
        };
    }

    // Курсор пропускает стоянки, чьи клетки уже стоят, и отдаёт координаты мира.
    @Test
    void cursor_SkipsFinishedStands_AndShiftsToWorld() {
        BuildPlanner.Plan plan = plan("family_house", BuildPlanner.Portion.FILL);
        BlockCoord origin = new BlockCoord(100, 64, -20);
        BuildPlanCursor cursor = new BuildPlanCursor(plan, origin);
        List<BuildPlanner.Work> works = new ArrayList<>();
        plan.steps().forEach(step -> {
            if (step instanceof BuildPlanner.Work work && !work.cells().isEmpty()) {
                works.add(work);
            }
        });
        BlockCoord firstCell = works.get(0).cells().get(0);
        Set<BlockCoord> firstStand = new HashSet<>();
        works.get(0)
                .cells()
                .forEach(cell -> firstStand.add(new BlockCoord(cell.x() + 100, cell.y() + 64, cell.z() - 20)));
        BuildPlanCursor.Next next = cursor.next(cell -> !firstStand.contains(cell));
        while (next instanceof BuildPlanCursor.Fetch) {
            cursor.advance();
            next = cursor.next(cell -> !firstStand.contains(cell));
        }
        BuildPlanner.Work second = works.get(1);
        assertEquals(
                new BuildPlanCursor.StandAt(
                        new BlockCoord(
                                second.stand().x() + 100,
                                second.stand().y() + 64,
                                second.stand().z() - 20),
                        second.cells().stream()
                                .map(cell -> new BlockCoord(cell.x() + 100, cell.y() + 64, cell.z() - 20))
                                .toList()),
                next,
                "первая стоянка готова (клетка " + firstCell + ") — курсор обязан перейти ко второй");
        while (!(cursor.next(cell -> false) instanceof BuildPlanCursor.Done)) {
            cursor.advance();
        }
        assertTrue(cursor.index() >= plan.steps().size() - 1, "всё стоит — план кончился");
    }
}
