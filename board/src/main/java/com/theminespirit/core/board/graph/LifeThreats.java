package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class LifeThreats {
    public static final long DAMAGE_WINDOW_TICKS = 200;
    public static final double THREAT_RADIUS = 16.0;
    private static final double FULL_PRESSURE_MOBS = 1.5;
    private static final double PRESSURE_BASE = 0.4;
    private static final double PRESSURE_WOUND = 0.7;
    private static final double HURT_SHARE = 0.2;
    private static final double INJURY_WOUND = 0.8;
    private static final double INJURY_HURT = 0.4;

    private LifeThreats() {}

    public static int priority(GraphFacts facts, GraphEvent event, GraphEvent.Task task, int max) {
        Objects.requireNonNull(event, "Событие жизни не может быть null: приоритет считается по его заданиям");
        Objects.requireNonNull(task, "Задание события не может быть null: у него есть вес");
        if (max <= 0) {
            return 0;
        }
        int top = event.tasks().stream().mapToInt(GraphEvent.Task::base).max().orElse(0);
        double severity = severity(facts, event.name());
        if (top <= 0 || severity <= 0) {
            return 0;
        }
        double share = clamp((double) task.base() / top);
        int value = (int) Math.round(max * severity * share);
        return Math.min(max, Math.max(0, value));
    }

    public static double severity(GraphFacts facts, String event) {
        Objects.requireNonNull(facts, "Факты жизни не могут быть null: без них нет ни угроз, ни здоровья");
        return switch (event) {
            case "life_threat" -> Math.max(spiritThreat(facts), Math.max(playerThreat(facts), injury(facts)));
            case "life_threat_spirit" -> spiritThreat(facts);
            case "life_threat_player" -> playerThreat(facts);
            case "life_injury" -> injury(facts);
            default -> 0.0;
        };
    }

    public static double risk(GraphFacts facts, String spiritId) {
        Objects.requireNonNull(spiritId, "id духа не может быть null: риск считается для конкретного духа");
        GraphFacts.Spirit spirit = facts.spirits().get(spiritId);
        double deficit = deficit(spirit);
        double hurt = hurt(spirit);
        double pressure = pressure(
                facts,
                threat -> threat.targetKind() == GraphFacts.TargetKind.SPIRIT
                        && (threat.target() == null || spiritId.equals(threat.target())));
        return clamp(pressure * (PRESSURE_BASE + PRESSURE_WOUND * deficit) + HURT_SHARE * Math.min(1, hurt));
    }

    private static double spiritThreat(GraphFacts facts) {
        Set<String> targets = new LinkedHashSet<>(facts.spirits().keySet());
        boolean anySpiritThreat = false;
        for (GraphFacts.Threat threat : facts.threats()) {
            if (threat.targetKind() != GraphFacts.TargetKind.SPIRIT) {
                continue;
            }
            anySpiritThreat = true;
            if (threat.target() != null) {
                targets.add(threat.target());
            }
        }
        if (targets.isEmpty() && anySpiritThreat) {
            targets.add("");
        }
        double worst = 0;
        for (String target : targets) {
            worst = Math.max(worst, risk(facts, target));
        }
        return worst;
    }

    private static double playerThreat(GraphFacts facts) {
        return pressure(facts, threat -> threat.targetKind() == GraphFacts.TargetKind.PLAYER);
    }

    private static double injury(GraphFacts facts) {
        double worst = 0;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            worst = Math.max(worst, clamp(INJURY_WOUND * deficit(spirit) + INJURY_HURT * Math.min(1, hurt(spirit))));
        }
        return worst;
    }

    private static double pressure(GraphFacts facts, Predicate<GraphFacts.Threat> match) {
        double sum = 0;
        for (GraphFacts.Threat threat : facts.threats()) {
            if (!match.test(threat)) {
                continue;
            }
            double closeness = Math.max(0, Math.min(1, 1 - threat.distance() / THREAT_RADIUS));
            sum += closeness;
        }
        return Math.min(1, sum / FULL_PRESSURE_MOBS);
    }

    private static double deficit(GraphFacts.@Nullable Spirit spirit) {
        if (spirit == null || spirit.maxHealth() <= 0) {
            return 0;
        }
        return clamp(1 - spirit.health() / spirit.maxHealth());
    }

    private static double hurt(GraphFacts.@Nullable Spirit spirit) {
        if (spirit == null || spirit.recentHurt() == null || spirit.maxHealth() <= 0) {
            return 0;
        }
        GraphFacts.Hurt hurt = spirit.recentHurt();
        double freshness = clamp(1 - hurt.ticksAgo() / (double) DAMAGE_WINDOW_TICKS);
        return clamp(hurt.amount() / spirit.maxHealth()) * freshness;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
