package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Сторож числа сцен GameTest (чеклист 56, п.3): сцена механики — класс {@code *ScenesGameTests} с 1–3 сценами, бывшие
 * сцены идут в нём шагами {@code @SceneStep}; общее число сцен не растёт.
 */
class SceneCountGuardTests {
    private static final Pattern GAME_TEST = Pattern.compile("^\\s*@GameTest\\s*\\(", Pattern.MULTILINE);
    private static final Pattern SCENES_CLASS = Pattern.compile("[A-Z][A-Za-z0-9]*ScenesGameTests");
    private static final int MAX_SCENES_PER_MECHANIC = 3;

    // Потолок сцен общего прогона (без ручных демо-аудитов) после сокращения 25.10: было 848, стало 49.
    private static final int MAX_SCENES_TOTAL = 49;

    // Классы вне схемы «механика → *ScenesGameTests» и их потолок: демо-аудиты — ручные, вне общего прогона;
    // FirstStageGameTests — сквозная сцена этапа 1 (заменила JungleSettlementGameTests). Новой записи сюда не
    // добавлять: новая сцена — шаг сцены механики.
    private static final Map<String, Integer> OUTSIDE_SCENES = Map.of(
            "DemoAuditGameTests", 110,
            "BoardFirstStepsGameTests", 11,
            "BoardSettlementStartGameTests", 4,
            "SettlementChestGameTests", 3,
            "SettlementWoodBoardGameTests", 1,
            "SettlementChunkGameTests", 1,
            "FirstStageGameTests", 1);

    @Test
    void scenes_PerMechanicAndTotal_DoNotGrow() {
        Map<String, Integer> counts = countsOf(SourceFiles.javaUnder(SourceFiles.GAME_TESTS));
        List<String> offenders = offenders(counts);
        int total = total(counts);

        assertEquals(
                List.of(),
                offenders,
                "Сцена GameTest — это класс механики *ScenesGameTests с 1–" + MAX_SCENES_PER_MECHANIC
                        + " сценами: каждая лишняя сцена — своя пачка и ~0,4 с прогона, а прогон ограничен 200 с. "
                        + "Новую проверку оформи шагом @SceneStep в классе механики (сцена соберёт её сама) или "
                        + "unit-тестом, если ей не нужен мир. Нарушители");
        assertTrue(
                total <= MAX_SCENES_TOTAL,
                "Сцен общего прогона " + total + " больше потолка " + MAX_SCENES_TOTAL
                        + ": число сцен не растёт (чеклист 56). Новая проверка — шаг @SceneStep сцены механики; "
                        + "потолок снижать, когда сцен стало меньше, а не поднимать");
    }

    @Test
    void sceneCountPattern_FourScenesInMechanic_Flagged() {
        String sample = """
                        class FarmScenesGameTests {
                            @GameTest(template = "arena") public static void a(GameTestHelper helper) {}
                            @GameTest(template = "arena") public static void b(GameTestHelper helper) {}
                            @GameTest(template = "arena") public static void c(GameTestHelper helper) {}
                            @GameTest(template = "arena") public static void d(GameTestHelper helper) {}
                        }
                        """;

        assertEquals(
                List.of("FarmScenesGameTests: сцен 4 > " + MAX_SCENES_PER_MECHANIC),
                offenders(Map.of("FarmScenesGameTests", count(sample))),
                "Сторож ослеп: 4 сцены в классе механики обязаны попадать в нарушители");
    }

    @Test
    void sceneCountPattern_GameTestOutsideScenesClass_Flagged() {
        String sample = """
                        @GameTestHolder(ModIdentity.MOD_ID)
                        class FarmGameTests {
                            @GameTest(template = "arena") public static void a(GameTestHelper helper) {}
                            @SceneStep(template = "arena", timeoutTicks = 20) public static void b(GameTestHelper h) {}
                        }
                        """;

        assertEquals(
                List.of("FarmGameTests: сцен 1 вне класса *ScenesGameTests"),
                offenders(Map.of("FarmGameTests", count(sample))),
                "Сторож ослеп: @GameTest вне класса сцен механики обязан попадать в нарушители, а @GameTestHolder и "
                        + "@SceneStep — нет");
    }

    @Test
    void sceneCountPattern_StepsOnly_NotCounted() {
        String sample = """
                        @GameTestHolder(ModIdentity.MOD_ID)
                        class FarmGameTests {
                            @SceneStep(template = "arena", timeoutTicks = 20) public static void b(GameTestHelper h) {}
                        }
                        """;

        assertEquals(0, count(sample), "Сторож ложно срабатывает: шаг @SceneStep — не сцена");
    }

    private static Map<String, Integer> countsOf(Map<Path, String> sources) {
        Map<String, Integer> counts = new TreeMap<>();
        sources.forEach((file, text) -> {
            int scenes = count(text);
            if (scenes > 0) {
                counts.put(file.getFileName().toString().replace(".java", ""), scenes);
            }
        });
        return counts;
    }

    private static int count(String text) {
        Matcher matcher = GAME_TEST.matcher(SourceFiles.withoutTextBlocks(text));
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static List<String> offenders(Map<String, Integer> counts) {
        List<String> offenders = new ArrayList<>();
        new TreeMap<>(counts).forEach((owner, scenes) -> {
            Integer ceiling = OUTSIDE_SCENES.get(owner);
            if (ceiling != null) {
                if (scenes > ceiling) {
                    offenders.add(owner + ": сцен " + scenes + " > " + ceiling + " (класс вне схемы, не растёт)");
                }
            } else if (!SCENES_CLASS.matcher(owner).matches()) {
                offenders.add(owner + ": сцен " + scenes + " вне класса *ScenesGameTests");
            } else if (scenes > MAX_SCENES_PER_MECHANIC) {
                offenders.add(owner + ": сцен " + scenes + " > " + MAX_SCENES_PER_MECHANIC);
            }
        });
        return offenders;
    }

    private static int total(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .filter(entry -> !"DemoAuditGameTests".equals(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }
}
