package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.building.api.BuildRotation;
import org.junit.jupiter.api.Test;

class BuildRotationTests {
    @Test
    void rotate_Clockwise90_MapsNorthToEastAndSwapsSizes() {
        BuildRotation.Cell cell = BuildRotation.CLOCKWISE_90.rotate(0, 0, 4, 5);
        BuildRotation.DirectionVector north = BuildRotation.rotateDirection(0, -1, 1);

        assertEquals(
                new BuildRotation.Cell(4, 0),
                cell,
                "Поворот клетки (0,0) схемы 4×5 по часовой на 90° обязан лечь в угол (4,0): формула поворота "
                        + "позиции расходится с размером схемы, и блоки встанут со сдвигом"
                        + " Иначе тест rotate_Clockwise90_MapsNorthToEastAndSwapsSizes перестанет ловить эту поломку. При смене rotate_Clockwise90_MapsNorthToEastAndSwapsSizes проверь это утверждение.");
        assertEquals(
                5,
                BuildRotation.CLOCKWISE_90.rotatedSizeX(4, 5),
                "После поворота на 90° сторона X берёт длину прежней Z: иначе коробка не накроет схему"
                        + " При смене rotate_Clockwise90_MapsNorthToEastAndSwapsSizes проверь это утверждение.");
        assertEquals(
                4,
                BuildRotation.CLOCKWISE_90.rotatedSizeZ(4, 5),
                "После поворота на 90° сторона Z берёт длину прежней X: иначе коробка не накроет схему"
                        + " При смене rotate_Clockwise90_MapsNorthToEastAndSwapsSizes проверь это утверждение.");
        assertEquals(
                1,
                north.dx(),
                "Север обязан стать востоком"
                        + " Иначе тест rotate_Clockwise90_MapsNorthToEastAndSwapsSizes перестанет ловить эту поломку. При смене rotate_Clockwise90_MapsNorthToEastAndSwapsSizes проверь это утверждение.");
        assertEquals(
                0,
                north.dz(),
                "Север обязан стать востоком"
                        + " Иначе тест rotate_Clockwise90_MapsNorthToEastAndSwapsSizes перестанет ловить эту поломку. При смене rotate_Clockwise90_MapsNorthToEastAndSwapsSizes проверь это утверждение.");
    }

    @Test
    void rotate_FourQuarterTurns_ReturnsOriginalCell() {
        int x = 2;
        int z = 1;
        int sizeX = 6;
        int sizeZ = 3;
        for (int step = 0; step < 4; step++) {
            BuildRotation.Cell cell = BuildRotation.CLOCKWISE_90.rotate(x, z, sizeX, sizeZ);
            x = cell.x();
            z = cell.z();
            int nextX = BuildRotation.CLOCKWISE_90.rotatedSizeX(sizeX, sizeZ);
            int nextZ = BuildRotation.CLOCKWISE_90.rotatedSizeZ(sizeX, sizeZ);
            sizeX = nextX;
            sizeZ = nextZ;
        }

        assertEquals(
                2,
                x,
                "Четыре поворота на 90° обязаны вернуть клетку на место: иначе поворот не круг"
                        + " При смене rotate_FourQuarterTurns_ReturnsOriginalCell проверь это утверждение.");
        assertEquals(
                1,
                z,
                "Четыре поворота на 90° обязаны вернуть клетку на место: иначе поворот не круг"
                        + " При смене rotate_FourQuarterTurns_ReturnsOriginalCell проверь это утверждение.");
    }

    @Test
    void parse_Degrees_ReturnsMatchingRotation() {
        assertEquals(
                BuildRotation.CLOCKWISE_180,
                BuildRotation.parse("180"),
                "Градусы 180 обязаны читаться как половина оборота: иначе параметр поворота не сработает"
                        + " При смене parse_Degrees_ReturnsMatchingRotation проверь это утверждение.");
        assertEquals(
                BuildRotation.NONE,
                BuildRotation.parse("0"),
                "Градусы 0 обязаны читаться как отсутствие поворота"
                        + " Иначе тест parse_Degrees_ReturnsMatchingRotation перестанет ловить эту поломку. При смене parse_Degrees_ReturnsMatchingRotation проверь это утверждение.");
        assertEquals(
                BuildRotation.COUNTERCLOCKWISE_90,
                BuildRotation.parse("270"),
                "Градусы 270 обязаны читаться как поворот против часовой на 90°"
                        + " Иначе тест parse_Degrees_ReturnsMatchingRotation перестанет ловить эту поломку. При смене parse_Degrees_ReturnsMatchingRotation проверь это утверждение.");
    }

    @Test
    void parse_UnknownDegrees_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildRotation.parse("45"),
                "Неизвестный угол 45 обязан отвергаться: поворот не на четверть оборота сломал бы расстановку"
                        + " Иначе тест parse_UnknownDegrees_IsRejected перестанет ловить эту поломку. При смене parse_UnknownDegrees_IsRejected проверь это утверждение.");
    }

    @Test
    void ofQuarterTurns_NegativeValue_NormalizesToCounterclockwise() {
        assertEquals(
                BuildRotation.COUNTERCLOCKWISE_90,
                BuildRotation.ofQuarterTurns(-1),
                "Четверть -1 обязана нормализоваться в поворот против часовой: иначе выбор поворота "
                        + "от взгляда игрока уйдёт в ошибку"
                        + " При смене ofQuarterTurns_NegativeValue_NormalizesToCounterclockwise проверь это утверждение.");
    }
}
