package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class TacticsScheduleProperties {
    @Property(tries = 200)
    void due_WithoutEvents_AtMostOneEvaluationPerInterval(@ForAll @IntRange(min = 1, max = 50) int interval,
            @ForAll @IntRange(min = 0, max = 100) int start, @ForAll @IntRange(min = 50, max = 400) int ticks) {
        TacticsSchedule schedule = new TacticsSchedule(interval);
        int[] evaluationTicks = new int[ticks];
        int count = 0;
        for (int tick = start; tick < start + ticks; tick++) {
            if (schedule.due(tick, false, false)) {
                schedule.evaluated(tick, false);
                evaluationTicks[count++] = tick;
            }
        }

        for (int i = 1; i < count; i++) {
            assertTrue(evaluationTicks[i] - evaluationTicks[i - 1] >= interval,
                    "Два вызова step ближе интервала " + interval + " без события: " + evaluationTicks[i - 1] + " и "
                            + evaluationTicks[i] + "; тактика не должна оценивать чаще раза в интервал");
        }
    }

    @Property(tries = 200)
    void due_HurtOnAnyTick_EvaluationsBoundedByIntervalPlusEventGap(@ForAll @IntRange(min = 10, max = 50) int interval,
            @ForAll @Size(min = 20, max = 200) List<Boolean> hurtPerTick) {
        TacticsSchedule schedule = new TacticsSchedule(interval);
        int evaluations = 0;
        for (int tick = 0; tick < hurtPerTick.size(); tick++) {
            boolean hurt = hurtPerTick.get(tick);
            if (schedule.due(tick, hurt, false)) {
                schedule.evaluated(tick, hurt);
                evaluations++;
            }
        }

        int bound = hurtPerTick.size() / interval + hurtPerTick.size() / TacticsSchedule.HURT_MIN_GAP_TICKS + 2;
        assertTrue(evaluations <= bound, "Оценок за " + hurtPerTick.size() + " тиков " + evaluations + " при пределе "
                + bound + " (интервал " + interval + " плюс события не чаще раза в " + TacticsSchedule.HURT_MIN_GAP_TICKS
                + " тиков); град ударов не должен превращаться в оценку на каждом тике");
    }
}
