package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseBookTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @Test
    void open_RecordAdded_KeepsOwnerAndCells() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "ticket-1", "scaffold", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);

        book.record(lease.id(), LeaseCell.placed(cell, "minecraft:dirt"));

        assertEquals(
                LeaseStatus.ACTIVE,
                lease.status(),
                "Открытая аренда действует: скан не трогает её клетки"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertEquals(
                SPIRIT,
                lease.spirit(),
                "Владелец аренды обязан читаться: по нему смерть бросает правку"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertEquals(
                "ticket-1",
                lease.ticket(),
                "Тикет обязан читаться: по нему проверяют, не ушёл ли он другому"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertEquals(
                "scaffold",
                lease.purpose(),
                "Назначение обязано читаться: по нему граф выбирает уборку"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertEquals(
                lease.id(),
                book.owner(cell).orElseThrow().id(),
                "Клетка обязана знать свою действующую аренду"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertTrue(
                book.isActive(cell),
                "Клетка действующей аренды обязана считаться занятой"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
        assertFalse(
                book.isOrphaned(cell),
                "Действующая аренда не бывает брошенной"
                        + " Иначе тест open_RecordAdded_KeepsOwnerAndCells перестанет ловить эту поломку. При смене open_RecordAdded_KeepsOwnerAndCells проверь это утверждение.");
    }

    @Test
    void record_SameCellInAnotherActiveLease_Rejected() {
        LeaseBook book = new LeaseBook();
        Lease first = book.open(SPIRIT, "t1", "scaffold", 10);
        Lease second = book.open(OTHER, "t2", "bridge", 11);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(first.id(), LeaseCell.placed(cell));

        IllegalStateException conflict = assertThrows(
                IllegalStateException.class,
                () -> book.record(second.id(), LeaseCell.placed(cell)),
                "Две механики не могут владеть одной клеткой: иначе откат одной сломает чужую правку"
                        + " При смене record_SameCellInAnotherActiveLease_Rejected проверь это утверждение.");

        assertTrue(
                conflict.getMessage().contains(first.id()),
                "Сообщение обязано называть действующую аренду: по id владельца видно, кого ждать"
                        + " Иначе тест record_SameCellInAnotherActiveLease_Rejected перестанет ловить эту поломку. При смене record_SameCellInAnotherActiveLease_Rejected проверь это утверждение.");
    }

    @Test
    void record_SameCellInSameLeaseTwice_Rejected() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(lease.id(), LeaseCell.placed(cell));

        assertThrows(
                IllegalStateException.class,
                () -> book.record(lease.id(), LeaseCell.placed(cell)),
                "Повторная запись клетки сломала бы обратный откат: клетку откатывали бы дважды"
                        + " Иначе тест record_SameCellInSameLeaseTwice_Rejected перестанет ловить эту поломку. При смене record_SameCellInSameLeaseTwice_Rejected проверь это утверждение.");
    }

    @Test
    void record_AfterCommit_Rejected() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        book.record(lease.id(), LeaseCell.placed(new BlockCoord(3, 70, 5)));
        book.commit(lease.id());

        assertThrows(
                IllegalStateException.class,
                () -> book.record(lease.id(), LeaseCell.placed(new BlockCoord(4, 70, 5))),
                "Закрытая аренда новый блок не принимает: иначе откат переменной длины станет непредсказуем"
                        + " При смене record_AfterCommit_Rejected проверь это утверждение.");
    }

    @Test
    void commit_ReleasesCells_AndKeepsHistory() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(lease.id(), LeaseCell.placed(cell));

        book.commit(lease.id());

        assertEquals(
                LeaseStatus.DONE,
                book.require(lease.id()).status(),
                "Откат механики закрывает аренду DONE"
                        + " Иначе тест commit_ReleasesCells_AndKeepsHistory перестанет ловить эту поломку. При смене commit_ReleasesCells_AndKeepsHistory проверь это утверждение.");
        assertFalse(
                book.isActive(cell),
                "Закрытая аренда клетку не держит: её можно использовать снова"
                        + " Иначе тест commit_ReleasesCells_AndKeepsHistory перестанет ловить эту поломку. При смене commit_ReleasesCells_AndKeepsHistory проверь это утверждение.");
        assertFalse(
                book.isOrphaned(cell),
                "Закрытая аренда не считается брошенной"
                        + " Иначе тест commit_ReleasesCells_AndKeepsHistory перестанет ловить эту поломку. При смене commit_ReleasesCells_AndKeepsHistory проверь это утверждение.");
        assertTrue(
                book.orphaned().isEmpty(),
                "DONE не попадает в уборку графа"
                        + " Иначе тест commit_ReleasesCells_AndKeepsHistory перестанет ловить эту поломку. При смене commit_ReleasesCells_AndKeepsHistory проверь это утверждение.");
    }

    @Test
    void orphan_MovesLeaseToCleanup_AndFindsOwner() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "bridge", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(lease.id(), LeaseCell.placed(cell));

        book.orphan(lease.id());

        assertEquals(
                LeaseStatus.ORPHANED,
                book.require(lease.id()).status(),
                "Брошенная правка ждёт уборки"
                        + " Иначе тест orphan_MovesLeaseToCleanup_AndFindsOwner перестанет ловить эту поломку. При смене orphan_MovesLeaseToCleanup_AndFindsOwner проверь это утверждение.");
        assertTrue(
                book.isOrphaned(cell),
                "Скан обязан видеть клетку брошенной аренды"
                        + " Иначе тест orphan_MovesLeaseToCleanup_AndFindsOwner перестанет ловить эту поломку. При смене orphan_MovesLeaseToCleanup_AndFindsOwner проверь это утверждение.");
        assertFalse(
                book.isActive(cell),
                "Брошенная аренда больше не действует"
                        + " Иначе тест orphan_MovesLeaseToCleanup_AndFindsOwner перестанет ловить эту поломку. При смене orphan_MovesLeaseToCleanup_AndFindsOwner проверь это утверждение.");
        assertEquals(
                lease.id(),
                book.owner(cell).orElseThrow().id(),
                "По клетке обязана находиться брошенная аренда: графу нужно её назначение и срок"
                        + " Иначе тест orphan_MovesLeaseToCleanup_AndFindsOwner перестанет ловить эту поломку. При смене orphan_MovesLeaseToCleanup_AndFindsOwner проверь это утверждение.");
        assertEquals(
                List.of(cell),
                book.orphanedCells().stream().map(LeaseCell::pos).toList(),
                "Очистка знает клетки"
                        + " Иначе тест orphan_MovesLeaseToCleanup_AndFindsOwner перестанет ловить эту поломку. При смене orphan_MovesLeaseToCleanup_AndFindsOwner проверь это утверждение.");
    }

    @Test
    void orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases() {
        LeaseBook book = new LeaseBook();
        Lease mine = book.open(SPIRIT, "t1", "scaffold", 10);
        Lease foreign = book.open(OTHER, "t2", "bridge", 11);
        book.open(OTHER, "t3", "route", 12);

        int broken = book.orphanSpirit(SPIRIT);

        assertEquals(
                1,
                broken,
                "Смерть духа бросает только его аренды"
                        + " Иначе тест orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases перестанет ловить эту поломку. При смене orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases проверь это утверждение.");
        assertEquals(
                LeaseStatus.ORPHANED,
                book.require(mine.id()).status(),
                "Аренда умершего брошена"
                        + " Иначе тест orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases перестанет ловить эту поломку. При смене orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases проверь это утверждение.");
        assertEquals(
                LeaseStatus.ACTIVE,
                book.require(foreign.id()).status(),
                "Чужая действующая аренда не тронута"
                        + " Иначе тест orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases перестанет ловить эту поломку. При смене orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases проверь это утверждение.");
    }

    @Test
    void rollbackOrder_AfterRecording_ReversesRecordOrder() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        BlockCoord first = new BlockCoord(0, 64, 0);
        BlockCoord second = new BlockCoord(0, 65, 0);
        BlockCoord third = new BlockCoord(0, 66, 0);
        book.record(lease.id(), List.of(LeaseCell.placed(first), LeaseCell.placed(second)));
        book.record(lease.id(), LeaseCell.placed(third));

        assertEquals(
                List.of(third, second, first),
                book.rollbackOrder(book.require(lease.id())).stream()
                        .map(LeaseCell::pos)
                        .toList(),
                "Откат идёт сверху вниз, обратно записи: иначе дух разберёт опору под собой"
                        + " При смене rollbackOrder_AfterRecording_ReversesRecordOrder проверь это утверждение.");
    }

    @Test
    void record_CellFromOrphanedLease_Rejected() {
        LeaseBook book = new LeaseBook();
        Lease broken = book.open(SPIRIT, "t1", "bridge", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(broken.id(), LeaseCell.placed(cell));
        book.orphan(broken.id());
        Lease next = book.open(OTHER, "t2", "scaffold", 11);

        assertThrows(
                IllegalStateException.class,
                () -> book.record(next.id(), LeaseCell.placed(cell)),
                "Новая правка не занимает клетку брошенной аренды: откат старой сломал бы новую"
                        + " Иначе тест record_CellFromOrphanedLease_Rejected перестанет ловить эту поломку. При смене record_CellFromOrphanedLease_Rejected проверь это утверждение.");
    }

    @Test
    void cell_BrokenWithoutBefore_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(0, 64, 0), LeaseKind.BROKEN, null, null),
                "Сломанная клетка без прежнего блока неоткатываема: сохранение потеряло бы материал"
                        + " Иначе тест cell_BrokenWithoutBefore_Rejected перестанет ловить эту поломку. При смене cell_BrokenWithoutBefore_Rejected проверь это утверждение.");
    }

    @Test
    void cell_StateWithoutAfter_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(0, 64, 0), LeaseKind.STATE, "open", null),
                "Состояние без «после» не вернуть: закрыть дверь будет нечем"
                        + " Иначе тест cell_StateWithoutAfter_Rejected перестанет ловить эту поломку. При смене cell_StateWithoutAfter_Rejected проверь это утверждение.");
    }

    @Test
    void open_NegativeSince_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Lease.opened("id", SPIRIT, null, "scaffold", -1),
                "Отрицательный момент открытия аренды ломает срок брошенной правки"
                        + " При смене open_NegativeSince_Rejected проверь это утверждение.");
    }
}
