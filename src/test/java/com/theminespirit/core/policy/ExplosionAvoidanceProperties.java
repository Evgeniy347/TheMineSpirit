package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatFact;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ExplosionAvoidanceProperties {
    private static final AsciiWorld ROOM = ExplosionAvoidanceTests.openRoom();

    private static double expectedHold(ThreatFact fact) {
        double base = switch (fact.type()) {
            case CREEPER -> 7.0;
            case CHARGED_CREEPER -> 13.0;
            case PRIMED_TNT -> 9.0;
        };
        return fact.swelling() ? base + 3.0 : base;
    }

    private static double distance(BlockCoord a, BlockCoord b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double worstSlack(BlockCoord cell, List<ThreatFact> facts) {
        double worst = Double.POSITIVE_INFINITY;
        for (ThreatFact fact : facts) {
            worst = Math.min(worst, distance(cell, fact.pos()) - expectedHold(fact));
        }
        return worst;
    }

    private static List<ThreatFact> randomThreats(Random random, int count) {
        List<ThreatFact> facts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ThreatType type = List.of(ThreatType.values()).get(random.nextInt(ThreatType.values().length));
            BlockCoord pos = new BlockCoord(random.nextInt(21), 1, random.nextInt(21));
            facts.add(new ThreatFact(type, pos, random.nextInt(4) == 0));
        }
        return facts;
    }

    @Property(tries = 200)
    void evaluate_RandomThreats_ClearExactlyWhenEveryHoldDistanceIsKept(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        BlockCoord self = new BlockCoord(4 + random.nextInt(13), 1, 4 + random.nextInt(13));
        List<ThreatFact> facts = randomThreats(random, random.nextInt(4));

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(self, facts, ROOM);

        boolean allKept = worstSlack(self, facts) >= 0;
        assertEquals(
                allKept,
                verdict instanceof PolicyVerdict.Clear,
                "Рефлекс нужен ровно тогда, когда хотя бы одна угроза ближе своего расстояния держания (7, 10, 9, +3 при набухании). Угрозы: "
                        + facts);
    }

    @Property(tries = 200)
    void evaluate_OneThreat_FleeCellIsNotCloserThanStart(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        BlockCoord self = new BlockCoord(4 + random.nextInt(13), 1, 4 + random.nextInt(13));
        ThreatFact fact = randomThreats(random, 1).get(0);

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(self, List.of(fact), ROOM);

        if (verdict instanceof PolicyVerdict.Reflex reflex && reflex.action() instanceof ReflexAction.Flee flee) {
            assertTrue(
                    distance(flee.cell(), fact.pos()) >= distance(self, fact.pos()),
                    "После плана расстояние до угрозы не меньше исходного: иначе дух бежит к взрыву. Угроза: " + fact
                            + ", цель: " + flee.cell());
        }
    }

    @Property(tries = 200)
    void evaluate_SeveralThreats_FleeCellKeepsHoldsOrIsNotWorseThanStart(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        BlockCoord self = new BlockCoord(4 + random.nextInt(13), 1, 4 + random.nextInt(13));
        List<ThreatFact> facts = randomThreats(random, 2 + random.nextInt(2));

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(self, facts, ROOM);

        if (verdict instanceof PolicyVerdict.Reflex reflex && reflex.action() instanceof ReflexAction.Flee flee) {
            assertTrue(
                    worstSlack(flee.cell(), facts) >= Math.min(0, worstSlack(self, facts)),
                    "С несколькими угрозами цель либо держит все расстояния, либо не хуже старта по самой близкой угрозе. Угрозы: "
                            + facts + ", цель: " + flee.cell());
        }
    }
}
