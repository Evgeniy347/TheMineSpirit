package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameTestTimeGuardTests {
    private static final String HELPER = "GameTestFixtures.java";
    private static final String[] TIME_MARKERS = {
            "setDayTime",
            "setRainLevel",
            "setWeatherParameters",
    };

    @Test
    void dayTimeChange_OutsideHelper_IsReported() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(List.of(), offenders,
                "Время дня и погоду в GameTest меняет только GameTestFixtures.holdDayTime: он запоминает значение, "
                        + "ставит нужное и возвращает его и при успехе, и при провале сцены. Прямой setDayTime в теле "
                        + "сцены при тайм-ауте не выполняется — ночь паука spider_NextToSpiritAtNight утекала "
                        + "в следующие сцены и ломала дневные тесты. Время/погоду держит хелпер, сцена только просит. "
                        + "Правило: todo.md → «Изоляция сцен GameTest». Нарушители");
    }

    @Test
    void dayTimePattern_DirectCallOutsideHelper_IsReported() {
        Map<Path, String> sources = Map.of(Path.of("MobTargetsGameTests.java"),
                "class Sample {\n"
                        + "    public static void spider_NextToSpiritAtNight_TakesTarget(GameTestHelper helper) {\n"
                        + "        helper.getLevel().setDayTime(18000);\n"
                        + "    }\n"
                        + "}\n");

        assertEquals(List.of("MobTargetsGameTests.java:3"), offendersIn(sources),
                "Сторож ослеп: прямой setDayTime вне хелпера должен попадать в нарушители");
    }

    @Test
    void dayTimePattern_HelperCallOutsideHelper_IsAccepted() {
        Map<Path, String> sources = Map.of(Path.of("MobTargetsGameTests.java"),
                "class Sample {\n"
                        + "    public static void spider_NextToSpiritAtNight_TakesTarget(GameTestHelper helper) {\n"
                        + "        GameTestFixtures.holdDayTime(helper, 18000);\n"
                        + "    }\n"
                        + "}\n");

        assertEquals(List.of(), offendersIn(sources),
                "Сторож ложно срабатывает: holdDayTime из сцены — законный способ держать ночь");
    }

    @Test
    void dayTimePattern_DirectCallInsideHelper_IsAccepted() {
        Map<Path, String> sources = Map.of(Path.of(HELPER),
                "class Sample {\n"
                        + "    static void holdDayTime(GameTestHelper helper, long dayTime) {\n"
                        + "        helper.getLevel().setDayTime(dayTime);\n"
                        + "    }\n"
                        + "}\n");

        assertEquals(List.of(), offendersIn(sources),
                "Сторож ложно срабатывает: сам хелпер менять время обязан — запрет только для сцен");
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> offenders.addAll(offendersIn(Map.of(file, text))));
    }

    private static List<String> offendersIn(Map<Path, String> sources) {
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            if (file.getFileName().toString().equals(HELPER)) {
                return;
            }
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                for (String marker : TIME_MARKERS) {
                    if (lines[i].contains(marker)) {
                        offenders.add(file + ":" + (i + 1));
                        break;
                    }
                }
            }
        });
        return offenders;
    }
}
