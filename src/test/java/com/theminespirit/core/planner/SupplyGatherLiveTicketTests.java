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
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SupplyGatherLiveTicketTests {
    private static final Path TICKET = Path.of("src/test/resources/live/supply_gather_oak_planks.json");
    private static final Path BOARD = Path.of("src/test/resources/live/board.json");
    private static final BlockCoord HOME = new BlockCoord(-179, 68, -515);

    @Test
    void liveTicket_SupplyGatherPlanks_TargetMatchesBoard() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(read(TICKET));
        JsonNode board = new ObjectMapper().readTree(read(BOARD));

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
    void liveTicket_Planks_ChopsTreesThenCraftsNeverGathersPlanks() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(read(TICKET));
        BoardCase boardCase = BoardCase.valueOf(ticket.get("case").asText());
        int level = ticket.get("level").asInt();
        String item = ticket.get("supplyItem").asText();
        int count = ticket.get("supplyCount").asInt();

        TaskNode plan = TicketPlans.plan(boardCase, level, TicketTarget.at(HOME).withSupply(item, count));
        List<ScriptCall> steps = calls(plan);

        int chop = index(
                steps,
                call -> "chop_tree".equals(call.script())
                        && "oak".equals(call.params().get("species")));
        int acquire = index(steps, call -> "acquire".equals(call.script()));
        assertTrue(
                chop >= 0 && acquire > chop,
                "Живой supply_gather:oak_planks не рубит дубы до крафта: " + steps
                        + ". Доски делаются из брёвен, брёвна берутся рубкой деревьев (chop_tree), "
                        + "а не поломкой размещённых брёвен/досок в постройках");

        ScriptCall acquireCall = steps.get(acquire);
        TaskNode subplan = acquireSubplan(acquireCall);
        List<ScriptCall> resolved = calls(subplan);

        assertFalse(
                resolved.stream().anyMatch(call -> gathers(call, item)),
                "supply_gather:oak_planks снова добывает доски блоком мира: " + resolved
                        + ". Доски — крафт, размещённые копии в постройках не запас");
        assertTrue(
                resolved.stream().anyMatch(call -> "craft".equals(call.script()) && crafts(call, item)),
                "Подплан acquire обязан крафтить доски: " + resolved);
    }

    private static int index(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (int i = 0; i < calls.size(); i++) {
            if (match.test(calls.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static TaskNode acquireSubplan(ScriptCall call) {
        ResourceKnowledge knowledge = new StubKnowledge(
                Set.of("oak_log"),
                Map.of("oak_planks", List.of(new Ingredient("oak_log", 1))),
                Map.of(),
                Map.of("oak_planks", 4));
        AcquireScript script = new AcquireScript(
                String.valueOf(call.params().get("item")),
                (Integer) call.params().get("count"),
                knowledge,
                new NoSupplies(),
                CoreScripts.defaultCatalog(),
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), ConditionRegistry.of(Map.of())));
        return script.buildSubplan()
                .orElseThrow(() -> new AssertionError("Подплан acquire пуст: цель уже в инвентаре?"));
    }

    private static boolean gathers(ScriptCall call, String item) {
        Object block = call.params().get("block");
        Object species = call.params().get("species");
        if ("gather".equals(call.script()) && block != null) {
            return bare(block.toString()).equals(bare(item));
        }
        return "chop_tree".equals(call.script()) && species != null && "oak".equals(species.toString());
    }

    private static boolean crafts(ScriptCall call, String item) {
        Object crafted = call.params().get("item");
        return crafted != null && bare(crafted.toString()).equals(bare(item));
    }

    private static String bare(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static final class NoSupplies implements AcquireSupplies {
        @Override
        public int inInventory(String item) {
            return 0;
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            return List.of();
        }

        @Override
        public Map<String, Integer> inventory() {
            return Map.of();
        }

        @Override
        public Set<String> nearbyStations() {
            return Set.of();
        }
    }
}
