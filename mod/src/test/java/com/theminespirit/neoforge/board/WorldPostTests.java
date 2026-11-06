package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldPostTests {
    @Test
    void handles_WorldCleanupAndRepairNodes_AreRecognized() {
        assertFalse(
                WorldPost.handles("demolish_stray"),
                "Снос лишнего уже исполняет PlanDemolition по узлу графа, а не заглушка мира");
        assertTrue(WorldPost.handles("backfill"), "Засыпка — узел мира");
        assertFalse(
                WorldPost.handles("clear_floating"),
                "Уборку висящего исполняет PlanDemolition по узлу графа, а не заглушка мира");
        assertTrue(WorldPost.handles("level_lot"), "Выравнивание — узел мира");
        assertTrue(WorldPost.handles("restore_trace"), "Возврат следа — узел мира");
        assertTrue(WorldPost.handles("repair:hut"), "Ремонт хижины — узел мира");
        assertTrue(WorldPost.handles("repair:clearing"), "Ремонт расчистки — узел мира");
        assertTrue(WorldPost.handles("repair:mine"), "Ремонт шахты — узел мира");
        assertFalse(WorldPost.handles("collect_drops"), "Чужой узел не перехватывается");
    }

    @Test
    void fresh_FirstCellsAlreadyOnBoard_PicksNextUnpostedWithinBatch() {
        List<BlockCoord> cells =
                List.of(cell(0), cell(1), cell(2), cell(3), cell(4), cell(5), cell(6), cell(7), cell(8), cell(9));

        List<BlockCoord> fresh = WorldPost.fresh(
                cells, 4, cell -> Set.of(cell(0), cell(1), cell(2)).contains(cell));

        assertEquals(
                List.of(cell(3), cell(4), cell(5), cell(6)),
                fresh,
                "Уже стоящие на доске клетки не занимают порцию: иначе первые недостижимые находки навсегда "
                        + "закрывают очередь и лишнее не убирается");
    }

    @Test
    void room_OpenBatch_LeavesOnlyMissingSlots() {
        assertEquals(8, WorldPost.room(0, 8), "пустая доска даёт всю порцию уборки");
        assertEquals(3, WorldPost.room(5, 8), "уже стоящие задания занимают места порции");
        assertEquals(
                0,
                WorldPost.room(9, 8),
                "порция не растёт: иначе 95 world:backfill забивают доску, и вытеснение снимает "
                        + "repair:build_mine с причиной always");
    }

    @Test
    void fresh_AllCellsOnBoard_ReturnsNothing() {
        List<BlockCoord> cells = List.of(cell(0), cell(1));

        List<BlockCoord> fresh = WorldPost.fresh(cells, 4, ignored -> true);

        assertEquals(
                List.of(),
                fresh,
                "Когда все находки уже на доске, новых заданий нет — иначе доска заполняется дублями");
    }

    private static BlockCoord cell(int x) {
        return new BlockCoord(x, 64, 0);
    }
}
