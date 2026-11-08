package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityLaunchTests {
    private static final UUID SPIRIT = UUID.fromString("c48663b4-da59-43d9-9804-81a680580300");

    @Test
    void stuck_StuckTrappedOrInTrap_IsNotSentToActivity() {
        for (GraphFacts.Spirit fact :
                List.of(spirit(true, false, null), spirit(false, true, null), spirit(false, false, "яма"))) {
            assertTrue(
                    ActivityLaunch.stuck(facts(fact), SPIRIT),
                    "Застрявшего духа не отправляют в поход/патруль: он вернёт NOT_REACHED на каждой точке "
                            + "(observe25 B12): stuck=" + fact.stuck() + " trapped=" + fact.trapped() + " trap="
                            + fact.trap());
        }
    }

    @Test
    void stuck_HealthySpirit_GoesToActivity() {
        assertFalse(
                ActivityLaunch.stuck(facts(spirit(false, false, null)), SPIRIT),
                "Свободный дух идёт в поход: запрет только для застрявших");
        assertFalse(
                ActivityLaunch.stuck(GraphFacts.empty(), SPIRIT),
                "Дух без факта в графе не считается застрявшим: сведения просто не собраны");
    }

    private static GraphFacts facts(GraphFacts.Spirit spirit) {
        return GraphFacts.empty().withSpirits(Map.of(spirit.id(), spirit));
    }

    private static GraphFacts.Spirit spirit(boolean stuck, boolean trapped, String trap) {
        return new GraphFacts.Spirit(
                SPIRIT.toString(),
                new GraphFacts.Position(0, 64, 0),
                20,
                20,
                null,
                20,
                300,
                false,
                trapped,
                stuck,
                null,
                Map.of(),
                List.of(),
                List.of(),
                trap);
    }
}
