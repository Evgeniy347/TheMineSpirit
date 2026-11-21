package com.theminespirit.core.coordinator.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkReservationsPart2Tests {
    private static final UUID ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final WorkTarget CELL = new WorkTarget(WorkKind.CELL, new BlockCoord(1, 64, 1));

    @Test
    void expire_ActiveReservation_KeepsIt() {
        WorkReservations reservations = new WorkReservations();
        reservations.reserve(CELL, ALPHA, 100, 50);

        assertEquals(
                0,
                reservations.expire(149),
                "Бронь с дедлайном 150 не снята раньше срока Иначе тест expire_ActiveReservation_KeepsIt перестанет ловить эту поломку. При смене expire_ActiveReservation_KeepsIt проверь это утверждение.");
        assertEquals(
                Optional.of(ALPHA),
                reservations.owner(CELL),
                "Цель остаётся под бронью до дедлайна Иначе тест expire_ActiveReservation_KeepsIt перестанет ловить эту поломку. При смене expire_ActiveReservation_KeepsIt проверь это утверждение.");
    }

    @Test
    void reserve_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(null, ALPHA, 100, 50),
                "Бронь без цели не имеет смысла: реестр стал бы безадресным Иначе тест reserve_NullTarget_Throws перестанет ловить эту поломку. При смене reserve_NullTarget_Throws проверь это утверждение.");
    }

    @Test
    void reserve_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, null, 100, 50),
                "Бронь без духа не имеет владельца: цель занята навсегда Иначе тест reserve_NullSpirit_Throws перестанет ловить эту поломку. При смене reserve_NullSpirit_Throws проверь это утверждение.");
    }

    @Test
    void reserve_NegativeNow_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, ALPHA, -1, 50),
                "Время брони назад противоречит реестру: дедлайн стал бы в прошлом Иначе тест reserve_NegativeNow_Throws перестанет ловить эту поломку. При смене reserve_NegativeNow_Throws проверь это утверждение.");
    }

    @Test
    void reserve_NonPositiveLease_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().reserve(CELL, ALPHA, 100, 0),
                "Нулевой срок брони занимает цель навсегда: перераспределение стало бы невозможным Иначе тест reserve_NonPositiveLease_Throws перестанет ловить эту поломку. При смене reserve_NonPositiveLease_Throws проверь это утверждение.");
    }

    @Test
    void release_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().release(null, ALPHA),
                "Снимать бронь не с чего: цель обязательна Иначе тест release_NullTarget_Throws перестанет ловить эту поломку. При смене release_NullTarget_Throws проверь это утверждение.");
    }

    @Test
    void releaseAll_NullSpirit_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().releaseAll(null),
                "Снимать брони не с кого: дух обязателен Иначе тест releaseAll_NullSpirit_Throws перестанет ловить эту поломку. При смене releaseAll_NullSpirit_Throws проверь это утверждение.");
    }

    @Test
    void owner_NullTarget_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().owner(null),
                "Владельца без цели не бывает: цель обязательна Иначе тест owner_NullTarget_Throws перестанет ловить эту поломку. При смене owner_NullTarget_Throws проверь это утверждение.");
    }

    @Test
    void expire_NegativeNow_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkReservations().expire(-1),
                "Время освобождения назад противоречит реестру Иначе тест expire_NegativeNow_Throws перестанет ловить эту поломку. При смене expire_NegativeNow_Throws проверь это утверждение.");
    }
}
