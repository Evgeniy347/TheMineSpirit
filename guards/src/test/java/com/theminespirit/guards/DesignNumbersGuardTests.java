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

class DesignNumbersGuardTests {
    private static final Set<String> MIGRATED = Set.of(
            "core/board/TicketPlans.java",
            "core/board/TorchPlans.java",
            "core/board/RoutePlans.java",
            "core/board/TicketStoragePlans.java",
            "neoforge/board/GraphCrafting.java");
    private static final Pattern NUMBER_CONSTANT =
            Pattern.compile("(?m)^\\s*(?:public|private|protected)?\\s*static\\s+final\\s+(?:int|long)\\s+"
                    + "([A-Z0-9_]*(?:RADIUS|SIZE|TAKE|STEPS|BATCH|PARTS|LOGS)[A-Z0-9_]*)\\s*=\\s*-?\\d");
    private static final String RULE = "Число плана снова стало Java-константой: баланс меняется только "
            + "пересборкой мода, а копия в другом классе расходится при правке (ревью 19.11, P1-3). Как надо: "
            + "значение — в data/theminespirit/board/board.json → «plans» (PlanTuning), код читает через "
            + "BoardConfig.current().plans(). Переносить числа разрешено только из Java в данные; расширять "
            + "список MIGRATED в этом стороже — вместе с переносом очередного класса.";

    @Test
    void designNumber_InMigratedClass_IsReported() {
        assertEquals(
                List.of("core/board/TicketPlans.java: MINE_FLOOR_TAKE"),
                problems(Map.of("core/board/TicketPlans.java", "    private static final int MINE_FLOOR_TAKE = 32;\n")),
                "Сторож ослеп: число плана в перенесённом классе обязано попасть в нарушители");
    }

    @Test
    void designNumber_InOtherClass_IsNotFlaggedHere() {
        assertEquals(
                List.of(),
                problems(Map.of("core/forestry/ChopTuning.java", "public static final int MAX_LOGS = 128;\n")),
                "Сторож ложно срабатывает: перенесённые классы перечислены явно, чужие правила сюда не входят");
    }

    @Test
    void migrated_RealClasses_HaveNoDesignNumbers() {
        Map<String, String> sources = new TreeMap<>();
        SourceFiles.allMain().forEach((file, text) -> sources.put(SourceFiles.logical(file), text));
        assertEquals(List.of(), problems(sources), RULE + System.lineSeparator() + "Нарушители (файл: константа):");
    }

    private static List<String> problems(Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(sources).forEach((logical, text) -> {
            if (!MIGRATED.contains(logical)) {
                return;
            }
            Matcher matcher = NUMBER_CONSTANT.matcher(text);
            while (matcher.find()) {
                found.add(logical + ": " + matcher.group(1));
            }
        });
        return found;
    }
}
