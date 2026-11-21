package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTrailBookTests {
    private static final RouteResource RESOURCE = RouteResource.SAND;

    @Test
    void retire_BuiltTrail_MovesItToRemovalQueue() {
        RouteTrailBook book = new RouteTrailBook();
        book.plan(RESOURCE, path());
        book.built(RESOURCE);

        book.retire(RESOURCE);

        assertEquals(
                1,
                book.retired().size(),
                "Перенос места ставит старую тропу в очередь снятия: мир не хранит след"
                        + " Иначе тест retire_BuiltTrail_MovesItToRemovalQueue перестанет ловить эту поломку. При смене retire_BuiltTrail_MovesItToRemovalQueue проверь это утверждение.");
        assertEquals(
                0,
                book.retired().get(0).id(),
                "Первому снятию достаётся номер ноль: по нему идёт задание"
                        + " Иначе тест retire_BuiltTrail_MovesItToRemovalQueue перестанет ловить эту поломку. При смене retire_BuiltTrail_MovesItToRemovalQueue проверь это утверждение.");
        assertEquals(
                RESOURCE,
                book.retired().get(0).resource(),
                "Снятие помнит свой маршрут"
                        + " Иначе тест retire_BuiltTrail_MovesItToRemovalQueue перестанет ловить эту поломку. При смене retire_BuiltTrail_MovesItToRemovalQueue проверь это утверждение.");
        assertTrue(
                book.trails().isEmpty(),
                "Перенесённая тропа больше не считается проложенной"
                        + " Иначе тест retire_BuiltTrail_MovesItToRemovalQueue перестанет ловить эту поломку. При смене retire_BuiltTrail_MovesItToRemovalQueue проверь это утверждение.");
    }

    @Test
    void retire_PlannedButNotBuiltTrail_LeavesNoRemoval() {
        RouteTrailBook book = new RouteTrailBook();
        book.plan(RESOURCE, path());

        book.retire(RESOURCE);

        assertTrue(
                book.retired().isEmpty(),
                "Непроложенную тропу не снимают: снимать в мире нечего"
                        + " Иначе тест retire_PlannedButNotBuiltTrail_LeavesNoRemoval перестанет ловить эту поломку. При смене retire_PlannedButNotBuiltTrail_LeavesNoRemoval проверь это утверждение.");
        assertTrue(
                book.planned().isEmpty(),
                "План несделанной тропы забыт вместе с переносом места"
                        + " Иначе тест retire_PlannedButNotBuiltTrail_LeavesNoRemoval перестанет ловить эту поломку. При смене retire_PlannedButNotBuiltTrail_LeavesNoRemoval проверь это утверждение.");
    }

    @Test
    void done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing() {
        RouteTrailBook book = new RouteTrailBook();
        book.plan(RESOURCE, path());
        book.built(RESOURCE);
        book.retire(RESOURCE);
        book.plan(RouteResource.DIRT, path());
        book.built(RouteResource.DIRT);
        book.retire(RouteResource.DIRT);

        assertTrue(
                book.done(0),
                "Выполненное снятие убирается из очереди: иначе граф ставит его снова"
                        + " При смене done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing проверь это утверждение.");
        assertEquals(
                1,
                book.retired().size(),
                "Второе снятие остаётся ждать своей работы"
                        + " Иначе тест done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing перестанет ловить эту поломку. При смене done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing проверь это утверждение.");
        assertEquals(
                RouteResource.DIRT,
                book.retired().get(0).resource(),
                "Осталось снятие земли, а не песка"
                        + " Иначе тест done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing перестанет ловить эту поломку. При смене done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing проверь это утверждение.");
        assertFalse(
                book.done(99),
                "Чужой номер не закрывает ни одной работы"
                        + " Иначе тест done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing перестанет ловить эту поломку. При смене done_RemovalEntry_LeavesOtherEntriesAndAdvancesNothing проверь это утверждение.");
    }

    @Test
    void restore_SavedRemoval_ContinuesNumbering() {
        RouteTrailBook book = new RouteTrailBook();

        book.restore(new RouteRetired(5, RESOURCE, trail()));
        book.restore(new RouteRetired(7, RESOURCE, trail()));

        assertEquals(
                8,
                book.nextRetire(),
                "Восстановление само поднимает счётчик выше занятых номеров"
                        + " Иначе тест restore_SavedRemoval_ContinuesNumbering перестанет ловить эту поломку. При смене restore_SavedRemoval_ContinuesNumbering проверь это утверждение.");
        book.nextRetire(9);
        book.plan(RouteResource.DIRT, path());
        book.built(RouteResource.DIRT);
        book.retire(RouteResource.DIRT);
        assertEquals(
                9,
                book.retired().get(2).id(),
                "Новое снятие продолжает нумерацию, а не повторяет старую"
                        + " Иначе тест restore_SavedRemoval_ContinuesNumbering перестанет ловить эту поломку. При смене restore_SavedRemoval_ContinuesNumbering проверь это утверждение.");
    }

    private static PlotLeveling path() {
        return new PlotLeveling(
                List.of(),
                List.of(new PlotLeveling.Box(1, 64, 1, 1, 64, 1), new PlotLeveling.Box(2, 64, 1, 2, 64, 1)),
                "sand");
    }

    private static RouteTrail trail() {
        return new RouteTrail(List.of(new BlockCoord(1, 64, 1)), "sand");
    }
}
