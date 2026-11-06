package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphFactInputsTests {
    @Test
    void chestFacts_ContentsAndFreeSlots_AreCounted() {
        ContainerSnapshot snapshot = new ContainerSnapshot(
                new BlockCoord(1, 64, 2),
                Arrays.asList(new Stack("minecraft:oak_log", 0, 5, 64), null, new Stack("minecraft:coal", 0, 2, 64)));

        List<GraphFacts.Chest> chests = GraphFactInputs.chestFacts(List.of(snapshot));

        assertEquals(1, chests.size(), "Один открытый сундук даёт один факт");
        GraphFacts.Chest chest = chests.get(0);
        assertEquals(new GraphFacts.Position(1, 64, 2), chest.position(), "Позиция сундука переносится");
        assertEquals(
                Map.of("minecraft:oak_log", 5, "minecraft:coal", 2),
                chest.contents(),
                "Содержимое сундука складывается по предметам");
        assertEquals(1, chest.freeSlots(), "Свободный слот считается по null-слотам");
        assertEquals(3, chest.totalSlots(), "Общее число слотов считается по размеру контейнера");
    }

    @Test
    void resourceFacts_NearestTag_KeepsDistanceAndUnknownReach() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphSurroundings.Around around = new GraphSurroundings.Around(
                Set.of("tree:oak"),
                Set.of(),
                Map.of(),
                Optional.empty(),
                Map.of("tree:oak", new BlockCoord(5, 64, 6)),
                Map.of("tree:oak", 4));

        List<GraphFacts.Resource> resources = GraphFactInputs.resourceFacts(around, graph);

        assertEquals(1, resources.size(), "Один тег с координатой даёт один факт ресурса");
        GraphFacts.Resource resource = resources.get(0);
        assertEquals("tree:oak", resource.tag(), "Тег ресурса переносится");
        assertEquals(new GraphFacts.Position(5, 64, 6), resource.position(), "Координата ресурса переносится");
        assertEquals(4, resource.distance(), "Расстояние до ресурса переносится в блоках");
        assertEquals(GraphFacts.Reach.UNKNOWN, resource.reach(), "Достижимость пока неизвестна модели");
        assertEquals(
                graph.costs().travel(4),
                resource.travelTicks(),
                "Время дороги считается теми же costs, что и в планировщике");
    }
}
