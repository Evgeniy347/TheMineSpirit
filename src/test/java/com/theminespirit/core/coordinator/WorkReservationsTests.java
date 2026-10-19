package com.theminespirit.core.coordinator;

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
                "Свободная цель выдаётся: первый взявший получает бронь");
        assertEquals(Optional.of(ALPHA), reservations.owner(CELL), "Владелец цели — тот, кто забронировал");
    }

    @Test
    void reserve_TakenByOther_ReturnsTakenByOther() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.TAKEN_BY_OTHER,
                reservations.reserve(CELL, BETA, 100, 50),
                "Цель под чужой бронью не берётся: два духа не работают на одной цели");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Владелец цели не меняется: чужая попытка не переносит бронь");
    }

    @Test
    void reserve_SameSpiritTwice_ReturnsTakenBySelf() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.TAKEN_BY_SELF,
                reservations.reserve(CELL, ALPHA, 100, 50),
                "Повторная бронь того же духа — та же бронь, а новая: срок не продляется молча");
        assertEquals(Optional.of(ALPHA), reservations.owner(CELL), "Владелец цели остаётся прежним");
    }

    @Test
    void reserve_ExpiredReservation_GrantsToAnother() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                WorkReservations.Outcome.GRANTED,
                reservations.reserve(CELL, BETA, 200, 50),
                "Просроченная бронь освобождается: затянувшаяся работа перераспределяется под руками");
        assertEquals(
                Optional.of(BETA),
                reservations.owner(CELL),
                "Освободившаяся цель достаётся следующему: работа не висит без дела");
    }

    @Test
    void release_AfterGrant_FreesTarget() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertTrue(reservations.release(CELL, ALPHA), "Владелец снимает свою бронь");
        assertEquals(Optional.empty(), reservations.owner(CELL), "Цель освобождена: её может взять другой");
        assertEquals(
                WorkReservations.Outcome.GRANTED,
                reservations.reserve(CELL, BETA, 100, 50),
                "Освободившаяся цель выдаётся следующему");
    }

    @Test
    void release_ForeignReservation_DoesNotFree() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertTrue(!reservations.release(CELL, BETA), "Чужую бронь снимать нельзя: иначе цель теряла бы владельца");
        assertEquals(Optional.of(ALPHA), reservations.owner(CELL), "Бронь остаётся у владельца");
    }

    @Test
    void releaseAll_TwoReservations_RemovesBoth() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, ALPHA, 100, 50);

        assertEquals(2, reservations.releaseAll(ALPHA), "Снимаются все брони духа");
        assertEquals(Optional.empty(), reservations.owner(CELL), "Первая цель освобождена");
        assertEquals(Optional.empty(), reservations.owner(TREE), "Вторая цель освобождена");
    }

    @Test
    void releaseAll_NoReservations_ReturnsZero() {
        assertEquals(
                0,
                new WorkReservations().releaseAll(GAMMA),
                "Дух без броней ничего не снимает: выгрузка духа без работ не ошибка");
    }

    @Test
    void ownedBy_TwoReservations_ListsBoth() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, ALPHA, 100, 50);

        assertEquals(
                Set.of(CELL, TREE),
                reservations.ownedBy(ALPHA),
                "Реестр перечисляет все брони духа: снимок берёт из них текущую цель работы");
    }

    @Test
    void ownedBy_NoReservations_IsEmpty() {
        assertEquals(
                Set.of(),
                new WorkReservations().ownedBy(ALPHA),
                "Свободный дух не владеет ничем: снимок несёт пустую цель, и координатор даёт ему работу");
    }

    @Test
    void ownedBy_AfterRelease_IsEmpty() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.release(CELL, ALPHA);

        assertEquals(
                Set.of(),
                reservations.ownedBy(ALPHA),
                "Снятая бронь не числится за духом: иначе снимок держал бы цель, которую дух уже отпустил");
    }

    @Test
    void ownedBy_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().ownedBy(null),
                "Владельца без духа не бывает: бронь привязана к конкретному духу");
    }

    @Test
    void owner_GrantedTarget_ReturnsOwner() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(TREE, GAMMA, 100, 50);

        assertEquals(Optional.of(GAMMA), reservations.owner(TREE), "Владелец цели виден по запросу");
    }

    @Test
    void owner_NoReservation_IsEmpty() {
        assertEquals(
                Optional.empty(),
                new WorkReservations().owner(CELL),
                "Цель без брони не имеет владельца: свободная цель берётся");
    }

    @Test
    void size_TwoReservations_CountsThem() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.reserve(TREE, BETA, 100, 50);

        assertEquals(2, reservations.size(), "Реестр считает активные брони: утечка видна по размеру");
    }

    @Test
    void size_AfterRelease_DropsCount() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);
        reservations.release(CELL, ALPHA);

        assertEquals(0, reservations.size(), "Снятая бронь не считается: иначе размер врёт о занятости");
    }

    @Test
    void expire_ExpiredReservation_RemovesIt() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(1, reservations.expire(150), "Бронь с дедлайном 150 снята на тике 150");
        assertEquals(Optional.empty(), reservations.owner(CELL), "Цель освобождена по сроку");
    }

    @Test
    void expire_ActiveReservation_KeepsIt() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(0, reservations.expire(149), "Бронь с дедлайном 150 не снята раньше срока");
        assertEquals(Optional.of(ALPHA), reservations.owner(CELL), "Цель остаётся под бронью до дедлайна");
    }

    @Test
    void reserve_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(null, ALPHA, 100, 50),
                "Бронь без цели не имеет смысла: реестр стал бы безадресным");
    }

    @Test
    void reserve_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, null, 100, 50),
                "Бронь без духа не имеет владельца: цель занята навсегда");
    }

    @Test
    void reserve_NegativeNow_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, ALPHA, -1, 50),
                "Время брони назад противоречит реестру: дедлайн стал бы в прошлом");
    }

    @Test
    void reserve_NonPositiveLease_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, ALPHA, 100, 0),
                "Нулевой срок брони занимает цель навсегда: перераспределение стало бы невозможным");
    }

    @Test
    void release_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().release(null, ALPHA),
                "Снимать бронь не с чего: цель обязательна");
    }

    @Test
    void releaseAll_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().releaseAll(null),
                "Снимать брони не с кого: дух обязателен");
    }

    @Test
    void owner_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().owner(null),
                "Владельца без цели не бывает: цель обязательна");
    }

    @Test
    void expire_NegativeNow_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().expire(-1),
                "Время освобождения назад противоречит реестру");
    }
}
