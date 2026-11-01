package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineApproach;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class MineOpening {
    private final SafetyView safety;
    private final Map<MineColumn, List<BlockCoord>> planned = new HashMap<>();

    private MineOpening(SafetyView safety) {
        this.safety = Objects.requireNonNull(safety, "Вид мира не может быть null: по нему ищется стена у ступени");
    }

    static MineOpening of(SafetyView safety) {
        return new MineOpening(safety);
    }

    boolean waiting(
            MineColumn column,
            BlockCoord feet,
            Predicate<BlockCoord> open,
            Consumer<BlockCoord> startBreak,
            Consumer<List<BlockCoord>> opened) {
        List<BlockCoord> cells = planned.get(column);
        if (cells == null) {
            List<BlockCoord> found = MineApproach.opening(column, feet, safety);
            if (found.isEmpty()) {
                return false;
            }
            cells = new ArrayList<>(found);
            planned.put(column, cells);
            opened.accept(found);
        }
        while (!cells.isEmpty() && open.test(cells.get(0))) {
            cells.remove(0);
        }
        if (cells.isEmpty()) {
            return false;
        }
        startBreak.accept(cells.get(0));
        return true;
    }
}
