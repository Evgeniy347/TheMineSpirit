package com.theminespirit.core.planner;

import java.util.Objects;

public final class ItemName {
    private static final String MINECRAFT = "minecraft:";

    private ItemName() {}

    public static String path(String item) {
        Objects.requireNonNull(item, "Имя предмета не может быть null: без него не сравнить предмет с блоком");
        return item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
    }

    public static boolean same(String left, String right) {
        return path(left).equals(path(right));
    }
}
