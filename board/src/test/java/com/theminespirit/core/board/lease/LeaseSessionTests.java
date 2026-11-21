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

        assertTrue(
                session.placed(cell, "minecraft:dirt"),
                "Первая постановка клетки попадает в аренду"
                        + " Иначе тест placedTwice_SameCell_RecordedOnce перестанет ловить эту поломку. При смене placedTwice_SameCell_RecordedOnce проверь это утверждение.");
        assertFalse(
                session.placed(cell, "minecraft:dirt"),
                "Повтор шага не перезаписывает клетку: откат идёт один раз"
                        + " Иначе тест placedTwice_SameCell_RecordedOnce перестанет ловить эту поломку. При смене placedTwice_SameCell_RecordedOnce проверь это утверждение.");
        assertEquals(
                List.of(cell),
                session.cells().stream().map(LeaseCell::pos).toList(),
                "В аренде одна клетка"
                        + " Иначе тест placedTwice_SameCell_RecordedOnce перестанет ловить эту поломку. При смене placedTwice_SameCell_RecordedOnce проверь это утверждение.");
    }

    @Test
    void brokenCell_WithoutBefore_RejectedByModel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(1, 70, 1), LeaseKind.BROKEN, null, null),
                "Сломанная клетка обязана помнить прежний блок: иначе брошенную аренду нечем вернуть"
                        + " При смене brokenCell_WithoutBefore_RejectedByModel проверь это утверждение.");
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
                "Клетку брошенной аренды вторая механика не перезаписывает: откат остаётся за первым владельцем"
                        + " Иначе тест placed_CellOwnedByOrphanedLease_LeftToItsOwner перестанет ловить эту поломку. При смене placed_CellOwnedByOrphanedLease_LeftToItsOwner проверь это утверждение.");
        assertEquals(
                first.id(),
                book.owner(cell).orElseThrow().id(),
                "Владелец клетки не сменился"
                        + " Иначе тест placed_CellOwnedByOrphanedLease_LeftToItsOwner перестанет ловить эту поломку. При смене placed_CellOwnedByOrphanedLease_LeftToItsOwner проверь это утверждение.");
        assertTrue(
                second.cells().isEmpty(),
                "Вторая аренда не забрала чужую клетку"
                        + " Иначе тест placed_CellOwnedByOrphanedLease_LeftToItsOwner перестанет ловить эту поломку. При смене placed_CellOwnedByOrphanedLease_LeftToItsOwner проверь это утверждение.");
    }

    @Test
    void foreignOwner_OtherLeaseAndOwnCell_Distinguished() {
        LeaseBook book = new LeaseBook();
        BlockCoord own = new BlockCoord(4, 60, 4);
        BlockCoord foreign = new BlockCoord(5, 60, 4);
        BlockCoord free = new BlockCoord(6, 60, 4);
        LeaseSession dig = LeaseSession.open(book, SPIRIT, null, "dig_out", 10);
        dig.broken(own, "minecraft:stone");
        LeaseSession bridge = LeaseSession.open(book, SPIRIT, null, "bridge", 20);
        bridge.placed(foreign, "minecraft:oak_planks");

        assertTrue(
                dig.foreignOwner(own).isEmpty(),
                "Своя клетка не чужая: повтор шага не диагностируют как конфликт механик"
                        + " Иначе тест foreignOwner_OtherLeaseAndOwnCell_Distinguished перестанет ловить эту поломку. При смене foreignOwner_OtherLeaseAndOwnCell_Distinguished проверь это утверждение.");
        assertEquals(
                bridge.id(),
                dig.foreignOwner(foreign).orElseThrow().id(),
                "Действующая аренда другой механики видна владельцем клетки"
                        + " Иначе тест foreignOwner_OtherLeaseAndOwnCell_Distinguished перестанет ловить эту поломку. При смене foreignOwner_OtherLeaseAndOwnCell_Distinguished проверь это утверждение.");
        assertTrue(
                dig.foreignOwner(free).isEmpty(),
                "Свободная клетка ничья: конфликта нет"
                        + " Иначе тест foreignOwner_OtherLeaseAndOwnCell_Distinguished перестанет ловить эту поломку. При смене foreignOwner_OtherLeaseAndOwnCell_Distinguished проверь это утверждение.");
    }

    @Test
    void forget_UnrecordedCell_Rejected() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "route", 10);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> session.forget(new BlockCoord(3, 70, 3)),
                "Забыть незаписанную клетку нельзя: механика разошлась бы с книгой аренд"
                        + " Иначе тест forget_UnrecordedCell_Rejected перестанет ловить эту поломку. При смене forget_UnrecordedCell_Rejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("не записана"),
                "Причина ошибки называет, что клетки нет в аренде"
                        + " Иначе тест forget_UnrecordedCell_Rejected перестанет ловить эту поломку. При смене forget_UnrecordedCell_Rejected проверь это утверждение.");
    }

    @Test
    void closeTwice_CancelAfterCommit_DoesNotReopenOrThrow() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "bridge", 10);
        session.placed(new BlockCoord(5, 64, 0), "minecraft:oak_planks");

        session.commit();
        session.orphan();
        session.commit();

        assertEquals(
                LeaseStatus.DONE,
                session.status(),
                "Повторное закрытие не бросает уже закрытую аренду"
                        + " Иначе тест closeTwice_CancelAfterCommit_DoesNotReopenOrThrow перестанет ловить эту поломку. При смене closeTwice_CancelAfterCommit_DoesNotReopenOrThrow проверь это утверждение.");
        assertTrue(
                book.orphanedCells().isEmpty(),
                "Успешная правка не попадает в уборку"
                        + " Иначе тест closeTwice_CancelAfterCommit_DoesNotReopenOrThrow перестанет ловить эту поломку. При смене closeTwice_CancelAfterCommit_DoesNotReopenOrThrow проверь это утверждение.");
    }

    @Test
    void orphan_WithoutCells_ClosesAsDone() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "scaffold", 10);

        session.orphan();

        assertEquals(
                LeaseStatus.DONE,
                session.status(),
                "Пустая аренда не засоряет уборку: закрывается DONE"
                        + " Иначе тест orphan_WithoutCells_ClosesAsDone перестанет ловить эту поломку. При смене orphan_WithoutCells_ClosesAsDone проверь это утверждение.");
        assertTrue(
                book.orphaned().isEmpty(),
                "Уборке нечего делать с пустой арендой"
                        + " Иначе тест orphan_WithoutCells_ClosesAsDone перестанет ловить эту поломку. При смене orphan_WithoutCells_ClosesAsDone проверь это утверждение.");
    }

    @Test
    void record_AfterCommit_Rejected() {
        LeaseBook book = new LeaseBook();
        LeaseSession session = LeaseSession.open(book, SPIRIT, null, "route", 10);
        session.commit();

        assertThrows(
                IllegalStateException.class,
                () -> session.placed(new BlockCoord(7, 70, 7)),
                "Закрытая аренда новый блок не принимает: иначе откат стал бы непредсказуемым"
                        + " При смене record_AfterCommit_Rejected проверь это утверждение.");
    }
}
