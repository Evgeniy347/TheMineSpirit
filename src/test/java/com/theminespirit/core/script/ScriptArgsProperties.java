package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ScriptArgsProperties {
    @Property
    void gotoParams_LongParameter_IsRejected(@ForAll @IntRange(min = 0, max = 100) int x,
            @ForAll @IntRange(min = 0, max = 100) int y, @ForAll @IntRange(min = 0, max = 100) int z) {
        assertThrows(IllegalArgumentException.class,
                () -> GotoParams.from(Map.of("x", (long) x, "y", (long) y, "z", (long) z)),
                "маппинг goto обязан принимать только Integer: валидатор плана приводит INT к Integer, "
                        + "а long означает, что значение не прошло валидатор");
    }

    @Property
    void optionalStringList_ListOfStrings_ReturnsSameElements(@ForAll List<String> values) {
        Optional<List<String>> result = ScriptArgs.optionalStringList(Map.of("filter", values), "filter");

        assertEquals(Optional.of(values), result,
                "Список строк из плана обязан дойти до скрипта без потерь и перестановок: по нему строится фильтр блоков");
    }

    @Property
    void optionalStringList_ListWithNonString_IsRejected(@ForAll List<String> values, @ForAll int intruder) {
        List<Object> mixed = new ArrayList<>(values);
        mixed.add(intruder);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.optionalStringList(Map.of("filter", mixed), "filter"),
                "Список с числом среди строк обязан отвергаться: тихое приведение скрыло бы ошибку плана");
        assertTrue(error.getMessage().contains("filter"),
                "Сообщение обязано называть параметр 'filter'. Фактическое: " + error.getMessage());
    }

    @Property
    void optionalStringList_NotAList_IsRejected(@ForAll String text) {
        assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.optionalStringList(Map.of("filter", text), "filter"),
                "Строка вместо списка строк обязана отвергаться: валидатор приводит STRING_LIST к списку до запуска");
    }

    @Property
    void requireBoolean_BooleanValue_IsReturned(@ForAll boolean value) {
        assertEquals(value, ScriptArgs.requireBoolean(Map.of("replant", value), "replant"),
                "requireBoolean обязан вернуть то же значение, что положил валидатор плана");
    }

    @Property
    void requireBoolean_StringValue_IsRejected(@ForAll String text) {
        assertThrows(IllegalArgumentException.class,
                () -> ScriptArgs.requireBoolean(Map.of("replant", text), "replant"),
                "Строка вместо true/false обязана отвергаться: \"false\" как строка истинна для проверки на null");
    }
}
