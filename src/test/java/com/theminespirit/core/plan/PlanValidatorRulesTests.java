package com.theminespirit.core.plan;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ParamType;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlanValidatorRulesTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();

    static Stream<Arguments> validationRules() {
        return Stream.of(
                Arguments.of("тип INT: строка вместо числа",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":\"5\"}}"),
                Arguments.of("диапазон снизу: wait.ticks < 1",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":0}}"),
                Arguments.of("диапазон сверху: wait.ticks > 6000",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":6001}}"),
                Arguments.of("диапазон count снизу: < 1",
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"any\",\"count\":0}}"),
                Arguments.of("лишний параметр",
                        "{\"type\":\"script\",\"script\":\"return_home\",\"params\":{\"x\":1}}"),
                Arguments.of("отсутствующий обязательный",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{}}"),
                Arguments.of("repeat.times < 1",
                        "{\"type\":\"repeat\",\"times\":0,\"step\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("repeat.times > 64",
                        "{\"type\":\"repeat\",\"times\":65,\"step\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("неизвестная проверка условия",
                        "{\"type\":\"condition\",\"check\":\"unknown_check\",\"then\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("count верхняя граница: > 256",
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"any\",\"count\":257}}"),
                Arguments.of("y ниже минимума: < -64",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":-65,\"z\":0}}"),
                Arguments.of("y выше максимума: > 319",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":320,\"z\":0}}"),
                Arguments.of("x ниже минимума: < -30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":-30000001,\"y\":0,\"z\":0}}"),
                Arguments.of("x выше максимума: > 30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":30000001,\"y\":0,\"z\":0}}"),
                Arguments.of("z ниже минимума: < -30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":0,\"z\":-30000001}}"),
                Arguments.of("z выше максимума: > 30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":0,\"z\":30000001}}"),
                Arguments.of("follow radius выше максимума: > 16",
                        "{\"type\":\"script\",\"script\":\"follow\",\"params\":{\"radius\":17}}")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationRules")
    void planValidation_InvalidInput_IsRejected(String description, String json) {
        assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                description + ": план должен быть отклонён валидатором");
    }

    @Test
    void waitScript_TicksInsideRange_IsAccepted() {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":3000}}";
        PlanParser.parse(json, CATALOG);
    }

    @Property
    void waitScript_TicksOutsideRange_IsRejected(@ForAll int ticks) {
        if (ticks >= 1 && ticks <= 6000) {
            String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":" + ticks + "}}";
            PlanParser.parse(json, CATALOG);
            return;
        }
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":" + ticks + "}}";
        assertThrows(PlanValidationException.class, () -> PlanParser.parse(json, CATALOG));
    }
}
