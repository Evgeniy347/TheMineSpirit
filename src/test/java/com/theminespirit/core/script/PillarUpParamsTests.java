package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PillarUpParamsTests {
    @Test
    void from_HeightOnly_BlockIsChosenByScript() {
        PillarUpParams params = PillarUpParams.from(Map.of("height", 4));

        assertEquals(4, params.height(), "высота из параметра доходит до плана без изменений");
        assertNull(params.block(), "блок не задан — скрипт обязан выбрать дешёвый из инвентаря, а не подставлять имя");
        assertEquals(false, params.down(), "по умолчанию столб остаётся стоять: разбор — явное намерение");
    }

    @Test
    void from_BlockAndDown_Parsed() {
        PillarUpParams params = PillarUpParams.from(Map.of("height", 2, "block", "cobblestone", "down", true));

        assertEquals("cobblestone", params.block(), "явный блок обязан дойти до скрипта");
        assertTrue(params.down(), "down=true обязан включать разбор столба после подъёма");
    }

    @Test
    void from_ZeroHeight_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PillarUpParams.from(Map.of("height", 0)),
                "нулевая высота не строит столб: параметр обязан отвергаться до запуска");

        assertTrue(error.getMessage().contains("0"),
                "сообщение обязано называть высоту 0, фактическое: " + error.getMessage());
    }
}
