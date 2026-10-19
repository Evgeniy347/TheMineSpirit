package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class CreeperHitAndRunTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    private record Hunt(List<TacticalOption> decisions, List<Double> distances, List<Boolean> struck) {
        int strikes() {
            return (int) struck.stream().filter(hit -> hit).count();
        }
    }

    @Provide
    Arbitrary<Integer> starts() {
        return Arbitraries.integers().between(6, 12);
    }

    private static Hunt hunt(double startDistance, int steps) {
        TacticsEngine engine = new TacticsEngine(CONFIG);
        List<TacticalOption> decisions = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        List<Boolean> struck = new ArrayList<>();
        double distance = startDistance;
        int sinceStrike = Situation.MAX_TICKS;
        TacticsMemory memory = null;
        for (int step = 0; step < steps; step++) {
            Situation situation = SituationBuilder.create()
                    .cover(4)
                    .enemy("creeper", distance)
                    .sinceStrike(sinceStrike)
                    .build();
            memory = memory == null ? Memories.fresh(situation) : memory;
            TacticalOutcome outcome = engine.step(situation, memory, 10L * step);
            decisions.add(outcome.decision());
            distances.add(distance);
            boolean hit = outcome.action() == TacticalAction.ENGAGE && distance <= 3;
            struck.add(hit);
            sinceStrike = hit ? 0 : Math.min(Situation.MAX_TICKS, sinceStrike + 10);
            memory = outcome.memory();
            if (outcome.decision() == TacticalOption.FIGHT) {
                distance = Math.max(1, distance - 1);
            } else if (outcome.decision() == TacticalOption.RETREAT) {
                distance = distance + 1;
            }
        }
        return new Hunt(decisions, distances, struck);
    }

    @Test
    void hunt_ChainFromNine_StrikesAtThreeThenRetreatsToSixAndApproaches() {
        Hunt hunt = hunt(9, 16);

        assertTrue(
                hunt.strikes() >= 1,
                "Дух с мечом без лука против крипера обязан ударить хотя бы раз: иначе бесконечный цикл 5↔8 "
                        + "оставляет крипера целым, а духа без урона. Дистанции: " + hunt.distances());
        double nearest = hunt.distances().stream()
                .mapToDouble(distance -> distance)
                .min()
                .orElseThrow();
        assertTrue(
                nearest <= 3,
                "Дух подходит вплотную (≤3), а не топчется на 5–8 блоках: удар мечом бьёт только вплотную, "
                        + "полоса 5/8 — для безоружного. Дистанции: " + hunt.distances());
        int firstStrike = hunt.struck().indexOf(true);
        boolean leftToSix = hunt
                .distances()
                .subList(firstStrike + 1, hunt.distances().size())
                .stream()
                .anyMatch(distance -> distance >= 6);
        assertTrue(
                leftToSix,
                "После удара дух отходит до 6 блоков: удар отбрасывает крипера и сбивает запал, повторный удар "
                        + "вплотную подряд — взрыв. Дистанции после удара: "
                        + hunt.distances().subList(firstStrike, hunt.distances().size()));
        boolean approachesAgain = false;
        List<Double> tail =
                hunt.distances().subList(firstStrike + 1, hunt.distances().size());
        for (int i = 1; i < tail.size(); i++) {
            if (tail.get(i) < tail.get(i - 1)) {
                approachesAgain = true;
            }
        }
        assertTrue(
                approachesAgain,
                "Отошедший до 6 дух снова подходит, а не стоит на месте: тактика «ударил–отошёл» повторяется, "
                        + "иначе крипер останется недобитым. Дистанции: " + hunt.distances());
        assertTrue(
                hunt.decisions().contains(TacticalOption.FIGHT)
                        && hunt.decisions().contains(TacticalOption.RETREAT),
                "За цепочку встречаются и подход, и отход: только бой — бег к взрыву, только отход — стояние "
                        + "на месте. Решения: " + hunt.decisions());
    }

    @Property(tries = 100)
    void hunt_EngagedWithoutBow_StrikesWithinHundredSteps(@ForAll("starts") int start) {
        Hunt hunt = hunt(start, 100);

        assertTrue(
                hunt.strikes() >= 1,
                "В ENGAGED с крипером без лука за 100 шагов есть хотя бы одно действие удара: иначе дух ходит "
                        + "5↔8 без удара (старт " + start + ", дистанции: "
                        + hunt.distances().stream()
                                .mapToDouble(distance -> distance)
                                .min()
                                .orElseThrow() + "–"
                        + hunt.distances().stream()
                                .mapToDouble(distance -> distance)
                                .max()
                                .orElseThrow() + ")");
    }
}
