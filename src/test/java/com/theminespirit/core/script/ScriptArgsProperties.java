package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

class ScriptArgsProperties {
    @Property
    void gotoParams_LongParameter_IsRejected(@ForAll @IntRange(min = 0, max = 100) int x,
            @ForAll @IntRange(min = 0, max = 100) int y, @ForAll @IntRange(min = 0, max = 100) int z) {
        assertThrows(IllegalArgumentException.class,
                () -> GotoParams.from(Map.of("x", (long) x, "y", (long) y, "z", (long) z)),
                "маппинг goto обязан принимать только Integer: валидатор плана приводит INT к Integer, "
                        + "а long означает, что значение не прошло валидатор");
    }
}
