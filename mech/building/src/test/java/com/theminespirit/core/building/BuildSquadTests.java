package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildSquadTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void join_TwoSpirits_SlotsFollowEntryOrder() {
        BuildSquad squad = new BuildSquad();

        squad.join(BETA);
        squad.join(ALPHA);

        assertEquals(
                0,
                squad.slotOf(BETA),
                "Первый вошедший получает нулевое место: доли не пересекаются"
                        + " Иначе тест join_TwoSpirits_SlotsFollowEntryOrder перестанет ловить эту поломку. При смене join_TwoSpirits_SlotsFollowEntryOrder проверь это утверждение.");
        assertEquals(
                1,
                squad.slotOf(ALPHA),
                "Второй вошедший получает следующее место"
                        + " Иначе тест join_TwoSpirits_SlotsFollowEntryOrder перестанет ловить эту поломку. При смене join_TwoSpirits_SlotsFollowEntryOrder проверь это утверждение.");
        assertEquals(
                2,
                squad.size(),
                "Отряд считает вошедших"
                        + " Иначе тест join_TwoSpirits_SlotsFollowEntryOrder перестанет ловить эту поломку. При смене join_TwoSpirits_SlotsFollowEntryOrder проверь это утверждение.");
    }

    @Test
    void join_SameSpiritTwice_NoDuplicateAndSlotKept() {
        BuildSquad squad = new BuildSquad();
        squad.join(ALPHA);
        long before = squad.version();

        squad.join(ALPHA);

        assertEquals(
                1,
                squad.size(),
                "Повторный вход того же духа не создаёт второе место"
                        + " Иначе тест join_SameSpiritTwice_NoDuplicateAndSlotKept перестанет ловить эту поломку. При смене join_SameSpiritTwice_NoDuplicateAndSlotKept проверь это утверждение.");
        assertEquals(
                0,
                squad.slotOf(ALPHA),
                "Место духа не сдвигается повторным входом"
                        + " Иначе тест join_SameSpiritTwice_NoDuplicateAndSlotKept перестанет ловить эту поломку. При смене join_SameSpiritTwice_NoDuplicateAndSlotKept проверь это утверждение.");
        assertEquals(
                before,
                squad.version(),
                "Повторный вход не меняет доли: пересборка была бы лишней"
                        + " Иначе тест join_SameSpiritTwice_NoDuplicateAndSlotKept перестанет ловить эту поломку. При смене join_SameSpiritTwice_NoDuplicateAndSlotKept проверь это утверждение.");
    }

    @Test
    void leave_KnownSpirit_RemovesAndFreesSlot() {
        BuildSquad squad = new BuildSquad();
        squad.join(ALPHA);
        squad.join(BETA);
        long before = squad.version();

        assertTrue(
                squad.leave(ALPHA),
                "Уход вошедшего духа обязан снимать его место"
                        + " Иначе тест leave_KnownSpirit_RemovesAndFreesSlot перестанет ловить эту поломку. При смене leave_KnownSpirit_RemovesAndFreesSlot проверь это утверждение.");

        assertEquals(
                -1,
                squad.slotOf(ALPHA),
                "Ушедший дух вне отряда: его доля достаётся остальным"
                        + " Иначе тест leave_KnownSpirit_RemovesAndFreesSlot перестанет ловить эту поломку. При смене leave_KnownSpirit_RemovesAndFreesSlot проверь это утверждение.");
        assertEquals(
                0,
                squad.slotOf(BETA),
                "Оставшийся сдвигается на освободившееся место"
                        + " Иначе тест leave_KnownSpirit_RemovesAndFreesSlot перестанет ловить эту поломку. При смене leave_KnownSpirit_RemovesAndFreesSlot проверь это утверждение.");
        assertEquals(
                before + 1,
                squad.version(),
                "Уход меняет доли — версия растёт"
                        + " Иначе тест leave_KnownSpirit_RemovesAndFreesSlot перестанет ловить эту поломку. При смене leave_KnownSpirit_RemovesAndFreesSlot проверь это утверждение.");
    }

    @Test
    void leave_UnknownSpirit_NoChange() {
        BuildSquad squad = new BuildSquad();
        squad.join(ALPHA);

        assertFalse(
                squad.leave(BETA),
                "Уход чужого духа не меняет отряд"
                        + " Иначе тест leave_UnknownSpirit_NoChange перестанет ловить эту поломку. При смене leave_UnknownSpirit_NoChange проверь это утверждение.");
        assertEquals(
                1,
                squad.size(),
                "Состав отряда прежний"
                        + " Иначе тест leave_UnknownSpirit_NoChange перестанет ловить эту поломку. При смене leave_UnknownSpirit_NoChange проверь это утверждение.");
        assertEquals(
                -1,
                squad.slotOf(BETA),
                "Чужой дух места не получает"
                        + " Иначе тест leave_UnknownSpirit_NoChange перестанет ловить эту поломку. При смене leave_UnknownSpirit_NoChange проверь это утверждение.");
    }

    @Test
    void empty_AfterLastLeave_IsEmpty() {
        BuildSquad squad = new BuildSquad();

        assertTrue(
                squad.isEmpty(),
                "Новый отряд пуст"
                        + " Иначе тест empty_AfterLastLeave_IsEmpty перестанет ловить эту поломку. При смене empty_AfterLastLeave_IsEmpty проверь это утверждение.");
        squad.join(ALPHA);
        assertFalse(
                squad.isEmpty(),
                "Вошедший дух делает отряд непустым"
                        + " Иначе тест empty_AfterLastLeave_IsEmpty перестанет ловить эту поломку. При смене empty_AfterLastLeave_IsEmpty проверь это утверждение.");
        squad.leave(ALPHA);

        assertTrue(
                squad.isEmpty(),
                "После ухода последнего отряд пуст — запись можно удалить"
                        + " Иначе тест empty_AfterLastLeave_IsEmpty перестанет ловить эту поломку. При смене empty_AfterLastLeave_IsEmpty проверь это утверждение.");
        assertEquals(
                0,
                squad.size(),
                "Пустой отряд не считает духов"
                        + " Иначе тест empty_AfterLastLeave_IsEmpty перестанет ловить эту поломку. При смене empty_AfterLastLeave_IsEmpty проверь это утверждение.");
    }

    @Test
    void members_AfterTwoJoins_SnapshotKeepsEntryOrder() {
        BuildSquad squad = new BuildSquad();
        squad.join(BETA);
        squad.join(GAMMA);

        assertEquals(
                List.of(BETA, GAMMA),
                squad.members(),
                "Снимок участников идёт в порядке входа: по нему строится разбиение"
                        + " Иначе тест members_AfterTwoJoins_SnapshotKeepsEntryOrder перестанет ловить эту поломку. При смене members_AfterTwoJoins_SnapshotKeepsEntryOrder проверь это утверждение.");
    }

    @Test
    void nullSpirit_AllOperations_Throw() {
        BuildSquad squad = new BuildSquad();

        assertThrows(
                NullPointerException.class,
                () -> squad.join(null),
                "null вместо духа не входит в отряд"
                        + " Иначе тест nullSpirit_AllOperations_Throw перестанет ловить эту поломку. При смене nullSpirit_AllOperations_Throw проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> squad.leave(null),
                "null вместо духа не выходит из отряда"
                        + " Иначе тест nullSpirit_AllOperations_Throw перестанет ловить эту поломку. При смене nullSpirit_AllOperations_Throw проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> squad.slotOf(null),
                "Место ищут конкретному духу"
                        + " Иначе тест nullSpirit_AllOperations_Throw перестанет ловить эту поломку. При смене nullSpirit_AllOperations_Throw проверь это утверждение.");
    }
}
