package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseSessionTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void placedTwice_SameCell_RecordedOnce() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "scaffold", 10);
        BlockCoord cell = new BlockCoord(1, 70, 1);

        assertTrue(session.placed(cell, "minecraft:dirt"), "Первая постановка клетки попадает в аренду");
        assertFalse(
                session.placed(cell, "minecraft:dirt"), "Повтор шага не перезаписывает клетку: откат идёт один раз");
        assertEquals(List.of(cell), session.cells().stream().map(LeaseCell::pos).toList(), "В аренде одна клетка");
    }

    @Test
    void brokenCell_WithoutBefore_RejectedByModel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(1, 70, 1), LeaseKind.BROKEN, null, null),
                "Сломанная клетка обязана помнить прежний блок: иначе брошенную аренду нечем вернуть");
    }

    @Test
    void placed_CellOwnedByOrphanedLease_LeftToItsOwner() {
        LeaseBook book = new LeaseBook();
        LeaseSession first = LeaseSession.open(book, SPIRIT, null, "dig_out", 10);
        BlockCoord cell = new BlockCoord(2, 50, 2);
        first.placed(cell, "minecraft:dirt");
        first.orphan();

        LeaseSession second = LeaseSession.open(book, SPIRIT, null, "dig_out", 20);
        assertFalse(
                second.placed(cell, "minecraft:dirt"),
                "Клетку брошенной аренды вторая механика не перезаписывает: откат остаётся за первым владельцем");
        assertEquals(first.id(), book.owner(cell).orElseThrow().id(), "Владелец клетки не сменился");
        assertTrue(second.cells().isEmpty(), "Вторая аренда не забрала чужую клетку");
    }

    @Test
    void forget_UnrecordedCell_Rejected() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "route", 10);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.forget(new BlockCoord(3, 70, 3)),
                "Забыть незаписанную клетку нельзя: механика разошлась бы с книгой аренд");

        assertTrue(error.getMessage().contains("не записана"), "Причина ошибки называет, что клетки нет в аренде");
    }

    @Test
    void closeTwice_CancelAfterCommit_DoesNotReopenOrThrow() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "bridge", 10);
        session.placed(new BlockCoord(5, 64, 0), "minecraft:oak_planks");

        session.commit();
        session.orphan();
        session.commit();

        assertEquals(LeaseStatus.DONE, session.status(), "Повторное закрытие не бросает уже закрытую аренду");
        assertTrue(book.orphanedCells().isEmpty(), "Успешная правка не попадает в уборку");
    }

    @Test
    void orphan_WithoutCells_ClosesAsDone() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "scaffold", 10);

        session.orphan();

        assertEquals(LeaseStatus.DONE, session.status(), "Пустая аренда не засоряет уборку: закрывается DONE");
        assertTrue(book.orphaned().isEmpty(), "Уборке нечего делать с пустой арендой");
    }

    @Test
    void record_AfterCommit_Rejected() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "route", 10);
        session.commit();

        assertThrows(
                IllegalStateException.class,
                () -> session.placed(new BlockCoord(7, 70, 7)),
                "Закрытая аренда новый блок не принимает: иначе откат стал бы непредсказуемым");
    }
}
