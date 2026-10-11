package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanJsonTests {
    @Test
    void toJson_ScriptNode_WritesTypeScriptAndParams() {
        String json = PlanJson.toJson(new ScriptCall("wait", Map.of("ticks", 5)));

        assertEquals("{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":5}}", json,
                "узел script обязан писаться как {type,script,params}: по этим ключам PlanParser собирает вызов "
                        + "обратно, и переименование любого из них ломает чтение снимка");
    }

    @Test
    void toJson_ScriptWithoutParams_WritesEmptyParamsObject() {
        String json = PlanJson.toJson(new ScriptCall("return_home", Map.of()));

        assertEquals("{\"type\":\"script\",\"script\":\"return_home\",\"params\":{}}", json,
                "скрипт без параметров обязан писать пустой объект params: пропуск ключа сделал бы JSON плана "
                        + "неполным, а reader пришлось бы учить отсутствию поля");
    }

    @Test
    void toJson_ParamsWithSeveralKeys_SortsKeysCanonically() {
        String json = PlanJson.toJson(new ScriptCall("x", Map.of("b", 1, "a", 2)));

        assertEquals("{\"type\":\"script\",\"script\":\"x\",\"params\":{\"a\":2,\"b\":1}}", json,
                "параметры обязаны сериализоваться в отсортированном порядке: без канонического порядка один план "
                        + "давал бы разный JSON и байтовое сравнение снимков перестало бы работать");
    }

    @Test
    void toJson_SequenceNode_WritesNestedStepsInOrder() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("wait", Map.of("ticks", 1)),
                new ScriptCall("say", Map.of("text", "привет"))));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"sequence\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":1}},"
                + "{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"привет\"}}]}", json,
                "узел sequence обязан писать шаги в исходном порядке: перестановка шагов исполнила бы план духа "
                        + "в другом порядке");
    }

    @Test
    void toJson_RepeatNode_WritesTimesAndStep() {
        String json = PlanJson.toJson(new RepeatNode(3, new ScriptCall("wait", Map.of("ticks", 2))));

        assertEquals("{\"type\":\"repeat\",\"times\":3,"
                + "\"step\":{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":2}}}", json,
                "узел repeat обязан писать times и step: без times после перезапуска дух повторил бы шаг не то "
                        + "число раз");
    }

    @Test
    void toJson_FallbackNode_WritesSteps() {
        TaskNode plan = new FallbackNode(List.of(
                new ScriptCall("say", Map.of("text", "a")),
                new ScriptCall("say", Map.of("text", "b"))));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"fallback\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"a\"}},"
                + "{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"b\"}}]}", json,
                "узел fallback обязан писать те же steps, что и sequence: иначе reader не восстановит порядок "
                        + "резервных веток");
    }

    @Test
    void toJson_ParallelAny_WritesModeAfterSteps() {
        TaskNode plan = new ParallelNode(ParallelMode.ANY, List.of(new ScriptCall("wait", Map.of("ticks", 1))));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"parallel\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":1}}],\"mode\":\"any\"}", json,
                "узел parallel обязан писать режим 'any' для ParallelMode.ANY: перепутанный режим изменил бы "
                        + "условие завершения после перезапуска");
    }

    @Test
    void toJson_ParallelAll_WritesModeAll() {
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(new ScriptCall("wait", Map.of("ticks", 1))));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"parallel\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":1}}],\"mode\":\"all\"}", json,
                "узел parallel обязан писать режим 'all' для ParallelMode.ALL: без него reader не отличит "
                        + "ожидание всех шагов от ожидания любого");
    }

    @Test
    void toJson_ConditionWithElse_WritesCheckParamsThenElse() {
        TaskNode plan = new ConditionNode("seen", Map.of("limit", 5),
                new ScriptCall("say", Map.of("text", "да")),
                new ScriptCall("say", Map.of("text", "нет")));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"condition\",\"check\":\"seen\",\"params\":{\"limit\":5},"
                + "\"then\":{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"да\"}},"
                + "\"else\":{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"нет\"}}}", json,
                "узел condition с веткой else обязан писать обе ветки: потеря else после перезапуска увела бы "
                        + "условие по неверному пути");
    }

    @Test
    void toJson_ConditionWithoutElse_OmitsElseKey() {
        TaskNode plan = new ConditionNode("seen", Map.of(),
                new ScriptCall("say", Map.of("text", "да")), null);

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"condition\",\"check\":\"seen\",\"params\":{},"
                + "\"then\":{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":\"да\"}}}", json,
                "узел condition без else обязан опускать ключ else: запись else:null заставила бы reader "
                        + "разбирать null как узел плана");
    }

    @Test
    void toJson_BoolAndListParams_PreserveTypes() {
        TaskNode plan = new ScriptCall("x", Map.of("flag", true, "list", List.of("a", "b")));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"script\",\"script\":\"x\","
                + "\"params\":{\"flag\":true,\"list\":[\"a\",\"b\"]}}", json,
                "булевы значения и списки строк обязаны писаться своими типами: число вместо флага или строка "
                        + "вместо списка сорвали бы проверку параметров при чтении");
    }

    @Test
    void toJson_LongParam_WritesWithoutDecimalPoint() {
        TaskNode plan = new ScriptCall("x", Map.of("big", 5_000_000_000L));

        String json = PlanJson.toJson(plan);

        assertEquals("{\"type\":\"script\",\"script\":\"x\",\"params\":{\"big\":5000000000}}", json,
                "целые значения обязаны писаться без десятичной точки: иначе JSON заставил бы reader вернуть "
                        + "Double и тип параметра разошёлся бы с каталогом");
    }

    @Test
    void toJson_NullNode_IsRejected() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> PlanJson.toJson(null),
                "toJson с null-узлом обязан отказывать: сериализовать нечего, а тихий null оставил бы пустой снимок");

        assertEquals("node не может быть null: сериализовать нечего", error.getMessage(),
                "отказ за null-узел обязан называть поле 'node': по тексту видно, что именно не задано");
    }
}
