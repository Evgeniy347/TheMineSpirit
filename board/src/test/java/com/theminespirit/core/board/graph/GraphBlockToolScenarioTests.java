package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class GraphBlockToolScenarioTests {
    @Test
    void tool_BuildingBlocked_AnySpiritToolClears() {
        GraphWorld world = GraphWorld.settled().with("wheat_seeds", 8).with("dirt", 8);
        world.kits.put("spirit-1", new HashMap<>());
        world.tools("spirit-2", 1);
        world.blocks(new GraphBlock("building:wheat_field", "", new GraphBlock.Tool("shovel", 0, "spirit-1")));

        GraphPlan.Task field = world.plan().task("building:wheat_field").orElseThrow();

        assertTrue(
                field.feasible(),
                "Инструмент у любого духа снимает старую блокировку постройки: иначе провал spirit-1 "
                        + "навсегда запрещает wheat_field даже после выдачи лопаты spirit-2. При смене "
                        + "tool_BuildingBlocked_AnySpiritToolClears проверь GraphBlocks.satisfied и живое поле.");
        assertTrue(
                field.blocked().isEmpty(),
                "Снятая блокировка не оставляет причину: иначе отчёт графа продолжает показывать ложную "
                        + "нехватку. При смене tool_BuildingBlocked_AnySpiritToolClears проверь GraphReporter.why.");
    }
}
