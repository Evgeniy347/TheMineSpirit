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

class SceneCountGuardTests {
    private static final Pattern GAME_TEST = Pattern.compile("^\\s*@GameTest\\s*\\(", Pattern.MULTILINE);
    private static final Pattern SCENE_STEP = Pattern.compile("^\\s*@SceneStep\\s*\\(", Pattern.MULTILINE);
    private static final Pattern SCENES_CLASS = Pattern.compile("[A-Z][A-Za-z0-9]*ScenesGameTests");
    private static final String DEMO_AUDITS = "DemoAuditGameTests";
    private static final int MAX_SCENES_PER_MECHANIC = 3;

    private static final int MAX_SCENES_TOTAL = 29;

    private static final Map<String, Integer> OUTSIDE_SCENES =
            Map.of(DEMO_AUDITS, 110, "SettlementChestGameTests", 1, "FirstStageGameTests", 1);

    @Test
    void scenes_PerMechanicAndTotal_DoNotGrow() {
        Map<Path, String> sources = SourceFiles.javaUnder(SourceFiles.GAME_TESTS);
        Map<String, Integer> counts = countsOf(sources);
        List<String> offenders = offenders(counts);
        int total = total(sources);

        assertEquals(
                List.of(),
                offenders,
                "Сцена GameTest — это класс механики *ScenesGameTests с 1–" + MAX_SCENES_PER_MECHANIC
                        + " аренами: каждая лишняя арена — своя пачка и ~0,4 с прогона, а прогон ограничен 200 с. "
                        + "Новую проверку оформи шагом @SceneStep в классе механики (арена соберёт её сама) или "
                        + "unit-тестом, если ей не нужен мир. Нарушители");
        assertTrue(
                total <= MAX_SCENES_TOTAL,
                "Сцен общего прогона (шагов @SceneStep и отдельных @GameTest вне арен) " + total
                        + " больше потолка " + MAX_SCENES_TOTAL + ": GameTest — только крупные интеграционные прогоны "
                        + "(решение владельца 28.10), мелкая проверка стоит секунды прогона и ловит то, что ловит "
                        + "JUnit в core. Как надо: проверку без мира — JUnit в core, новую сцену — вместо старой; "
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
                offenders(Map.of("FarmScenesGameTests", count(GAME_TEST, sample))),
                "Сторож ослеп: 4 арены в классе механики обязаны попадать в нарушители");
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
                offenders(Map.of("FarmGameTests", count(GAME_TEST, sample))),
                "Сторож ослеп: @GameTest вне класса сцен механики обязан попадать в нарушители, а @GameTestHolder и "
                        + "@SceneStep — нет");
    }

    @Test
    void sceneTotalPattern_StepsAndOutsideTests_CountedArenasAndDemoNot() {
        Map<Path, String> sources = Map.of(
                Path.of("FarmGameTests.java"),
                """
                        class FarmGameTests {
                            @SceneStep(template = "arena", timeoutTicks = 20) public static void a(GameTestHelper h) {}
                            @SceneStep(template = "arena", timeoutTicks = 20) public static void b(GameTestHelper h) {}
                        }
                        """,
                Path.of("FarmScenesGameTests.java"),
                """
                        class FarmScenesGameTests {
                            @GameTest(template = "arena") public static void arena(GameTestHelper helper) {}
                        }
                        """,
                Path.of("FirstStageGameTests.java"),
                """
                        class FirstStageGameTests {
                            @GameTest(template = "empty") public static void stage(GameTestHelper helper) {}
                        }
                        """,
                Path.of(DEMO_AUDITS + ".java"),
                """
                        class DemoAuditGameTests {
                            @GameTest(template = "empty") public static void audit(GameTestHelper helper) {}
                        }
                        """);

        assertEquals(
                3,
                total(sources),
                "Сторож числа сцен ослеп или ложно считает: шаги @SceneStep и @GameTest вне арен — сцены, "
                        + "обёртка-арена *ScenesGameTests и ручные демо-аудиты — нет");
    }

    private static Map<String, Integer> countsOf(Map<Path, String> sources) {
        Map<String, Integer> counts = new TreeMap<>();
        sources.forEach((file, text) -> {
            int scenes = count(GAME_TEST, text);
            if (scenes > 0) {
                counts.put(owner(file), scenes);
            }
        });
        return counts;
    }

    private static String owner(Path file) {
        return file.getFileName().toString().replace(".java", "");
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(SourceFiles.withoutTextBlocks(text));
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

    private static int total(Map<Path, String> sources) {
        int total = 0;
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String owner = owner(source.getKey());
            total += count(SCENE_STEP, source.getValue());
            if (!DEMO_AUDITS.equals(owner) && !SCENES_CLASS.matcher(owner).matches()) {
                total += count(GAME_TEST, source.getValue());
            }
        }
        return total;
    }
}
