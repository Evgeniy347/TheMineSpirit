package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GameTestStoreGuardTests {
    private static final Pattern GAMETEST_ANNOTATION =
            Pattern.compile("@GameTest\\s*\\(([^)]*)\\)", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern BATCH = Pattern.compile("batch\\s*=\\s*\"([^\"]*)\"");
    private static final String STORE = "SettlementPlotStore";

    @Test
    void gameTest_SharedLevelStoreWithoutOwnBatch_IsReported() {
        List<String> offenders = new ArrayList<>();
        Map<String, String> batches = new HashMap<>();
        SourceFiles.javaUnder(SourceFiles.GAME_TESTS).forEach((file, text) -> {
            if (!text.contains(STORE)) {
                return;
            }
            for (Method method : methods(text)) {
                if (method.batch() == null || method.batch().isBlank()) {
                    offenders.add(
                            file + ":" + SourceFiles.lineOf(text, method.annotationStart()) + " — " + method.name());
                    continue;
                }
                String previous = batches.putIfAbsent(method.batch(), method.name());
                if (previous != null) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, method.annotationStart()) + " — "
                            + method.name() + " делит batch с " + previous);
                }
            }
        });
        assertEquals(
                List.of(),
                offenders,
                "Сцена с общим SettlementPlotStore обязана иметь свою batch: без этого параллельные арены "
                        + "пишут в одно хранилище уровня и валят друг друга на ровном месте; при смене "
                        + "разметки сцен проверь уникальность batch у всех файлов с SettlementPlotStore");
    }

    @Test
    void gameTest_StoreSceneWithOwnBatch_IsClean() {
        String sample = """
                        class SampleScenesGameTests {
                            @GameTest(template = "arena", batch = "scene_store_a", timeoutTicks = 40)
                            public static void level_Stored_Restores(GameTestHelper helper) {
                                SettlementPlotStore.get(helper.getLevel()).clear();
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                offendersOf(sample),
                "Сторож ложно срабатывает: сцена со своей batch — законное решение, иначе авторы начнут "
                        + "обходить сторожа пустой batch; при смене проверки батчей проверь этот образец");
    }

    @Test
    void gameTest_StoreSceneWithoutBatch_IsReported() {
        String sample = """
                        class SampleScenesGameTests {
                            @GameTest(template = "arena", timeoutTicks = 40)
                            public static void level_Stored_Restores(GameTestHelper helper) {
                                SettlementPlotStore.get(helper.getLevel()).clear();
                            }
                        }
                        """;

        assertEquals(
                List.of("level_Stored_Restores"),
                offendersOf(sample),
                "Сторож ослеп: сцена с SettlementPlotStore без batch обязана попадать в нарушители, иначе "
                        + "чужие арены снова делят хранилище; при смене разметки проверь этот образец");
    }

    @Test
    void gameTest_TwoStoreScenesShareBatch_IsReported() {
        String sample = """
                        class SampleScenesGameTests {
                            @GameTest(template = "arena", batch = "scene_store", timeoutTicks = 40)
                            public static void first_Stored_Writes(GameTestHelper helper) {
                                SettlementPlotStore.get(helper.getLevel()).clear();
                            }
                            @GameTest(template = "arena", batch = "scene_store", timeoutTicks = 40)
                            public static void second_Stored_Writes(GameTestHelper helper) {
                                SettlementPlotStore.get(helper.getLevel()).clear();
                            }
                        }
                        """;

        assertEquals(
                List.of("second_Stored_Writes делит batch с first_Stored_Writes"),
                offendersOf(sample),
                "Сторож ослеп: две сцены с хранилищем в одной batch снова пишут одновременно, иначе тест "
                        + "флекает на ровном месте; при смене проверки уникальности проверь этот образец");
    }

    private static List<String> offendersOf(String text) {
        List<String> offenders = new ArrayList<>();
        Map<String, String> batches = new HashMap<>();
        if (!text.contains(STORE)) {
            return offenders;
        }
        for (Method method : methods(text)) {
            if (method.batch() == null || method.batch().isBlank()) {
                offenders.add(method.name());
                continue;
            }
            String previous = batches.putIfAbsent(method.batch(), method.name());
            if (previous != null) {
                offenders.add(method.name() + " делит batch с " + previous);
            }
        }
        return offenders;
    }

    private static List<Method> methods(String text) {
        List<Method> methods = new ArrayList<>();
        Matcher matcher = GAMETEST_ANNOTATION.matcher(text);
        while (matcher.find()) {
            int nameStart = text.indexOf('(', text.indexOf("void", matcher.end()));
            if (nameStart < 0) {
                continue;
            }
            int nameEnd = nameStart;
            while (nameEnd > 0 && Character.isJavaIdentifierPart(text.charAt(nameEnd - 1))) {
                nameEnd--;
            }
            Matcher batch = BATCH.matcher(matcher.group(1));
            methods.add(new Method(
                    text.substring(nameEnd, nameStart), matcher.start(), batch.find() ? batch.group(1) : null));
        }
        return methods;
    }

    private record Method(String name, int annotationStart, String batch) {}
}
