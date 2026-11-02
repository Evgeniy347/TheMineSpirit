package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YardFloorTests {
    @Test
    void of_StationOverWaterHole_ReportsCellsUnderIt() {
        BlockCoord table = new BlockCoord(-181, 70, -519);
        Set<BlockCoord> solid = Set.of(new BlockCoord(-181, 60, -519));

        YardFloor.Audit audit = YardFloor.of(List.of(table), 8, cell -> solid.contains(cell));

        assertTrue(audit.damaged(), "Под верстаком воздух/вода до y=61 — двор повреждён: " + audit);
        assertEquals(
                8, audit.holes().size(), "В пределах глубины 8 под верстаком только воздух/вода: " + audit.holes());
        assertEquals(
                new BlockCoord(-181, 62, -519),
                audit.holes().get(0),
                "Заделывать начинают с нижней клетки над опорой: " + audit.holes());
        assertEquals(0, audit.supported(), "Опора дальше 8 клеток — станок висит: " + audit);
    }

    @Test
    void of_SolidFoundDeepBelowHole_StationStillDamaged() {
        BlockCoord table = new BlockCoord(-181, 70, -519);
        YardFloor.Audit audit = YardFloor.of(List.of(table), 12, cell -> cell.y() == 60);

        assertTrue(audit.damaged(), "Опора найдена на глубине 10, но между станцией и ей дыра: " + audit);
        assertEquals(0, audit.supported(), "Дыра до опоры — станок не считается стоящим: " + audit);
    }

    @Test
    void of_StationOnSolidGround_NotDamaged() {
        BlockCoord chest = new BlockCoord(-180, 69, -518);
        YardFloor.Audit audit = YardFloor.of(List.of(chest), 12, cell -> cell.y() == 68);

        assertEquals(List.of(), audit.holes(), "Под сундуком твёрдый пол — дыр нет: " + audit);
        assertEquals(1, audit.supported(), "Станок стоит на опоре: " + audit);
    }

    @Test
    void due_SixtySecondsElapsed_TrueRegardlessOfTickMultiple() {
        assertTrue(
                YardFloor.due(750, 150, 600),
                "Проверка двора идёт по прошедшему времени, а не по кратности тика: 750 не делится на 600, "
                        + "но 600 тиков прошло");
        assertFalse(YardFloor.due(749, 150, 600), "Меньше периода — не проверяем");
        assertTrue(YardFloor.due(10, 700, 600), "Тик пошёл назад (перезапуск) — проверяем сразу");
    }
}
