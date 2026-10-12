package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.UniqueElements;

class ToolBreakCostProperties {
    @Property
    void orderByCost_EntryOrderChanged_SameOrder(
            @ForAll @Size(value = 6) @UniqueElements List<@StringLength(min = 1, max = 8) String> ids,
            @ForAll @IntRange(min = 1, max = 1000) int base) {
        Map<String, Integer> forward = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            forward.put(ids.get(i), base + (i * 37) % 101);
        }
        List<String> reversedKeys = new ArrayList<>(forward.keySet());
        Collections.reverse(reversedKeys);
        Map<String, Integer> backward = new LinkedHashMap<>();
        for (String id : reversedKeys) {
            backward.put(id, forward.get(id));
        }

        assertEquals(ToolBreakCost.orderByCost(forward), ToolBreakCost.orderByCost(backward),
                "Порядок целей по стоимости обязан зависеть только от стоимостей, а не от порядка входа: "
                        + "иначе один и тот же набор целей даёт разные маршруты от запуска к запуску");
    }
}
