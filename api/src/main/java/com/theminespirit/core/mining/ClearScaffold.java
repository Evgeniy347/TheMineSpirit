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

    public static List<BlockCoord> bases(BlockCoord feet, BlockCoord target, int radius) {
        List<BlockCoord> bases = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || (dx == 0 && dz == 0)) {
                    continue;
                }
                for (int y = feet.y() - radius; y <= Math.min(feet.y() + radius, target.y() - 1); y++) {
                    bases.add(new BlockCoord(target.x() + dx, y, target.z() + dz));
                }
            }
        }
        bases.sort(Comparator.comparingLong((BlockCoord cell) -> {
                    long dx = (long) cell.x() - feet.x();
                    long dy = (long) cell.y() - feet.y();
                    long dz = (long) cell.z() - feet.z();
                    return dx * dx + dy * dy + dz * dz;
                })
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(bases);
    }

    // lib: дух в яме стоит ниже поверхности у цели, и полоса оснований «ноги ± радиус» её не достаёт —
    // lib: живой случай: столб -175,92..95,-517 оставался «на 24 выше духа — не подняться».
    public static List<BlockCoord> surfaceBases(
            BlockCoord feet, BlockCoord target, int radius, Predicate<BlockCoord> standable) {
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё считается полоса оснований");
        Objects.requireNonNull(target, "Клетка цели не может быть null: без неё нет столбца оснований");
        Objects.requireNonNull(standable, "Проверка стоянки не может быть null: без неё не найти поверхность");
        if (radius < 1) {
            throw new IllegalArgumentException("Радиус " + radius + " меньше 1: оснований у цели нет");
        }
        List<BlockCoord> bases = new ArrayList<>();
        int top = Math.min(feet.y() + radius, target.y() - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || (dx == 0 && dz == 0)) {
                    continue;
                }
                for (int y = target.y() - 1; y > top; y--) {
                    BlockCoord base = new BlockCoord(target.x() + dx, y, target.z() + dz);
                    if (standable.test(base)) {
                        bases.add(base);
                        break;
                    }
                }
            }
        }
        bases.sort(Comparator.comparingLong((BlockCoord cell) -> {
                    long dx = (long) cell.x() - feet.x();
                    long dy = (long) cell.y() - feet.y();
                    long dz = (long) cell.z() - feet.z();
                    return dx * dx + dy * dy + dz * dz;
                })
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(bases);
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
