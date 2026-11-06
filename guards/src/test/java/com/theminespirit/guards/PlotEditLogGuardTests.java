package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlotEditLogGuardTests {
    private static final Map<String, String> HOOKS = hooks();

    @Test
    void productionSources_BreakHooks_LogPlotEdits() {
        Map<String, String> texts = new LinkedHashMap<>();
        HOOKS.keySet().forEach(file -> texts.put(file, read(file)));

        assertEquals(
                List.of(),
                offenders(HOOKS, texts),
                "Ломка блока у постройки без записи в лог делает повреждение хижины нерасследуемым: в observe11 "
                        + "дом потерял 12% блоков, а в логе не было ни строки, кто и что сломал. Как надо: ломающий "
                        + "дух идёт через BreakNotice.broken, игрок и взрыв — через PlotEditLog.broken в PolicyEvents. "
                        + "Нарушители");
    }

    @Test
    void hookScan_HookWithoutCall_IsReported() {
        Map<String, String> required = Map.of("a.java", "BreakNotice.broken(");

        assertEquals(
                List.of("a.java: нет BreakNotice.broken("),
                offenders(required, Map.of("a.java", "level.destroyBlock(pos, false, spirit);")),
                "Сторож ослеп: файл без вызова лога правок мира обязан попасть в нарушители");
    }

    @Test
    void hookScan_HookWithCall_IsAccepted() {
        Map<String, String> required = Map.of("a.java", "BreakNotice.broken(");

        assertEquals(
                List.of(),
                offenders(required, Map.of("a.java", "BreakNotice.broken(level, pos, state, spirit);")),
                "Сторож ложно срабатывает: вызов лога правок мира есть в файле");
    }

    private static List<String> offenders(Map<String, String> required, Map<String, String> texts) {
        List<String> offenders = new ArrayList<>();
        required.forEach((file, needle) -> {
            String text = texts.getOrDefault(file, "");
            if (!text.contains(needle)) {
                offenders.add(file + ": нет " + needle);
            }
        });
        return List.copyOf(offenders);
    }

    private static String read(String file) {
        try {
            return Files.readString(SourceFiles.path(file), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Файл хука правок мира '" + file + "' не прочитан: " + error.getMessage(), error);
        }
    }

    private static Map<String, String> hooks() {
        Map<String, String> hooks = new LinkedHashMap<>();
        hooks.put("neoforge/BlockBreaker.java", "BreakNotice.broken(");
        hooks.put("neoforge/policy/PolicyEvents.java", "PlotEditLog.broken(");
        return Map.copyOf(hooks);
    }
}
