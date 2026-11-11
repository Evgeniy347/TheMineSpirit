package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LifeGraphPlannerTests {
    private static final String DEFEND = "event:life_threat:defend";

    @Test
    void fragment_LifeThreat_LivesInOwnFileWithLifeFacts() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        GraphEvent life =
                graph.events().getOrDefault("life_threat", new GraphEvent("", List.of(), Map.of(), List.of()));

        assertEquals("life_threat", life.name(), "Фрагмент life.json обязан добавить событие life_threat");
        assertTrue(life.life(), "Событие жизни помечено фактами жизни: иначе потолок 100 не отличить от работы");
        assertTrue(
                life.tasks().stream()
                        .map(GraphEvent.Task::id)
                        .toList()
                        .containsAll(List.of("defend", "retreat", "arm", "heal", "eat", "sleep")),
                "В событии жизни есть пути защищаться, отходить, вооружаться, лечиться, есть и спать: " + life.tasks());
    }

    @Test
    void plan_WoundedSpiritTwoMobs_DefendAtCeilingAboveEveryWork() {
        GraphWorld world = GraphWorld.settled()
                .event("life_threat")
                .facts(danger("spirit-1", 4, 20, GraphFacts.TargetKind.SPIRIT, 2, 2));
        GraphPlan plan = world.plan();

        assertEquals(
                GraphWorld.GRAPH.maxPriority(),
                plan.priority(DEFEND),
                "Раненый дух и два сагренных моба дают защите потолок графа, а не константу: " + plan.tasks());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= GraphPriorities.work(GraphWorld.GRAPH)),
                "Полоса жизни не поднимает обычные работы выше потолка 99: " + plan.tasks());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !DEFEND.equals(task.id()))
                        .allMatch(task -> task.priority() <= plan.priority(DEFEND)),
                "Защита жизни выше остальных заданий: " + plan.tasks());
    }

    @Test
    void plan_ThreatToSpirit_ProtectSpiritAtCeiling() {
        GraphWorld world = GraphWorld.settled()
                .event("life_threat_spirit")
                .facts(danger("spirit-1", 4, 20, GraphFacts.TargetKind.SPIRIT, 2, 2));
        GraphPlan plan = world.plan();

        assertEquals(
                GraphWorld.GRAPH.maxPriority(),
                plan.priority("event:life_threat_spirit:protect_spirit"),
                "Есть угроза духу — защита духа на потолке: " + plan.tasks());
    }

    @Test
    void plan_ThreatToPlayer_ProtectPlayerAtCeiling() {
        GraphWorld world = GraphWorld.settled()
                .event("life_threat_player")
                .facts(danger("spirit-1", 20, 20, GraphFacts.TargetKind.PLAYER, 2, 2));
        GraphPlan plan = world.plan();

        assertEquals(
                GraphWorld.GRAPH.maxPriority(),
                plan.priority("event:life_threat_player:life_protect_player"),
                "Два моба у игрока — защита игрока на потолке: " + plan.tasks());
    }

    @Test
    void plan_HealthySpiritWithoutThreat_DoesNotRaiseLife() {
        GraphWorld world = GraphWorld.settled().event("life_threat").facts(healthy("spirit-1", 20, 20));
        GraphPlan plan = world.plan();

        assertFalse(plan.raised(DEFEND), "Здоровый дух без угроз не поднимает жизнь: " + plan.tasks());
    }

    @Test
    void risk_MobAggroedToOtherSpirit_RaisesOnlyItsTarget() {
        GraphFacts facts = GraphFacts.empty()
                .withSpirits(Map.of(
                        "spirit-a", spirit("spirit-a", 4, 20, null),
                        "spirit-b", spirit("spirit-b", 20, 20, null),
                        "spirit-c", spirit("spirit-c", 10, 20, null)))
                .withThreats(List.of(
                        threat("threat-b", "spirit-b", GraphFacts.TargetKind.SPIRIT, 2),
                        threat("threat-player", "player", GraphFacts.TargetKind.PLAYER, 5)));

        assertEquals(0, LifeThreats.risk(facts, "spirit-a"), "Моб на другого духа не поднимает риск раненого духа");
        assertTrue(LifeThreats.risk(facts, "spirit-b") > 0.2, "Моб, сагренный на духа, поднимает риск именно его");
        assertEquals(0, LifeThreats.risk(facts, "spirit-c"), "Здоровый дух вне агра не получает чужую угрозу");
    }

    @Test
    void plan_ManySpiritsAndMobs_LifeAtCeilingOnlyForAggroTargets() {
        Map<String, GraphFacts.Spirit> spirits = new LinkedHashMap<>();
        spirits.put("spirit-a", spirit("spirit-a", 4, 20, new GraphFacts.Hurt(6, "minecraft:zombie", 5)));
        spirits.put("spirit-b", spirit("spirit-b", 20, 20, null));
        spirits.put("spirit-c", spirit("spirit-c", 10, 20, null));
        GraphFacts facts = GraphFacts.empty()
                .withSpirits(spirits)
                .withThreats(List.of(
                        threat("threat-a1", "spirit-a", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("threat-a2", "spirit-a", GraphFacts.TargetKind.SPIRIT, 3),
                        threat("threat-c", "spirit-c", GraphFacts.TargetKind.SPIRIT, 8),
                        threat("threat-player", "player", GraphFacts.TargetKind.PLAYER, 5)));
        GraphWorld world = GraphWorld.settled()
                .event("life_threat")
                .event("life_threat_spirit")
                .event("life_threat_player")
                .event("hungry")
                .facts(facts);
        GraphPlan plan = world.plan();

        assertEquals(
                List.of(),
                CrossScene.ahead(plan, plan.task(DEFEND).orElseThrow()),
                "Раненый дух под двумя мобами — защита выбирается раньше всего, в том числе еды: " + plan.tasks());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= GraphPriorities.need(GraphWorld.GRAPH)),
                "Обычные задания и потребности не выше полосы потребности: " + plan.tasks());
        assertTrue(
                LifeThreats.risk(facts, "spirit-a") > LifeThreats.risk(facts, "spirit-c"),
                "Два моба у духа опаснее одного дальнего");
        assertEquals(0, LifeThreats.risk(facts, "spirit-b"), "Моб на другого духа не поднимает здорового духа");
    }

    @Test
    void plan_OrdinaryChain_StopsBelowLifeCeiling() {
        GraphWorld world = GraphWorld.settled()
                .event("hungry")
                .event("night")
                .event("expedition_ready")
                .event("more7_evening_return")
                .with("oak_log", 0)
                .with("oak_planks", 0)
                .with("stick", 0);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.tasks().stream().allMatch(task -> task.priority() <= GraphPriorities.need(GraphWorld.GRAPH)),
                "Цепочка бревно → доски → палки не выходит за полосу потребности: " + plan.tasks());
        assertTrue(
                GraphFlow.priority(1_000_000, GraphPriorities.work(GraphWorld.GRAPH))
                        < VitalNeeds.priority(0, List.of(0), GraphPriorities.need(GraphWorld.GRAPH)),
                "Даже бесконечный поток обычной работы ниже любой жизненной нужды");
    }

    @Property(tries = 200)
    void plan_AnyLifeFacts_KeepsEveryPriorityInBoardRange(
            @ForAll @IntRange(min = 0, max = 20) int health,
            @ForAll @IntRange(min = 0, max = 4) int mobs,
            @ForAll @IntRange(min = 0, max = 30) int distance,
            @ForAll @IntRange(min = 0, max = 240) int hurtAge) {
        GraphFacts.Hurt hurt =
                health < 20 && hurtAge <= GraphWorld.GRAPH.costs().life().damageWindowTicks()
                        ? new GraphFacts.Hurt(6, "minecraft:zombie", hurtAge)
                        : null;
        List<GraphFacts.Threat> threats = new ArrayList<>();
        for (int index = 0; index < mobs; index++) {
            threats.add(threat("threat-" + index, "spirit-1", GraphFacts.TargetKind.SPIRIT, distance + index));
        }
        GraphFacts facts = GraphFacts.empty()
                .withSpirits(Map.of(
                        "spirit-1",
                        spirit("spirit-1", health, 20, health < 20 ? hurt : null),
                        "spirit-2",
                        spirit("spirit-2", 20, 20, null)))
                .withThreats(threats);
        GraphPlan plan = GraphWorld.settled()
                .event("life_threat")
                .event("life_threat_spirit")
                .event("hungry")
                .facts(facts)
                .plan();

        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= GraphPriorities.need(GraphWorld.GRAPH)),
                "Свойство: обычные задания и потребности не выше полосы потребности, факты " + health + "/" + mobs + "/"
                        + distance + "/" + hurtAge + ": " + plan.tasks());
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
