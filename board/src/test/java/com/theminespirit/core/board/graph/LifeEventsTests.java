package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifeEventsTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 2);

    @Test
    void events_NoDanger_KeepOnlySuppliedEvents() {
        Set<String> events = LifeEvents.events(Set.of("night"), List.of(), List.of());

        assertEquals(Set.of("night"), events, "Без угроз и бед события округи остаются как есть");
    }

    @Test
    void events_ThreatToSpirit_AddsLifeThreatAndProtectSpirit() {
        Set<String> events =
                LifeEvents.events(Set.of(), List.of(threat(GraphFacts.TargetKind.SPIRIT, true)), List.of());

        assertTrue(events.contains("life_threat"), "Угроза рядом — событие жизни: " + events);
        assertTrue(events.contains("life_threat_spirit"), "Враг целится в духа — связь «защитить духа»: " + events);
        assertFalse(events.contains("life_threat_player"), "Игрок не цель — защита игрока не поднимается: " + events);
    }

    @Test
    void events_ThreatToPlayer_AddsProtectPlayer() {
        Set<String> events =
                LifeEvents.events(Set.of(), List.of(threat(GraphFacts.TargetKind.PLAYER, false)), List.of());

        assertTrue(events.contains("life_threat_player"), "Враг целится в игрока — связь «защитить игрока»: " + events);
        assertFalse(events.contains("life_threat_spirit"), "Дух не цель — защита духа не поднимается: " + events);
    }

    @Test
    void events_Conditions_AddEachLifeEvent() {
        LifeEvents.Condition all = new LifeEvents.Condition(true, true, true, true, true, true, true, true);

        Set<String> events = LifeEvents.events(Set.of(), List.of(), List.of(all));

        assertEquals(
                Set.of("drowning", "suffocation", "fall_risk", "falling", "fire", "poisoned", "hungry", "life_injury"),
                events,
                "Каждое состояние духа подаётся своим событием графа: " + events);
    }

    @Test
    void events_CalmCondition_NoLifeEvent() {
        LifeEvents.Condition calm = new LifeEvents.Condition(false, false, false, false, false, false, false, false);

        assertEquals(
                Set.of("night"),
                LifeEvents.events(Set.of("night"), List.of(), List.of(calm)),
                "Спокойный дух не поднимает события жизни");
    }

    @Test
    void domainEvents_ThreatsInFacts_DeriveLifeBand() {
        GraphFacts facts = GraphFacts.empty().withThreats(List.of(threat(GraphFacts.TargetKind.SPIRIT, true)));

        Set<String> events = GraphDomainEvents.of(facts, 1);

        assertTrue(
                events.contains("life_threat") && events.contains("life_threat_spirit"),
                "События жизни поднимаются из фактов угроз и без отдельной подачи входа: " + events);
        assertFalse(events.contains("life_threat_player"), "Игрок не цель — защита игрока не поднимается: " + events);
    }

    @Test
    void events_WeakPlayerFact_AddsPlayerThreat() {
        Set<String> events =
                LifeEvents.events(Set.of(), List.of(), List.of(), List.of(player(4.0F, 20)), GraphLife.DEFAULTS);

        assertTrue(
                events.contains("life_threat_player"),
                "Факт здоровья игрока сам поднимает жизнь: легаси-событие more5_player_weak больше не нужно: "
                        + events);
    }

    @Test
    void events_HealthyPlayerFact_NoPlayerThreat() {
        Set<String> events =
                LifeEvents.events(Set.of(), List.of(), List.of(), List.of(player(20.0F, 20)), GraphLife.DEFAULTS);

        assertFalse(events.contains("life_threat_player"), "Здоровый игрок без угроз не поднимает жизнь: " + events);
    }

    private static GraphFacts.Player player(float health, int food) {
        return new GraphFacts.Player("Steve", AT, health, 20.0F, food);
    }

    private static GraphFacts.Threat threat(GraphFacts.TargetKind kind, boolean targetingSelf) {
        return new GraphFacts.Threat(
                "threat-1",
                "minecraft:zombie",
                20,
                20,
                AT,
                "target-1",
                kind,
                targetingSelf,
                true,
                "burning",
                5,
                null,
                0.23);
    }
}
