package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseScenarioPart2Tests {
    private static final UUID BUILDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WALKER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DIGGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static void assertOrphaned(LeaseBook book, String id, List<BlockCoord> cells, String reason) {
        assertEquals(
                LeaseStatus.ORPHANED,
                book.require(id).status(),
                reason
                        + ": аренда обязана быть брошена Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
        for (BlockCoord cell : cells) {
            assertTrue(
                    book.isOrphaned(cell),
                    reason + ": клетка " + cell
                            + " обязана быть видна скану как брошенная Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
            assertFalse(
                    book.isActive(cell),
                    reason
                            + ": брошенная клетка не считается действующей Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.");
        }
    }

    @Test
    void pillarDown_Success_RollsBackOwnCellsAndCommits() {
        LeaseBook book = new LeaseBook();
        LeaseSession column = LeaseSession.open(book, BUILDER, null, "scaffold", 100);
        BlockCoord first = new BlockCoord(0, 70, 0);
        BlockCoord second = new BlockCoord(0, 71, 0);
        column.placed(first, "minecraft:dirt");
        column.placed(second, "minecraft:dirt");
        column.forget(second);
        column.forget(first);
        column.commit();
        assertEquals(
                LeaseStatus.DONE,
                column.status(),
                "Успешный разбор закрывает аренду столба Иначе тест pillarDown_Success_RollsBackOwnCellsAndCommits перестанет ловить эту поломку. При смене pillarDown_Success_RollsBackOwnCellsAndCommits проверь это утверждение.");
        assertTrue(
                book.orphanedCells().isEmpty(),
                "После успешного разбора уборке нечего делать Иначе тест pillarDown_Success_RollsBackOwnCellsAndCommits перестанет ловить эту поломку. При смене pillarDown_Success_RollsBackOwnCellsAndCommits проверь это утверждение.");
        assertFalse(
                book.isActive(first),
                "Разобранная клетка больше не держится арендой Иначе тест pillarDown_Success_RollsBackOwnCellsAndCommits перестанет ловить эту поломку. При смене pillarDown_Success_RollsBackOwnCellsAndCommits проверь это утверждение.");
    }

    @Test
    void bridge_CancelAndSuccess_DifferByStatusAndCleanup() {
        LeaseBook book = new LeaseBook();
        LeaseSession deck = LeaseSession.open(book, WALKER, null, "bridge", 100);
        BlockCoord first = new BlockCoord(10, 64, 0);
        BlockCoord second = new BlockCoord(11, 64, 0);
        deck.placed(first, "minecraft:oak_planks");
        deck.placed(second, "minecraft:oak_planks");
        deck.orphan();
        assertOrphaned(book, deck.id(), List.of(first, second), "cancel посреди моста");
        LeaseSession crossed = LeaseSession.open(book, DIGGER, null, "bridge", 120);
        BlockCoord cell = new BlockCoord(20, 64, 0);
        crossed.placed(cell, "minecraft:oak_planks");
        crossed.orphan();
        assertEquals(
                LeaseStatus.ORPHANED,
                crossed.status(),
                "Перейдённый мост не становится постоянным: успешное использование — не план постройки Иначе тест bridge_CancelAndSuccess_DifferByStatusAndCleanup перестанет ловить эту поломку. При смене bridge_CancelAndSuccess_DifferByStatusAndCleanup проверь это утверждение.");
        assertTrue(
                book.isOrphaned(cell),
                "Настил после переправы уходит в узел отката: внутри поселения мосты и столбы убираются Иначе тест bridge_CancelAndSuccess_DifferByStatusAndCleanup перестанет ловить эту поломку. При смене bridge_CancelAndSuccess_DifferByStatusAndCleanup проверь это утверждение.");
    }

    @Test
    void route_PassOrphansCells_BreakAndCancelOrphans() {
        LeaseBook book = new LeaseBook();
        LeaseSession passed = LeaseSession.open(book, WALKER, null, "route", 100);
        BlockCoord placed = new BlockCoord(30, 71, 0);
        BlockCoord broken = new BlockCoord(31, 70, 0);
        passed.placed(placed, "minecraft:dirt");
        passed.broken(broken, "minecraft:stone");
        passed.orphan();
        assertEquals(
                LeaseStatus.ORPHANED,
                passed.status(),
                "Пройденный маршрут не становится постоянным: его клетки откатывает узел undo_lease Иначе тест route_PassOrphansCells_BreakAndCancelOrphans перестанет ловить эту поломку. При смене route_PassOrphansCells_BreakAndCancelOrphans проверь это утверждение.");
        assertTrue(
                book.isOrphaned(placed),
                "Поставленная клетка маршрута ждёт отката Иначе тест route_PassOrphansCells_BreakAndCancelOrphans перестанет ловить эту поломку. При смене route_PassOrphansCells_BreakAndCancelOrphans проверь это утверждение.");
        assertTrue(
                book.isOrphaned(broken),
                "Сломанная клетка маршрута ждёт возврата before Иначе тест route_PassOrphansCells_BreakAndCancelOrphans перестанет ловить эту поломку. При смене route_PassOrphansCells_BreakAndCancelOrphans проверь это утверждение.");
        LeaseSession abandoned = LeaseSession.open(book, BUILDER, null, "route", 120);
        BlockCoord hole = new BlockCoord(40, 70, 0);
        abandoned.broken(hole, "minecraft:stone");
        abandoned.orphan();
        assertOrphaned(book, abandoned.id(), List.of(hole), "cancel посреди маршрута");
        assertEquals(
                "minecraft:stone",
                book.require(abandoned.id()).cells().get(0).before(),
                "Пролом маршрута помнит прежний блок: брошенная аренда вернёт его по before Иначе тест route_PassOrphansCells_BreakAndCancelOrphans перестанет ловить эту поломку. При смене route_PassOrphansCells_BreakAndCancelOrphans проверь это утверждение.");
    }

    @Test
    void digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything() {
        LeaseBook book = new LeaseBook();
        LeaseSession escape = LeaseSession.open(book, DIGGER, null, "dig_out", 100);
        BlockCoord broken = new BlockCoord(50, 50, 0);
        BlockCoord floor = new BlockCoord(51, 51, 0);
        escape.broken(broken, "minecraft:stone");
        escape.placed(floor, "minecraft:dirt");
        escape.forget(broken);
        escape.orphan();
        assertEquals(
                LeaseStatus.ORPHANED,
                escape.status(),
                "Успешный выход убирает spoil, но оставшийся пол ступени — временная правка, а не план Иначе тест digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything перестанет ловить эту поломку. При смене digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything проверь это утверждение.");
        assertTrue(
                book.isOrphaned(floor),
                "Пол ступени после выхода ждёт отката, а не остаётся навсегда Иначе тест digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything перестанет ловить эту поломку. При смене digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything проверь это утверждение.");
        assertFalse(
                book.isOrphaned(broken),
                "Возвращённый spoil уже не ждёт откат: клетка забыта механикой Иначе тест digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything перестанет ловить эту поломку. При смене digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything проверь это утверждение.");
        LeaseSession interrupted = LeaseSession.open(book, BUILDER, null, "dig_out", 120);
        BlockCoord hole = new BlockCoord(60, 50, 0);
        BlockCoord step = new BlockCoord(61, 51, 0);
        interrupted.broken(hole, "minecraft:dirt");
        interrupted.placed(step, "minecraft:cobblestone");
        interrupted.orphan();
        assertOrphaned(book, interrupted.id(), List.of(hole, step), "cancel посреди dig_out");
        assertEquals(
                List.of(step, hole),
                book.require(interrupted.id()).rollbackOrder().stream()
                        .map(LeaseCell::pos)
                        .toList(),
                "Брошенный dig_out откатывается сверху вниз: сначала свой пол, потом возврат spoil Иначе тест digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything перестанет ловить эту поломку. При смене digOut_SuccessOrphansRemainingCells_InterruptOrphansEverything проверь это утверждение.");
    }

    @Test
    void twoSpirits_Hunger_LeavesBothLeasesOrphaned() {
        LeaseBook book = new LeaseBook();
        LeaseSession pillar = LeaseSession.open(book, BUILDER, "pillar-ticket", "scaffold", 100);
        LeaseSession bridge = LeaseSession.open(book, WALKER, "bridge-ticket", "bridge", 100);
        BlockCoord columnLow = new BlockCoord(0, 70, 0);
        BlockCoord columnHigh = new BlockCoord(0, 73, 0);
        BlockCoord deckA = new BlockCoord(10, 64, 0);
        BlockCoord deckB = new BlockCoord(11, 64, 0);
        pillar.placed(columnLow, "minecraft:dirt");
        pillar.placed(columnHigh, "minecraft:dirt");
        bridge.placed(deckA, "minecraft:oak_planks");
        bridge.placed(deckB, "minecraft:oak_planks");
        Map<UUID, LeaseSupervisor.SpiritState> hungry = new HashMap<>();
        hungry.put(BUILDER, new LeaseSupervisor.SpiritState(true, Set.of("pillar-ticket")));
        hungry.put(WALKER, new LeaseSupervisor.SpiritState(true, Set.of("bridge-ticket")));
        Map<String, Long> suspendedSince = new HashMap<>();
        assertTrue(
                LeaseSupervisor.due(book, hungry, suspendedSince, 200, 100).isEmpty(),
                "Сразу после прерывания аренда ещё действует: пауза начинается с этого тика Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
        List<Lease> due = LeaseSupervisor.due(book, hungry, suspendedSince, 400, 100);
        assertEquals(
                2,
                due.size(),
                "Оба духа прерваны голодом: обе аренды ждут уборки, а не только одна Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
        for (Lease lease : due) {
            book.orphan(lease.id());
        }
        List<BlockCoord> cleanup =
                book.orphanedCells().stream().map(LeaseCell::pos).toList();
        assertEquals(
                List.of(deckB, deckA, columnHigh, columnLow),
                cleanup,
                "Скан видит обе брошенные правки: настил второго духа и столб первого, сверху вниз Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
        assertTrue(
                book.active().isEmpty(),
                "Действующих аренд после голода нет: активные клетки скан не трогает Иначе тест twoSpirits_Hunger_LeavesBothLeasesOrphaned перестанет ловить эту поломку. При смене twoSpirits_Hunger_LeavesBothLeasesOrphaned проверь это утверждение.");
    }

    @Test
    void twoMechanics_SameCell_FirstOwnerKeepsRollback() {
        LeaseBook book = new LeaseBook();
        BlockCoord shared = new BlockCoord(70, 50, 0);
        LeaseSession dig = LeaseSession.open(book, DIGGER, "dig-1", "dig_out", 100);
        assertTrue(
                dig.broken(shared, "minecraft:stone"),
                "Тоннель записывает пролом первым Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        LeaseSession bridge = LeaseSession.open(book, WALKER, "bridge-1", "bridge", 110);
        assertFalse(
                bridge.placed(shared, "minecraft:oak_planks"),
                "Мост не забирает клетку тоннеля: откат остаётся за первым владельцем, а перекрытие не роняет стройку Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        assertEquals(
                dig.id(),
                book.owner(shared).orElseThrow().id(),
                "Владелец клетки не сменился Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        assertTrue(
                bridge.cells().isEmpty(),
                "Вторая аренда не записала чужую клетку Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        BlockCoord deck = new BlockCoord(71, 64, 0);
        assertTrue(
                bridge.placed(deck, "minecraft:oak_planks"),
                "Свои клетки мост по-прежнему записывает Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        dig.orphan();
        assertEquals(
                List.of(shared),
                book.orphanedCells().stream().map(LeaseCell::pos).toList(),
                "Уборка видит пролом тоннеля один раз: клетка осталась за брошенной арендой Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
        assertTrue(
                book.activeOwner(deck).isPresent(),
                "Настил моста остаётся за живым владельцем Иначе тест twoMechanics_SameCell_FirstOwnerKeepsRollback перестанет ловить эту поломку. При смене twoMechanics_SameCell_FirstOwnerKeepsRollback проверь это утверждение.");
    }
}
