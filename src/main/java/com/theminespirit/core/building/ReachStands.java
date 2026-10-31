package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class ReachStands {
    private ReachStands() {}

    public static Optional<BlockCoord> next(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            ReachPlanner.World world,
            Set<BlockCoord> excluded,
            Predicate<BlockCoord> reachable,
            int probes) {
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё стоянку не выбрать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда шагать");
        Objects.requireNonNull(targets, "Клетки не могут быть null: без них неизвестно, к чему шагать");
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, где стоять");
        Objects.requireNonNull(excluded, "Исключённые стоянки не могут быть null: пустое множество — Set.of()");
        Objects.requireNonNull(reachable, "Проверка пути не может быть null: без неё стоянка снова будет за преградой");
        if (probes < 1) {
            throw new IllegalArgumentException(
                    "Число проверок стоянки " + probes + ": проверяют хотя бы одну, иначе подхода нет");
        }
        Set<BlockCoord> skipped = new LinkedHashSet<>(excluded);
        Set<BlockCoord> blocked = Set.copyOf(targets);
        BlockCoord first = null;
        for (int probe = 0; probe < probes; probe++) {
            Optional<BlockCoord> next = ReachPlanner.nextStand(reach, feet, targets, world, skipped, blocked);
            if (next.isEmpty()) {
                return Optional.ofNullable(first);
            }
            BlockCoord stand = next.get();
            if (first == null) {
                first = stand;
            }
            if (reachable.test(stand)) {
                return Optional.of(stand);
            }
            skipped.add(stand);
        }
        return Optional.ofNullable(first);
    }
}
