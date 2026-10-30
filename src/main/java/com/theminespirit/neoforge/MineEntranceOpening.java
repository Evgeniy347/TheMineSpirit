package com.theminespirit.neoforge;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.mining.MineApproach;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class MineEntranceOpening {
    private final @Nullable MineColumn column;
    private final SafetyView safety;
    private final List<BlockCoord> cells = new ArrayList<>();
    private boolean checked;

    private MineEntranceOpening(@Nullable MineColumn column, SafetyView safety) {
        this.column = column;
        this.safety = Objects.requireNonNull(safety, "Вид мира не может быть null: по нему ищется стена у входа");
    }

    static MineEntranceOpening of(List<MineColumn> plan, MineLayout layout, SafetyView safety) {
        Objects.requireNonNull(plan, "План колонок не может быть null: по нему ищется первая ступень");
        Objects.requireNonNull(layout, "Разметка шахты не может быть null: без неё неизвестен вход");
        MineColumn entrance = plan.stream()
                .filter(column -> column.contains(layout.entrance()))
                .findFirst()
                .orElse(null);
        return new MineEntranceOpening(entrance, safety);
    }

    boolean waiting(
            MineColumn current,
            BlockCoord feet,
            Predicate<BlockCoord> open,
            Consumer<BlockCoord> startBreak,
            Consumer<List<BlockCoord>> opened) {
        if (!checked && current.equals(column)) {
            checked = true;
            List<BlockCoord> found = MineApproach.opening(current, feet, safety);
            opened.accept(found);
            cells.addAll(found);
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
