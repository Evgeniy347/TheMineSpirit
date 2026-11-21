package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoreApiPurityGuardTests {
    private static final Set<String> OWNERS = Set.of("core/inventory/api/JsonResource.java");
    private static final Pattern PUBLIC_JSON =
            Pattern.compile("(?m)^[ \\t]*public\\s+[^\\n;{]*\\b(?:JsonNode|ObjectNode)\\b");
    private static final String RULE = "Jackson снова в публичной сигнатуре ядра: замена библиотеки или "
            + "формата затронет публичный API, а упаковка через jarJar связность не снимает (ревью 19.11, "
            + "P2-2). Как надо: JsonNode/ObjectNode — только внутри *JsonResource и кодеков (package-private), "
            + "наружу — доменные типы или String; разбор файла остаётся приватной деталью.";

    @Test
    void publicMethod_InCoreWithJsonNode_IsReported() {
        assertEquals(
                List.of("core/board/ToolsConfig.java: public static ToolsConfig fromJson(JsonNode"),
                problems(Map.of(
                        "core/board/ToolsConfig.java", "    public static ToolsConfig fromJson(JsonNode node) {\n")),
                "Сторож ослеп: JsonNode в public-сигнатуре ядра обязан попасть в нарушители"
                        + " Иначе тест publicMethod_InCoreWithJsonNode_IsReported перестанет ловить эту поломку. При смене publicMethod_InCoreWithJsonNode_IsReported проверь это утверждение.");
    }

    @Test
    void packagePrivate_Parser_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of("core/board/PlanTuning.java", "    static PlanTuning fromJson(JsonNode node) {\n")),
                "Сторож ложно срабатывает: package-private разбор файла — внутренняя деталь, а не API"
                        + " Иначе тест packagePrivate_Parser_IsAccepted перестанет ловить эту поломку. При смене packagePrivate_Parser_IsAccepted проверь это утверждение.");
    }

    @Test
    void jsonResource_ExemptType_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of(
                        "core/inventory/api/JsonResource.java",
                        "    public static JsonNode parse(String json, String origin) {\n")),
                "Сторож ложно срабатывает: JsonResource — владелец типа дерева, его сигнатуры осознанно работают с JsonNode"
                        + " Иначе тест jsonResource_ExemptType_IsAccepted перестанет ловить эту поломку. При смене jsonResource_ExemptType_IsAccepted проверь это утверждение.");
    }

    @Test
    void core_RealSignatures_HaveNoPublicJackson() {
        Map<String, String> sources = new TreeMap<>();
        SourceFiles.allMain().forEach((file, text) -> sources.put(SourceFiles.logical(file), text));
        assertEquals(
                List.of(),
                problems(sources),
                RULE + System.lineSeparator() + "Нарушители (файл: строка):"
                        + " Иначе тест core_RealSignatures_HaveNoPublicJackson перестанет ловить эту поломку. При смене core_RealSignatures_HaveNoPublicJackson проверь это утверждение.");
    }

    private static List<String> problems(Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(sources).forEach((logical, text) -> {
            if (!logical.startsWith("core/") || OWNERS.contains(logical)) {
                return;
            }
            Matcher matcher = PUBLIC_JSON.matcher(text);
            while (matcher.find()) {
                found.add(logical + ": "
                        + text.substring(matcher.start(), matcher.end()).trim());
            }
        });
        return found;
    }
}
