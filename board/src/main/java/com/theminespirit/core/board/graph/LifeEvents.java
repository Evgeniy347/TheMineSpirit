package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LifeEvents {
    public record Condition(
            boolean drowning,
            boolean suffocating,
            boolean fallRisk,
            boolean falling,
            boolean fire,
            boolean poisoned,
            boolean hungry,
            boolean injured) {}

    private LifeEvents() {}

    public static Set<String> events(Set<String> base, List<GraphFacts.Threat> threats, List<Condition> conditions) {
        return events(base, threats, conditions, List.of(), GraphLife.DEFAULTS);
    }

    public static Set<String> events(
            Set<String> base,
            List<GraphFacts.Threat> threats,
            List<Condition> conditions,
            List<GraphFacts.Player> players,
            GraphLife life) {
        Set<String> events = new LinkedHashSet<>(base);
        if (!threats.isEmpty()) {
            events.add("life_threat");
        }
        if (threats.stream().anyMatch(threat -> threat.targetKind() == GraphFacts.TargetKind.SPIRIT)) {
            events.add("life_threat_spirit");
        }
        if (threats.stream().anyMatch(threat -> threat.targetKind() == GraphFacts.TargetKind.PLAYER)
                || players.stream().anyMatch(player -> weak(player, life))) {
            events.add("life_threat_player");
        }
        for (Condition condition : conditions) {
            add(events, condition.drowning(), "drowning");
            add(events, condition.suffocating(), "suffocation");
            add(events, condition.fallRisk(), "fall_risk");
            add(events, condition.falling(), "falling");
            add(events, condition.fire(), "fire");
            add(events, condition.poisoned(), "poisoned");
            add(events, condition.hungry(), "hungry");
            add(events, condition.injured(), "life_injury");
        }
        return Set.copyOf(events);
    }

    static boolean weak(GraphFacts.Player player, GraphLife life) {
        if (player.maxHealth() <= 0) {
            return false;
        }
        return player.health() / player.maxHealth() < life.weakHealth() || player.food() <= life.weakFood();
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}
