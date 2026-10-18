package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildShelterParamsTests {
    @Test
    void from_SideThreeAndFour_Accepted() {
        assertEquals(3, BuildShelterParams.from(Map.of("side", 3)).side(),
                "Укрытие 3×3 обязано приниматься: тесное, но дух встаёт внутри");
        assertEquals(4, BuildShelterParams.from(Map.of("side", 4)).side(),
                "Укрытие 4×4 обязано приниматься: это размер быстрого укрепления Fortify");
    }

    @Test
    void from_SideTwo_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BuildShelterParams.from(Map.of("side", 2)),
                "Сторона 2 обязана отвергаться: внутри нет клетки для духа");
    }

    @Test
    void from_SideFive_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BuildShelterParams.from(Map.of("side", 5)),
                "Сторона 5 обязана отвергаться: бюджет укрепления не рассчитан на такую коробку");
    }

    @Test
    void from_MissingSide_IsRejectedByParams() {
        assertThrows(IllegalArgumentException.class, () -> BuildShelterParams.from(Map.of()),
                "Параметры без стороны обязаны отвергаться: умолчание подставляет валидатор плана, а не Params");
    }
}
