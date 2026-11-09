package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.StockCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChestReachTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 1);

    @Test
    void reachableStock_UnreachableChest_CutsOnlyItsContents() {
        Map<String, Integer> stock = Map.of("minecraft:bread", 12, "minecraft:oak_log", 8);
        List<GraphFacts.Chest> chests =
                List.of(new GraphFacts.Chest(AT, Map.of("minecraft:bread", 10), 10, 27, GraphFacts.Reach.NO_PATH));

        assertEquals(
                Map.of("bread", 2, "oak_log", 8),
                ChestReach.reachableStock(stock, chests),
                "Еда в недостижимом сундуке не считается запасом: граф не объявит еду выполнимой, пока её не принесут");
    }

    @Test
    void reachableStock_UnknownReach_KeepsStock() {
        Map<String, Integer> stock = Map.of("minecraft:bread", 12);
        List<GraphFacts.Chest> chests =
                List.of(new GraphFacts.Chest(AT, Map.of("minecraft:bread", 10), 10, 27, GraphFacts.Reach.UNKNOWN));

        assertEquals(
                stock,
                ChestReach.reachableStock(stock, chests),
                "Неизвестная достижимость — не «нет пути»: такой запас пока считают доступным");
    }

    @Test
    void reachableStock_MoreUnreachableThanStock_LeavesZeroAndNoNegative() {
        Map<String, Integer> stock = Map.of("minecraft:bread", 3);

        assertEquals(
                Map.of(),
                ChestReach.reachableStock(
                        stock,
                        List.of(new GraphFacts.Chest(
                                AT, Map.of("minecraft:bread", 10), 10, 27, GraphFacts.Reach.NO_PATH))),
                "Списание недостижимого не уводит запас в минус: пустой запас — ноль, а не отрицательное число");
    }

    @Test
    void verdict_UnloadedChunk_IsUnknownNotNoPath() {
        assertEquals(
                GraphFacts.Reach.UNKNOWN,
                ChestReach.verdict(false, false),
                "Незагруженный чанк — «неизвестно», а не «пути нет»: архитектура запрещает считать неизвестное воздухом");
        assertEquals(GraphFacts.Reach.PATH, ChestReach.verdict(true, true), "Сундук в участке поселения достижим");
        assertEquals(
                GraphFacts.Reach.NO_PATH,
                ChestReach.verdict(true, false),
                "Сундук вне границы поселения недостижим тем же правилом, что и выдача еды");
    }

    @Test
    void eat_FoodOnlyInUnreachableChest_IsNotFeasibleAndHuntTakesOver() {
        GraphWorld world = GraphWorld.settled().event("hungry");
        GraphFacts facts = GraphFacts.empty()
                .withChests(List.of(
                        new GraphFacts.Chest(AT, Map.of("minecraft:bread", 8), 10, 27, GraphFacts.Reach.NO_PATH)));
        Map<String, Integer> available = ChestReach.reachableStock(Map.of("minecraft:bread", 8), facts.chests());
        GraphState state = new GraphState(
                GraphNames.stock(
                        available, StockCatalog.defaultTable().nutrition().keySet()),
                world.stage,
                world.kits,
                world.buildings,
                world.materials,
                world.storageFree,
                world.events,
                world.world,
                world.distances,
                world.stations,
                Map.of(),
                facts);

        GraphPlan plan = GraphPlanner.plan(world.graph, state);

        assertTrue(
                plan.task("event:hungry:eat").map(task -> !task.feasible()).orElse(true),
                "Еда в недостижимом сундуке не делает eat выполнимым: иначе граф ставит задачу, а выдача пишет "
                        + "только [food]-лог: " + plan.tasks());
        assertTrue(
                plan.task("event:hungry:hunt").filter(GraphPlan.Task::feasible).isPresent(),
                "Голодный дух получает доступный путь — охоту, а не ждёт недостижимый сундук: " + plan.tasks());
    }
}
