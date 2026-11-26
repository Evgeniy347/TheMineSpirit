package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import org.junit.jupiter.api.Test;

class GraphBlockPathScenarioTests {
    @Test
    void path_BuildingBlocked_DoesNotBlockAllCandidates() {
        GraphWorld world = GraphWorld.settled().with("wheat_seeds", 8).with("dirt", 8);
        world.blocks(new GraphBlock("building:wheat_field", "-134,64,-467", new GraphBlock.Path("-134,64,-467")));

        GraphPlan.Task field = world.plan().task("building:wheat_field").orElseThrow();

        assertTrue(
                field.feasible(),
                "Отказ пути одного духа не запрещает постройку всем: доска сама проверит достижимость "
                        + "каждого кандидата. Иначе wheat_field остаётся заблокированным даже рядом со свободным "
                        + "духом. При смене path_BuildingBlocked_DoesNotBlockAllCandidates проверь GraphBlocks.satisfied "
                        + "и DispatchPick.");
        assertTrue(
                field.blocked().isEmpty(),
                "Снятая глобальная блокировка не оставляет причину: иначе граф продолжает скрывать работу "
                        + "от доски. При смене path_BuildingBlocked_DoesNotBlockAllCandidates проверь GraphReporter.why.");
    }
}
