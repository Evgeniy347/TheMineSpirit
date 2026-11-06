package com.theminespirit.core.planner;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LiveJungleGatherTicketTests {
    private static final Path JUNGLE_TICKET =
            Path.of("src/test/resources/live/observe11-task-graph-make-jungle-log-2.json");
    private static final Path OAK_TICKET =
            Path.of("src/test/resources/live/observe11-task-supply-gather-oak-planks.json");
    private static final Path BOARD = Path.of("src/test/resources/live/observe11-n6-board.json");
    private static final Path WORLD = Path.of("src/test/resources/live/observe11-jungle-tree.json");
    private static final BlockCoord WORK_YARD = new BlockCoord(-181, 69, -519);

    @Test
    void liveJungleTicket_TreeSearch_IsNotCutToThirtyTwo() throws IOException {
        JsonNode ticket = ticket(JUNGLE_TICKET);
        assertOnBoard(ticket);
        assertEquals("jungle_log", ticket.get("supplyItem").asText(), "Живой тикет обязан снабжать брёвнами jungle");
        assertEquals("GATHER_MATERIALS", ticket.get("case").asText(), "Живой тикет обязан добывать материалы");

        List<ScriptCall> steps = calls(livePlan(ticket));
        ScriptCall chop = first(
                steps,
                call -> "chop_tree".equals(call.script())
                        && "jungle".equals(call.params().get("species")));
        assertTrue(chop != null, "Живой graph:make:jungle_log обязан рубить дерево породы jungle: " + steps);
        assertEquals(
                BoardTuning.chopRadius(),
                chop.params().get("radius"),
                "Живой graph:make:jungle_log обязан просить полный радиус рубки доски, иначе поиск снова упрётся "
                        + "в 32 блока: " + steps);
        assertEquals(
                ChopTuning.maxDistance((Integer) chop.params().get("radius")),
                chop.params().get("radius"),
                "Поиск бревна срезан ниже запрошенного радиуса: в живой игре дух искал "
                        + ChopTuning.maxDistance((Integer) chop.params().get("radius"))
                        + " вместо "
                        + chop.params().get("radius"));
    }

    @Test
    void liveJungleTree_FiftyFiveBlocksAway_IsFoundOnlyByWideSearch() throws IOException {
        JsonNode world = new ObjectMapper().readTree(read(WORLD));
        JsonNode base = world.get("tree").get("base");
        BlockCoord trunk = new BlockCoord(
                base.get(0).asInt(), base.get(1).asInt(), base.get(2).asInt());
        BlockCoord anchor = new BlockCoord(-181, 69, -513);
        int radius = BoardTuning.chopRadius();

        assertTrue(
                TreeChooser.near(anchor, radius, trunk.x(), trunk.y(), trunk.z(), List.of(trunk), Set.of())
                        .isPresent(),
                "Живое дерево jungle в 55 блоках от поселения не попадает в поиск радиуса " + radius
                        + ": дух снова останется без брёвен");
        assertTrue(
                TreeChooser.near(anchor, 32, trunk.x(), trunk.y(), trunk.z(), List.of(trunk), Set.of())
                        .isEmpty(),
                "Живое дерево в 55 блоках не должно находиться старым пределом 32: именно этот предел дал провал "
                        + "observe11, и его снимает поиск до 100");
    }

    @Test
    void liveOakPlanksTicket_MissingTrees_FallsBackToCrafting() throws IOException {
        JsonNode ticket = ticket(OAK_TICKET);
        assertOnBoard(ticket);
        assertEquals("oak_planks", ticket.get("supplyItem").asText(), "Живой тикет обязан снабжать дубовыми досками");

        TaskNode plan = livePlan(ticket);
        List<ScriptCall> steps = calls(plan);

        assertTrue(
                steps.stream().anyMatch(call -> "acquire".equals(call.script())),
                "План обязан крафтить доски через acquire: " + steps);
        ScriptCall chop = first(steps, call -> "chop_tree".equals(call.script()));
        assertFalse(
                chop == null,
                "План досок снова идёт в сбор пней без рубки: после observe14 настоящий дуб ищется только "
                        + "рубкой целого дерева, иначе задание бьётся в (−144,72,−503). План: " + steps);
        assertTrue(
                fallbackContains(plan, "chop_tree", "acquire"),
                "Рубка обязана быть запасной веткой перед acquire: при отсутствии деревьев провал рубки "
                        + "не должен валить крафт из брёвен в инвентаре (observe12 N18). План: " + steps);
    }

    private static boolean fallbackContains(TaskNode node, String first, String second) {
        if (node instanceof FallbackNode fallback) {
            List<String> scripts = fallback.steps().stream()
                    .flatMap(step -> calls(step).stream())
                    .map(ScriptCall::script)
                    .toList();
            return scripts.contains(first) && scripts.contains(second);
        }
        if (node instanceof SequenceNode sequence) {
            return sequence.steps().stream().anyMatch(step -> fallbackContains(step, first, second));
        }
        return false;
    }

    private static TaskNode livePlan(JsonNode ticket) {
        return TicketPlans.plan(
                BoardCase.valueOf(ticket.get("case").asText()),
                ticket.get("level").asInt(),
                TicketTarget.at(WORK_YARD)
                        .withSupply(
                                ticket.get("supplyItem").asText(),
                                ticket.get("supplyCount").asInt()));
    }

    private static void assertOnBoard(JsonNode ticket) throws IOException {
        JsonNode board = new ObjectMapper().readTree(read(BOARD));
        JsonNode onBoard = null;
        for (JsonNode task : board.get("tasks")) {
            if (ticket.get("id").asText().equals(task.get("id").asText())) {
                onBoard = task;
            }
        }
        assertTrue(
                onBoard != null,
                "Доска из живого мира не содержит тикета " + ticket.get("id").asText());
        assertEquals(
                ticket.get("status").asText(),
                onBoard.get("status").asText(),
                "Тикет и доска разошлись по статусу: тест пошёл бы не тем путём, что в игре");
        assertEquals(
                ticket.get("supplyCount").asInt(),
                onBoard.get("supplyCount").asInt(),
                "Тикет и доска разошлись по количеству: тест проверял бы не тот запас");
    }

    private static ScriptCall first(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (ScriptCall call : calls) {
            if (match.test(call)) {
                return call;
            }
        }
        return null;
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
        } else if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        } else {
            throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается тестом");
        }
    }

    private static JsonNode ticket(Path path) throws IOException {
        return new ObjectMapper().readTree(read(path));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
