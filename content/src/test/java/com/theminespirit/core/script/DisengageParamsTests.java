package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DisengageParamsTests {
    @Test
    void from_EmptyMap_TargetsHostileAtDefaultDistance() {
        DisengageParams params = DisengageParams.from(Map.of());

        assertEquals(
                "hostile",
                params.target(),
                "disengage без цели обязан выходить из боя с враждебными мобами — это и есть бой по умолчанию"
                        + " Иначе тест from_EmptyMap_TargetsHostileAtDefaultDistance перестанет ловить эту поломку. При смене from_EmptyMap_TargetsHostileAtDefaultDistance проверь это утверждение.");
        assertEquals(
                CatalogLimits.DISENGAGE_DISTANCE_DEFAULT,
                params.distance(),
                "умолчание дистанции берётся из CatalogLimits, а не из литерала в скрипте"
                        + " Иначе тест from_EmptyMap_TargetsHostileAtDefaultDistance перестанет ловить эту поломку. При смене from_EmptyMap_TargetsHostileAtDefaultDistance проверь это утверждение.");
        assertEquals(
                5,
                CatalogLimits.DISENGAGE_DISTANCE_DEFAULT,
                "умолчание disengage согласовано как 5 блоков: дальше — это уже flee, ближе — дух остаётся в бою"
                        + " Иначе тест from_EmptyMap_TargetsHostileAtDefaultDistance перестанет ловить эту поломку. При смене from_EmptyMap_TargetsHostileAtDefaultDistance проверь это утверждение.");
    }

    @Test
    void from_TargetAndDistance_Parsed() {
        DisengageParams params = DisengageParams.from(Map.of("target", "nearest", "distance", 3));

        assertEquals(
                "nearest",
                params.target(),
                "явная цель обязана дойти до скрипта"
                        + " Иначе тест from_TargetAndDistance_Parsed перестанет ловить эту поломку. При смене from_TargetAndDistance_Parsed проверь это утверждение.");
        assertEquals(
                3,
                params.distance(),
                "явная дистанция обязана дойти до скрипта"
                        + " Иначе тест from_TargetAndDistance_Parsed перестанет ловить эту поломку. При смене from_TargetAndDistance_Parsed проверь это утверждение.");
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DisengageParams.from(Map.of("target", "  ")),
                "пустая цель обязана отвергаться: пустую строку нельзя сопоставить с существом, и дух "
                        + "отойдёт не от того"
                        + " Иначе тест from_BlankTarget_IsRejected перестанет ловить эту поломку. При смене from_BlankTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnparseableTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DisengageParams.from(Map.of("target", "dragon")),
                "неразбираемая цель обязана отвергаться до запуска: иначе скрипт упадёт уже в мире"
                        + " При смене from_UnparseableTarget_IsRejected проверь это утверждение.");
    }

    @Test
    void from_DistanceAboveMaximum_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DisengageParams.from(Map.of("distance", 17)),
                "дистанция больше максимума обязана отвергаться: дальний отход — это flee или retreat, а не disengage"
                        + " Иначе тест from_DistanceAboveMaximum_IsRejected перестанет ловить эту поломку. При смене from_DistanceAboveMaximum_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("17"),
                "отказ обязан называть полученную дистанцию 17, фактическое: " + error.getMessage()
                        + " Иначе тест from_DistanceAboveMaximum_IsRejected перестанет ловить эту поломку. При смене from_DistanceAboveMaximum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_DistanceBelowMinimum_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DisengageParams.from(Map.of("distance", 0)),
                "дистанция меньше минимума обязана отвергаться: отход на 0 блоков оставляет духа в бою"
                        + " Иначе тест from_DistanceBelowMinimum_IsRejected перестанет ловить эту поломку. При смене from_DistanceBelowMinimum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DisengageParams.from(null),
                "DisengageParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
