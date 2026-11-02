package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.CraftedItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LiveWoodSourceTests {
    private static final Path DIR = Path.of("src/test/resources/live/observe19");
    private static final BlockCoord YARD = new BlockCoord(-171, 69, -489);

    @Test
    void liveJunglePlanks_FarLogs_AreOutsideSettlementGatherRadius() throws IOException {
        JsonNode failure = json("failures.json");
        BlockCoord spirit = coord(failure.get("spirit"));
        BlockCoord block = coord(failure.get("block"));
        int distance = (int) Math.ceil(Math.hypot(block.x() - spirit.x(), block.z() - spirit.z()));

        assertTrue(
                distance > ChopTuning.GATHER_RADIUS_MAX,
                "Живой провал обязан быть дальше радиуса поселения: " + distance + " против "
                        + ChopTuning.GATHER_RADIUS_MAX);
        assertEquals(
                ChopTuning.GATHER_RADIUS_MAX,
                ChopTuning.gatherDistance(distance),
                "Сбор обязан отсечь дальний блок и назвать причину, а не идти за 100+ блоков (observe19 E)");
    }

    @Test
    void liveJunglePlanksPlan_CraftsFromLogs_NoPlanksBlockGather() throws IOException {
        JsonNode ticket = json("task-graph_craft_jungle_planks.json");
        TicketTarget target = TicketTarget.at(YARD)
                .withSupply(
                        ticket.get("supplyItem").asText(),
                        ticket.get("supplyCount").asInt());
        String scripts = TicketPlans.plan(BoardCase.GATHER_MATERIALS, target).toString();

        assertTrue(
                scripts.contains("chop_tree") || scripts.contains("acquire"),
                "План обязан рубить/крафтить: " + scripts);
        assertFalse(
                scripts.contains("block=jungle_planks"),
                "Доски нельзя добывать блоком мира: живой провал искал их в блоке (-80,80,-423): " + scripts);
    }

    @Test
    void liveStrippedJungle_AxeComesFromCraft_NotBlockTrip() throws IOException {
        JsonNode ticket = json("task-graph_strip_jungle_log.json");
        TicketTarget target = TicketTarget.at(YARD)
                .withSupply(
                        ticket.get("supplyItem").asText(),
                        ticket.get("supplyCount").asInt());
        String scripts = TicketPlans.plan(BoardCase.GATHER_MATERIALS, target).toString();

        assertTrue(
                CraftedItems.isCrafted("wooden_axe"),
                "Инструмент — не-блочный предмет: только крафт, без похода за блоком (-273,112,-614)");
        assertTrue(scripts.contains("wooden_axe"), "План обтёсывания обязан крафтить топор: " + scripts);
        assertFalse(scripts.contains("block=wooden_axe"), "Топор не добывается блоком мира: " + scripts);
    }

    private static JsonNode json(String file) throws IOException {
        return new ObjectMapper().readTree(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8));
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
