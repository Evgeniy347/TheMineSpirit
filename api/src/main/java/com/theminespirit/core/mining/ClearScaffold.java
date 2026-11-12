package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class ClearScaffold {
    public enum Action {
        WORK,
        CLIMB,
        DESCEND,
        DONE
    }

    public record Decision(Action action, int height) {
        public Decision {
            Objects.requireNonNull(action, "Действие не может быть null: без него дух не знает шага");
            if (action == Action.CLIMB && height < 1) {
                throw new IllegalArgumentException("Подъём высотой " + height + ": столб строят хотя бы на один блок");
            }
            if (action != Action.CLIMB && height != 0) {
                throw new IllegalArgumentException("Высота " + height + " у действия " + action
                        + ": её задаёт только подъём, остальные шаги не строят столб");
            }
        }

        static Decision of(Action action) {
            return new Decision(action, 0);
        }
    }

    private ClearScaffold() {}

    public static Decision decide(
            BlockCoord feet, @Nullable BlockCoord highest, int maxStandRise, boolean onOwnScaffold, int reach) {
        if (highest != null
                && !onOwnScaffold
                && Math.max(Math.abs(highest.x() - feet.x()), Math.abs(highest.z() - feet.z())) > reach) {
            return Decision.of(Action.WORK);
        }
        return decide(feet, highest, maxStandRise, onOwnScaffold);
    }

    public static Decision decide(
            BlockCoord feet, @Nullable BlockCoord highest, int maxStandRise, boolean onOwnScaffold) {
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё считается подъём и спуск");
        if (maxStandRise < 0) {
            throw new IllegalArgumentException("Предел подъёма " + maxStandRise + " отрицателен: шаг не идёт вниз");
        }
        if (highest == null) {
            return Decision.of(Action.DONE);
        }
        int rise = highest.y() - feet.y();
        if (rise > maxStandRise) {
            return new Decision(Action.CLIMB, rise);
        }
        if (highest.y() < feet.y() && onOwnScaffold) {
            return Decision.of(Action.DESCEND);
        }
        return Decision.of(Action.WORK);
    }

    public static @Nullable BlockCoord highest(List<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки целей не могут быть null: пустой список задают пустым");
        BlockCoord best = null;
        for (BlockCoord cell : cells) {
            if (best == null || cell.y() > best.y()) {
                best = cell;
            }
        }
        return best;
    }

    public static List<BlockCoord> withoutLeased(List<BlockCoord> cells, Predicate<BlockCoord> leased) {
        Objects.requireNonNull(cells, "Клетки целей не могут быть null: пустой список задают пустым");
        Objects.requireNonNull(leased, "Владелец клетки не может быть null: без него не отличить свою аренду");
        List<BlockCoord> left = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            if (!leased.test(cell)) {
                left.add(cell);
            }
        }
        return List.copyOf(left);
    }
}
