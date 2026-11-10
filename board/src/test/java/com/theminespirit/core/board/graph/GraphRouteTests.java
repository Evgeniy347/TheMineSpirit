package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphRouteTests {
    private static final SupplyGraph GRAPH = GraphWorld.GRAPH;

    @Test
    void routeBuildingsAndWays_GraphData_AreImplemented() {
        for (String building : List.of(
                "path_desert",
                "path_plains",
                "path_forest",
                "path_snowy",
                "route_desert",
                "route_snow",
                "route_dirt")) {
            assertTrue(GRAPH.implemented(building), "Маршрут к ресурсу реализован: " + building);
        }
        for (String way : List.of("silk_ice", "dig_snowball", "dig_sand_route", "dig_dirt_route")) {
            assertTrue(GRAPH.implemented(way), "Добыча с маршрута реализована: " + way);
        }
    }

    @Test
    void routeWays_AfterBuild_RequireRouteBuilding() {
        assertEquals(
                "route_desert",
                GRAPH.way("dig_sand_route").orElseThrow().building(),
                "Песок с маршрута требует постройки маршрута пустыни");
        assertEquals(
                "route_dirt",
                GRAPH.way("dig_dirt_route").orElseThrow().building(),
                "Земля с маршрута требует постройки маршрута равнины");
        assertEquals(
                "route_snow",
                GRAPH.way("dig_snowball").orElseThrow().building(),
                "Снежки с маршрута требуют постройки снежного маршрута");
        assertEquals(
                "route_snow",
                GRAPH.way("silk_ice").orElseThrow().building(),
                "Лёд с маршрута требует постройки снежного маршрута");
    }

    @Test
    void plan_BuiltDesertRoute_SandGatheredFromRoute() {
        GraphWorld world = GraphWorld.settled().with("sand", 0).built("route_desert", Built.BUILT);
        world.stage = 3;
        world.world.add("biome:desert");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("dig_sand_route").map(GraphPlan.Task::feasible).orElse(false),
                "Построенный маршрут к пустыне даёт песок с точки маршрута: " + plan.task("dig_sand_route"));
    }

    @Test
    void plan_NoRoute_SandRouteTaskNotFeasible() {
        GraphWorld world = GraphWorld.settled().with("sand", 0);
        world.stage = 3;
        world.world.remove("sand");
        world.world.add("biome:desert");

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("dig_sand_route").map(task -> !task.feasible()).orElse(true),
                "Без маршрута песок с точки не добывают: " + plan.task("dig_sand_route"));
    }

    @Test
    void removeRoute_Event_PostsCleanupWithoutMaterials() {
        GraphPlan plan =
                GraphWorld.settled().event(GraphWorldEvents.REMOVE_ROUTE).plan();
        GraphPlan.Task task = plan.task("event:remove_route:remove_route")
                .orElseThrow(() -> new AssertionError("Нет задания снять старую тропу: " + plan.tasks()));

        assertEquals(
                BoardCase.CLEAR_GROUNDS,
                task.boardCase(),
                "Старую тропу убирают расчисткой: ломать и возвращать блоки — узел мира, а не стройка");
        assertEquals(Map.of(), task.consumes(), "Снятию тропы не нужны материалы: оно только убирает проложенное");
        assertTrue(task.feasible(), "Задание снятия выполнимо, когда мир сообщил о старой тропе: " + plan.tasks());
    }
}
