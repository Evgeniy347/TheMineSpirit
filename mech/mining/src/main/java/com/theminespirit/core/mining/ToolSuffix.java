package com.theminespirit.core.mining;

import com.theminespirit.core.base.NamespacedId;
import java.util.List;
import java.util.Objects;

public final class ToolSuffix {
    private static final List<String> SUFFIXES = List.of("_pickaxe", "_shovel", "_axe", "_hoe");

    private ToolSuffix() {}

    public static String ofItem(String itemId) {
        Objects.requireNonNull(itemId, "Предмет инструмента не может быть null: по нему выбирают замену");
        String name =
                itemId.startsWith(NamespacedId.MINECRAFT) ? itemId.substring(NamespacedId.MINECRAFT.length()) : itemId;
        for (String suffix : SUFFIXES) {
            if (name.endsWith(suffix)) {
                return suffix;
            }
        }
        return "";
    }
}
