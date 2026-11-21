package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NamespacedIdGuardTests {
    private static final String OWNER = "core/base/NamespacedId.java";
    private static final Pattern PREFIX_CONSTANT =
            Pattern.compile("(?m)^\\s*(?:public |private |protected )?static final String ([A-Z_]+) = \"minecraft:\";");
    private static final String RULE = "Префикс «minecraft:» снова объявлен отдельно: копии расходятся при "
            + "смене пространства имён или формата id, а правка одной оставляет остальные (ревью 19.11, P2-3). "
            + "Как надо: единственный источник — com.theminespirit.core.base.NamespacedId.MINECRAFT.";

    @Test
    void prefixConstant_OutsideOwner_IsReported() {
        assertEquals(
                List.of("core/board/PlanSteps.java: MINECRAFT"),
                problems(Map.of("core/board/PlanSteps.java", "    static final String MINECRAFT = \"minecraft:\";\n")),
                "Сторож ослеп: отдельное объявление префикса обязано попасть в нарушители"
                        + " Иначе тест prefixConstant_OutsideOwner_IsReported перестанет ловить эту поломку. При смене prefixConstant_OutsideOwner_IsReported проверь это утверждение.");
    }

    @Test
    void prefixConstant_InOwner_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of(OWNER, "    public static final String MINECRAFT = \"minecraft:\";\n")),
                "Сторож ложно срабатывает: NamespacedId — сам источник префикса"
                        + " Иначе тест prefixConstant_InOwner_IsAccepted перестанет ловить эту поломку. При смене prefixConstant_InOwner_IsAccepted проверь это утверждение.");
    }

    @Test
    void prefixes_RealSources_AreSingle() {
        assertEquals(
                List.of(),
                problems(sources()),
                RULE + System.lineSeparator() + "Нарушители (файл: имя):"
                        + " Иначе тест prefixes_RealSources_AreSingle перестанет ловить эту поломку. При смене prefixes_RealSources_AreSingle проверь это утверждение.");
    }

    private static Map<String, String> sources() {
        Map<String, String> sources = new TreeMap<>();
        SourceFiles.allMain().forEach((file, text) -> sources.put(SourceFiles.logical(file), text));
        return sources;
    }

    private static List<String> problems(Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(sources).forEach((logical, text) -> {
            if (OWNER.equals(logical)) {
                return;
            }
            Matcher matcher = PREFIX_CONSTANT.matcher(text);
            while (matcher.find()) {
                found.add(logical + ": " + matcher.group(1));
            }
        });
        return found;
    }
}
