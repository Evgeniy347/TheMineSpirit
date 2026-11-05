package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class LiveOakSupplyPlanTests {
    private static final Path DIR = Path.of("src/test/resources/live/woodreach");
    private static final BlockCoord WORK_YARD = new BlockCoord(-181, 69, -519);

    @Test
    void livePlanksTicket_StumpField_ChopsRealOakBeforeCrafting() throws IOException {
        JsonNode ticket = ticket("task-supply_gather_oak_planks.json");
        TicketTarget target = TicketTarget.at(WORK_YARD)
                .withSupply(
                        ticket.get("supplyItem").asText(),
                        ticket.get("supplyCount").asInt());
        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.GATHER_MATERIALS, target));

        ScriptCall chop = first(calls, call -> "chop_tree".equals(call.script()));
        assertTrue(
                chop != null,
                "Живой supply_gather:oak_planks снова идёт в сбор отдельных брёвен: " + calls
                        + ". На базе observe14 вокруг поселения одни пни, и сбор бьётся в (−144,72,−503) "
                        + "«нет пути к блоку» — дуб надо рубить целым деревом");
        assertEquals("oak", chop.params().get("species"), "Доски дуба обязаны рубить дуб: " + chop);
        assertEquals(
                BoardTuning.chopRadius(),
                chop.params().get("radius"),
                "Рубка дуба обязана искать настоящее дерево в том же радиусе, что и сад: " + chop);
        assertTrue(
                index(calls, call -> "acquire".equals(call.script()) && planks(call))
                        > index(calls, call -> "chop_tree".equals(call.script())),
                "Крафт досок идёт запасной веткой после рубки: при брёвнах в инвентаре acquire крафтит, "
                        + "а при настоящем дереве рядом сначала рубит его: " + calls);
    }

    private static boolean planks(ScriptCall call) {
        return String.valueOf(call.params().get("item")).endsWith("oak_planks");
    }

    @Test
    void liveStripTicket_NearestOakBeyondThirtyTwo_IsInRadius() throws IOException {
        JsonNode ticket = ticket("task-graph-strip_oak_log.json");
        TicketTarget target = TicketTarget.at(WORK_YARD)
                .withSupply(
                        ticket.get("supplyItem").asText(),
                        ticket.get("supplyCount").asInt());
        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.GATHER_MATERIALS, target));

        ScriptCall strip = first(calls, call -> "strip_log".equals(call.script()));
        assertTrue(strip != null, "Живой graph:strip_oak_log обязан обтёсывать брёвна: " + calls);
        assertEquals(
                BoardTuning.chopRadius(),
                strip.params().get("radius"),
                "Обтёсывание дуба ищет брёвна в радиусе 32, а ближайший настоящий дуб observe14 — в 83 блоках: "
                        + "живой провал «брёвна oak в радиусе 32 закончились» повторится: " + strip);
    }

    private static JsonNode ticket(String file) throws IOException {
        return new ObjectMapper().readTree(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8));
    }

    private static ScriptCall first(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (ScriptCall call : calls) {
            if (match.test(call)) {
                return call;
            }
        }
        return null;
    }

    private static int index(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (int i = 0; i < calls.size(); i++) {
            if (match.test(calls.get(i))) {
                return i;
            }
        }
        return -1;
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
}
