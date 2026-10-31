package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.TreeChooser;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrchardSupplyLiveTests {
    private static final Path DIR = Path.of("src/test/resources/live/orchardtree");
    private static final Path BOARD = DIR.resolve("board.json");
    private static final Path FAILURE = DIR.resolve("failure.json");
    private static final Path PLANKS = DIR.resolve("task-graph_craft_oak_planks.json");

    @Test
    void liveFailure_NearestOakAt109_IsReachedWhenSearchExtendsInsideForceload() throws IOException {
        JsonNode failure = read(FAILURE);
        int limit = failure.get("limit").asInt();
        int nearest = failure.get("nearestTree").asInt();
        BlockCoord anchor = coord(failure.get("anchor"));
        JsonNode board = read(BOARD);

        JsonNode ticket = find(board, "graph:chop_oak_log");
        assertTrue(ticket != null, "На живой доске нет тикета graph:chop_oak_log");
        assertEquals("oak_log", ticket.get("supplyItem").asText(), "Живой тикет обязан заготавливать брёвна дуба");
        assertEquals("GATHER_MATERIALS", ticket.get("case").asText(), "Живой тикет обязан быть добычей материала");

        int plannedRadius = 0;
        for (ScriptCall call : calls(plan(ticket, anchor))) {
            if ("chop_tree".equals(call.script())) {
                plannedRadius = (Integer) call.params().get("radius");
            }
        }
        assertTrue(plannedRadius > 0, "В плане живой заготовки брёвен нет рубки деревьев: " + plan(ticket, anchor));
        assertEquals(limit, plannedRadius, "Живой провал снят при пределе 100: план обязан просить тот же радиус");
        assertTrue(nearest > limit, "Живой провал говорит о дубе дальше запрошенного радиуса");

        int extended = ChopTuning.extendedDistance(plannedRadius);
        assertTrue(
                extended >= nearest,
                "Дуб в " + nearest + " блоках не попадает в расширенный поиск " + extended
                        + ": «посадите саженцы» снова не станет задачей и сад останется 0 из 24");
        BlockCoord tree = new BlockCoord(anchor.x() + nearest, anchor.y(), anchor.z());
        Optional<BlockCoord> chosen =
                TreeChooser.near(anchor, extended, tree.x(), tree.y(), tree.z(), List.of(tree), Set.of());
        assertTrue(
                chosen.isPresent(),
                "Расширенный поиск не выбирает живой дуб в " + nearest + " блоках при пределе " + extended);
    }

    @Test
    void livePlanksCraft_FullNearestChest_DepositsBySpread() throws IOException {
        JsonNode ticket = read(PLANKS);
        BlockCoord anchor = coord(read(FAILURE).get("anchor"));
        String item = ticket.get("supplyItem").asText();

        List<ScriptCall> deposits = calls(plan(ticket, anchor)).stream()
                .filter(call -> "deposit".equals(call.script())
                        && item.equals(bare(call.params().get("item"))))
                .toList();
        assertFalse(deposits.isEmpty(), "План крафта досок не сдаёт досок на склад: " + plan(ticket, anchor));
        assertTrue(
                deposits.stream().anyMatch(call -> "auto".equals(call.params().get("container"))),
                "Сдача крафта идёт в один ближайший сундук: при полном соседе DepositSpread не раскладывает доски "
                        + "по остальным. Сдача: " + deposits);
    }

    private static TaskNode plan(JsonNode ticket, BlockCoord anchor) {
        return TicketPlans.plan(
                BoardCase.valueOf(ticket.get("case").asText()),
                ticket.get("level").asInt(),
                TicketTarget.at(anchor)
                        .withSupply(
                                ticket.get("supplyItem").asText(),
                                ticket.get("supplyCount").asInt()));
    }

    private static JsonNode find(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (id.equals(task.get("id").asText())) {
                return task;
            }
        }
        return null;
    }

    private static String bare(Object id) {
        String text = String.valueOf(id);
        return text.startsWith("minecraft:") ? text.substring("minecraft:".length()) : text;
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
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
            return;
        }
        throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается тестом");
    }

    private static JsonNode read(Path path) throws IOException {
        return new ObjectMapper().readTree(Files.readString(path, StandardCharsets.UTF_8));
    }
}
