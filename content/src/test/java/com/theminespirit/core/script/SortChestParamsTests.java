package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SortChestParamsTests {
    @Test
    void from_NearestContainer_ParsesTargetAndRadius() {
        SortChestParams params = SortChestParams.from(Map.of("container", "nearest", "radius", 8));

        assertEquals(
                ContainerTarget.NEAREST,
                params.container(),
                "Контейнер nearest обязан разбираться в цель поиска ближайшего: иначе дух искал бы "
                        + "контейнер с буквальным именем nearest"
                        + " При смене from_NearestContainer_ParsesTargetAndRadius проверь это утверждение.");
        assertEquals(
                8,
                params.radius(),
                "Радиус 8 обязан сохраняться: по нему ограничивается поиск ближайшего контейнера"
                        + " Иначе тест from_NearestContainer_ParsesTargetAndRadius перестанет ловить эту поломку. При смене from_NearestContainer_ParsesTargetAndRadius проверь это утверждение.");
    }

    @Test
    void from_CoordinatesTarget_ParsesPosition() {
        SortChestParams params = SortChestParams.from(Map.of("container", "12,64,-7", "radius", 8));

        assertEquals(
                new ContainerTarget.Position(12, 64, -7),
                params.container(),
                "Координаты обязаны разбираться в точную позицию: иначе сортировка шла бы не в тот сундук"
                        + " При смене from_CoordinatesTarget_ParsesPosition проверь это утверждение.");
    }

    @Test
    void from_RadiusBeyondLimits_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SortChestParams.from(Map.of("container", "nearest", "radius", 17)),
                "Радиус 17 при пределе 16 обязан отклоняться: иначе дух уходил бы из базовой зоны"
                        + " При смене from_RadiusBeyondLimits_Rejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вне диапазона"),
                "Радиус 17 при пределе 16 обязан отклоняться с объяснением диапазона, иначе дух уходил бы "
                        + "из базовой зоны: " + error.getMessage()
                        + " При смене from_RadiusBeyondLimits_Rejected проверь это утверждение.");
    }
}
