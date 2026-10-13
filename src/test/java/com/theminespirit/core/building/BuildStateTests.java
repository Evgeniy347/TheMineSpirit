package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildStateTests {
    @Test
    void toMap_FromMap_RoundTripKeepsCounters() {
        BuildState state = new BuildState(3, 5, 1, 2, true);

        BuildState restored = BuildState.fromMap(state.toMap());

        assertEquals(state, restored,
                "Снимок обязан переживать сериализацию: иначе перезапуск теряет счёт поставленных и убранных блоков");
    }

    @Test
    void fromMap_NegativeValue_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BuildState.fromMap(Map.of("placed", -1)),
                "Отрицательный счётчик обязан отвергаться: иначе прогресс врёт и расход считается дважды");
    }

    @Test
    void fromMap_MissingKeys_GivesZeros() {
        BuildState restored = BuildState.fromMap(Map.of());

        assertEquals(new BuildState(0, 0, 0, 0, true), restored,
                "Старый снимок без ключей обязан читаться нулями: иначе обновление мода ломает незавершённые стройки");
        assertTrue(restored.unfinished(),
                "Снимок без флага обязан считаться незавершённым: иначе стройка не продолжится после перезапуска");
    }
}
