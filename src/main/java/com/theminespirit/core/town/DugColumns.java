package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class DugColumns {
    public static final int MAX_COLUMNS = 65_536;

    private final Map<Long, Integer> tops = new LinkedHashMap<>();

    public void remember(int x, int y, int z) {
        remember(key(x, z), y);
    }

    public OptionalInt top(int x, int z) {
        Integer top = tops.get(key(x, z));
        return top == null ? OptionalInt.empty() : OptionalInt.of(top);
    }

    public List<Column> columns() {
        List<Column> columns = new ArrayList<>(tops.size());
        for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
            columns.add(new Column(xOf(entry.getKey()), zOf(entry.getKey()), entry.getValue()));
        }
        return List.copyOf(columns);
    }

    public int size() {
        return tops.size();
    }

    private void remember(long key, int y) {
        Integer before = tops.get(key);
        if (before != null) {
            if (y > before) {
                tops.put(key, y);
            }
            return;
        }
        if (tops.size() >= MAX_COLUMNS) {
            Iterator<Long> oldest = tops.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        tops.put(key, y);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int xOf(long key) {
        return (int) (key >> 32);
    }

    private static int zOf(long key) {
        return (int) key;
    }

    public record Column(int x, int z, int top) {}
}
