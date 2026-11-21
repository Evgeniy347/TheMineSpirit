package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LifeGraphPlannerTests {
    @Test
    void fragment_LifeThreat_LivesInOwnFileWithLifeFacts() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent life =
                graph.events().getOrDefault("life_threat", new GraphEvent("", List.of(), Map.of(), List.of()));

        assertEquals(
                "life_threat",
                life.name(),
                "Фрагмент life.json обязан добавить событие life_threat Иначе тест fragment_LifeThreat_LivesInOwnFileWithLifeFacts перестанет ловить эту поломку. При смене fragment_LifeThreat_LivesInOwnFileWithLifeFacts проверь это утверждение.");
        assertTrue(
                life.life(),
                "Событие жизни помечено фактами жизни: иначе потолок 100 не отличить от работы При смене fragment_LifeThreat_LivesInOwnFileWithLifeFacts проверь это утверждение.");
        assertTrue(
                life.tasks().stream()
                        .map(GraphEvent.Task::id)
                        .toList()
                        .containsAll(List.of("defend", "retreat", "arm", "heal", "eat", "sleep")),
                "В событии жизни есть пути защищаться, отходить, вооружаться, лечиться, есть и спать:  Иначе тест fragment_LifeThreat_LivesInOwnFileWithLifeFacts перестанет ловить эту поломку. При смене fragment_LifeThreat_LivesInOwnFileWithLifeFacts проверь это утверждение."
                        + life.tasks());
    }

    static GraphFacts danger(
            String spiritId, float health, float maxHealth, GraphFacts.TargetKind kind, int mobs, int distance) {
        List<GraphFacts.Threat> threats = new ArrayList<>();
        for (int index = 0; index < mobs; index++) {
            threats.add(new GraphFacts.Threat(
                    "threat-" + index,
                    "minecraft:zombie",
                    20,
                    20,
                    new GraphFacts.Position(index, 64, 0),
                    spiritId,
                    kind,
                    kind == GraphFacts.TargetKind.SPIRIT,
                    true,
                    null,
                    distance + index,
                    null,
                    0.2));
        }
        GraphFacts facts = GraphFacts.empty().withThreats(threats);
        if (kind == GraphFacts.TargetKind.SPIRIT) {
            facts = facts.withSpirits(Map.of(
                    spiritId,
                    new GraphFacts.Spirit(
                            spiritId,
                            new GraphFacts.Position(0, 64, 0),
                            health,
                            maxHealth,
                            new GraphFacts.Hurt(6, "minecraft:zombie", 5),
                            20,
                            300,
                            false,
                            false,
                            false,
                            null,
                            Map.of(),
                            List.of(),
                            List.of(),
                            null)));
        }
        return facts;
    }

    static GraphFacts healthy(String spiritId, float health, float maxHealth) {
        return GraphFacts.empty().withSpirits(Map.of(spiritId, spirit(spiritId, health, maxHealth, null)));
    }

    static GraphFacts.Spirit spirit(String spiritId, float health, float maxHealth, GraphFacts.@Nullable Hurt hurt) {
        return new GraphFacts.Spirit(
                spiritId,
                new GraphFacts.Position(0, 64, 0),
                health,
                maxHealth,
                hurt,
                20,
                300,
                false,
                false,
                false,
                null,
                Map.of(),
                List.of(),
                List.of(),
                null);
    }

    static GraphFacts.Threat threat(String id, String target, GraphFacts.TargetKind kind, int distance) {
        return new GraphFacts.Threat(
                id,
                "minecraft:zombie",
                20,
                20,
                new GraphFacts.Position(0, 64, distance),
                target,
                kind,
                kind == GraphFacts.TargetKind.SPIRIT,
                true,
                null,
                distance,
                null,
                0.2);
    }
}
