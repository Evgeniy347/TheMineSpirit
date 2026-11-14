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

class LeaseScenarioTests {
    private static final UUID BUILDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WALKER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DIGGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void threeSpirits_OneDead_CleansOnlyAbandonedAndDead() {
        LeaseBook book = new LeaseBook();
        BlockCoord scaffoldLow = new BlockCoord(0, 70, 0);
        BlockCoord scaffoldHigh = new BlockCoord(0, 71, 0);
        BlockCoord bridgeA = new BlockCoord(10, 64, 0);
        BlockCoord bridgeB = new BlockCoord(11, 64, 0);
        BlockCoord graveA = new BlockCoord(20, 40, 0);
        BlockCoord graveB = new BlockCoord(20, 41, 0);

        Lease scaffold = book.open(BUILDER, "scaffold-1", "scaffold", 100);
        book.record(
                scaffold.id(),
                List.of(
                        LeaseCell.placed(scaffoldLow, "minecraft:dirt"),
                        LeaseCell.placed(scaffoldHigh, "minecraft:dirt")));

        Lease bridge = book.open(WALKER, "bridge-1", "bridge", 110);
        book.record(
                bridge.id(),
                List.of(
                        LeaseCell.placed(bridgeA, "minecraft:oak_planks"),
                        LeaseCell.placed(bridgeB, "minecraft:oak_planks")));
        book.orphan(bridge.id());

        Lease grave = book.open(DIGGER, "digout-1", "dig_out", 120);
        book.record(
                grave.id(),
                List.of(
                        LeaseCell.broken(graveA, "minecraft:stone"),
                        LeaseCell.placed(graveB, "minecraft:cobblestone")));
        Lease graveLease = book.require(grave.id());

        Map<UUID, LeaseSupervisor.SpiritState> alive = new HashMap<>();
        alive.put(BUILDER, new LeaseSupervisor.SpiritState(false, Set.of("scaffold-1")));
        alive.put(WALKER, new LeaseSupervisor.SpiritState(false, Set.of("bridge-1")));
        List<Lease> due = LeaseSupervisor.due(book, alive, new HashMap<>(), 200, 600);

        assertEquals(
                List.of(graveLease),
                due,
                "Среди трёх духов только третий пропал: его аренда бросается, чужие действующие — нет");
        for (Lease lease : due) {
            book.orphan(lease.id());
        }

        List<BlockCoord> cleanup =
                book.orphanedCells().stream().map(LeaseCell::pos).toList();

        assertEquals(
                List.of(graveB, graveA, bridgeB, bridgeA),
                cleanup,
                "Скан даёт уборку только брошенного моста и аренды умершего, сверху вниз внутри каждой аренды");
        assertFalse(cleanup.contains(scaffoldLow), "Действующие подмости первого духа скан не трогает");
        assertFalse(cleanup.contains(scaffoldHigh), "Действующие подмости первого духа скан не трогает");
        assertTrue(
                book.activeOwner(scaffoldLow).isPresent(),
                "Клетка подмостей остаётся за живым владельцем: чужой дух её не разберёт");
        assertEquals(
                List.of("dig_out", "bridge"),
                book.orphaned().stream().map(Lease::purpose).toList(),
                "В уборке две брошенные аренды: мост отменён, аренда умершего снята событием смерти");
    }

    @Test
    void pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease() {
        LeaseBook book = new LeaseBook();
        LeaseSession column = LeaseSession.open(book, BUILDER, null, "scaffold", 100);
        BlockCoord low = new BlockCoord(0, 70, 0);
        BlockCoord high = new BlockCoord(0, 71, 0);
        column.placed(low, "minecraft:dirt");
        column.placed(high, "minecraft:dirt");

        column.orphan();

        assertOrphaned(book, column.id(), List.of(low, high), "cancel посреди столба");
        assertEquals(
                List.of(high, low),
                book.orphanedCells().stream().map(LeaseCell::pos).toList(),
                "Скан видит обе клетки брошенного столба и разбирает его сверху вниз");

        LeaseSession death = LeaseSession.open(book, BUILDER, null, "scaffold", 110);
        death.placed(new BlockCoord(2, 70, 0), "minecraft:dirt");
        book.orphanSpirit(BUILDER);
        assertOrphaned(book, death.id(), List.of(new BlockCoord(2, 70, 0)), "смерть духа");

        LeaseSession restart = LeaseSession.open(book, WALKER, null, "scaffold", 120);
        restart.placed(new BlockCoord(4, 70, 0), "minecraft:dirt");
        assertEquals(
                LeaseStatus.ACTIVE,
                book.require(restart.id()).status(),
                "Рестарт сервера (публикация) не бросает аренду: духи и аренды сохраняются, владелец вернётся "
                        + "к работе. Брошенной аренду делает только Supervisor по таймауту простоя или смерть "
                        + "владельца — иначе каждая публикация порождает undo-шторм: духи откатывают живую "
                        + "правку, а стройка по плану тут же ставит блок обратно (живой сервер 14.11: ломает "
                        + "блок земли и тут же ставит, 3 духа одновременно в undo)");
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

        assertEquals(LeaseStatus.DONE, column.status(), "Успешный разбор закрывает аренду столба");
        assertTrue(book.orphanedCells().isEmpty(), "После успешного разбора уборке нечего делать");
        assertFalse(book.isActive(first), "Разобранная клетка больше не держится арендой");
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
                "Перейдённый мост не становится постоянным: успешное использование — не план постройки");
        assertTrue(
                book.isOrphaned(cell),
                "Настил после переправы уходит в узел отката: внутри поселения мосты и столбы убираются");
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
                "Пройденный маршрут не становится постоянным: его клетки откатывает узел undo_lease");
        assertTrue(book.isOrphaned(placed), "Поставленная клетка маршрута ждёт отката");
        assertTrue(book.isOrphaned(broken), "Сломанная клетка маршрута ждёт возврата before");

        LeaseSession abandoned = LeaseSession.open(book, BUILDER, null, "route", 120);
        BlockCoord hole = new BlockCoord(40, 70, 0);
        abandoned.broken(hole, "minecraft:stone");
        abandoned.orphan();

        assertOrphaned(book, abandoned.id(), List.of(hole), "cancel посреди маршрута");
        assertEquals(
                "minecraft:stone",
                book.require(abandoned.id()).cells().get(0).before(),
                "Пролом маршрута помнит прежний блок: брошенная аренда вернёт его по before");
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
                "Успешный выход убирает spoil, но оставшийся пол ступени — временная правка, а не план");
        assertTrue(book.isOrphaned(floor), "Пол ступени после выхода ждёт отката, а не остаётся навсегда");
        assertFalse(book.isOrphaned(broken), "Возвращённый spoil уже не ждёт откат: клетка забыта механикой");

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
                "Брошенный dig_out откатывается сверху вниз: сначала свой пол, потом возврат spoil");
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
                "Сразу после прерывания аренда ещё действует: пауза начинается с этого тика");
        List<Lease> due = LeaseSupervisor.due(book, hungry, suspendedSince, 400, 100);
        assertEquals(2, due.size(), "Оба духа прерваны голодом: обе аренды ждут уборки, а не только одна");
        for (Lease lease : due) {
            book.orphan(lease.id());
        }

        List<BlockCoord> cleanup =
                book.orphanedCells().stream().map(LeaseCell::pos).toList();
        assertEquals(
                List.of(deckB, deckA, columnHigh, columnLow),
                cleanup,
                "Скан видит обе брошенные правки: настил второго духа и столб первого, сверху вниз");
        assertTrue(book.active().isEmpty(), "Действующих аренд после голода нет: активные клетки скан не трогает");
    }

    private static void assertOrphaned(LeaseBook book, String id, List<BlockCoord> cells, String reason) {
        assertEquals(LeaseStatus.ORPHANED, book.require(id).status(), reason + ": аренда обязана быть брошена");
        for (BlockCoord cell : cells) {
            assertTrue(book.isOrphaned(cell), reason + ": клетка " + cell + " обязана быть видна скану как брошенная");
            assertFalse(book.isActive(cell), reason + ": брошенная клетка не считается действующей");
        }
    }

    @Test
    void twoMechanics_SameCell_FirstOwnerKeepsRollback() {
        LeaseBook book = new LeaseBook();
        BlockCoord shared = new BlockCoord(70, 50, 0);
        LeaseSession dig = LeaseSession.open(book, DIGGER, "dig-1", "dig_out", 100);
        assertTrue(dig.broken(shared, "minecraft:stone"), "Тоннель записывает пролом первым");

        LeaseSession bridge = LeaseSession.open(book, WALKER, "bridge-1", "bridge", 110);
        assertFalse(
                bridge.placed(shared, "minecraft:oak_planks"),
                "Мост не забирает клетку тоннеля: откат остаётся за первым владельцем, а перекрытие не роняет стройку");
        assertEquals(dig.id(), book.owner(shared).orElseThrow().id(), "Владелец клетки не сменился");
        assertTrue(bridge.cells().isEmpty(), "Вторая аренда не записала чужую клетку");

        BlockCoord deck = new BlockCoord(71, 64, 0);
        assertTrue(bridge.placed(deck, "minecraft:oak_planks"), "Свои клетки мост по-прежнему записывает");

        dig.orphan();
        assertEquals(
                List.of(shared),
                book.orphanedCells().stream().map(LeaseCell::pos).toList(),
                "Уборка видит пролом тоннеля один раз: клетка осталась за брошенной арендой");
        assertTrue(book.activeOwner(deck).isPresent(), "Настил моста остаётся за живым владельцем");
    }
}
