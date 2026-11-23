package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CrossingSpan;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossingSpanTests {
    private static final Set<BlockCoord> WATER =
            Set.of(new BlockCoord(2, 62, 0), new BlockCoord(3, 62, 0), new BlockCoord(4, 62, 0));

    @Test
    void span_WaterInMiddle_ReturnsBothShores() {
        CrossingSpan span =
                CrossingSpan.of(new BlockCoord(0, 62, 0), new BlockCoord(6, 62, 0), WATER::contains, cell -> 5);

        assertEquals(
                3,
                span.width(),
                "Три клетки воды подряд — ширина заплыва 3: по ней скрипт решает плыть, а не обходить"
                        + " Иначе тест span_WaterInMiddle_ReturnsBothShores перестанет ловить эту поломку. При смене span_WaterInMiddle_ReturnsBothShores проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 62, 0),
                span.dry(),
                "Сухой берег перед водой — клетка входа: дух подходит к ней обычным путём"
                        + " Иначе тест span_WaterInMiddle_ReturnsBothShores перестанет ловить эту поломку. При смене span_WaterInMiddle_ReturnsBothShores проверь это утверждение.");
        assertEquals(
                new BlockCoord(2, 62, 0),
                span.wet(),
                "Первая клетка воды — точка шага в воду: дальше идёт прямой заплыв"
                        + " Иначе тест span_WaterInMiddle_ReturnsBothShores перестанет ловить эту поломку. При смене span_WaterInMiddle_ReturnsBothShores проверь это утверждение.");
    }

    @Test
    void span_NoWater_SaysNoCrossing() {
        CrossingSpan span =
                CrossingSpan.of(new BlockCoord(0, 62, 0), new BlockCoord(6, 62, 0), cell -> false, cell -> 5);

        assertTrue(
                !span.crosses(),
                "Без воды переправы нет: скрипт идёт обычным путём к цели"
                        + " Иначе тест span_NoWater_SaysNoCrossing перестанет ловить эту поломку. При смене span_NoWater_SaysNoCrossing проверь это утверждение.");
        assertNull(
                span.wet(),
                "Без воды клетки входа в воду нет: подставлять первую клетку линии нельзя"
                        + " Иначе тест span_NoWater_SaysNoCrossing перестанет ловить эту поломку. При смене span_NoWater_SaysNoCrossing проверь это утверждение.");
    }

    @Test
    void span_DeeperColumn_ReportsMaxDepth() {
        CrossingSpan span = CrossingSpan.of(
                new BlockCoord(0, 62, 0), new BlockCoord(6, 62, 0), WATER::contains, cell -> cell.x() == 3 ? 7 : 2);

        assertEquals(
                7,
                span.deepest(),
                "Глубина заплыва — максимум по линии: по ней выбирается плавание вместо обхода"
                        + " Иначе тест span_DeeperColumn_ReportsMaxDepth перестанет ловить эту поломку. При смене span_DeeperColumn_ReportsMaxDepth проверь это утверждение.");
    }

    @Test
    void span_WaterFromFirstCell_KeepsEntryWithoutShore() {
        CrossingSpan span =
                CrossingSpan.of(new BlockCoord(2, 62, 0), new BlockCoord(6, 62, 0), WATER::contains, cell -> 4);

        assertNull(
                span.dry(),
                "Если дух уже стоит у кромки и линия начинается с воды, сухого берега входа нет: скрипт шагает в воду с места"
                        + " Иначе тест span_WaterFromFirstCell_KeepsEntryWithoutShore перестанет ловить эту поломку. При смене span_WaterFromFirstCell_KeepsEntryWithoutShore проверь это утверждение.");
        assertEquals(
                new BlockCoord(2, 62, 0),
                span.wet(),
                "Первая клетка воды запоминается и без сухого берега: по ней дух шагает в заплыв"
                        + " Иначе тест span_WaterFromFirstCell_KeepsEntryWithoutShore перестанет ловить эту поломку. При смене span_WaterFromFirstCell_KeepsEntryWithoutShore проверь это утверждение.");
    }
}
