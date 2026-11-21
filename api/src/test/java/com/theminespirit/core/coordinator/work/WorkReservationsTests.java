package com.theminespirit.core.coordinator.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkReservationsTests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GAMMA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final WorkTarget CELL = new WorkTarget(WorkKind.CELL, new BlockCoord(1, 64, 1));
    private static final WorkTarget TREE = new WorkTarget(WorkKind.TREE, new BlockCoord(2, 64, 2));

    @Test
    void reserve_FreeTarget_Grants() {
        WorkReservations reservations = new WorkReservations();

        assertEquals(
                WorkReservations.Outcome.GRANTED,
                reservations.reserve(CELL, ALPHA, 100, 50),
                "Свободная цель выдаётся: первый взявший получает бронь Иначе тест reserve_FreeTarget_Grants перестанет ловить эту поломку. При смене reserve_FreeTarget_Grants проверь это утверждение.");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Владелец цели — тот, кто забронировал Иначе тест reserve_FreeTarget_Grants перестанет ловить эту поломку. При смене reserve_FreeTarget_Grants проверь это утверждение.");
    }

    @Test
    void reserve_TakenByOther_ReturnsTakenByOther() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.TAKEN_BY_OTHER,
                reservations.reserve(CELL, BETA, 100, 50),
                "Цель под чужой бронью не берётся: два духа не работают на одной цели Иначе тест reserve_TakenByOther_ReturnsTakenByOther перестанет ловить эту поломку. При смене reserve_TakenByOther_ReturnsTakenByOther проверь это утверждение.");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Владелец цели не меняется: чужая попытка не переносит бронь Иначе тест reserve_TakenByOther_ReturnsTakenByOther перестанет ловить эту поломку. При смене reserve_TakenByOther_ReturnsTakenByOther проверь это утверждение.");
    }

    @Test
    void reserve_SameSpiritTwice_ReturnsTakenBySelf() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.TAKEN_BY_SELF,
                reservations.reserve(CELL, ALPHA, 100, 50),
                "Повторная бронь того же духа — та же бронь, а новая: срок не продляется молча Иначе тест reserve_SameSpiritTwice_ReturnsTakenBySelf перестанет ловить эту поломку. При смене reserve_SameSpiritTwice_ReturnsTakenBySelf проверь это утверждение.");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Владелец цели остаётся прежним Иначе тест reserve_SameSpiritTwice_ReturnsTakenBySelf перестанет ловить эту поломку. При смене reserve_SameSpiritTwice_ReturnsTakenBySelf проверь это утверждение.");
    }

    @Test
    void reserve_ExpiredReservation_GrantsToAnother() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.GRANTED,
                reservations.reserve(CELL, BETA, 200, 50),
                "Просроченная бронь освобождается: затянувшаяся работа перераспределяется под руками Иначе тест reserve_ExpiredReservation_GrantsToAnother перестанет ловить эту поломку. При смене reserve_ExpiredReservation_GrantsToAnother проверь это утверждение.");
        assertEquals(
                Optional.of(BETA),
                reservations.owner(CELL),
                "Освободившаяся цель достаётся следующему: работа не висит без дела Иначе тест reserve_ExpiredReservation_GrantsToAnother перестанет ловить эту поломку. При смене reserve_ExpiredReservation_GrantsToAnother проверь это утверждение.");
    }

    @Test
    void release_AfterGrant_FreesTarget() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertTrue(
                reservations.release(CELL, ALPHA),
                "Владелец снимает свою бронь Иначе тест release_AfterGrant_FreesTarget перестанет ловить эту поломку. При смене release_AfterGrant_FreesTarget проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                reservations.owner(CELL),
                "Цель освобождена: её может взять другой Иначе тест release_AfterGrant_FreesTarget перестанет ловить эту поломку. При смене release_AfterGrant_FreesTarget проверь это утверждение.");
        assertEquals(
                WorkReservations.Outcome.GRANTED,
                reservations.reserve(CELL, BETA, 100, 50),
                "Освободившаяся цель выдаётся следующему Иначе тест release_AfterGrant_FreesTarget перестанет ловить эту поломку. При смене release_AfterGrant_FreesTarget проверь это утверждение.");
    }

    @Test
    void release_ForeignReservation_DoesNotFree() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertTrue(
                !reservations.release(CELL, BETA),
                "Чужую бронь снимать нельзя: иначе цель теряла бы владельца При смене release_ForeignReservation_DoesNotFree проверь это утверждение.");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Бронь остаётся у владельца Иначе тест release_ForeignReservation_DoesNotFree перестанет ловить эту поломку. При смене release_ForeignReservation_DoesNotFree проверь это утверждение.");
    }

    @Test
    void releaseAll_TwoReservations_RemovesBoth() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, ALPHA, 100, 50);

        assertEquals(
                2,
                reservations.releaseAll(ALPHA),
                "Снимаются все брони духа Иначе тест releaseAll_TwoReservations_RemovesBoth перестанет ловить эту поломку. При смене releaseAll_TwoReservations_RemovesBoth проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                reservations.owner(CELL),
                "Первая цель освобождена Иначе тест releaseAll_TwoReservations_RemovesBoth перестанет ловить эту поломку. При смене releaseAll_TwoReservations_RemovesBoth проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                reservations.owner(TREE),
                "Вторая цель освобождена Иначе тест releaseAll_TwoReservations_RemovesBoth перестанет ловить эту поломку. При смене releaseAll_TwoReservations_RemovesBoth проверь это утверждение.");
    }

    @Test
    void releaseAll_NoReservations_ReturnsZero() {
        assertEquals(
                0,
                new WorkReservations().releaseAll(GAMMA),
                "Дух без броней ничего не снимает: выгрузка духа без работ не ошибка Иначе тест releaseAll_NoReservations_ReturnsZero перестанет ловить эту поломку. При смене releaseAll_NoReservations_ReturnsZero проверь это утверждение.");
    }

    @Test
    void ownedBy_TwoReservations_ListsBoth() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, ALPHA, 100, 50);

        assertEquals(
                Set.of(CELL, TREE),
                reservations.ownedBy(ALPHA),
                "Реестр перечисляет все брони духа: снимок берёт из них текущую цель работы Иначе тест ownedBy_TwoReservations_ListsBoth перестанет ловить эту поломку. При смене ownedBy_TwoReservations_ListsBoth проверь это утверждение.");
    }

    @Test
    void ownedBy_NoReservations_IsEmpty() {
        assertEquals(
                Set.of(),
                new WorkReservations().ownedBy(ALPHA),
                "Свободный дух не владеет ничем: снимок несёт пустую цель, и координатор даёт ему работу Иначе тест ownedBy_NoReservations_IsEmpty перестанет ловить эту поломку. При смене ownedBy_NoReservations_IsEmpty проверь это утверждение.");
    }

    @Test
    void ownedBy_AfterRelease_IsEmpty() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.release(CELL, ALPHA);

        assertEquals(
                Set.of(),
                reservations.ownedBy(ALPHA),
                "Снятая бронь не числится за духом: иначе снимок держал бы цель, которую дух уже отпустил При смене ownedBy_AfterRelease_IsEmpty проверь это утверждение.");
    }

    @Test
    void ownedBy_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().ownedBy(null),
                "Владельца без духа не бывает: бронь привязана к конкретному духу Иначе тест ownedBy_NullSpirit_Throws перестанет ловить эту поломку. При смене ownedBy_NullSpirit_Throws проверь это утверждение.");
    }

    @Test
    void owner_GrantedTarget_ReturnsOwner() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(TREE, GAMMA, 100, 50);

        assertEquals(
                Optional.of(GAMMA),
                reservations.owner(TREE),
                "Владелец цели виден по запросу Иначе тест owner_GrantedTarget_ReturnsOwner перестанет ловить эту поломку. При смене owner_GrantedTarget_ReturnsOwner проверь это утверждение.");
    }

    @Test
    void owner_NoReservation_IsEmpty() {
        assertEquals(
                Optional.empty(),
                new WorkReservations().owner(CELL),
                "Цель без брони не имеет владельца: свободная цель берётся Иначе тест owner_NoReservation_IsEmpty перестанет ловить эту поломку. При смене owner_NoReservation_IsEmpty проверь это утверждение.");
    }

    @Test
    void size_TwoReservations_CountsThem() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, BETA, 100, 50);

        assertEquals(
                2,
                reservations.size(),
                "Реестр считает активные брони: утечка видна по размеру Иначе тест size_TwoReservations_CountsThem перестанет ловить эту поломку. При смене size_TwoReservations_CountsThem проверь это утверждение.");
    }

    @Test
    void size_AfterRelease_DropsCount() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.release(CELL, ALPHA);

        assertEquals(
                0,
                reservations.size(),
                "Снятая бронь не считается: иначе размер врёт о занятости При смене size_AfterRelease_DropsCount проверь это утверждение.");
    }

    @Test
    void expire_ExpiredReservation_RemovesIt() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                1,
                reservations.expire(150),
                "Бронь с дедлайном 150 снята на тике 150 Иначе тест expire_ExpiredReservation_RemovesIt перестанет ловить эту поломку. При смене expire_ExpiredReservation_RemovesIt проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                reservations.owner(CELL),
                "Цель освобождена по сроку Иначе тест expire_ExpiredReservation_RemovesIt перестанет ловить эту поломку. При смене expire_ExpiredReservation_RemovesIt проверь это утверждение.");
    }
}
