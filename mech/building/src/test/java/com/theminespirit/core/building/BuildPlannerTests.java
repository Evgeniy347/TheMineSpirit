package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.mining.api.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
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

    @Test
    void plan_OrchardGroundLayer_PlansWithoutBelowBottomError() {
        BuildPlanner.Plan plan = plan("orchard_1", BuildPlanner.Portion.FILL);

        assertTrue(
                !plan.steps().isEmpty(),
                "План сада обязан строиться: нижний слой схемы уходит на -1 и раньше валил расчёт высоты полосы, "
                        + "ремонт сада откатывался на обычную стройку"
                        + " Иначе тест plan_OrchardGroundLayer_PlansWithoutBelowBottomError перестанет ловить эту поломку. При смене plan_OrchardGroundLayer_PlansWithoutBelowBottomError проверь это утверждение.");
    }

    @Test
    void plan_FillPortion_TakesDecorOnlyOnLastTrip() {
        List<BuildPlanner.Trip> trips = trips(plan("family_house_3", BuildPlanner.Portion.FILL));
        for (BuildPlanner.Trip trip : trips.subList(0, trips.size() - 1)) {
            for (BuildPlanner.Taken taken : trip.taken()) {
                assertTrue(
                        BuildPhase.rough(taken.item()),
                        "заход " + trip.number() + " берёт '" + taken.item() + "' — декор и отделка берутся только на "
                                + "этапе отделки, иначе инвентарь занят тем, что ставится последним"
                                + " При смене plan_FillPortion_TakesDecorOnlyOnLastTrip проверь это утверждение.");
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
                        + "будущие этапы — гипотеза владельца подтверждена замером на плане; слотов будущего " + later
                        + " Иначе тест plan_AllPortion_FillsInventoryWithLaterMaterial перестанет ловить эту поломку. При смене plan_AllPortion_FillsInventoryWithLaterMaterial проверь это утверждение.");
    }

    @Test
    void plan_PhaseOnlyPortion_NeedsManyMoreTripsThanFill() {
        long phase = plan("family_house_3", BuildPlanner.Portion.PHASE).trips();
        long fill = plan("family_house_3", BuildPlanner.Portion.FILL).trips();
        assertTrue(
                phase >= 5 * fill,
                "порция только текущей полосы — заходов " + phase + " против " + fill + " у порции «черновые от "
                        + "текущей полосы вверх»: строгое правило гоняет духа к сундуку почти за каждым слоем"
                        + " Иначе тест plan_PhaseOnlyPortion_NeedsManyMoreTripsThanFill перестанет ловить эту поломку. При смене plan_PhaseOnlyPortion_NeedsManyMoreTripsThanFill проверь это утверждение.");
    }

    @Test
    @Tag("slow")
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
                        + "владельца 20.10: стоянки на самой крыше не нужны)"
                        + " Иначе тест plans_EveryCatalogBuilding_PlaceEverythingFromInsideWithoutRoofStands перестанет ловить эту поломку. При смене plans_EveryCatalogBuilding_PlaceEverythingFromInsideWithoutRoofStands проверь это утверждение.");
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

    @Test
    void plan_AllMaterialInInventory_NeedsNoTrips() {
        BuildPlanner.Plan plan = planFrom(
                "family_house",
                new BuildPlanner.Start(
                        Set.of(), Set.of(), materials("family_house"), Map.of(), BuildPlanner.SLOTS, 0, Map.of()));
        assertEquals(
                0,
                plan.trips(),
                "материал при себе — к сундуку ходить незачем: " + plan.lines()
                        + " Иначе тест plan_AllMaterialInInventory_NeedsNoTrips перестанет ловить эту поломку. При смене plan_AllMaterialInInventory_NeedsNoTrips проверь это утверждение.");
        assertEquals(
                0,
                plan.stuck(),
                "всё при себе — всё ставится: " + plan.lines()
                        + " Иначе тест plan_AllMaterialInInventory_NeedsNoTrips перестанет ловить эту поломку. При смене plan_AllMaterialInInventory_NeedsNoTrips проверь это утверждение.");
    }

    @Test
    void plan_EmptyStart_EqualsDefaultPlan() {
        BuildPlanner.Plan plain = plan("family_house_3", BuildPlanner.Portion.FILL);
        BuildPlanner.Plan started = planFrom(
                "family_house_3",
                new BuildPlanner.Start(
                        Set.of(), Set.of(), Map.of(), materials("family_house_3"), BuildPlanner.SLOTS, 0, Map.of()));
        assertEquals(
                plain.lines(),
                started.lines(),
                "старт «пусто, всё в сундуке» обязан дать тот же план"
                        + " Иначе тест plan_EmptyStart_EqualsDefaultPlan перестанет ловить эту поломку. При смене plan_EmptyStart_EqualsDefaultPlan проверь это утверждение.");
    }

    @Test
    void plan_JunkSlots_ReleasedWhenNoFreeSlots() {
        BuildPlanner.Plan plan = planFrom(
                "storage_hut",
                new BuildPlanner.Start(
                        Set.of(), Set.of(), Map.of(), materials("storage_hut"), 2, BuildPlanner.SLOTS - 2, Map.of()));
        assertEquals(
                0,
                plan.stuck(),
                "лишнее освобождает слоты — стройка доходит до конца на схеме без полей участка: " + plan.lines()
                        + " Иначе тест plan_JunkSlots_ReleasedWhenNoFreeSlots перестанет ловить эту поломку. При смене plan_JunkSlots_ReleasedWhenNoFreeSlots проверь это утверждение.");
    }

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
                    assertTrue(
                            !done.contains(cell) && seen.add(cell),
                            "клетка " + cell + " запланирована дважды"
                                    + " Иначе тест plan_DoneCells_AreNotPlannedAgain перестанет ловить эту поломку. При смене plan_DoneCells_AreNotPlannedAgain проверь это утверждение.");
                }
            }
        }
    }

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
                                    + "другую стоянку, и подходов станет больше плана"
                                    + " Иначе тест plan_PlazaStands_PassExecutorStandRule перестанет ловить эту поломку. При смене plan_PlazaStands_PassExecutorStandRule проверь это утверждение.");
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
}
