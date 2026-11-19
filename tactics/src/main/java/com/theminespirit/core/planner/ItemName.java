package com.theminespirit.core.planner;

import com.theminespirit.core.base.NamespacedId;
import java.util.Objects;

public final class ItemName {

    private ItemName() {}

    public static String path(String item) {
        Objects.requireNonNull(item, "Имя предмета не может быть null: без него не сравнить предмет с блоком");
        return item.startsWith(NamespacedId.MINECRAFT) ? item.substring(NamespacedId.MINECRAFT.length()) : item;
    }

    public static boolean same(String left, String right) {
        return path(left).equals(path(right));
    }
}
