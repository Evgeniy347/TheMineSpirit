package com.theminespirit.core;

import java.util.Map;

public final class Lookup {
    private Lookup() {}

    public static <K, V> V present(Map<K, V> map, K key, String table) {
        V value = map.get(key);
        if (value == null) {
            throw new IllegalStateException(table + ": нет значения для " + key
                    + " — таблица обязана покрывать все ключи, проверьте её сборку");
        }
        return value;
    }
}
