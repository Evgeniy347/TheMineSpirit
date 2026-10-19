package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AttackParamsTests {
    @Test
    void from_TargetAbsent_ReturnsHostileDefault() {
        AttackParams params = AttackParams.from(Map.of());

        assertEquals(
                AttackParams.DEFAULT_TARGET,
                params.target(),
                "attack обязан бить ближайшую угрозу, когда цель не названа: умолчание каталога 'hostile' "
                        + "должно совпадать с умолчанием маппинга, иначе валидатор подставит одно значение, "
                        + "а скрипт получит другое");
        assertEquals(
                "hostile",
                AttackParams.DEFAULT_TARGET,
                "Умолчание каталога attack.target обязано быть 'hostile': это тот же вид цели, что в "
                        + "CoreScripts.defaultCatalog, другое значение разведёт валидатор и маппинг");
    }

    @Test
    void from_TargetPresent_KeepsNamedTarget() {
        AttackParams params = AttackParams.from(Map.of("target", "player:Steve"));

        assertEquals(
                "player:Steve",
                params.target(),
                "attack обязан сохранить названную цель: по строке тактика разрешает конкретного игрока, "
                        + "а не ближайшую угрозу");
    }

    @Test
    void from_BlankTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AttackParams.from(Map.of("target", "   ")),
                "attack с пустой целью обязан отказывать: пустую строку TargetSelector не разберёт ни в один вид");

        assertTrue(
                error.getMessage().contains("цель"),
                "Отказ обязан называть причину — пустую цель. Фактическое: " + error.getMessage());
    }

    @Test
    void from_NonStringTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AttackParams.from(Map.of("target", 7)),
                "attack с нестроковой целью обязан отказывать: число не превращается в вид цели молча");

        assertTrue(
                error.getMessage().contains("target"),
                "Отказ обязан называть параметр 'target'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> AttackParams.from(null),
                "AttackParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}
