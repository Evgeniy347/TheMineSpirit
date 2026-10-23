package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementChestsTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);

    @Test
    void add_RepeatedChest_DoesNotDuplicate() {
        SettlementChests chests = new SettlementChests();

        assertTrue(chests.add(new BlockCoord(1, 64, 1)), "первая установка сундука обязана попасть в реестр");
        assertFalse(chests.add(new BlockCoord(1, 64, 1)), "повторное событие того же сундука не делает дубль");
        assertEquals(1, chests.size(), "в реестре один сундук: повторная установка не раздувает его");
    }

    @Test
    void remove_RegisteredChest_LeavesNoNeighbour() {
        SettlementChests chests = new SettlementChests();
        BlockCoord chest = new BlockCoord(5, 64, 5);
        chests.add(chest);

        assertTrue(chests.remove(chest), "снятие поставленного сундука обязано менять реестр");
        assertFalse(chests.remove(chest), "повторное снятие не считается изменением");
        assertEquals(List.of(), chests.nearest(ORIGIN, 1), "снятый сундук не находится поиском ближайших");
    }

    @Test
    void nearest_ChestsAround_ReturnsDistanceOrder() {
        SettlementChests chests = new SettlementChests();
        BlockCoord far = new BlockCoord(30, 64, 0);
        BlockCoord near = new BlockCoord(3, 64, 0);
        BlockCoord middle = new BlockCoord(10, 64, 0);
        chests.add(far);
        chests.add(near);
        chests.add(middle);

        assertEquals(
                List.of(near, middle, far),
                chests.nearest(ORIGIN, 3),
                "заход берёт ближайший сундук первым: порядок обязан идти по расстоянию, а не по установке");
        assertEquals(
                List.of(near),
                chests.nearest(ORIGIN, 1),
                "порция из одного сундука — только самый ближний: дальше дух не ходит зря");
    }

    @Test
    void nearest_AcrossCellRings_ReturnsEachChestOnceAndInOrder() {
        SettlementChests chests = new SettlementChests();
        List<BlockCoord> all = new ArrayList<>();
        for (int x = -40; x <= 40; x += 8) {
            for (int z = -40; z <= 40; z += 8) {
                BlockCoord pos = new BlockCoord(x, 64, z);
                chests.add(pos);
                all.add(pos);
            }
        }
        all.sort((left, right) -> {
            int byDistance = Long.compare(distance(left), distance(right));
            return byDistance != 0 ? byDistance : left.compareTo(right);
        });

        List<BlockCoord> found = chests.nearest(ORIGIN, all.size());

        assertEquals(all, found, "поиск ближайших обязан отдать каждый сундук ровно раз и в порядке расстояния");
        assertEquals(new HashSet<>(all).size(), new HashSet<>(found).size(), "дубликатов в выдаче реестра нет");
    }

    @Test
    void settlementChests_ManyDistantChests_NearestLookupCostStaysFlat() {
        SettlementChests small = crowded(100);
        SettlementChests large = crowded(1000);
        BlockCoord near = new BlockCoord(1, 64, 1);

        long smallBefore = small.probes();
        List<BlockCoord> smallFound = small.nearest(ORIGIN, 1);
        long smallCost = small.probes() - smallBefore;

        long largeBefore = large.probes();
        List<BlockCoord> largeFound = large.nearest(ORIGIN, 1);
        long largeCost = large.probes() - largeBefore;

        assertEquals(List.of(near), smallFound, "ближний сундук обязан находиться и среди сотни далёких");
        assertEquals(List.of(near), largeFound, "ближний сундук обязан находиться и среди тысячи далёких");
        assertEquals(
                smallCost,
                largeCost,
                "стоимость поиска ближайшего не растёт от числа далёких сундуков: реестр не перебирает весь склад, "
                        + "а читает ячейки от ближних к дальним. Было " + smallCost + ", стало " + largeCost);
        assertTrue(largeCost <= 16, "поиск ближайшего читает лишь ближние ячейки, а прочитал " + largeCost);
    }

    @Test
    void all_RegisteredChests_KeepInsertionSetWithoutDuplicates() {
        SettlementChests chests = new SettlementChests();
        chests.add(new BlockCoord(2, 64, 2));
        chests.add(new BlockCoord(2, 64, 2));
        chests.add(new BlockCoord(1, 64, 1));

        Set<BlockCoord> saved = new HashSet<>(chests.all());

        assertEquals(2, saved.size(), "снимок для сохранения содержит каждый сундук один раз");
    }

    private static SettlementChests crowded(int farChests) {
        SettlementChests chests = new SettlementChests();
        chests.add(new BlockCoord(1, 64, 1));
        for (int i = 0; i < farChests; i++) {
            chests.add(new BlockCoord(10_000 + i * 32, 64, 10_000));
        }
        return chests;
    }

    private static long distance(BlockCoord pos) {
        long dx = (long) pos.x() - ORIGIN.x();
        long dz = (long) pos.z() - ORIGIN.z();
        return dx * dx + dz * dz;
    }
}
