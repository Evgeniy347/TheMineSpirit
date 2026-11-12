package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
            return new Decision(Action.CLIMB, rise - maxStandRise);
        }
        if (highest.y() < feet.y() && onOwnScaffold) {
            return Decision.of(Action.DESCEND);
        }
        return Decision.of(Action.WORK);
    }

    public static Decision step(
            BlockCoord feet, List<BlockCoord> tops, int maxStandRise, boolean onOwnScaffold, int reach) {
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё считается ступень");
        Objects.requireNonNull(tops, "Верхушки колонок не могут быть null: пустые задают пустым списком");
        List<BlockCoord> near = tops.stream()
                .filter(top -> Math.max(Math.abs(top.x() - feet.x()), Math.abs(top.z() - feet.z())) <= reach)
                .toList();
        if (near.isEmpty()) {
            return tops.isEmpty() ? Decision.of(Action.DONE) : Decision.of(Action.WORK);
        }
        boolean reachable = near.stream().anyMatch(top -> top.y() - feet.y() <= maxStandRise && top.y() >= feet.y());
        if (reachable) {
            return Decision.of(Action.WORK);
        }
        Optional<BlockCoord> next = near.stream()
                .filter(top -> top.y() - feet.y() > maxStandRise)
                .min(Comparator.comparingInt(BlockCoord::y));
        if (next.isPresent()) {
            return new Decision(Action.CLIMB, next.orElseThrow().y() - feet.y() - maxStandRise);
        }
        return onOwnScaffold ? Decision.of(Action.DESCEND) : Decision.of(Action.WORK);
    }

    public static Optional<BlockCoord> pillarSpot(
            BlockCoord feet,
            BlockCoord highest,
            Predicate<BlockCoord> walkway,
            Predicate<BlockCoord> standable,
            int reach) {
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё ищется место столба");
        Objects.requireNonNull(highest, "Верхняя цель не может быть null: столб ставят у её колонки");
        if (!walkway.test(feet)) {
            return Optional.empty();
        }
        BlockCoord best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                BlockCoord spot = new BlockCoord(highest.x() + dx, feet.y(), highest.z() + dz);
                int distance = Math.abs(spot.x() - feet.x()) + Math.abs(spot.z() - feet.z());
                if ((dx != 0 || dz != 0) && distance < bestDistance && !walkway.test(spot) && standable.test(spot)) {
                    best = spot;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
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
