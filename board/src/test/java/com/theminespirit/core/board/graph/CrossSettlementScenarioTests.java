package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossSettlementScenarioTests {
    @Test
    void fill_PitOnHouseLot_FillAndBuildStayPostedTogether() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64).with("dirt", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task backfill = task(plan, "event:backfill:backfill");
        GraphPlan.Task house = task(plan, "building:hut");

        assertTrue(
                backfill.feasible(),
                "Яма на участке дома засыпается:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + ids(plan));
        assertTrue(
                house.feasible(),
                "Стройка дома не исчезает из-за ямы на участке:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + ids(plan));
        assertTrue(
                scripts(BoardCase.FILL_CRATER).contains("fill"),
                "Засыпка выполняется скриптом fill, а не выравниванием руками:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + scripts(BoardCase.FILL_CRATER));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Дом строится схемой build_schematic:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + scripts(BoardCase.BUILD_HOUSE));
        assertTrue(
                plan.tasks().stream().noneMatch(task -> "dirt".equals(task.makes()) && task.feasible()),
                "Земли в сундуках хватает — граф не ставит новую добычу земли:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + ids(plan));
        GraphPlan flat = plan(
                world,
                facts(named(spirit("spirit-1")), List.of(), List.of(), GraphSiteFacts.Terrain.empty(), Map.of()));
        assertTrue(
                flat.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "После засыпки дом остаётся выполнимой работой:  Иначе тест fill_PitOnHouseLot_FillAndBuildStayPostedTogether перестанет ловить эту поломку. При смене fill_PitOnHouseLot_FillAndBuildStayPostedTogether проверь это утверждение."
                        + ids(flat));
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}
