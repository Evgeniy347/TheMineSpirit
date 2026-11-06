package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class WarehouseRules {
    public static final int MAX_LEVEL = 4;
    public static final int LAST_LEVEL = 4;

    private WarehouseRules() {}

    public static BlockCoord site(BlockCoord home, int level) {
        Objects.requireNonNull(home, "Дом не может быть null: склад ставится от него");
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + MAX_LEVEL + ": места для такого уровня нет");
        }
        return home;
    }

    public static int levelOf(String node) {
        Objects.requireNonNull(node, "Узел склада не может быть null: по нему берут уровень");
        int cut = node.lastIndexOf('_');
        if (cut < 0) {
            return 1;
        }
        try {
            return Integer.parseInt(node.substring(cut + 1));
        } catch (NumberFormatException notLevel) {
            return 1;
        }
    }

    public static String scheme(int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + MAX_LEVEL + ": схемы для него нет");
        }
        return "storage_v" + level;
    }
}
