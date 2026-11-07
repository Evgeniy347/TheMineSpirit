package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record WarehouseOrder(int level, BlockCoord site, String by, String reason, long tick) {
    public static final String DEFAULT_BY = "strategist";
    public static final String DEFAULT_REASON = "склад по решению стратега";

    public WarehouseOrder {
        if (level < 1 || level > WarehouseRules.MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень склада " + level + " вне 1.." + WarehouseRules.MAX_LEVEL
                    + ": место и схема такого уровня не существуют");
        }
        Objects.requireNonNull(site, "Место склада не может быть null: стратег называет, где строить");
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("Кто решил строить склад — не названо: карточка обязана назвать автора");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Причина решения о складе пуста: по ней видно, зачем строить");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Тик решения " + tick + " отрицателен: время не идёт назад");
        }
    }
}
