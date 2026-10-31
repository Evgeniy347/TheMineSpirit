package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.forestry.ChopTuning;
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
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LiveJungleGatherTicketTests {
    private static final Path JUNGLE_TICKET =
            Path.of("src/test/resources/live/observe11-task-graph-make-jungle-log-2.json");
    private static final Path OAK_TICKET =
            Path.of("src/test/resources/live/observe11-task-supply-gather-oak-planks.json");
    private static final Path BOARD = Path.of("src/test/resources/live/observe11-board.json");
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
    void liveOakPlanksTicket_WideTreeSearch_ChopsThenCrafts() throws IOException {
        JsonNode ticket = ticket(OAK_TICKET);
        assertOnBoard(ticket);
        assertEquals("oak_planks", ticket.get("supplyItem").asText(), "Живой тикет обязан снабжать дубовыми досками");

        List<ScriptCall> steps = calls(livePlan(ticket));
        ScriptCall chop = first(
                steps,
                call -> "chop_tree".equals(call.script())
                        && "oak".equals(call.params().get("species")));
        assertTrue(chop != null, "Живой supply_gather:oak_planks обязан рубить дубы: " + steps);
        assertEquals(
                BoardTuning.chopRadius(),
                chop.params().get("radius"),
                "Живой supply_gather:oak_planks обязан просить полный радиус рубки доски: " + steps);
        assertEquals(
                ChopTuning.maxDistance((Integer) chop.params().get("radius")),
                chop.params().get("radius"),
                "Рубка дубов снова упирается в предел 32: живой провал «ближайшее дерево 33» повторится");
        assertTrue(
                steps.stream().anyMatch(call -> "acquire".equals(call.script())),
                "После рубки план обязан крафтить доски через acquire: " + steps);
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
