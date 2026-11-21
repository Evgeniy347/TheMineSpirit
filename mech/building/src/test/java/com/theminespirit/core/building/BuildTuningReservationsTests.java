package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.work.WorkKind;
import com.theminespirit.core.coordinator.work.WorkReservations;
import com.theminespirit.core.coordinator.work.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildTuningReservationsTests {
    @Test
    void timeoutTicks_FartherTarget_NeedsMoreTicks() {
        int near = BuildTuning.timeoutTicks(1.0);
        int far = BuildTuning.timeoutTicks(10.0);

        assertTrue(
                far > near,
                "Тайм-аут обязан расти с расстоянием: иначе дальняя стоянка обрывается раньше, чем дух доходит"
                        + " При смене timeoutTicks_FartherTarget_NeedsMoreTicks проверь это утверждение.");
        assertTrue(
                near > 0 && BuildTuning.STAND_ARRIVAL > 0 && BuildTuning.MAX_CONSECUTIVE_SKIPS > 0,
                "В тюнинге не должно быть нулей: нулевая дистанция или нулевой лимит пропусков вешают стройку"
                        + " Иначе тест timeoutTicks_FartherTarget_NeedsMoreTicks перестанет ловить эту поломку. При смене timeoutTicks_FartherTarget_NeedsMoreTicks проверь это утверждение.");
    }

    @Test
    void reserve_TwoSpiritsSameCell_SecondSeesForeign() {
        BuildReservations reservations = new BuildReservations();
        BlockCoord cell = new BlockCoord(0, 64, 0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(
                reservations.reserve(cell, first),
                "Первая бронь обязана вставать: иначе ни один дух не строит"
                        + " При смене reserve_TwoSpiritsSameCell_SecondSeesForeign проверь это утверждение.");
        assertTrue(
                !reservations.reserve(cell, second),
                "Вторая бронь чужой клетки обязана отклоняться: иначе два духа ставят один блок и тратят два предмета"
                        + " При смене reserve_TwoSpiritsSameCell_SecondSeesForeign проверь это утверждение.");
        assertTrue(
                reservations.foreign(cell, second),
                "Чужая бронь обязана опознаваться: иначе клетка не пропускается со счётчиком, а роняет задачу"
                        + " При смене reserve_TwoSpiritsSameCell_SecondSeesForeign проверь это утверждение.");
        reservations.releaseAll(first);
        assertTrue(
                reservations.reserve(cell, second),
                "После снятия брони клетка обязана освобождаться: иначе отмена задачи вечно держит клетки"
                        + " При смене reserve_TwoSpiritsSameCell_SecondSeesForeign проверь это утверждение.");
        assertEquals(
                1,
                reservations.size(),
                "Реестр обязан считать брони: иначе утечка брони незаметна"
                        + " При смене reserve_TwoSpiritsSameCell_SecondSeesForeign проверь это утверждение.");
    }

    @Test
    void reserve_HasFiniteLease_ExpiresAndRefreshesForOwner() {
        BuildReservations reservations = new BuildReservations();
        BlockCoord cell = new BlockCoord(0, 64, 0);
        UUID builder = UUID.randomUUID();

        assertTrue(
                reservations.reserve(cell, builder, 0),
                "Бронь встаёт на клетку"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");
        assertEquals(
                0,
                reservations.expire(BuildTuning.RESERVATION_TICKS - 1),
                "До срока бронь держится"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");
        assertEquals(
                1,
                reservations.expire(BuildTuning.RESERVATION_TICKS),
                "Срок брони конечен: забытая бронь не держит клетку вечно"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");
        assertTrue(
                reservations.reserve(cell, builder, 0),
                "Освобождённую клетку можно взять снова"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");

        assertTrue(
                reservations.reserve(cell, builder, 1000),
                "Владелец продлевает свою бронь"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");
        assertEquals(
                0,
                reservations.expire(BuildTuning.RESERVATION_TICKS + 500),
                "Повторная бронь владельцем продлевает срок: стройка не теряет клетки на середине"
                        + " Иначе тест reserve_HasFiniteLease_ExpiresAndRefreshesForOwner перестанет ловить эту поломку. При смене reserve_HasFiniteLease_ExpiresAndRefreshesForOwner проверь это утверждение.");
    }

    @Test
    void reserve_SharedRegistry_ConflictsWithWorkReservation() {
        WorkReservations shared = new WorkReservations();
        BuildReservations reservations = new BuildReservations(shared);
        BlockCoord cell = new BlockCoord(0, 64, 0);
        UUID builder = UUID.randomUUID();
        UUID worker = UUID.randomUUID();

        assertTrue(
                reservations.reserve(cell, builder),
                "Бронь клетки встаёт в общий реестр: строить некому, если реестр пуст"
                        + " Иначе тест reserve_SharedRegistry_ConflictsWithWorkReservation перестанет ловить эту поломку. При смене reserve_SharedRegistry_ConflictsWithWorkReservation проверь это утверждение.");
        assertEquals(
                WorkReservations.Outcome.TAKEN_BY_OTHER,
                shared.reserve(new WorkTarget(WorkKind.CELL, cell), worker, 0, 50),
                "Общий реестр видит бронь клетки: второго реестра нет, иначе двое возьмут одну клетку"
                        + " При смене reserve_SharedRegistry_ConflictsWithWorkReservation проверь это утверждение.");
    }
}
