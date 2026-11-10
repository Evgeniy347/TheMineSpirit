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

        assertEquals(LeaseStatus.ACTIVE, lease.status(), "Открытая аренда действует: скан не трогает её клетки");
        assertEquals(SPIRIT, lease.spirit(), "Владелец аренды обязан читаться: по нему смерть бросает правку");
        assertEquals("ticket-1", lease.ticket(), "Тикет обязан читаться: по нему проверяют, не ушёл ли он другому");
        assertEquals("scaffold", lease.purpose(), "Назначение обязано читаться: по нему граф выбирает уборку");
        assertEquals(lease.id(), book.owner(cell).orElseThrow().id(), "Клетка обязана знать свою действующую аренду");
        assertTrue(book.isActive(cell), "Клетка действующей аренды обязана считаться занятой");
        assertFalse(book.isOrphaned(cell), "Действующая аренда не бывает брошенной");
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
                "Две механики не могут владеть одной клеткой: иначе откат одной сломает чужую правку");

        assertTrue(
                conflict.getMessage().contains(first.id()),
                "Сообщение обязано называть действующую аренду: по id владельца видно, кого ждать");
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
                "Повторная запись клетки сломала бы обратный откат: клетку откатывали бы дважды");
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
                "Закрытая аренда новый блок не принимает: иначе откат переменной длины станет непредсказуем");
    }

    @Test
    void commit_ReleasesCells_AndKeepsHistory() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(lease.id(), LeaseCell.placed(cell));

        book.commit(lease.id());

        assertEquals(LeaseStatus.DONE, book.require(lease.id()).status(), "Откат механики закрывает аренду DONE");
        assertFalse(book.isActive(cell), "Закрытая аренда клетку не держит: её можно использовать снова");
        assertFalse(book.isOrphaned(cell), "Закрытая аренда не считается брошенной");
        assertTrue(book.orphaned().isEmpty(), "DONE не попадает в уборку графа");
    }

    @Test
    void orphan_MovesLeaseToCleanup_AndFindsOwner() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "bridge", 10);
        BlockCoord cell = new BlockCoord(3, 70, 5);
        book.record(lease.id(), LeaseCell.placed(cell));

        book.orphan(lease.id());

        assertEquals(LeaseStatus.ORPHANED, book.require(lease.id()).status(), "Брошенная правка ждёт уборки");
        assertTrue(book.isOrphaned(cell), "Скан обязан видеть клетку брошенной аренды");
        assertFalse(book.isActive(cell), "Брошенная аренда больше не действует");
        assertEquals(
                lease.id(),
                book.owner(cell).orElseThrow().id(),
                "По клетке обязана находиться брошенная аренда: графу нужно её назначение и срок");
        assertEquals(
                List.of(cell), book.orphanedCells().stream().map(LeaseCell::pos).toList(), "Очистка знает клетки");
    }

    @Test
    void orphanSpirit_OtherSpiritAlive_BreaksOnlyItsLeases() {
        LeaseBook book = new LeaseBook();
        Lease mine = book.open(SPIRIT, "t1", "scaffold", 10);
        Lease foreign = book.open(OTHER, "t2", "bridge", 11);
        book.open(OTHER, "t3", "route", 12);

        int broken = book.orphanSpirit(SPIRIT);

        assertEquals(1, broken, "Смерть духа бросает только его аренды");
        assertEquals(LeaseStatus.ORPHANED, book.require(mine.id()).status(), "Аренда умершего брошена");
        assertEquals(LeaseStatus.ACTIVE, book.require(foreign.id()).status(), "Чужая действующая аренда не тронута");
    }

    @Test
    void orphanAllActive_Restart_OrphansEveryActiveLease() {
        LeaseBook book = new LeaseBook();
        book.open(SPIRIT, "t1", "scaffold", 10);
        book.open(OTHER, "t2", "bridge", 11);
        Lease done = book.open(OTHER, "t3", "route", 12);
        book.commit(done.id());

        int broken = book.orphanAllActive();

        assertEquals(2, broken, "Рестарт бросает все действующие аренды: скрипты его не пережили");
        assertEquals(2, book.orphaned().size(), "Обе действующие аренды попали в уборку");
        assertEquals(LeaseStatus.DONE, book.require(done.id()).status(), "Уже закрытая аренда остаётся закрытой");
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
                "Откат идёт сверху вниз, обратно записи: иначе дух разберёт опору под собой");
    }

    @Test
    void legacy_FirstMigration_MakesSingleOrphanedLease() {
        LeaseBook book = new LeaseBook();
        BlockCoord one = new BlockCoord(1, 64, 1);
        BlockCoord two = new BlockCoord(2, 64, 1);

        Lease legacy = book.legacy(List.of(one, two), 42);

        assertEquals(
                LeaseStatus.ORPHANED, legacy.status(), "Старые T-метки не имеют живого владельца: их убирает граф");
        assertEquals(LeaseBook.LEGACY_PURPOSE, legacy.purpose(), "Миграция узнаётся по единому назначению legacy");
        assertEquals(2, legacy.cells().size(), "В одну аренду сливаются все старые метки");
        assertTrue(book.isOrphaned(one) && book.isOrphaned(two), "Старые метки видны скану как брошенные");
        assertEquals(
                legacy.id(), book.legacy(List.of(one), 43).id(), "Повторная миграция не плодит вторую legacy-аренду");
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
                "Новая правка не занимает клетку брошенной аренды: откат старой сломал бы новую");
    }

    @Test
    void cell_BrokenWithoutBefore_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(0, 64, 0), LeaseKind.BROKEN, null, null),
                "Сломанная клетка без прежнего блока неоткатываема: сохранение потеряло бы материал");
    }

    @Test
    void cell_StateWithoutAfter_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseCell(new BlockCoord(0, 64, 0), LeaseKind.STATE, "open", null),
                "Состояние без «после» не вернуть: закрыть дверь будет нечем");
    }

    @Test
    void open_NegativeSince_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Lease.opened("id", SPIRIT, null, "scaffold", -1),
                "Отрицательный момент открытия аренды ломает срок брошенной правки");
    }
}
