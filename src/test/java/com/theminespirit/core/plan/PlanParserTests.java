package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanParserTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();

    @Test
    void parse_ScriptNode_ReturnsScriptCallWithTypedParams() {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":5}}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new ScriptCall("wait", Map.of("ticks", 5)), node,
                "Узел script обязан собираться в ScriptCall с типизированными параметрами: иначе движок получит "
                        + "не тот тип и не сможет исполнить шаг");
    }

    @Test
    void parse_SequenceNode_ReturnsOrderedSteps() {
        String json = "{\"type\":\"sequence\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":1}},"
                + "{\"type\":\"script\",\"script\":\"return_home\"}]}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new SequenceNode(List.of(
                new ScriptCall("wait", Map.of("ticks", 1)),
                new ScriptCall("return_home", Map.of()))), node,
                "Узел sequence обязан сохранять порядок шагов: перестановка шагов меняет смысл плана");
    }

    @Test
    void parse_RepeatNode_ReturnsTimesAndStep() {
        String json = "{\"type\":\"repeat\",\"times\":3,"
                + "\"step\":{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":2}}}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new RepeatNode(3, new ScriptCall("wait", Map.of("ticks", 2))), node,
                "Узел repeat обязан нести число повторов и шаг: потеря times превращает повтор в одиночное действие");
    }

    @Test
    void parse_FallbackNode_ReturnsSteps() {
        String json = "{\"type\":\"fallback\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"sort_chest\"},"
                + "{\"type\":\"script\",\"script\":\"return_home\"}]}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new FallbackNode(List.of(
                new ScriptCall("sort_chest", Map.of("container", "nearest", "radius",
                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                new ScriptCall("return_home", Map.of()))), node,
                "Узел fallback обязан нести шаги по порядку: потеря порядка ломает стратегию 'первый успешный'. "
                        + "Заодно видны подставленные умолчания sort_chest: без них скрипт контейнера не понял бы, "
                        + "куда идти");
    }

    @Test
    void parse_ParallelNode_ReturnsModeAndSteps() {
        String json = "{\"type\":\"parallel\",\"mode\":\"any\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"attack\",\"params\":{\"target\":\"zombie\"}},"
                + "{\"type\":\"script\",\"script\":\"flee\"}]}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new ParallelNode(ParallelMode.ANY, List.of(
                new ScriptCall("attack", Map.of("target", "zombie")),
                new ScriptCall("flee", Map.of()))), node,
                "Узел parallel обязан нести режим all/any: перепутанный режим меняет условие завершения "
                        + "и дух либо ждёт вечно, либо бросает начатое");
    }

    @Test
    void parse_ConditionNode_ReturnsBranchesAndParams() {
        String json = "{\"type\":\"condition\",\"check\":\"has_item\",\"params\":{\"item\":\"oak_log\"},"
                + "\"then\":{\"type\":\"script\",\"script\":\"craft\",\"params\":{\"item\":\"stick\",\"count\":4}},"
                + "\"else\":{\"type\":\"script\",\"script\":\"acquire\",\"params\":{\"item\":\"oak_log\",\"count\":1}}}";
        List<ConditionSpec> specs = List.of(
                new ConditionSpec("has_item", "has_item",
                        List.of(ParamSpec.text("item", true))));
        ConditionCatalog conditions = ConditionCatalog.of(specs);

        TaskNode node = PlanParser.parse(json, CATALOG, conditions);

        assertEquals(new ConditionNode("has_item", Map.of("item", "oak_log"),
                new ScriptCall("craft", Map.of("item", "stick", "count", 4)),
                new ScriptCall("acquire", Map.of("item", "oak_log", "count", 1))), node,
                "Узел condition обязан нести проверку, параметры и обе ветки: потеря ветки else лишает план запасного пути");
    }

    @Test
    void parse_ConditionNodeWithoutElse_ReturnsNullElseBranch() {
        String json = "{\"type\":\"condition\",\"check\":\"is_day\","
                + "\"then\":{\"type\":\"script\",\"script\":\"return_home\"}}";
        List<ConditionSpec> specs = List.of(
                new ConditionSpec("is_day", "is_day", List.of()));
        ConditionCatalog conditions = ConditionCatalog.of(specs);

        TaskNode node = PlanParser.parse(json, CATALOG, conditions);

        assertEquals(new ConditionNode("is_day", Map.of(),
                new ScriptCall("return_home", Map.of()), null), node,
                "Условие без ветки else обязано давать null в elseStep: движок трактует это как 'ничего не делать'");
    }

    @Test
    void parse_ScanWithFilterArray_ReturnsStringList() {
        String json = "{\"type\":\"script\",\"script\":\"scan\","
                + "\"params\":{\"radius\":32,\"filter\":[\"*_log\",\"*_ore\"]}}";

        TaskNode node = PlanParser.parse(json, CATALOG);

        assertEquals(new ScriptCall("scan", Map.of("radius", 32, "filter", List.of("*_log", "*_ore"))), node,
                "scan.filter принимает массив строк: иначе планировщик не сможет задать фильтр блоков, как в API.md");
    }

    @Test
    void parse_ScanWithFilterString_Throws() {
        String json = "{\"type\":\"script\",\"script\":\"scan\",\"params\":{\"filter\":\"*_log\"}}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "scan.filter объявлен как string[] и обязан принимать только массив: одиночная строка молча "
                        + "превратилась бы в список из символов и фильтр блоков перестал бы работать");

        assertTrue(error.getMessage().contains("filter"),
                "Сообщение обязано называть параметр 'filter' с неверным типом. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_StringParamWithArray_Throws() {
        String json = "{\"type\":\"script\",\"script\":\"say\",\"params\":{\"text\":[\"привет\",\"мир\"]}}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Параметр типа string обязан принимать только JSON-строку: приём массива маскировал бы ошибку "
                        + "типа в плане и ломал контракт string-параметров");

        assertTrue(error.getMessage().contains("text"),
                "Сообщение обязано называть параметр 'text' с неверным типом. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownScript_ThrowsNamingScript() {
        String json = "{\"type\":\"script\",\"script\":\"fly_to_moon\"}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Неизвестный скрипт обязан отвергаться: иначе whitelist не работает и ИИ исполнит произвольное действие");

        assertTrue(error.getMessage().contains("fly_to_moon"),
                "Сообщение обязано называть неизвестный скрипт 'fly_to_moon': по нему правят план. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_MissingRequiredParam_ThrowsNamingParam() {
        String json = "{\"type\":\"script\",\"script\":\"say\",\"params\":{}}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Отсутствие обязательного параметра должно отвергаться: иначе скрипт получит null вместо значения "
                        + "и поведение станет неопределённым");

        assertTrue(error.getMessage().contains("text"),
                "Сообщение обязано называть пропущенный параметр 'text': по нему правят план. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_WrongParamType_Throws() {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":\"soon\"}}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Неверный тип параметра обязан отвергаться: иначе движок получит строку вместо числа тиков "
                        + "и упадёт в рантайме уже после старта задачи");

        assertTrue(error.getMessage().contains("ticks"),
                "Сообщение обязано называть параметр 'ticks' с неверным типом. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownParam_ThrowsNamingParam() {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":5,\"speed\":9}}";

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Лишний параметр обязан отвергаться: иначе опечатка в имени молча игнорируется, и скрипт работает "
                        + "не так, как задумал планировщик");

        assertTrue(error.getMessage().contains("speed"),
                "Сообщение обязано называть лишний параметр 'speed'. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_DepthOverLimit_Throws() {
        String json = nestedSequences(20);

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                "Глубина дерева больше 16 обязана отвергаться: без лимита рекурсивный план переполнит стек "
                        + "и уронит серверный тик");

        assertTrue(error.getMessage().contains("16"),
                "Сообщение о глубине обязано называть лимит 16. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_NodeCountOverLimit_Throws() {
        StringBuilder json = new StringBuilder("{\"type\":\"sequence\",\"steps\":[");
        for (int i = 0; i < 300; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":1}}");
        }
        json.append("]}");

        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json.toString(), CATALOG),
                "Больше 256 узлов обязано отвергаться: без лимита огромный план займёт серверный тик надолго");

        assertTrue(error.getMessage().contains("256"),
                "Сообщение о числе узлов обязано называть лимит 256. Фактическое: " + error.getMessage());
    }

    private static String nestedSequences(int depth) {
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
