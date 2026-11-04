package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldReportTests {
    @Test
    void worldFacts_StraysTerrainAndDamage_AreCounted() {
        GraphFacts facts = new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(
                        "hut",
                        new GraphFacts.Building("hut", 1, 75, 5, Map.of("minecraft:dirt", 5), Map.of(), 0, null, false),
                        "warehouse",
                        new GraphFacts.Building(
                                "warehouse", 1, 90, 4, Map.of("minecraft:oak_planks", 4), Map.of(), 0, null, false),
                        "mine",
                        new GraphFacts.Building("mine", 1, 0, 9, Map.of(), Map.of(), 0, null, true)),
                List.of(new GraphSiteFacts.Stray(
                        "stray:fence", "fence", new GraphFacts.Position(1, 64, 1), null, Map.of("oak_fence", 3))),
                new GraphSiteFacts.Terrain(
                        2, 4, 3, 5, 1, Map.of("minecraft:dirt", 2), Map.of("minecraft:dirt", 2), Map.of(), false, 9),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()));

        WorldReport report = WorldReport.of(facts);

        assertEquals(3, report.strayBlocks(), "лишних блоков — сумма по кускам");
        assertEquals(1, report.strayGroups(), "кусков лишнего — по связным находкам");
        assertEquals(2, report.pits(), "ямы берутся из рельефа");
        assertEquals(2, report.mounds(), "насыпи — из состава срезки");
        assertEquals(9, report.natural(), "природный склон отдаётся отдельно и не считается работой");
        assertEquals(4, report.hanging(), "висящее берётся из рельефа");
        assertEquals(2, report.damaged(), "повреждёнными считаются известные постройки с уроном, а не неизвестные");
        assertTrue(!report.unknown(), "загруженный мир не помечается неизвестным");
        assertEquals(
                List.of("hut", "warehouse"),
                report.buildings().stream().map(WorldReport.Damaged::id).toList(),
                "повреждённые постройки называются по id и идут по алфавиту: наблюдатель видит, что чинить");
    }

    @Test
    void of_UnknownTerrain_IsReported() {
        GraphFacts facts = GraphFacts.empty()
                .withWorld(new GraphWorldFacts(
                        List.of(),
                        new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), true),
                        Map.of()));

        assertTrue(WorldReport.of(facts).unknown(), "незагруженные чанки видны наблюдателю как неизвестность");
    }
}
