package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.TreeChooser;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChopSearchLiveOrchardTests {
    private static final Path TICKET = Path.of("src/test/resources/live/task-build-orchard.json");
    private static final Path BOARD = Path.of("src/test/resources/live/board-build-orchard.json");
    private static final Path INCIDENT = Path.of("src/test/resources/live/observe10-orchard-incident.json");
    private static final String ORCHARD = "orchard_oak";

    @Test
    void liveTicket_OrchardPlan_KeepsChopRadiusAndSearchDoesNotCutItToThirtyTwo() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(read(TICKET));
        JsonNode board = new ObjectMapper().readTree(read(BOARD));
        JsonNode onBoard = find(board, ticket.get("id").asText());

        assertTrue(
                onBoard != null, "На живой доске нет тикета " + ticket.get("id").asText());
        assertEquals("BUILD_ORCHARD", ticket.get("case").asText(), "Живой тикет обязан быть постройкой сада");
        assertEquals(
                ticket.get("status").asText(),
                onBoard.get("status").asText(),
                "Тикет и доска разошлись по статусу: тест пошёл бы не тем путём, что в игре");
        assertFalse(ticket.get("done").asBoolean(), "Живой тикет сада провален: сад не построен");

        int plannedRadius = plannedChopRadius(ticket);
        assertEquals(
                BoardTuning.chopRadius(),
                plannedRadius,
                "План заготовки брёвен обязан просить полный радиус рубки из board.json, иначе поиск снова упрётся в 32");
        assertEquals(
                plannedRadius,
                ChopTuning.maxDistance(plannedRadius),
                "Поиск деревьев режет запрошенный радиус: в живой игре план просил "
                        + plannedRadius
                        + ", а дух искал только "
                        + ChopTuning.maxDistance(plannedRadius)
                        + " — так и появилось «в радиусе 32 закончились»");
    }

    @Test
    void liveFailure_NearestTreeAtThirtyThree_IsFoundWithinPlannedRadius() throws IOException {
        JsonNode incident = new ObjectMapper().readTree(read(INCIDENT));
        JsonNode failure = incident.get("failure");
        int oldLimit = failure.get("limit").asInt();
        int nearest = failure.get("nearestTree").asInt();

        assertTrue(nearest > oldLimit, "Живой провал говорит о дереве дальше старого предела 32");

        BlockCoord anchor = coord(failure.get("anchor"));
        BlockCoord tree = new BlockCoord(anchor.x() + nearest, anchor.y(), anchor.z());
        int plannedRadius = plannedChopRadius(new ObjectMapper().readTree(read(TICKET)));
        Optional<BlockCoord> chosen = TreeChooser.near(
                anchor, ChopTuning.maxDistance(plannedRadius), tree.x(), tree.y(), tree.z(), List.of(tree), Set.of());

        assertTrue(
                chosen.isPresent(),
                "Ближайшее живое дерево в " + nearest + " блоках от поселения не попадает в поиск: предел "
                        + ChopTuning.maxDistance(plannedRadius) + " вместо запланированных " + plannedRadius
                        + " — сад снова останется 0 из 24, хотя дерево достижимо");
    }

    @Test
    void liveTicket_OrchardPlan_CraftsTorchAndTreadsDirtPath() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(read(TICKET));
        List<ScriptCall> calls = calls(plan(ticket));

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "craft".equals(call.script())
                                && "minecraft:torch".equals(call.params().get("item"))),
                "Сад обязан крафтить факелы из угля и палки, а не добывать их блоком мира: " + calls);
        assertFalse(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("dirt_path")),
                "Дорожка сада не покупается и не добывается: она утаптывается лопатой по схеме: " + calls);
        assertTrue(
                calls.stream().anyMatch(call -> "build_schematic".equals(call.script())),
                "Сад строится по схеме: без build_schematic дорожка и факелы не встанут: " + calls);
    }

    private static int plannedChopRadius(JsonNode ticket) {
        TaskNode forest = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS,
                ticket.get("level").asInt(),
                TicketTarget.at(new BlockCoord(-171, 69, -489)).withSupply("oak_log", 8));
        ScriptCall chop = calls(forest).stream()
                .filter(call -> "chop_tree".equals(call.script()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В плане заготовки брёвен нет рубки дерева"));
        return (Integer) chop.params().get("radius");
    }

    private static TaskNode plan(JsonNode ticket) {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard())
                .withWork(new StageWork(site, List.of()))
                .withBuilding(ORCHARD);
        return TicketPlans.plan(
                BoardCase.valueOf(ticket.get("case").asText()),
                ticket.get("level").asInt(),
                target);
    }

    private static JsonNode find(JsonNode board, String id) {
        for (JsonNode task : board.get("tasks")) {
            if (id.equals(task.get("id").asText())) {
                return task;
            }
        }
        return null;
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

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
