package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.WorldReport;
import java.util.List;
import java.util.Objects;

public final class WorldFactsStore {
    private static volatile WorldReport latest = new WorldReport(0, 0, 0, 0, 0, 0, true, List.of(), List.of());

    private WorldFactsStore() {}

    public static void set(WorldReport report) {
        latest = Objects.requireNonNull(report, "Сводка мира не может быть null: наблюдателю нужен честный ответ");
    }

    public static WorldReport get() {
        return latest;
    }
}
