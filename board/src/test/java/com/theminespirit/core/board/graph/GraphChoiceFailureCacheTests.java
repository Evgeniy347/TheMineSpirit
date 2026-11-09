package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.GraphBlock.Need;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
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
    void failureBackoff_UnknownNeed_DoesNotChangeWeight() {
        GraphWorld world = GraphWorld.settled();
        String key = "craft_jungle_planks@spirit-1";
        double calm = new GraphChoiceRules(GRAPH, world.state()).value(key, 1.0, false);
        GraphWorld nodeUnknown = failed(world, "craft_jungle_planks", "1,64,0", null);
        double one = new GraphChoiceRules(GRAPH, nodeUnknown.state()).value(key, 1.0, false);
        GraphWorld bothUnknown = failed(nodeUnknown, key, "2,64,0", null);
        double two = new GraphChoiceRules(GRAPH, bothUnknown.state()).value(key, 1.0, false);
        assertEquals(calm, one, 1e-9, "Провал без нужды не штрафует узел");
        assertEquals(calm, two, 1e-9, "Провал без нужды не штрафует и личный ключ");
    }

    private static GraphWorld failed(GraphWorld world, String node, String target) {
        return failed(world, node, target, new GraphBlock.Path(target));
    }

    private static GraphWorld failed(GraphWorld world, String node, String target, @Nullable Need cause) {
        GraphFeedback known = world.facts.feedback();
        List<GraphFeedback.Failure> failures = new ArrayList<>(known.failures());
        failures.add(new GraphFeedback.Failure(node, target, 1, cause, 100));
        return world.facts(world.facts.withFeedback(new GraphFeedback(known.clock(), known.player(), failures)));
    }
}
