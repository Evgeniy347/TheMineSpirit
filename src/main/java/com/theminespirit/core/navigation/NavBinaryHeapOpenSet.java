// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/pathing/calc/openset/BinaryHeapOpenSet.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Arrays;

public final class NavBinaryHeapOpenSet implements NavOpenSet {
    private static final int INITIAL_CAPACITY = 1024;

    private NavNode[] array;
    private int size;

    public NavBinaryHeapOpenSet() {
        this(INITIAL_CAPACITY);
    }

    public NavBinaryHeapOpenSet(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("Ёмкость кучи " + capacity + " меньше двух: корень и элемент не влезут");
        }
        this.array = new NavNode[capacity];
    }

    @Override
    public void insert(NavNode value) {
        if (size >= array.length - 1) {
            array = Arrays.copyOf(array, array.length << 1);
        }
        size++;
        value.heapPosition = size;
        array[size] = value;
        update(value);
    }

    @Override
    public void update(NavNode value) {
        int index = value.heapPosition;
        double cost = value.combinedCost();
        while (index > 1) {
            int parentIndex = index >>> 1;
            NavNode parent = array[parentIndex];
            if (parent.combinedCost() <= cost) {
                break;
            }
            array[index] = parent;
            array[parentIndex] = value;
            value.heapPosition = parentIndex;
            parent.heapPosition = index;
            index = parentIndex;
        }
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    @Override
    public NavNode removeLowest() {
        if (size == 0) {
            throw new IllegalStateException("Куча пуста: снимать нечего");
        }
        NavNode result = array[1];
        NavNode value = array[size];
        array[1] = value;
        value.heapPosition = 1;
        size--;
        result.heapPosition = -1;
        if (size < 2) {
            return result;
        }
        int index = 1;
        int smallerChild = 2;
        double cost = value.combinedCost();
        while (true) {
            NavNode child = array[smallerChild];
            double childCost = child.combinedCost();
            if (smallerChild < size) {
                NavNode right = array[smallerChild + 1];
                double rightCost = right.combinedCost();
                if (childCost > rightCost) {
                    smallerChild++;
                    childCost = rightCost;
                    child = right;
                }
            }
            if (cost <= childCost) {
                break;
            }
            array[index] = child;
            array[smallerChild] = value;
            value.heapPosition = smallerChild;
            child.heapPosition = index;
            index = smallerChild;
            smallerChild <<= 1;
            if (smallerChild > size) {
                break;
            }
        }
        return result;
    }
}
