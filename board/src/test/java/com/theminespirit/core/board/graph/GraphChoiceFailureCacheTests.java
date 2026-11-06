package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphChoiceFailureCacheTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void failureBackoff_SpiritSuffixAndNodeTargets_AreAggregatedPerRun() {
        GraphWorld world = GraphWorld.settled();
        String key = "craft_jungle_planks@spirit-1";
        double calm = new GraphChoiceRules(GRAPH, world.state()).value(key, 1.0, false);
        double oneTarget = new GraphChoiceRules(
                        GRAPH, failed(world, "craft_jungle_planks", "1,64,0").state())
                .value(key, 1.0, false);
        double twoTargets = new GraphChoiceRules(
                        GRAPH,
                        failed(failed(world, "craft_jungle_planks", "1,64,0"), key, "2,64,0")
                                .state())
                .value(key, 1.0, false);

        assertEquals(
                calm,
                oneTarget,
                1e-9,
                "Одна недоступная цель узла не штрафует личный путь духа: узел ещё не доказан плохим");
        assertTrue(
                twoTargets < calm,
                "Провал узла и провал личного ключа — разные цели одного узла: агрегат плана складывает их, "
                        + "а не смотрит только на точный ключ: " + twoTargets + " против " + calm);
    }

    @Test
    void failureBackoff_SoftFailuresOnNodeAndSuffix_AreSummedInOneRun() {
        GraphWorld world = GraphWorld.settled();
        String key = "craft_jungle_planks@spirit-1";
        GraphWorld nodeSoft = failed(world, "craft_jungle_planks", "1,64,0", GraphFeedback.Cause.OTHER);
        double one = new GraphChoiceRules(GRAPH, nodeSoft.state()).value(key, 1.0, false);
        GraphWorld bothSoft = failed(nodeSoft, key, "2,64,0", GraphFeedback.Cause.OTHER);
        double two = new GraphChoiceRules(GRAPH, bothSoft.state()).value(key, 1.0, false);
        assertTrue(
                two < one,
                "Мягкий штраф узла и личного ключа складывается в агрегате прогона: прочие провалы всех целей "
                        + "узла дают один суммарный вес, а не последний найденный: " + two + " против " + one);
    }

    private static GraphWorld failed(GraphWorld world, String node, String target) {
        return failed(world, node, target, GraphFeedback.Cause.NO_PATH);
    }

    private static GraphWorld failed(GraphWorld world, String node, String target, GraphFeedback.Cause cause) {
        GraphFeedback known = world.facts.feedback();
        List<GraphFeedback.Failure> failures = new ArrayList<>(known.failures());
        failures.add(new GraphFeedback.Failure(node, target, 1, cause, 100));
        return world.facts(world.facts.withFeedback(new GraphFeedback(known.clock(), known.player(), failures)));
    }
}
