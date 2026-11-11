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

    public static final class Search {
        private final SpiritReach reach;
        private final BlockCoord feet;
        private final List<BlockCoord> targets;
        private final ReachPlanner.World world;
        private final Set<BlockCoord> excluded;
        private final Set<BlockCoord> skipped;
        private final Set<BlockCoord> blocked;

        private Search(
                SpiritReach reach,
                BlockCoord feet,
                List<BlockCoord> targets,
                ReachPlanner.World world,
                Set<BlockCoord> excluded) {
            this.reach = reach;
            this.feet = feet;
            this.targets = targets;
            this.world = world;
            this.excluded = excluded;
            this.skipped = new LinkedHashSet<>(excluded);
            this.blocked = Set.copyOf(targets);
        }

        public Optional<BlockCoord> nextCandidate() {
            return ReachPlanner.nextStand(reach, feet, targets, world, skipped, blocked);
        }

        public void skip(BlockCoord stand) {
            skipped.add(stand);
            excluded.add(stand);
        }
    }

    public static Search search(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            ReachPlanner.World world,
            Set<BlockCoord> excluded) {
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё стоянку не выбрать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда шагать");
        Objects.requireNonNull(targets, "Клетки не могут быть null: без них неизвестно, к чему шагать");
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, где стоять");
        Objects.requireNonNull(excluded, "Исключённые стоянки не могут быть null: пустое множество — Set.of()");
        return new Search(reach, feet, List.copyOf(targets), world, excluded);
    }

    public static Optional<BlockCoord> next(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            ReachPlanner.World world,
            Set<BlockCoord> excluded,
            Predicate<BlockCoord> reachable,
            int probes) {
        Objects.requireNonNull(excluded, "Исключённые стоянки не могут быть null: пустое множество — Set.of()");
        Objects.requireNonNull(reachable, "Проверка пути не может быть null: без неё стоянка снова будет за преградой");
        if (probes < 1) {
            throw new IllegalArgumentException(
                    "Число проверок стоянки " + probes + ": проверяют хотя бы одну, иначе подхода нет");
        }
        Search search = new Search(
                Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё стоянку не выбрать"),
                Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда шагать"),
                List.copyOf(Objects.requireNonNull(
                        targets, "Клетки не могут быть null: без них неизвестно, к чему шагать")),
                Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, где стоять"),
                new LinkedHashSet<>(excluded));
        for (int probe = 0; probe < probes; probe++) {
            Optional<BlockCoord> next = search.nextCandidate();
            if (next.isEmpty()) {
                return Optional.empty();
            }
            BlockCoord stand = next.get();
            if (reachable.test(stand)) {
                return Optional.of(stand);
            }
            search.skip(stand);
        }
        return Optional.empty();
    }
}
