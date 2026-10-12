package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FarmStandSpotsTests {
    private static final BlockCoord TARGET = new BlockCoord(0, 0, 0);
    private static final BlockCoord FROM = new BlockCoord(6, 1, 0);
    private static final FarmTile DIRT_AIR = new FarmTile(Ground.DIRT, Above.AIR, Optional.empty());

    private static CountingSafety flatFloor() {
        return new CountingSafety().fillFloor(0, -5, -5, 5, 5);
    }

    private static MapFarmView dirtPlane() {
        return new MapFarmView().fillBed(0, -5, -5, 5, 5, DIRT_AIR);
    }

    @Test
    void of_FlatGroundTarget_ReturnsAdjacentStand() {
        List<BlockCoord> stands = FarmStandSpots.of(TARGET, FROM, flatFloor(), dirtPlane());

        assertFalse(stands.isEmpty(),
                "Цель на ровной земле обязана давать стоянку: иначе дух не подойдёт ни к одной клетке грядки");
        for (BlockCoord stand : stands) {
            assertTrue(Math.max(Math.abs(stand.x()), Math.abs(stand.z())) <= 3,
                    "Стоянка обязана лежать в радиусе 3 клеток от цели: дальняя стоянка ломает приём R4. Стоянка: "
                            + stand);
            assertFalse(stand.x() == 0 && stand.z() == 0,
                    "Столбец цели исключён: дух не встаёт в клетку, которую обрабатывает. Стоянка: " + stand);
        }
    }

    @Test
    void maxSafetyReads_SearchBoxVolume_Is324() {
        assertEquals(9 * 4 * 9, FarmStandSpots.MAX_SAFETY_READS,
                "Предел чтений обязан равняться объёму ящика 9×4×9 (окрестность ±4 по земле, 4 уровня по высоте): "
                        + "поднятая константа тихо ослабит тест масштаба. Число правится только с тестом масштаба");
    }

    @Test
    void of_CachedView_ReadsStayWithinBound() {
        CountingSafety safety = flatFloor();

        FarmStandSpots.of(TARGET, FROM, new CachedSafetyView(safety), dirtPlane());

        assertTrue(safety.reads() <= FarmStandSpots.MAX_SAFETY_READS,
                "Поиск стоянки через кэш читает не более ящика 9×4×9=" + FarmStandSpots.MAX_SAFETY_READS
                        + " клеток: иначе перебор стоянок кладёт серверный тик. Чтений: " + safety.reads());
    }

    @Test
    void of_LavaNeighbor_RejectsAdjacentStandButKeepsWaterOne() {
        CountingSafety safety = flatFloor().set(2, 1, 0, Cell.LAVA).set(-2, 1, 0, Cell.WATER);

        List<BlockCoord> stands = FarmStandSpots.of(TARGET, FROM, safety, dirtPlane());

        assertFalse(stands.contains(new BlockCoord(1, 1, 0)),
                "Стоянка рядом с лавой отвергается: дух сгорит раньше, чем взмахнёт мотыгой");
        assertTrue(stands.contains(new BlockCoord(-1, 1, 0)),
                "Вода у грядки — норма и стоянку рядом с ней разрешает: в отличие от шахты, канал обязан быть рядом");
    }

    @Test
    void of_DirtStand_IsPreferredOverStrictlyCloserFarmland() {
        CountingSafety safety = flatFloor();
        MapFarmView farm = dirtPlane()
                .set(1, 0, 0, new FarmTile(Ground.FARMLAND, Above.CROP,
                        Optional.of(new CropState(CropKind.WHEAT, 7))));
        BlockCoord from = new BlockCoord(1, 1, 4);
        BlockCoord farmlandStand = new BlockCoord(1, 1, 0);
        BlockCoord dirtStand = new BlockCoord(2, 1, 0);

        List<BlockCoord> stands = FarmStandSpots.of(TARGET, from, safety, farm);

        assertTrue(stands.contains(farmlandStand) && stands.contains(dirtStand),
                "Обе стоянки обязаны подходить: иначе тест сравнивает несравнимое. Стоянки: " + stands);
        assertTrue(stands.indexOf(dirtStand) < stands.indexOf(farmlandStand),
                "Земляная стоянка идёт первой, хотя пашня строго ближе к духу (16 < 17): дух не топчет farmland "
                        + "даже ценой лишнего шага. Стоянки: " + stands.stream().limit(4).toList());
    }

    @Test
    void of_OpenGround_MaxStandOffsetIsSqrtEleven() {
        List<BlockCoord> stands = FarmStandSpots.of(TARGET, FROM, flatFloor(), dirtPlane());

        long maxOffset = stands.stream()
                .mapToLong(stand -> (long) stand.x() * stand.x() + (long) stand.y() * stand.y()
                        + (long) stand.z() * stand.z())
                .max()
                .orElseThrow();

        assertEquals(11, maxOffset,
                "Наибольшее смещение стоянки обязано равняться 11 = 3²+1²+1² (по земле 3 и 1, вверх 1 — предел "
                        + "досягаемости глаз 4.0): по нему планировщик вычитает √11 в раннем выходе, а не радиус 3. "
                        + "Стоянок: " + stands.size());
    }

    static Stream<Arguments> hazardRows() {
        return Stream.of(
                Arguments.of(Cell.LAVA, new BlockCoord(2, 1, 0), "лава у ног"),
                Arguments.of(Cell.FIRE, new BlockCoord(2, 1, 0), "огонь у ног"),
                Arguments.of(Cell.HAZARD, new BlockCoord(2, 1, 0), "магма и кактус у ног"),
                Arguments.of(Cell.FALLING, new BlockCoord(2, 1, 0), "падающий блок у ног"),
                Arguments.of(Cell.LAVA, new BlockCoord(2, 0, 0), "лава у пола"),
                Arguments.of(Cell.FIRE, new BlockCoord(2, 0, 0), "огонь у пола"),
                Arguments.of(Cell.HAZARD, new BlockCoord(2, 0, 0), "магма и кактус у пола"),
                Arguments.of(Cell.FALLING, new BlockCoord(2, 0, 0), "падающий блок у пола"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("hazardRows")
    void of_HazardNeighbor_RejectsAdjacentStand(Cell hazard, BlockCoord hazardCell, String reason) {
        CountingSafety clean = flatFloor();
        CountingSafety exposed = flatFloor().set(hazardCell.x(), hazardCell.y(), hazardCell.z(), hazard);
        BlockCoord stand = new BlockCoord(1, 1, 0);

        assertTrue(FarmStandSpots.of(TARGET, FROM, clean, dirtPlane()).contains(stand),
                "Контроль: стоянка без опасности обязана подходить, иначе строка таблицы проверяет не опасность");
        assertFalse(FarmStandSpots.of(TARGET, FROM, exposed, dirtPlane()).contains(stand),
                "Стоянка рядом с опасностью отвергается (" + reason + "): дух сгорит раньше, чем взмахнёт мотыгой");
    }

    @Test
    void of_ReachCheckedFirst_OutOfReachCostsNoReads() {
        CountingSafety safety = new CountingSafety()
                .fillFloor(0, -5, -5, 5, 5)
                .fill(1, -5, -5, 5, 5, Cell.SOLID);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= 6) {
                    safety.set(dx, 2, dz, Cell.SOLID);
                }
            }
        }

        FarmStandSpots.of(TARGET, FROM, safety, dirtPlane());

        assertTrue(safety.reads() < 96,
                "Досягаемость проверяется до чтения мира: кандидаты вне глаз 4.0 не стоят ни одного чтения, "
                        + "иначе каждая из 96 клеток читается минимум по разу. Чтений: " + safety.reads());
    }

    @Test
    void of_WalledTarget_ReturnsEmpty() {
        CountingSafety safety = new CountingSafety()
                .fillFloor(0, -5, -5, 5, 5)
                .fill(1, -4, -4, 4, 4, Cell.SOLID)
                .fill(2, -4, -4, 4, 4, Cell.SOLID);

        assertTrue(FarmStandSpots.of(TARGET, FROM, safety, dirtPlane()).isEmpty(),
                "Замурованная цель не даёт стоянки: планировщик посчитает её в noStand, а не повесит духа");
    }

    @Test
    void of_HighPillarStandBeyondReach_IsExcluded() {
        CountingSafety safety = flatFloor().set(3, 1, 0, Cell.SOLID);

        List<BlockCoord> stands = FarmStandSpots.of(TARGET, FROM, safety, dirtPlane());

        assertTrue(stands.contains(new BlockCoord(2, 1, 0)),
                "Стоянка в 2 клетках по земле в пределах досягаемости глаз 4.0 обязана подходить");
        assertFalse(stands.contains(new BlockCoord(3, 2, 0)),
                "Стоянка на столбе выше досягаемости (глаза 1.62, предел 4.0) отвергается: дух не достанет до клетки");
    }
}
