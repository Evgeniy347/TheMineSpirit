package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.reload.DataTables;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardLevelsConfigTests {
    private static Map<String, Integer> expectedLevels() {
        return Map.of(
                "BUILD_MINE",
                4,
                "BUILD_PEN",
                3,
                "ENCHANT_ROOM",
                4,
                "BUILD_HOUSE",
                4,
                "BUILD_WAREHOUSE",
                4,
                "BUILD_STREETS",
                2,
                "BUILD_FENCE",
                3,
                "LIGHTING",
                2);
    }

    @Test
    void defaults_FromClasspath_LevelsMatchLiterals() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(
                expectedLevels(),
                config.levels(),
                "Таблица уровней обязана совпадать с литералами слово в слово: лишний ключ в "
                        + "файле без теста и забытый ключ в тесте красят прогон, а молчаливое "
                        + "расхождение изменило бы число уровней постройки");
        assertEquals(
                4,
                config.levels().get("BUILD_MINE"),
                "Предел в тесте, факт из production: у шахты 4 уровня (проход, расширение, " + "ступеньки, рельсы)");
    }
}
