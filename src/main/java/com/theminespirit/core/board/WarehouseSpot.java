package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class WarehouseSpot {
    private WarehouseSpot() {}

    public static String line(int level, BlockCoord home, @Nullable BlockCoord slot, String reason) {
        Objects.requireNonNull(home, "Дом не может быть null: место склада считается от него");
        Objects.requireNonNull(reason, "Причина выбора места не может быть null: без неё строка бесполезна");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Причина выбора места пуста: склад ставится не молча");
        }
        return "[warehouse] склад уровня " + level + ": site=" + coord(WarehouseRules.site(home, level)) + ", slot="
                + (slot == null ? "—" : coord(slot)) + ", причина=" + reason;
    }

    private static String coord(BlockCoord pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }
}
