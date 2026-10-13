package com.theminespirit.core.intellect;

import com.theminespirit.core.building.BarricadePlan;
import com.theminespirit.core.building.FortifyBatcher;
import com.theminespirit.core.building.ShelterPlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record FortifyPlan(Optional<FortifyOrder> order, List<List<BlockCoord>> batches) {
    public FortifyPlan {
        Objects.requireNonNull(order, "План укрепления без контейнера приказа не собирается: нет приказа — Optional.empty()");
        Objects.requireNonNull(batches, "План укрепления без порций не собирается: пустой план — пустым списком");
        batches = List.copyOf(batches.stream().map(List::copyOf).toList());
    }

    public static FortifyPlan empty() {
        return new FortifyPlan(Optional.empty(), List.of());
    }

    public static FortifyPlan plan(Optional<FortifyRoster> roster, BlockCoord feet,
            Optional<BlockCoord> passageLeft, Optional<BlockCoord> passageRight) {
        Objects.requireNonNull(roster, "План укрепления без ростера не собирается: без него неизвестно, кто строит");
        Objects.requireNonNull(feet, "План укрепления без клетки строителя не собирается: без неё духа замуруют");
        Objects.requireNonNull(passageLeft,
                "План укрепления без контейнера левого берега не собирается: нет прохода — Optional.empty()");
        Objects.requireNonNull(passageRight,
                "План укрепления без контейнера правого берега не собирается: нет прохода — Optional.empty()");
        if (roster.isEmpty() || roster.get().mode() == FortifyMode.NONE) {
            return empty();
        }
        FortifyRoster crew = roster.get();
        List<BlockCoord> cells = new ArrayList<>(cellsFor(crew.mode(), feet, passageLeft, passageRight));
        if (cells.size() > FortifyTuning.FORTIFY_BLOCK_BUDGET) {
            cells = new ArrayList<>(cells.subList(0, FortifyTuning.FORTIFY_BLOCK_BUDGET));
        }
        FortifyOrder order = new FortifyOrder(crew.builder(), crew.mode(), cells, crew.guards());
        return new FortifyPlan(Optional.of(order),
                FortifyBatcher.batches(order.cells(), FortifyTuning.FORTIFY_BLOCKS_PER_TICK));
    }

    private static List<BlockCoord> cellsFor(FortifyMode mode, BlockCoord feet, Optional<BlockCoord> passageLeft,
            Optional<BlockCoord> passageRight) {
        if (mode == FortifyMode.BARRICADE && passageLeft.isPresent() && passageRight.isPresent()
                && sealable(passageLeft.get(), passageRight.get())) {
            return BarricadePlan.seal(passageLeft.get(), passageRight.get(), FortifyTuning.SHELTER_WALL_HEIGHT, feet);
        }
        return ShelterPlan.shelter(new BlockCoord(feet.x() - 1, feet.y(), feet.z() - 1),
                FortifyTuning.SHELTER_WALL_HEIGHT, FortifyTuning.FORTIFY_BLOCK_BUDGET);
    }

    private static boolean sealable(BlockCoord left, BlockCoord right) {
        int width = Math.max(Math.abs(right.x() - left.x()), Math.abs(right.z() - left.z())) + 1;
        return width >= 2 && width <= FortifyTuning.FORTIFY_PASSAGE_WIDTH_MAX;
    }
}
