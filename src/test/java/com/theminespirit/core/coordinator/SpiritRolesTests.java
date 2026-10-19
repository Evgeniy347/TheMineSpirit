package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpiritRolesTests {
    @Test
    void classify_NoScriptNoPlan_IsDefender() {
        assertEquals(
                SpiritRole.DEFENDER,
                SpiritRoles.classify(Optional.empty(), false),
                "Дух без задачи и плана — свободный защитник: иначе координатор не поставит его на пост");
    }

    @Test
    void classify_NoScriptButActivePlan_IsIdleNotDefender() {
        assertEquals(
                SpiritRole.IDLE,
                SpiritRoles.classify(Optional.empty(), true),
                "Дух с назначенной, но ещё не тикнувшей задачей не защитник: иначе координатор вешает приказ"
                        + " обороны, тактика забирает управление и задача не стартует никогда");
    }

    @Test
    void classify_WorkerScript_IsWorker() {
        assertEquals(
                SpiritRole.WORKER,
                SpiritRoles.classify(Optional.of("chop_tree"), true),
                "Скрипт-работа делает духа работником: иначе поселение считает его простаивающим");
    }

    @Test
    void classify_BuildingScript_IsBuilding() {
        assertEquals(
                SpiritRole.BUILDING,
                SpiritRoles.classify(Optional.of("build_schematic"), true),
                "Скрипт-стройка делает духа строителем: иначе поселение считает его простаивающим");
    }

    @Test
    void classify_OtherScript_IsIdle() {
        assertEquals(
                SpiritRole.IDLE,
                SpiritRoles.classify(Optional.of("close_door"), true),
                "Незнакомый скрипт — простаивающий дух: иначе роль угадывается по имени вне списков");
    }

    @Test
    void classify_NullScript_ThrowsWithReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritRoles.classify(null, false),
                "Отсутствие скрипта — это Optional.empty(), а не null: иначе снимок роли роняет тик");
    }
}
