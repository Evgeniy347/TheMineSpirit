package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoCityTests {
    private static final int[][] LOOKS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final Map<String, Integer> STAGES =
            Map.of("hut_1", 0, "hut_2", 1, "hut_3", 2, "lamp_1", 0, "old", 0, "hall_1", 3);

    private static List<DemoBuildings.Building> catalog() {
        return List.of(
                new DemoBuildings.Building(
                        "hut",
                        "Хижина",
                        BuildingType.HOUSING,
                        List.of(
                                new DemoBuildings.Level(1, "hut_1"),
                                new DemoBuildings.Level(2, "hut_2"),
                                new DemoBuildings.Level(3, "hut_3"))),
                new DemoBuildings.Building(
                        "lamp",
                        "Светильник",
                        BuildingType.INFRASTRUCTURE,
                        List.of(new DemoBuildings.Level(1, "lamp_1"))),
                new DemoBuildings.Building(
                        "old", "Старая", BuildingType.HOUSING, List.of(new DemoBuildings.Level(0, "old"))),
                new DemoBuildings.Building(
                        "hall", "Ратуша", BuildingType.SOCIAL, List.of(new DemoBuildings.Level(1, "hall_1"))));
    }

    private static List<String> schemes(int cityLevel) {
        return DemoCity.pick(catalog(), STAGES::get, cityLevel).stream()
                .map(pick -> pick.level().scheme())
                .toList();
    }

    @Test
    void pick_CityOne_TakesOnlyStageZeroLeveledBuildings() {
        assertEquals(
                List.of("hut_1", "lamp_1"),
                schemes(1),
                "Витрина 1 обязана показать этап 0: уровни этапов выше и старые схемы без уровней в неё не входят");
    }

    @Test
    void pick_LaterCity_TakesHighestAvailableLevelOfEachFamily() {
        assertEquals(
                List.of("hut_3", "lamp_1"),
                schemes(3),
                "Витрина 3 обязана показать каждую семью старшим уровнем этапа 2: младшие уровни там же не нужны");
        assertEquals(
                List.of("hut_3", "lamp_1", "hall_1"),
                schemes(5),
                "Витрина 5 обязана включить всё, что есть в каталоге с уровнями");
    }

    @Test
    void pick_LevelOutOfRange_Refuses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> schemes(0),
                "Витрина 0 не существует: уровни 1..5 — этапы 0..4, иначе игрок получит пустую улицу молча");
        assertThrows(
                IllegalArgumentException.class,
                () -> schemes(DemoCity.MAX_LEVEL + 1),
                "Витрина выше последнего этапа не существует: команда обязана назвать допустимый диапазон");
    }

    private static List<DemoCity.Plot> plots() {
        return List.of(
                new DemoCity.Plot(new DemoPlacement.Door(5, 0, 0, -1, 0), new DemoPlacement.Size(11, 10, 13)),
                new DemoCity.Plot(new DemoPlacement.Door(1, 0, 0, -1, 0), new DemoPlacement.Size(2, 4, 1)),
                new DemoCity.Plot(new DemoPlacement.Door(7, 1, 0, -1, 1), new DemoPlacement.Size(16, 18, 18)),
                new DemoCity.Plot(new DemoPlacement.Door(2, 0, 0, -1, 0), new DemoPlacement.Size(5, 6, 6)),
                new DemoCity.Plot(new DemoPlacement.Door(6, 0, 0, -1, 0), new DemoPlacement.Size(13, 12, 15)));
    }

    private static boolean overlapXz(BuildBox a, BuildBox b) {
        return a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
    }

    @Test
    void layout_EveryLook_PlatformsNeverOverlapAndStreetAxisStaysFree() {
        DemoPlacement.Anchor start = new DemoPlacement.Anchor(100, 64, 100);
        for (int[] look : LOOKS) {
            List<DemoPlacement> placements = DemoCity.layout(start, new DemoPlacement.Look(look[0], look[1]), plots());
            for (int i = 0; i < placements.size(); i++) {
                for (int j = i + 1; j < placements.size(); j++) {
                    assertFalse(
                            overlapXz(
                                    placements.get(i).platform(),
                                    placements.get(j).platform()),
                            "Площадки " + i + " и " + j + " (взгляд " + look[0] + "," + look[1] + ") наложились: "
                                    + "подготовка второй снесёт сундук или стройку первой");
                }
                for (int step = 0; step < 200; step++) {
                    int x = start.x() + step * look[0];
                    int z = start.z() + step * look[1];
                    assertFalse(
                            placements.get(i).platform().contains(new BlockCoord(x, start.y(), z)),
                            "Ось улицы обязана оставаться свободной: площадка " + i + " заняла клетку " + x + "," + z);
                }
            }
        }
    }

    @Test
    void layout_EveryLook_DoorsFaceTheStreet() {
        DemoPlacement.Anchor start = new DemoPlacement.Anchor(0, 64, 0);
        for (int[] look : LOOKS) {
            List<DemoCity.Plot> plots = plots();
            List<DemoPlacement> placements = DemoCity.layout(start, new DemoPlacement.Look(look[0], look[1]), plots);
            for (int i = 0; i < placements.size(); i++) {
                DemoPlacement placement = placements.get(i);
                DemoPlacement.Door door = plots.get(i).door();
                DemoPlacement.Size size = plots.get(i).size();
                BuildRotation.Cell cell = placement.rotation().rotate(door.x(), door.z(), size.x(), size.z());
                int doorX = placement.origin().x() + cell.x();
                int doorZ = placement.origin().z() + cell.z();
                int across = Math.abs(doorX * -look[1] + doorZ * look[0]);
                int centreAcross = Math.abs((placement.origin().x() + placement.sizeX() / 2) * -look[1]
                        + (placement.origin().z() + placement.sizeZ() / 2) * look[0]);
                assertEquals(
                        BuildTuning.DEMO_PLATFORM_PADDING + 1 + door.porch(),
                        across,
                        "Дверь постройки " + i + " обязана стоять у края улицы, в клетке за точкой подхода");
                assertTrue(
                        centreAcross >= across,
                        "Постройка " + i + " обязана уходить от улицы вглубь: иначе дверь смотрит не на улицу");
            }
        }
    }
}
