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
                1, book.retired().size(), "Перенос места ставит старую тропу в очередь снятия: мир не хранит след");
        assertEquals(0, book.retired().get(0).id(), "Первому снятию достаётся номер ноль: по нему идёт задание");
        assertEquals(RESOURCE, book.retired().get(0).resource(), "Снятие помнит свой маршрут");
        assertTrue(book.trails().isEmpty(), "Перенесённая тропа больше не считается проложенной");
    }

    @Test
    void retire_PlannedButNotBuiltTrail_LeavesNoRemoval() {
        RouteTrailBook book = new RouteTrailBook();
        book.plan(RESOURCE, path());

        book.retire(RESOURCE);

        assertTrue(book.retired().isEmpty(), "Непроложенную тропу не снимают: снимать в мире нечего");
        assertTrue(book.planned().isEmpty(), "План несделанной тропы забыт вместе с переносом места");
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

        assertTrue(book.done(0), "Выполненное снятие убирается из очереди: иначе граф ставит его снова");
        assertEquals(1, book.retired().size(), "Второе снятие остаётся ждать своей работы");
        assertEquals(RouteResource.DIRT, book.retired().get(0).resource(), "Осталось снятие земли, а не песка");
        assertFalse(book.done(99), "Чужой номер не закрывает ни одной работы");
    }

    @Test
    void restore_SavedRemoval_ContinuesNumbering() {
        RouteTrailBook book = new RouteTrailBook();

        book.restore(new RouteRetired(5, RESOURCE, trail()));
        book.restore(new RouteRetired(7, RESOURCE, trail()));

        assertEquals(8, book.nextRetire(), "Восстановление само поднимает счётчик выше занятых номеров");
        book.nextRetire(9);
        book.plan(RouteResource.DIRT, path());
        book.built(RouteResource.DIRT);
        book.retire(RouteResource.DIRT);
        assertEquals(9, book.retired().get(2).id(), "Новое снятие продолжает нумерацию, а не повторяет старую");
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
