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
import java.util.stream.Collectors;
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
                "Среди трёх духов только третий пропал: его аренда бросается, чужие действующие — нет Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
        for (Lease lease : due) {
            book.orphan(lease.id());
        }
        List<BlockCoord> cleanup =
                book.orphanedCells().stream().map(LeaseCell::pos).toList();
        assertEquals(
                List.of(graveB, graveA, bridgeB, bridgeA),
                cleanup,
                "Скан даёт уборку только брошенного моста и аренды умершего, сверху вниз внутри каждой аренды Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
        assertFalse(
                cleanup.contains(scaffoldLow),
                "Действующие подмости первого духа скан не трогает Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
        assertFalse(
                cleanup.contains(scaffoldHigh),
                "Действующие подмости первого духа скан не трогает Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
        assertTrue(
                book.activeOwner(scaffoldLow).isPresent(),
                "Клетка подмостей остаётся за живым владельцем: чужой дух её не разберёт Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
        assertEquals(
                List.of("dig_out", "bridge"),
                book.orphaned().stream().map(Lease::purpose).toList(),
                "В уборке две брошенные аренды: мост отменён, аренда умершего снята событием смерти Иначе тест threeSpirits_OneDead_CleansOnlyAbandonedAndDead перестанет ловить эту поломку. При смене threeSpirits_OneDead_CleansOnlyAbandonedAndDead проверь это утверждение.");
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
                "Скан видит обе клетки брошенного столба и разбирает его сверху вниз Иначе тест pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease перестанет ловить эту поломку. При смене pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease проверь это утверждение.");
        LeaseSession death = LeaseSession.open(book, BUILDER, null, "scaffold", 110);
        death.placed(new BlockCoord(2, 70, 0), "minecraft:dirt");
        book.orphanSpirit(BUILDER);
        assertOrphaned(book, death.id(), List.of(new BlockCoord(2, 70, 0)), "смерть духа");
        LeaseSession restart = LeaseSession.open(book, WALKER, null, "scaffold", 120);
        restart.placed(new BlockCoord(4, 70, 0), "minecraft:dirt");
        LeaseSession displaced = LeaseSession.open(book, WALKER, null, "pillar_up", 130);
        displaced.placed(new BlockCoord(6, 70, 0), "minecraft:dirt");
        book.orphanSpirit(WALKER);
        Set<String> orphanedIds = book.orphaned().stream().map(Lease::id).collect(Collectors.toSet());
        assertTrue(
                orphanedIds.containsAll(Set.of(restart.id(), displaced.id())),
                "Рестарт сервера (публикация) не бросает аренды — бросает их вытеснение/смерть владельца: "
                        + "orphanSpirit берёт обе аренды WALKER, включая столб pillar_up, который без задачи "
                        + "обязан уйти в разбор (живой сервер 14.11: undo-шторм «ломает и тут же ставит» и "
                        + "вечный столб spirit-5 у бугра) При смене pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease проверь это утверждение.");
    }

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
                            + ": брошенная клетка не считается действующей Иначе тест pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease перестанет ловить эту поломку. При смене pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease проверь это утверждение.");
        }
    }
}
