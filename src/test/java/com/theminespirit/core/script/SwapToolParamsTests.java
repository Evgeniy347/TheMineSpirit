package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SwapToolParamsTests {
    @Test
    void from_BlockPresent_KeepsIt() {
        SwapToolParams params = SwapToolParams.from(Map.of("block", "minecraft:stone"));

        assertEquals("minecraft:stone", params.block(),
                "swap_tool обязан сохранить блок: по нему выбирается инструмент");
    }

    @Test
    void from_ShortBlockId_IsKept() {
        SwapToolParams params = SwapToolParams.from(Map.of("block", "stone"));

        assertEquals("stone", params.block(),
                "swap_tool обязан принимать короткий id блока: скрипт сам добавит пространство имён minecraft");
    }

    @Test
    void from_MissingBlock_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SwapToolParams.from(Map.of()),
                "swap_tool без блока обязан отказывать: неизвестно, для чего выбирать инструмент");

        assertTrue(error.getMessage().contains("block"),
                "Отказ обязан называть параметр 'block'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankBlock_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SwapToolParams.from(Map.of("block", " ")),
                "swap_tool с пустым блоком обязан отказывать: пустой id не сопоставить с блоком");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> SwapToolParams.from(null),
                "SwapToolParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать");
    }
}
