package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardScenario;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageOneAcceptanceTests {
    private static final int GRAPH_LIMIT = 4000;
    private static final int BOARD_ROUNDS = 18;
    private static final String SAPLING = "minecraft:oak_sapling";

    @Test
    void simulate_ThreeSpiritsAndStock_ReachesStageTwoWithoutBlocked() {
        GraphWorld world = GraphWorld.empty()
                .tools("spirit-2", 1)
                .tools("spirit-3", 1)
                .with("oak_log", 64)
                .with("coal", 16);

        GraphSimulation.Outcome outcome = GraphSimulation.runUntil(world, GRAPH_LIMIT, 2);

        assertTrue(
                outcome.finished(),
                "Три духа с запасом проходят этап 1 без затора: " + outcome.stuck() + "; шагов " + outcome.steps());
        assertEquals(
                2,
                outcome.reachedStage(),
                "Расчистка, шахта, забор, сад, хижина, двор, свет и улицы закрывают этап 1 — поселение переходит "
                        + "на этап 2; шагов этапа 1 " + outcome.stageSteps().getOrDefault(1, 0));
    }

    @Test
    void board_ThreeSpiritsAndStock_StageOneReachesSecondStageWithoutBlocked() {
        BoardScenario scenario = BoardScenario.stageOne(1);

        for (int round = 0; round < BOARD_ROUNDS; round++) {
            scenario.round(round);
        }
        List<String> blocked = scenario.tickets().stream()
                .filter(ticket -> scenario.status(ticket.id()) == BoardStatus.BLOCKED)
                .map(ticket -> ticket.id() + " " + ticket.boardCase())
                .toList();

        assertEquals(
                List.of(),
                blocked,
                "Ни один шаг этапа 1 не уходит в BLOCKED: доска с запасом и тремя духами доводит этап до конца");
        assertEquals(
                2,
                scenario.settlementStage(),
                "Все шаги этапа 1 закрыты — поселение переходит на этап 2: "
                        + scenario.tickets().stream()
                                .map(ticket -> ticket.id() + "=" + scenario.status(ticket.id()))
                                .toList());
        assertTrue(
                scenario.anomalies().isEmpty(),
                "Выдача не отказывает годным духам и не оставляет задание без владельца: " + scenario.anomalies());
    }

    @Test
    void plan_FencePart_CraftsFromOwnWoodInsteadOfWithdraw() {
        TaskNode plan = plan(
                BoardCase.BUILD_FENCE, List.of(new SchemeBuild("fence_long", site().workYard(), BuildRotation.NONE)));
        List<ScriptCall> calls = calls(plan);

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "craft".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("_fence")),
                "Строитель ограды крафтит забор из своего дерева: забор в общем сундуке ждёт передачу от другого "
                        + "духа — часть ограды стоит, а этап 1 не закрывается (забор). План: " + calls);
        assertTrue(
                calls.stream()
                        .noneMatch(call -> "withdraw".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("_fence")),
                "Забор не забирают из сундука: доски лежат у другого духа, часть ждёт передачу. План: " + calls);
    }

    @Test
    void plan_Orchard_SaplingsComeFromFelledTreeNotGardenSpot() {
        TaskNode plan = plan(BoardCase.BUILD_ORCHARD, List.of());
        List<ScriptCall> calls = calls(plan);

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "chop_tree".equals(call.script())
                                && "oak".equals(call.params().get("species"))
                                && Boolean.FALSE.equals(call.params().get("replant"))),
                "Сад валит дуб без пересадки: саженцы даёт листва поваленного дерева, а не посаженный саженец "
                        + "в точке сада (N8). План: " + calls);
        assertTrue(
                calls.stream()
                        .noneMatch(call -> "acquire".equals(call.script())
                                && SAPLING.equals(call.params().get("item"))),
                "Сад не просит acquire саженца из мира: цель — дерево, а не центр участка (N8). План: " + calls);
    }

    private static TaskNode plan(BoardCase boardCase, List<SchemeBuild> builds) {
        StageSite site = site();
        StageWork work = new StageWork(site, List.of(), builds);
        return TicketPlans.plan(boardCase, 1, TicketTarget.at(site.workYard()).withWork(work));
    }

    private static StageSite site() {
        return TownFixtures.flatSite();
    }

    private static List<ScriptCall> calls(TaskNode plan) {
        List<ScriptCall> found = new ArrayList<>();
        collect(plan, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        switch (node) {
            case ScriptCall call -> found.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, found));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collect(step, found));
            default -> {}
        }
    }
}
