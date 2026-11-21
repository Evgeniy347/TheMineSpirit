package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LookAtParamsTests {
    @Test
    void target_Coordinates_BuildBlockCoordWithSameValues() {
        BlockCoord target = LookAtParams.target(Map.of("x", -5, "y", 70, "z", 12));

        assertEquals(
                new BlockCoord(-5, 70, 12),
                target,
                "look_at обязан смотреть ровно на заданную клетку: иначе ИИ получит описание чужой клетки"
                        + " При смене target_Coordinates_BuildBlockCoordWithSameValues проверь это утверждение.");
    }

    @Test
    void target_HeightOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LookAtParams.target(Map.of("x", 0, "y", 400, "z", 0)),
                "клетка выше мира обязана отклоняться до запуска скрипта"
                        + " Иначе тест target_HeightOutsideWorld_IsRejected перестанет ловить эту поломку. При смене target_HeightOutsideWorld_IsRejected проверь это утверждение.");
    }

    @Test
    void target_MissingCoordinate_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LookAtParams.target(Map.of("x", 0, "y", 64)),
                "без координаты z неизвестно, на какую клетку смотреть: параметр обязателен"
                        + " Иначе тест target_MissingCoordinate_IsRejected перестанет ловить эту поломку. При смене target_MissingCoordinate_IsRejected проверь это утверждение.");
    }
}
