package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LifePlayerTests {
    @Test
    void plan_WeakPlayerWithThreat_ProtectsPlayerFromLifeNotLegacyEvent() {
        GraphFacts facts = danger(4, 4)
                .withFeedback(new GraphFeedback(
                        GraphFeedback.Clock.unknown(),
                        new GraphFeedback.Player(
                                new GraphFacts.Position(0, 64, 0), true, false, false, false, 4, 20, 4),
                        List.of()));
        GraphPlan plan = plan(facts);

        assertTrue(
                GraphDomainEvents.of(facts, 1).contains("life_threat_player"),
                "Слабый игрок рядом с угрозой обязан поднять событие жизни, а не легаси-событие more5");
        assertTrue(
                plan.priority("event:life_threat_player:protect_player") >= 80,
                "Защита слабого игрока идёт полосой жизни с высоким весом: " + plan.tasks());
        assertTrue(
                plan.priority("event:life_threat_player:protect_player")
                        > plan(danger(20, 18)).priority("event:life_threat_player:protect_player"),
                "Слабый игрок опаснее здорового при той же угрозе: " + plan.tasks());
        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.id().contains("more5_player_weak")),
                "Легаси-событие more5_player_weak сведено к жизни и больше не появляется: " + plan.tasks());
    }

    @Test
    void plan_HealthyPlayerWithThreat_DoesNotRaisePlayerLifeAnyway() {
        GraphFacts weak = danger(20, 18)
                .withFeedback(new GraphFeedback(
                        GraphFeedback.Clock.unknown(),
                        new GraphFeedback.Player(
                                new GraphFacts.Position(0, 64, 0), true, false, false, false, 20, 20, 18),
                        List.of()));

        assertEquals(
                plan(danger(20, 18)).priority("event:life_threat_player:protect_player"),
                plan(weak).priority("event:life_threat_player:protect_player"),
                "Здоровый и сытый игрок не добавляет опасности к той же угрозе");
    }

    private static GraphFacts danger(int health, int food) {
        return LifeGraphPlannerTests.danger("spirit-1", 20, 20, GraphFacts.TargetKind.PLAYER, 1, 6)
                .withFeedback(new GraphFeedback(
                        GraphFeedback.Clock.unknown(),
                        new GraphFeedback.Player(
                                new GraphFacts.Position(0, 64, 0), true, false, false, false, health, 20, food),
                        List.of()));
    }

    private static GraphPlan plan(GraphFacts facts) {
        return GraphWorld.settled().facts(facts).plan();
    }
}
