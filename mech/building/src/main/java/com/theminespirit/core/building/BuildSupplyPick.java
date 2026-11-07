package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class BuildSupplyPick {
    public record Candidate(BlockCoord chest, BlockCoord stand, boolean needed) {
        public Candidate {
            Objects.requireNonNull(chest, "Сундук снабжения не может быть null: без него нечего выбирать");
            Objects.requireNonNull(stand, "Стоянка сундука не может быть null: к сундуку без неё не подойти");
        }
    }

    private BuildSupplyPick() {}

    public static BlockCoord choose(BlockCoord feet, List<Candidate> reachable) {
        Objects.requireNonNull(feet, "Позиция духа не может быть null: без неё некуда отступать");
        Objects.requireNonNull(reachable, "Список достижимых сундуков не может быть null: пустой — пустым списком");
        for (Candidate candidate : reachable) {
            if (candidate.needed()) {
                return candidate.stand();
            }
        }
        return reachable.isEmpty() ? feet : reachable.get(0).stand();
    }
}
