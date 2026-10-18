package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DisengageParamsTests {
    @Test
    void from_EmptyMap_TargetsHostileAtDefaultDistance() {
        DisengageParams params = DisengageParams.from(Map.of());

        assertEquals("hostile", params.target(),
                "disengage без цели обязан выходить из боя с враждебными мобами — это и есть бой по умолчанию");
        assertEquals(CatalogLimits.DISENGAGE_DISTANCE_DEFAULT, params.distance(),
                "умолчание дистанции берётся из CatalogLimits, а не из литерала в скрипте");
        assertEquals(5, CatalogLimits.DISENGAGE_DISTANCE_DEFAULT,
                "умолчание disengage согласовано как 5 блоков: дальше — это уже flee, ближе — дух остаётся в бою");
    }

    @Test
    void from_TargetAndDistance_Parsed() {
        DisengageParams params = DisengageParams.from(Map.of("target", "nearest", "distance", 3));

        assertEquals("nearest", params.target(), "явная цель обязана дойти до скрипта");
        assertEquals(3, params.distance(), "явная дистанция обязана дойти до скрипта");
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisengageParams.from(Map.of("target", "  ")),
                "пустая цель обязана отвергаться: пустую строку нельзя сопоставить с существом, и дух "
                        + "отойдёт не от того");
    }

    @Test
    void from_UnparseableTarget_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisengageParams.from(Map.of("target", "dragon")),
                "неразбираемая цель обязана отвергаться до запуска: иначе скрипт упадёт уже в мире");
    }

    @Test
    void from_DistanceAboveMaximum_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DisengageParams.from(Map.of("distance", 17)),
                "дистанция больше максимума обязана отвергаться: дальний отход — это flee или retreat, а не disengage");

        assertTrue(error.getMessage().contains("17"),
                "отказ обязан называть полученную дистанцию 17, фактическое: " + error.getMessage());
    }

    @Test
    void from_DistanceBelowMinimum_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisengageParams.from(Map.of("distance", 0)),
                "дистанция меньше минимума обязана отвергаться: отход на 0 блоков оставляет духа в бою");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> DisengageParams.from(null),
                "DisengageParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}
