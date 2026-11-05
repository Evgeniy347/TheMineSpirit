package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class BuildPlanCursor {
    public sealed interface Next permits Fetch, StandAt, Done {}

    public record Fetch(int number, List<BuildPlanner.Taken> taken) implements Next {
        public Fetch {
            taken = List.copyOf(taken);
        }
    }

    public record StandAt(BlockCoord stand, List<BlockCoord> cells) implements Next {
        public StandAt {
            cells = List.copyOf(cells);
        }
    }

    public record Done() implements Next {}

    private final List<BuildPlanner.Step> steps;
    private final BlockCoord origin;
    private int index;

    public BuildPlanCursor(BuildPlanner.Plan plan, BlockCoord origin) {
        this.steps = Objects.requireNonNull(plan, "План не может быть null: без него нечего исполнять")
                .steps();
        this.origin = Objects.requireNonNull(origin, "Угол стройки не может быть null: от него координаты плана");
    }

    public Next next(Predicate<BlockCoord> pending) {
        Objects.requireNonNull(pending, "Проверка клеток не может быть null: без неё не пропустить готовое");
        while (index < steps.size()) {
            BuildPlanner.Step step = steps.get(index);
            switch (step) {
                case BuildPlanner.Trip trip -> {
                    return new Fetch(trip.number(), trip.taken());
                }
                case BuildPlanner.Work work -> {
                    List<BlockCoord> cells = new ArrayList<>();
                    for (BlockCoord cell : work.cells()) {
                        BlockCoord world = shift(cell);
                        if (pending.test(world)) {
                            cells.add(world);
                        }
                    }
                    if (!cells.isEmpty()) {
                        return new StandAt(shift(work.stand()), cells);
                    }
                }
                case BuildPlanner.Stuck ignored -> {}
            }
            index++;
        }
        return new Done();
    }

    public void advance() {
        if (index < steps.size()) {
            index++;
        }
    }

    public int index() {
        return index;
    }

    private BlockCoord shift(BlockCoord cell) {
        return new BlockCoord(origin.x() + cell.x(), origin.y() + cell.y(), origin.z() + cell.z());
    }
}
