package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class ScriptArgsProperties {
    @Property
    void gotoParams_IntegerOrLongEncoding_ProducesEqualRecords(@ForAll int x, @ForAll int y, @ForAll int z) {
        GotoParams byInt = GotoParams.from(Map.of("x", x, "y", y, "z", z));
        GotoParams byLong = GotoParams.from(Map.of("x", (long) x, "y", (long) y, "z", (long) z));

        assertEquals(byInt, byLong,
                "маппинг goto обязан давать одинаковый результат для int и long из JSON-плана: иначе одно и то же "
                        + "наблюдение зависит от того, как планировщик закодировал число, и дух идёт в разные точки");
    }
}