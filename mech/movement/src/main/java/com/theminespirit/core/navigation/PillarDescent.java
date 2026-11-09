package com.theminespirit.core.navigation;

import com.theminespirit.core.movement.BodyMotion;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class PillarDescent {
    public record Plan(List<BlockCoord> dig, BlockCoord landing, int depth) {
        public Plan {
            dig = List.copyOf(
                    Objects.requireNonNull(dig, "Клетки столба не могут быть null: без них нечего разбирать"));
            Objects.requireNonNull(landing, "Точка приземления не может быть null: духу некуда спускаться");
            if (depth < 1 || depth != dig.size()) {
                throw new IllegalArgumentException("Глубина спуска " + depth + " не совпадает с числом блоков "
                        + dig.size() + ": каждый шаг вниз убирает ровно один свой блок");
            }
        }
    }

    private PillarDescent() {}

    public static Optional<Plan> plan(RouteWorld world, BlockCoord feet, Predicate<BlockCoord> own, int maxDepth) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно столба");
        Objects.requireNonNull(feet, "Клетка ног не может быть null: спуск считается от неё");
        Objects.requireNonNull(own, "Владелец блока не может быть null: чужие блоки не разбирают");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Предел спуска " + maxDepth + " меньше одного блока");
        }
        if (!BodyMotion.standable(world, feet)) {
            return Optional.empty();
        }
        Set<BlockCoord> removed = new HashSet<>();
        RouteWorld digging = after(world, removed);
        List<BlockCoord> dig = new ArrayList<>();
        BlockCoord cursor = feet;
        for (int step = 0; step < maxDepth; step++) {
            BlockCoord below = BodyMotion.below(cursor);
            if (!own.test(below) || !BodyMotion.solid(digging, below)) {
                break;
            }
            removed.add(below);
            BlockCoord next = below;
            if (!BodyMotion.fits(digging, next)) {
                return Optional.empty();
            }
            dig.add(below);
            cursor = next;
        }
        if (dig.isEmpty() || !BodyMotion.standable(digging, cursor)) {
            return Optional.empty();
        }
        return Optional.of(new Plan(dig, cursor, dig.size()));
    }

    private static RouteWorld after(RouteWorld world, Set<BlockCoord> removed) {
        return (x, y, z) -> removed.contains(new BlockCoord(x, y, z)) ? RouteCell.AIR : world.cell(x, y, z);
    }
}
