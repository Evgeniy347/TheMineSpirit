package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class WarehouseGraphNoticeTests {
    @Test
    void plan_WarehousePlannedInTown_NoGraphTaskAndNoNotice() {
        GraphWorld world = GraphWorld.settled().built("warehouse", Built.PLANNED);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:warehouse").isEmpty(),
                "Склад ставит свой сторож по лоту storage: задание графа построило бы его мимо плана — "
                        + plan.task("building:warehouse"));
        assertTrue(
                plan.notices().stream().noneMatch(notice -> "warehouse".equals(notice.building())),
                "Лот склада есть в плане — просьба стратегу «её нет в плане» была бы ложью: " + plan.notices());
    }

    @Test
    void plan_WarehouseMissingAtStageTwo_StillAsksStrategist() {
        GraphWorld world = GraphWorld.settled().built("warehouse", Built.ABSENT);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Лота склада нет — просьба стратегу уместна: " + plan.notices());
        assertTrue(
                plan.task("building:warehouse").isEmpty(),
                "Склада нет в плане — задание не ставится без лота: " + plan.task("building:warehouse"));
    }
}
