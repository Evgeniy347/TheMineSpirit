package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class UnreachableCells {
    public static final int EXCLUDE_AFTER = 2;
    public static final int ESCALATE_AFTER = 2;

    private final Map<BlockCoord, Integer> failures = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();

    public int noteFailure(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё нечего запоминать в чёрном списке");
        return failures.merge(cell, 1, Integer::sum);
    }

    public int failureCount(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: счётчик повторов ищется по ней");
        return failures.getOrDefault(cell, 0);
    }

    public boolean excluded(BlockCoord cell) {
        return failureCount(cell) >= EXCLUDE_AFTER;
    }

    public void reached(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: убранный блок выходит из чёрного списка");
        failures.remove(cell);
    }

    public int noteAttempt(String area) {
        Objects.requireNonNull(area, "Участок не может быть null: без него не видно, что расчистка повторяется");
        return attempts.merge(area, 1, Integer::sum);
    }

    public boolean escalating(String area) {
        Objects.requireNonNull(area, "Участок не может быть null: эскалация считается по нему");
        return attempts.getOrDefault(area, 0) >= ESCALATE_AFTER;
    }
}
