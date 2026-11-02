package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class MineCellPlan {
    public enum Kind {
        READY,
        APPROACH,
        WAIT,
        REFUSE
    }

    public record Plan(Kind kind, List<BlockCoord> stands, String reason) {
        public Plan {
            Objects.requireNonNull(kind, "Вид решения не может быть null: без него неизвестно, что делать");
            stands = List.copyOf(Objects.requireNonNull(stands, "Стоянки не могут быть null: пусто — нет стоянки"));
            Objects.requireNonNull(reason, "Причина не может быть null: без неё отказ не объяснить");
        }
    }

    public record Seen(@Nullable String occupant, boolean selfBody, boolean visible, boolean inReach) {}

    private MineCellPlan() {}

    public static Plan of(
            BlockCoord cell, BlockCoord feet, SafetyView view, Seen seen, Predicate<BlockCoord> allowedStands) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что делать");
        Objects.requireNonNull(feet, "Точка духа не может быть null: по ней ищется стоянка");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется подход");
        Objects.requireNonNull(seen, "Наблюдение клетки не может быть null: без него не выбрать правило");
        Objects.requireNonNull(allowedStands, "Отбор стоянок не может быть null: пустое правило задаётся истиной");
        if (seen.occupant() != null) {
            return new Plan(
                    Kind.WAIT, List.of(), "клетку " + cell + " занимает " + seen.occupant() + ": ждём, пока освободит");
        }
        if (!seen.selfBody() && seen.visible() && seen.inReach()) {
            return new Plan(Kind.READY, List.of(), "");
        }
        List<BlockCoord> stands = MineCellStands.of(
                cell, feet, view, stand -> !MineCellStands.occupies(stand, cell) && allowedStands.test(stand), true);
        if (stands.isEmpty()) {
            return new Plan(
                    Kind.REFUSE, List.of(), "к клетке " + cell + " нет стоянки с прямой видимостью: подойти некуда");
        }
        return new Plan(Kind.APPROACH, stands, "");
    }
}
