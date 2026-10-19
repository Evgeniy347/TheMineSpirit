package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class PlanParserProperties {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    private static final int MAX_ALLOWED_DEPTH = 15;

    @Property
    void parse_WaitScriptWithAnyTicks_ReturnsSameTicks(@ForAll @IntRange(min = 1, max = 6000) int ticks) {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":" + ticks + "}}";

        assertEquals(
                new ScriptCall("wait", Map.of("ticks", ticks)),
                PlanParser.parse(json, CATALOG),
                "узел script с любым целым ticks обязан разбираться без потерь: иначе движок ждёт не то время, "
                        + "что задал планировщик");
    }

    @Property
    void parse_SequenceWithinDepthLimit_Succeeds(@ForAll @IntRange(min = 1, max = MAX_ALLOWED_DEPTH) int depth) {
        assertInstanceOf(
                SequenceNode.class,
                PlanParser.parse(nestedSequence(depth), CATALOG),
                "план глубиной до лимита обязан разбираться: иначе валидатор режет допустимые вложенные планы");
    }

    @Property
    void parse_SequenceOverDepthLimit_IsRejected(@ForAll @IntRange(min = 17, max = 40) int depth) {
        assertThrows(
                PlanValidationException.class,
                () -> PlanParser.parse(nestedSequence(depth), CATALOG),
                "план глубиной больше 16 обязан отвергаться: рекурсивный план переполнит стек и уронит серверный тик");
    }

    @Provide
    Arbitrary<JsonNode> jsonTrees() {
        return JsonTrees.any();
    }

    @Property
    void parse_AnyJsonTree_ThrowsOnlyPlanValidationException(@ForAll("jsonTrees") JsonNode tree) {
        String json = tree.toString();
        try {
            PlanParser.parse(json, CATALOG);
        } catch (PlanValidationException expected) {
        } catch (RuntimeException other) {
            fail(
                    "парсер плана на любом JSON-дереве обязан бросать только PlanValidationException, а упал с "
                            + other.getClass().getSimpleName() + ": " + other.getMessage()
                            + ". Исключение другого типа прорвётся в серверный тик и замаскирует дефект валидатора. JSON="
                            + json,
                    other);
        }
    }

    private static String nestedSequence(int depth) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            json.append("{\"type\":\"sequence\",\"steps\":[");
        }
        json.append("{\"type\":\"script\",\"script\":\"return_home\"}");
        for (int i = 0; i < depth; i++) {
            json.append("]}");
        }
        return json.toString();
    }
}
