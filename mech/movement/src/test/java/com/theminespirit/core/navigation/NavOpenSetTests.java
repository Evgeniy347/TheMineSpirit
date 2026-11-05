// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/openset/OpenSetsTest.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NavOpenSetTests {
    private static final NavGoal ZERO = new NavGoal() {
        @Override
        public boolean isInGoal(int x, int y, int z) {
            return false;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return 0;
        }
    };

    @Test
    void heap_InsertUpdateRemove_KeepsAscendingCosts() {
        Random random = new Random(42);
        for (int size : new int[] {1, 2, 17, 128, 1024}) {
            assertAscending(size, random);
        }
    }

    static void assertAscending(int size, Random random) {
        NavNode[] nodes = new NavNode[size];
        List<NavNode> expected = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            NavNode node = new NavNode(0, 0, 0, ZERO);
            node.combinedCost = random.nextDouble();
            nodes[i] = node;
            expected.add(node);
        }
        NavOpenSet heap = new NavBinaryHeapOpenSet();
        assertTrue(heap.isEmpty(), "новая куча пуста");
        for (NavNode node : nodes) {
            heap.insert(node);
        }
        assertFalse(heap.isEmpty(), "после вставки куча не пуста");
        expected.sort(Comparator.comparingDouble(NavNode::combinedCost));

        for (int i = 0; i < size / 2; i++) {
            assertEquals(expected.get(i), heap.removeLowest(), "куча отдаёт узел с наименьшей ценой");
        }
        for (NavNode node : nodes) {
            if (!node.isOpen()) {
                continue;
            }
            node.combinedCost *= random.nextDouble();
            heap.update(node);
        }
        expected.sort(Comparator.comparingDouble(NavNode::combinedCost));
        double previous = Double.NEGATIVE_INFINITY;
        while (!heap.isEmpty()) {
            double cost = heap.removeLowest().combinedCost();
            assertTrue(cost >= previous, "после update порядок остаётся по возрастанию цены");
            previous = cost;
        }
    }
}
