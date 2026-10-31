package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupplyGatherLiveTicketTests {
    private static final Path TICKET = Path.of("src/test/resources/live/observe12-task-supply-gather-oak-planks.json");
    private static final Path BOARD = Path.of("src/test/resources/live/observe12-planks6-board.json");
    private static final BlockCoord HOME = new BlockCoord(-179, 68, -515);
    private static final int LUMBER_PER_TREE = 4;
    private static final int LOGS_IN_HAND = 37;
    private static final int TICKS = 100;

    @Test
    void liveTicket_SupplyGatherPlanks_TargetMatchesBoard() throws IOException {
        JsonNode ticket = readTicket();
        JsonNode board = readBoard();

        JsonNode onBoard = null;
        for (JsonNode task : board.get("tasks")) {
            if (ticket.get("id").asText().equals(task.get("id").asText())) {
                onBoard = task;
            }
        }

        assertTrue(
                onBoard != null,
                "Доска из живого мира не содержит живого тикета "
                        + ticket.get("id").asText());
        assertEquals("GATHER_MATERIALS", ticket.get("case").asText(), "Живой тикет обязан быть добычей материалов");
        assertEquals("oak_planks", ticket.get("supplyItem").asText(), "Живой тикет обязан снабжать досками");
        assertEquals(
                ticket.get("supplyItem").asText(),
                onBoard.get("supplyItem").asText(),
                "Тикет и доска разошлись по предмету: тест пошёл бы не тем путём, что в игре");
    }

    @Test
    void liveTicket_LogsInHand_CraftsWhenTreesAreGone() throws IOException {
        JsonNode ticket = readTicket();
        BoardCase boardCase = BoardCase.valueOf(ticket.get("case").asText());
        int level = ticket.get("level").asInt();
        String item = ticket.get("supplyItem").asText();
        int count = ticket.get("supplyCount").asInt();

        TaskNode plan = TicketPlans.plan(boardCase, level, TicketTarget.at(HOME).withSupply(item, count));
        List<String> ran = new ArrayList<>();
        Map<String, Integer> inventory = new HashMap<>();
        inventory.put("oak_log", LOGS_IN_HAND);
        AtomicReference<ScriptRegistry> registry = new AtomicReference<>();
        ScriptRegistry scripts = registry(ran, inventory, registry);
        registry.set(scripts);

        TaskRunner runner = TaskRunner.start(plan, scripts, ConditionRegistry.of(Map.of()));
        RunStatus status = RunStatus.RUNNING;
        for (int tick = 0; tick < TICKS && status == RunStatus.RUNNING; tick++) {
            status = runner.tick();
        }

        assertTrue(
                ran.contains("craft"),
                "В живом supply_gather:oak_planks шаг после рубки — крафт досок из брёвен в инвентаре — не "
                        + "выполняется: " + ran
                        + ". Рубка перед крафтом падала «деревья oak в радиусе 100 закончились» и валила всё "
                        + "задание, хотя 37 брёвен уже лежат в инвентаре (observe12). План: " + plan);
        assertFalse(ran.contains("gather_planks"), "Доски снова добываются блоком мира: " + ran);
        assertEquals(RunStatus.SUCCEEDED, status, "План досок обязан дойти до сдачи: " + ran);
    }

    private static ScriptRegistry registry(
            List<String> ran, Map<String, Integer> inventory, AtomicReference<ScriptRegistry> self) {
        Map<String, ScriptFactory> factories = new HashMap<>();
        factories.put("goto", params -> () -> ScriptStatus.SUCCEEDED);
        factories.put("wait", params -> () -> ScriptStatus.SUCCEEDED);
        factories.put("deposit", params -> () -> ScriptStatus.SUCCEEDED);
        factories.put("store", params -> () -> ScriptStatus.SUCCEEDED);
        factories.put("take_from", params -> () -> ScriptStatus.SUCCEEDED);
        factories.put("chop_tree", params -> {
            ran.add("chop_tree");
            return () -> ScriptStatus.FAILED;
        });
        factories.put("gather", params -> {
            String block = String.valueOf(params.get("block"));
            ran.add(block.endsWith("_planks") ? "gather_planks" : "gather");
            return () -> ScriptStatus.SUCCEEDED;
        });
        factories.put("craft", params -> {
            ran.add("craft");
            return () -> ScriptStatus.SUCCEEDED;
        });
        factories.put("acquire", params -> {
            String item = String.valueOf(params.get("item"));
            int count = ((Number) params.get("count")).intValue();
            return new AcquireScript(
                    item,
                    count,
                    knowledge(item),
                    new Supplies(inventory),
                    CoreScripts.defaultCatalog(),
                    ConditionCatalog.empty(),
                    new AcquireScript.Registries(self::get, ConditionRegistry.of(Map.of())));
        });
        return ScriptRegistry.of(factories);
    }

    private static ResourceKnowledge knowledge(String goal) {
        Set<String> gatherable = Set.of("oak_log");
        Map<String, List<Ingredient>> crafts =
                goal.endsWith("_planks") ? Map.of(goal, List.of(new Ingredient("oak_log", 1))) : Map.of();
        Map<String, Integer> outputs = goal.endsWith("_planks") ? Map.of(goal, LUMBER_PER_TREE) : Map.of();
        return new StubKnowledge(gatherable, crafts, Map.of(), outputs);
    }

    private static JsonNode readTicket() throws IOException {
        return new ObjectMapper().readTree(read(TICKET));
    }

    private static JsonNode readBoard() throws IOException {
        return new ObjectMapper().readTree(read(BOARD));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static final class Supplies implements AcquireSupplies {
        private final Map<String, Integer> inventory;

        Supplies(Map<String, Integer> inventory) {
            this.inventory = inventory;
        }

        @Override
        public int inInventory(String item) {
            return inventory.getOrDefault(bare(item), 0);
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            return List.of();
        }

        @Override
        public Map<String, Integer> inventory() {
            return inventory;
        }

        @Override
        public Set<String> nearbyStations() {
            return Set.of();
        }

        private static String bare(String item) {
            return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        }
    }
}
