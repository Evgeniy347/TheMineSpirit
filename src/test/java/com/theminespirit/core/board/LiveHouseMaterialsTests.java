package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.planner.CraftedItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LiveHouseMaterialsTests {
    private static final Path DIR = Path.of("src/test/resources/live/observe18");

    @Test
    void liveHouseLevelTwo_BrickMaterials_AreCraftedNotGathered() throws IOException {
        JsonNode house = task("task-house_level_2.json");
        JsonNode missing = house.get("missing");
        assertEquals(111, missing.get("stone_bricks").asInt(), "Живой house_level_2 ждёт 111 кирпичей");
        assertEquals(1, missing.get("stone_brick_stairs").asInt(), "Живой house_level_2 ждёт кирпичную ступень");

        for (String item : new String[] {"stone_bricks", "stone_brick_stairs"}) {
            assertTrue(CraftedItems.isCrafted(item), "Живой '" + item + "' обязан считаться крафтом, а не лесом мира");
            assertInstanceOf(
                    GatherPolicy.Refused.class,
                    GatherPolicy.sourceCheck("minecraft:" + item),
                    "Сбор '" + item + "' обязан быть запрещён как источника: размещённые кирпичи в постройке — "
                            + "не запас, а провал «нет пути к блоку» в observe18 B5");
        }
    }

    @Test
    void liveSupplyTickets_StoneBricks_DoNotAskForWorldBlocks() throws IOException {
        JsonNode bricks = task("task-supply_gather_stone_bricks.json");
        JsonNode stairs = task("task-supply_gather_stone_brick_stairs.json");

        assertEquals("stone_bricks", bricks.get("supplyItem").asText(), "Живой тикет обязан снабжать кирпичами");
        assertEquals(111, bricks.get("supplyCount").asInt(), "Живой тикет обязан нести 111 кирпичей");
        assertEquals("stone_brick_stairs", stairs.get("supplyItem").asText(), "Живой тикет обязан снабжать ступенями");
        assertTrue(
                CraftedItems.isCrafted(bricks.get("supplyItem").asText())
                        && CraftedItems.isCrafted(stairs.get("supplyItem").asText()),
                "Оба живых тикета обязаны идти путём крафта: иначе дух ищет их блоки в мире");
    }

    private static JsonNode task(String file) throws IOException {
        return new ObjectMapper().readTree(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8));
    }
}
