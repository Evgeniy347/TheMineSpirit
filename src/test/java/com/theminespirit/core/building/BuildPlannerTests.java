package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
}
