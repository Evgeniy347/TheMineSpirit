package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HazardDetectorTests {
    private static final BlockCoord STAND = new BlockCoord(0, 5, 0);

    private static GridTerrain floorAtFour() {
        GridTerrain terrain = new GridTerrain(Terrain.AIR);
        for (int x = -3; x <= 0; x++) {
            for (int z = -3; z <= 3; z++) {
                terrain.with(x, 4, z, Terrain.SOLID);
            }
        }
        return terrain;
    }

    static Stream<Arguments> scenes() {
        return Stream.of(
                Arguments.of(
                        "лава в клетке",
                        new GridTerrain(Terrain.AIR).with(0, 5, 0, Terrain.LAVA),
                        Optional.of(HazardType.LAVA)),
                Arguments.of(
                        "вода в клетке",
                        new GridTerrain(Terrain.AIR).with(0, 5, 0, Terrain.WATER),
                        Optional.of(HazardType.WATER)),
                Arguments.of("камень", new GridTerrain(Terrain.SOLID), Optional.empty()),
                Arguments.of("воздух над пустотой, пола нет", new GridTerrain(Terrain.AIR), Optional.empty()),
                Arguments.of(
                        "пол со всех сторон",
                        new GridTerrain(Terrain.AIR)
                                .column(0, 0, 4, 4, Terrain.SOLID)
                                .with(1, 4, 0, Terrain.SOLID)
                                .with(-1, 4, 0, Terrain.SOLID)
                                .with(0, 4, 1, Terrain.SOLID)
                                .with(0, 4, -1, Terrain.SOLID),
                        Optional.empty()),
                Arguments.of("край пола над провалом в три клетки", floorAtFour(), Optional.of(HazardType.DROP)),
                Arguments.of(
                        "перепад в две клетки не обрыв", floorAtFour().with(1, 2, 0, Terrain.SOLID), Optional.empty()),
                Arguments.of(
                        "сосед за краем залит водой, падения нет",
                        floorAtFour().with(1, 5, 0, Terrain.WATER),
                        Optional.empty()),
                Arguments.of("незагруженный сосед не делает обрыв", surroundedBy(Terrain.UNLOADED), Optional.empty()),
                Arguments.of("под соседом вода, прыжок не падение", surroundedBy(Terrain.WATER), Optional.empty()));
    }

    private static GridTerrain shallowPit() {
        GridTerrain terrain = new GridTerrain(Terrain.SOLID);
        terrain.with(0, 5, 0, Terrain.AIR).with(0, 4, 0, Terrain.SOLID);
        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            terrain.with(step[0], 5, step[1], Terrain.AIR);
            terrain.column(step[0], step[1], 3, 4, Terrain.AIR);
        }
        return terrain;
    }

    private static GridTerrain surroundedBy(Terrain below) {
        GridTerrain terrain = new GridTerrain(Terrain.SOLID);
        terrain.with(0, 5, 0, Terrain.AIR).with(0, 4, 0, Terrain.SOLID);
        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            terrain.with(step[0], 5, step[1], Terrain.AIR);
            terrain.column(step[0], step[1], 2, 4, below);
        }
        return terrain;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void classify_Scene_ReportsHazardOfStandingCell(String scene, GridTerrain terrain, Optional<HazardType> expected) {
        assertEquals(
                expected,
                HazardDetector.classify(terrain, STAND),
                scene + ": опасность клетки обязана определяться по окружению, иначе sense_hazard врёт ИИ "
                        + "об обрывах, лаве и воде");
    }

    @Test
    void classify_EdgeAtOneSideOnly_DetectedAndFarCellIsNot() {
        GridTerrain terrain = floorAtFour();

        assertEquals(
                Optional.of(HazardType.DROP),
                HazardDetector.classify(terrain, new BlockCoord(0, 5, 3)),
                "клетка на краю пола у провала обязана быть обрывом");
        assertEquals(
                Optional.empty(),
                HazardDetector.classify(terrain, new BlockCoord(-2, 5, 0)),
                "клетка в глубине пола, вдали от края, обрывом не является");
    }

    @Test
    void classify_AirCellWithoutFloor_ReadsAtMostTwoCells() {
        GridTerrain terrain = new GridTerrain(Terrain.AIR);

        HazardDetector.classify(terrain, STAND);

        assertTrue(
                terrain.reads() <= 2,
                "воздух без пола отсекается двумя чтениями (клетка и пол), прочитано " + terrain.reads()
                        + ": иначе каждая клетка среза стоит как у обрыва, и тик не держит порцию срезов");
    }

    @Test
    void classify_CellWithFloorAndShallowPitOnAllSides_ReadsAtMostEighteenCells() {
        GridTerrain terrain = shallowPit();

        Optional<HazardType> result = HazardDetector.classify(terrain, STAND);

        assertEquals(
                Optional.empty(),
                result,
                "яма в две клетки со всех сторон обрывом не является, а проверка обязана дойти до самого дальнего чтения");
        assertTrue(
                terrain.reads() <= 2 + 4 * (1 + HazardDetector.DROP_DEPTH),
                "одна клетка читает себя, пол и по четыре клетки (сосед и " + HazardDetector.DROP_DEPTH
                        + " под ним) на каждого из четырёх соседей, прочитано " + terrain.reads());
    }

    @Test
    void dropDepth_Constant_IsThree() {
        assertEquals(
                3,
                HazardDetector.DROP_DEPTH,
                "обрыв — перепад в три клетки и больше: меньший перепад дух проходит шагом, больший ломает ноги; "
                        + "при смене числа пересмотри тесты сцен и сценарий GameTest sense_hazard");
    }

    @Test
    void refOf_PositionAndOrigin_MeasuresEuclideanDistance() {
        HazardRef ref = HazardDetector.refOf(HazardType.LAVA, new BlockCoord(3, 5, 4), new BlockCoord(0, 5, 0));

        assertEquals(5.0, ref.distance(), 1e-9, "расстояние до опасности (3, 0, 4) от духа обязано быть 5");
        assertEquals(HazardType.LAVA, ref.type(), "вид опасности обязан сохраниться");
        assertEquals(new BlockCoord(3, 5, 4), ref.pos(), "позиция опасности обязана сохраниться");
    }
}
