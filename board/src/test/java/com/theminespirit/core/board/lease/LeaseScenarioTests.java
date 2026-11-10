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
    void activeLease_AfterCommit_StopsCleanup() {
        LeaseBook book = new LeaseBook();
        BlockCoord cell = new BlockCoord(0, 70, 0);
        Lease scaffold = book.open(BUILDER, "scaffold-1", "scaffold", 100);
        book.record(scaffold.id(), LeaseCell.placed(cell));

        book.commit(scaffold.id());

        assertFalse(book.isActive(cell), "После успешного спуска столб перестал быть действующей арендой");
        assertFalse(book.isOrphaned(cell), "Успешно убранная правка не попадает в уборку графа");
        assertTrue(book.orphanedCells().isEmpty(), "Уборке нечего делать: механика закрыла аренду сама");
    }
}
