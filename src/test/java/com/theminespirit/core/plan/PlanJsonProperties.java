package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class PlanJsonProperties {
    @Property
    void toJson_AnyValidPlan_RoundTripsThroughParser(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        TaskNode plan = RandomPlans.valid(new Random(seed));

        TaskNode reparsed = PlanParser.parse(PlanJson.toJson(plan), RandomPlans.SCRIPTS, RandomPlans.CONDITIONS);

        assertEquals(
                plan,
                reparsed,
                "любой валидный план обязан переживать запись в JSON и обратный разбор без потерь: потеря шага, "
                        + "типа параметра или ветки условия после перезапуска заставит духа исполнить не тот план. "
                        + "seed=" + seed + ", план=" + plan);
    }
}
