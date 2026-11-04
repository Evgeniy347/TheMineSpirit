package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphEvent;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.MoreActions;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class GraphMorePostTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final String[] FRAGMENTS = {"more5_", "more6_", "more7_"};

    @Test
    void implementedWaysAndEvents_FragmentLoaded_HaveActionHandlers() {
        Set<String> expected = new TreeSet<>();
        for (SupplyGraph.Way way : GRAPH.ways()) {
            if (fragment(way.id()) && GRAPH.implemented(way.id())) {
                expected.add(way.id());
            }
        }
        for (GraphEvent event : GRAPH.events().values()) {
            if (!fragment(event.name())) {
                continue;
            }
            for (GraphEvent.Task task : event.tasks()) {
                if (task.implemented()) {
                    expected.add(task.id());
                }
            }
        }

        assertEquals(
                expected,
                new TreeSet<>(MoreActions.cases().keySet()),
                "Каждый реализованный путь/событие more5/6/7 обязан иметь действие, и наоборот: иначе граф "
                        + "выбирает работу, которую некому исполнить");
    }

    @Test
    void actionBoardCases_PlannedThroughBoard_HaveValidCatalogPlans() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 64, -5))
                .withSupply("oak_log", 8)
                .withCount(1)
                .withBuilding("player:Steve");
        for (Map.Entry<String, BoardCase> action : MoreActions.cases().entrySet()) {
            TaskNode plan = MoreActivityPlans.handles(action.getValue())
                    ? MoreActivityPlans.plan(action.getValue(), target)
                    : TicketPlans.plan(action.getValue(), target);
            TaskNode validated = PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
            List<String> scripts = scripts(plan);
            assertFalse(scripts.isEmpty(), "У действия " + action.getKey() + " нет ни одного шага плана");
            assertEquals(
                    scripts,
                    scripts(validated),
                    "План действия " + action.getKey() + " обязан проходить каталог скриптов теми же шагами: "
                            + "иначе доска выдаст духу задание, которое валидатор отвергнет");
        }
    }

    @Test
    void bridgeAction_PlanBuilt_UsesExistingBridgeScript() {
        TaskNode plan = MoreActivityPlans.plan(
                BoardCase.ACTIVITY_BUILD,
                TicketTarget.at(new BlockCoord(10, 64, -5))
                        .withSupply("oak_planks", 8)
                        .withBuilding("bridge"));

        assertTrue(
                scripts(plan).contains("bridge"),
                "Мост чинится существующим скриптом bridge, а не новым проходом: " + scripts(plan));
    }

    @Test
    void eventTicket_KeyDerived_MatchesFragmentAction() {
        GraphPlan.Task task = new GraphPlan.Task(
                "event:more5_fuel_low:more5_gather_fuel",
                BoardCase.GATHER_MATERIALS,
                "more5:fuel_ready",
                Optional.empty(),
                1,
                Map.of(),
                true,
                false,
                true,
                100);
        GraphTickets.Ticket ticket = new GraphTickets.Ticket(
                "graph:event:more5_fuel_low:more5_gather_fuel", GraphTickets.Kind.EVENT, "more5:fuel_ready", task, 1);

        assertEquals("more5_gather_fuel", MoreActions.key(ticket), "Событие области ищет действие по id задачи");
        assertTrue(MoreActions.handles(ticket), "Событие области обязано попасть в свой обработчик");
    }

    @Test
    void wayTicket_KeyDerived_MatchesFragmentAction() {
        GraphPlan.Task task = new GraphPlan.Task(
                "more5_gather_fuel",
                BoardCase.GATHER_MATERIALS,
                "more5:fuel_ready",
                Optional.empty(),
                1,
                Map.of(),
                true,
                false,
                true,
                100);
        GraphTickets.Ticket ticket =
                new GraphTickets.Ticket("graph:more5_gather_fuel", GraphTickets.Kind.WAY, "more5_gather_fuel", task, 1);

        assertEquals("more5_gather_fuel", MoreActions.key(ticket), "Путь области ищет действие по своему id");
    }

    private static boolean fragment(String id) {
        for (String prefix : FRAGMENTS) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> scripts(TaskNode root) {
        List<String> scripts = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> scripts.add(call.script());
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
            }
        }
        return scripts;
    }
}
