package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class LifeThreats {
    private LifeThreats() {}

    public static int damageWindowTicks() {
        return tuning().damageWindowTicks();
    }

    public static int priority(GraphFacts facts, GraphEvent event, GraphEvent.Task task, int max) {
        return priority(facts, event, task, max, tuning());
    }

    public static int priority(GraphFacts facts, GraphEvent event, GraphEvent.Task task, int max, GraphLife life) {
        Objects.requireNonNull(event, "Событие жизни не может быть null: приоритет считается по его заданиям");
        Objects.requireNonNull(task, "Задание события не может быть null: у него есть вес");
        if (max <= 0) {
            return 0;
        }
        int top = event.tasks().stream().mapToInt(GraphEvent.Task::base).max().orElse(0);
        double severity = severity(facts, event.name(), life);
        if (top <= 0 || severity <= 0) {
            return 0;
        }
        double share = clamp((double) task.base() / top);
        int value = (int) Math.round(max * severity * share);
        return Math.min(max, Math.max(0, value));
    }

    public static double severity(GraphFacts facts, String event) {
        return severity(facts, event, tuning());
    }

    public static double severity(GraphFacts facts, String event, GraphLife life) {
        Objects.requireNonNull(facts, "Факты жизни не могут быть null: без них нет ни угроз, ни здоровья");
        Objects.requireNonNull(life, "Коэффициенты жизни не могут быть null: они лежат в graph.json");
        return switch (event) {
            case "life_threat" ->
                Math.max(spiritThreat(facts, life), Math.max(playerThreat(facts, life), injury(facts, life)));
            case "life_threat_spirit" -> spiritThreat(facts, life);
            case "life_threat_player" -> playerThreat(facts, life);
            case "life_injury" -> injury(facts, life);
            default -> 0.0;
        };
    }

    public static double risk(GraphFacts facts, String spiritId) {
        return risk(facts, spiritId, tuning());
    }

    public static double risk(GraphFacts facts, String spiritId, GraphLife life) {
        Objects.requireNonNull(spiritId, "id духа не может быть null: риск считается для конкретного духа");
        Objects.requireNonNull(life, "Коэффициенты жизни не могут быть null: они лежат в graph.json");
        GraphFacts.Spirit spirit = facts.spirits().get(spiritId);
        double deficit = deficit(spirit);
        double hurt = hurt(spirit, life);
        double pressure = pressure(
                facts,
                life,
                threat -> threat.targetKind() == GraphFacts.TargetKind.SPIRIT
                        && (threat.target() == null || spiritId.equals(threat.target())));
        return clamp(pressure * (life.pressureBase() + life.pressureWound() * deficit)
                + life.hurtShare() * Math.min(1, hurt));
    }

    public static boolean weakPlayer(GraphFacts facts) {
        return weakPlayer(facts, tuning());
    }

    public static boolean weakPlayer(GraphFacts facts, GraphLife life) {
        Objects.requireNonNull(facts, "Факты жизни не могут быть null: слабость игрока — факт о мире");
        Objects.requireNonNull(life, "Коэффициенты жизни не могут быть null: пороги лежат в graph.json");
        GraphFeedback.Player player = facts.feedback().player();
        if (!player.near() || player.maxHealth() <= 0) {
            return false;
        }
        double ratio = player.health() / player.maxHealth();
        return ratio < life.weakHealth() || player.food() <= life.weakFood();
    }

    private static GraphLife tuning() {
        return SupplyGraph.current().costs().life();
    }

    private static double spiritThreat(GraphFacts facts, GraphLife life) {
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
            worst = Math.max(worst, risk(facts, target, life));
        }
        return worst;
    }

    private static double playerThreat(GraphFacts facts, GraphLife life) {
        double pressure = pressure(facts, life, threat -> threat.targetKind() == GraphFacts.TargetKind.PLAYER);
        return weakPlayer(facts, life) ? Math.max(pressure, clamp(life.weakPlayerSeverity())) : pressure;
    }

    private static double injury(GraphFacts facts, GraphLife life) {
        double worst = 0;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            worst = Math.max(
                    worst,
                    clamp(life.injuryWound() * deficit(spirit) + life.injuryHurt() * Math.min(1, hurt(spirit, life))));
        }
        return worst;
    }

    private static double pressure(GraphFacts facts, GraphLife life, Predicate<GraphFacts.Threat> match) {
        double sum = 0;
        for (GraphFacts.Threat threat : facts.threats()) {
            if (!match.test(threat)) {
                continue;
            }
            double closeness = Math.max(0, Math.min(1, 1 - threat.distance() / life.threatRadius()));
            sum += closeness;
        }
        return Math.min(1, sum / life.fullPressureMobs());
    }

    private static double deficit(GraphFacts.@Nullable Spirit spirit) {
        if (spirit == null || spirit.maxHealth() <= 0) {
            return 0;
        }
        return clamp(1 - spirit.health() / spirit.maxHealth());
    }

    private static double hurt(GraphFacts.@Nullable Spirit spirit, GraphLife life) {
        if (spirit == null || spirit.recentHurt() == null || spirit.maxHealth() <= 0) {
            return 0;
        }
        GraphFacts.Hurt hurt = spirit.recentHurt();
        double freshness = clamp(1 - hurt.ticksAgo() / (double) life.damageWindowTicks());
        return clamp(hurt.amount() / spirit.maxHealth()) * freshness;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
