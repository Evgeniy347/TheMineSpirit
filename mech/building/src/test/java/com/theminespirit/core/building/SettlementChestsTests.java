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

        assertTrue(
                chests.add(new BlockCoord(1, 64, 1)),
                "первая установка сундука обязана попасть в реестр"
                        + " Иначе тест add_RepeatedChest_DoesNotDuplicate перестанет ловить эту поломку. При смене add_RepeatedChest_DoesNotDuplicate проверь это утверждение.");
        assertFalse(
                chests.add(new BlockCoord(1, 64, 1)),
                "повторное событие того же сундука не делает дубль"
                        + " Иначе тест add_RepeatedChest_DoesNotDuplicate перестанет ловить эту поломку. При смене add_RepeatedChest_DoesNotDuplicate проверь это утверждение.");
        assertEquals(
                1,
                chests.size(),
                "в реестре один сундук: повторная установка не раздувает его"
                        + " Иначе тест add_RepeatedChest_DoesNotDuplicate перестанет ловить эту поломку. При смене add_RepeatedChest_DoesNotDuplicate проверь это утверждение.");
    }

    @Test
    void remove_RegisteredChest_LeavesNoNeighbour() {
        SettlementChests chests = new SettlementChests();
        BlockCoord chest = new BlockCoord(5, 64, 5);
        chests.add(chest);

        assertTrue(
                chests.remove(chest),
                "снятие поставленного сундука обязано менять реестр"
                        + " Иначе тест remove_RegisteredChest_LeavesNoNeighbour перестанет ловить эту поломку. При смене remove_RegisteredChest_LeavesNoNeighbour проверь это утверждение.");
        assertFalse(
                chests.remove(chest),
                "повторное снятие не считается изменением"
                        + " Иначе тест remove_RegisteredChest_LeavesNoNeighbour перестанет ловить эту поломку. При смене remove_RegisteredChest_LeavesNoNeighbour проверь это утверждение.");
        assertEquals(
                List.of(),
                chests.nearest(ORIGIN, 1),
                "снятый сундук не находится поиском ближайших"
                        + " Иначе тест remove_RegisteredChest_LeavesNoNeighbour перестанет ловить эту поломку. При смене remove_RegisteredChest_LeavesNoNeighbour проверь это утверждение.");
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
                "заход берёт ближайший сундук первым: порядок обязан идти по расстоянию, а не по установке"
                        + " Иначе тест nearest_ChestsAround_ReturnsDistanceOrder перестанет ловить эту поломку. При смене nearest_ChestsAround_ReturnsDistanceOrder проверь это утверждение.");
        assertEquals(
                List.of(near),
                chests.nearest(ORIGIN, 1),
                "порция из одного сундука — только самый ближний: дальше дух не ходит зря"
                        + " Иначе тест nearest_ChestsAround_ReturnsDistanceOrder перестанет ловить эту поломку. При смене nearest_ChestsAround_ReturnsDistanceOrder проверь это утверждение.");
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

        assertEquals(
                all,
                found,
                "поиск ближайших обязан отдать каждый сундук ровно раз и в порядке расстояния"
                        + " Иначе тест nearest_AcrossCellRings_ReturnsEachChestOnceAndInOrder перестанет ловить эту поломку. При смене nearest_AcrossCellRings_ReturnsEachChestOnceAndInOrder проверь это утверждение.");
        assertEquals(
                new HashSet<>(all).size(),
                new HashSet<>(found).size(),
                "дубликатов в выдаче реестра нет"
                        + " Иначе тест nearest_AcrossCellRings_ReturnsEachChestOnceAndInOrder перестанет ловить эту поломку. При смене nearest_AcrossCellRings_ReturnsEachChestOnceAndInOrder проверь это утверждение.");
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

        assertEquals(
                List.of(near),
                smallFound,
                "ближний сундук обязан находиться и среди сотни далёких"
                        + " Иначе тест settlementChests_ManyDistantChests_NearestLookupCostStaysFlat перестанет ловить эту поломку. При смене settlementChests_ManyDistantChests_NearestLookupCostStaysFlat проверь это утверждение.");
        assertEquals(
                List.of(near),
                largeFound,
                "ближний сундук обязан находиться и среди тысячи далёких"
                        + " Иначе тест settlementChests_ManyDistantChests_NearestLookupCostStaysFlat перестанет ловить эту поломку. При смене settlementChests_ManyDistantChests_NearestLookupCostStaysFlat проверь это утверждение.");
        assertEquals(
                smallCost,
                largeCost,
                "стоимость поиска ближайшего не растёт от числа далёких сундуков: реестр не перебирает весь склад, "
                        + "а читает ячейки от ближних к дальним. Было " + smallCost + ", стало " + largeCost
                        + " Иначе тест settlementChests_ManyDistantChests_NearestLookupCostStaysFlat перестанет ловить эту поломку. При смене settlementChests_ManyDistantChests_NearestLookupCostStaysFlat проверь это утверждение.");
        assertTrue(
                largeCost <= 16,
                "поиск ближайшего читает лишь ближние ячейки, а прочитал " + largeCost
                        + " Иначе тест settlementChests_ManyDistantChests_NearestLookupCostStaysFlat перестанет ловить эту поломку. При смене settlementChests_ManyDistantChests_NearestLookupCostStaysFlat проверь это утверждение.");
    }

    @Test
    void warehouse_WithoutSettlementBoundary_KeepsWholeRegistry() {
        SettlementChests chests = new SettlementChests();
        BlockCoord spirit = new BlockCoord(-179, 68, -515);
        BlockCoord yard = new BlockCoord(-171, 71, -487);
        chests.add(yard);

        assertEquals(
                List.of(yard),
                chests.warehouse(spirit),
                "без границы поселения склад — весь реестр сундуков, а не 16 блоков вокруг случайной точки духа: "
                        + "сундук двора в 29 блоках обязан попасть в выбор точки сдачи и еды"
                        + " Иначе тест warehouse_WithoutSettlementBoundary_KeepsWholeRegistry перестанет ловить эту поломку. При смене warehouse_WithoutSettlementBoundary_KeepsWholeRegistry проверь это утверждение.");
    }

    @Test
    void warehouse_ChestOutsideSettlement_IsNotOffered() {
        SettlementChests chests = new SettlementChests();
        BlockCoord spirit = new BlockCoord(-179, 68, -515);
        BlockCoord yard = new BlockCoord(-171, 71, -487);
        BlockCoord farBarrel = new BlockCoord(-73, 75, -119);
        chests.add(yard);
        chests.add(farBarrel);
        assertEquals(
                List.of(yard),
                chests.warehouse(
                        spirit, pos -> pos.x() >= -200 && pos.x() <= -140 && pos.z() >= -540 && pos.z() <= -460),
                "Склад поселения — только контейнеры внутри его границ: бочка −73,75,−119 в ~100 блоках вне ограды "
                        + "не склад поселения (аномалия 6), иначе дух идёт за материалом за пределы поселения"
                        + " При смене warehouse_ChestOutsideSettlement_IsNotOffered проверь это утверждение.");
    }

    @Test
    void all_RegisteredChests_KeepInsertionSetWithoutDuplicates() {
        SettlementChests chests = new SettlementChests();
        chests.add(new BlockCoord(2, 64, 2));
        chests.add(new BlockCoord(2, 64, 2));
        chests.add(new BlockCoord(1, 64, 1));

        Set<BlockCoord> saved = new HashSet<>(chests.all());

        assertEquals(
                2,
                saved.size(),
                "снимок для сохранения содержит каждый сундук один раз"
                        + " Иначе тест all_RegisteredChests_KeepInsertionSetWithoutDuplicates перестанет ловить эту поломку. При смене all_RegisteredChests_KeepInsertionSetWithoutDuplicates проверь это утверждение.");
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
