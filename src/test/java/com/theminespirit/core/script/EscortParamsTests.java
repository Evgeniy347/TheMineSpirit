package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.TargetSelector;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscortParamsTests {
    @Test
    void from_OnlyTarget_FillsRadiusAndUntilFromCatalogDefaults() {
        EscortParams params = EscortParams.from(Map.of("target", "Steve"));

        assertEquals(
                3,
                params.radius(),
                "escort без радиуса обязан взять умолчание каталога 3: без дистанции дух не знает, где держаться");
        assertEquals(
                0,
                params.untilTicks(),
                "escort без until идёт до команды стоп: нулевой срок заканчивает задачу только отменой");
        assertTrue(
                params.untilStop(), "until = 0 обязан означать «до команды стоп», иначе сопровождение завершится само");
    }

    @Test
    void from_ExplicitValues_ReturnsThem() {
        EscortParams params = EscortParams.from(Map.of("target", "player:Steve", "radius", 5, "until", 120));

        assertEquals(
                5,
                params.radius(),
                "escort обязан принять радиус удержания из параметров: им задаётся расстояние до цели");
        assertEquals(
                120,
                params.untilTicks(),
                "escort обязан принять срок until в тиках: им ограничивается длительное сопровождение");
        assertFalse(params.untilStop(), "положительный until — это срок, а не режим «до команды стоп»");
    }

    @Test
    void selector_PlainPlayerName_IsPlayerTarget() {
        TargetSelector.Target selector = new EscortParams("Steve", 3, 0).selector();

        assertEquals(
                TargetSelector.Kind.PLAYER,
                selector.kind(),
                "escort по имени игрока обязан искать игрока: иначе охрана пойдёт не за тем существом");
        assertEquals(
                "Steve",
                selector.name().orElseThrow(),
                "выбранная цель обязана сохранить имя игрока: по нему ScriptTargetResolver находит подопечного");
    }

    @Test
    void selector_SpiritUuid_IsSpiritTarget() {
        String uuid = UUID.randomUUID().toString();

        TargetSelector.Target selector = new EscortParams(uuid, 3, 0).selector();

        assertEquals(
                TargetSelector.Kind.SPIRIT,
                selector.kind(),
                "escort по UUID обязан искать духа: сопровождение духа и игрока — разные цели резолвера");
        assertEquals(
                uuid,
                selector.name().orElseThrow(),
                "UUID духа обязан дойти до резолвера без искажений: иначе цель не найдётся");
    }

    @Test
    void selector_SpiritPrefix_IsSpiritTarget() {
        String uuid = UUID.randomUUID().toString();

        TargetSelector.Target selector = new EscortParams("spirit:" + uuid, 3, 0).selector();

        assertEquals(
                TargetSelector.Kind.SPIRIT,
                selector.kind(),
                "явный префикс spirit: обязан выбирать духа, даже если имя не похоже на UUID-строку");
    }

    @Test
    void target_Blank_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EscortParams("  ", 3, 0),
                "escort с пустой целью обязан отказывать: некого сопровождать и охранять");
    }

    @Test
    void radius_OutsideCatalogBounds_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EscortParams("Steve", -1, 0),
                "escort с отрицательным радиусом обязан отказывать: дистанция меньше нуля бессмысленна");
        assertThrows(
                IllegalArgumentException.class,
                () -> new EscortParams("Steve", 17, 0),
                "escort с радиусом больше 16 обязан отказывать: дальше дух уходит из тактической зоны охраны");
    }

    @Test
    void until_OutsideCatalogBounds_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EscortParams("Steve", 3, -1),
                "escort с отрицательным сроком обязан отказывать: срок не может идти назад");
        assertThrows(
                IllegalArgumentException.class,
                () -> new EscortParams("Steve", 3, 6001),
                "escort со сроком больше 6000 тиков обязан отказывать: предел совпадает с лимитом ожидания плана");
    }

    @Test
    void from_WrongRadiusType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EscortParams.from(Map.of("target", "Steve", "radius", "близко")),
                "escort с нецелым радиусом обязан отказывать: строку нельзя тихо превратить в дистанцию");
    }
}
