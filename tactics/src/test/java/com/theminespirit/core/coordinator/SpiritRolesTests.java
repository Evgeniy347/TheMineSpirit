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
                "Дух без задачи и плана — свободный защитник: иначе координатор не поставит его на пост"
                        + " При смене classify_NoScriptNoPlan_IsDefender проверь это утверждение.");
    }

    @Test
    void classify_NoScriptButActivePlan_IsIdleNotDefender() {
        assertEquals(
                SpiritRole.IDLE,
                SpiritRoles.classify(Optional.empty(), true),
                "Дух с назначенной, но ещё не тикнувшей задачей не защитник: иначе координатор вешает приказ"
                        + " обороны, тактика забирает управление и задача не стартует никогда"
                        + " При смене classify_NoScriptButActivePlan_IsIdleNotDefender проверь это утверждение.");
    }

    @Test
    void classify_NoScriptButAssignedActivity_IsIdleNotDefender() {
        assertEquals(
                SpiritRole.IDLE,
                SpiritRoles.classify(Optional.empty(), false, true),
                "Дух с выданным занятием (плавка ждёт повторной попытки) не свободный защитник: иначе "
                        + "координатор на каждом провале ставит его на пост охраны, приказ то появляется, то "
                        + "исчезает, и режим тактики качается DEFENSIVE ↔ GUARD_AREA без врагов"
                        + " При смене classify_NoScriptButAssignedActivity_IsIdleNotDefender проверь это утверждение.");
    }

    @Test
    void classify_WorkerScript_IsWorker() {
        assertEquals(
                SpiritRole.WORKER,
                SpiritRoles.classify(Optional.of("chop_tree"), true),
                "Скрипт-работа делает духа работником: иначе поселение считает его простаивающим"
                        + " При смене classify_WorkerScript_IsWorker проверь это утверждение.");
    }

    @Test
    void classify_BuildingScript_IsBuilding() {
        assertEquals(
                SpiritRole.BUILDING,
                SpiritRoles.classify(Optional.of("build_schematic"), true),
                "Скрипт-стройка делает духа строителем: иначе поселение считает его простаивающим"
                        + " При смене classify_BuildingScript_IsBuilding проверь это утверждение.");
    }

    @Test
    void classify_OtherScript_IsIdle() {
        assertEquals(
                SpiritRole.IDLE,
                SpiritRoles.classify(Optional.of("close_door"), true),
                "Незнакомый скрипт — простаивающий дух: иначе роль угадывается по имени вне списков"
                        + " При смене classify_OtherScript_IsIdle проверь это утверждение.");
    }

    @Test
    void classify_NullScript_ThrowsWithReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritRoles.classify(null, false),
                "Отсутствие скрипта — это Optional.empty(), а не null: иначе снимок роли роняет тик"
                        + " При смене classify_NullScript_ThrowsWithReason проверь это утверждение.");
    }
}
