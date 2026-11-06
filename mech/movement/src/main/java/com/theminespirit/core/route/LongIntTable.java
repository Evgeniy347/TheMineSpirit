package com.theminespirit.core.route;

import java.util.Arrays;

final class LongIntTable {
    static final int ABSENT = -1;
    private static final long EMPTY = Long.MIN_VALUE;
    private static final long MIX = 0x9E3779B97F4A7C15L;
    private long[] keys;
    private int[] values;
    private int mask;
    private int size;

    LongIntTable(int expected) {
        int capacity = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
        allocate(capacity);
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    int get(long key) {
        int index = slot(key);
        while (keys[index] != EMPTY) {
            if (keys[index] == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
        return ABSENT;
    }

    void put(long key, int value) {
        if ((size + 1) * 2 > keys.length) {
            grow();
        }
        int index = slot(key);
        while (keys[index] != EMPTY && keys[index] != key) {
            index = (index + 1) & mask;
        }
        if (keys[index] == EMPTY) {
            size++;
        }
        keys[index] = key;
        values[index] = value;
    }

    private int slot(long key) {
        long mixed = key * MIX;
        return (int) (mixed ^ (mixed >>> 32)) & mask;
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, EMPTY);
        values = new int[capacity];
        mask = capacity - 1;
        size = 0;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        allocate(oldKeys.length * 2);
        for (int index = 0; index < oldKeys.length; index++) {
            if (oldKeys[index] != EMPTY) {
                put(oldKeys[index], oldValues[index]);
            }
        }
    }
}
