package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record OrchardPlot(Placement place, String building, String wood, List<BlockCoord> saplings) {
    public OrchardPlot {
        Objects.requireNonNull(place, "Место сада не может быть null: у сада своя площадка в плане");
        Objects.requireNonNull(building, "Постройка сада не может быть null: по ней берут породу");
        Objects.requireNonNull(wood, "Порода сада не может быть null: саженцы садятся по ней");
        saplings = List.copyOf(Objects.requireNonNull(saplings, "Места деревьев сада не могут быть null"));
    }

    public BlockCoord origin(int levelY) {
        return new BlockCoord(place.x(), levelY, place.z());
    }

    public boolean tree(int x, int y, int z, int levelY) {
        if (y < levelY - 1 || y > levelY + StageSite.ORCHARD_TREE_HEIGHT) {
            return false;
        }
        for (BlockCoord spot : saplings) {
            if (spot.x() == x && spot.z() == z) {
                return true;
            }
        }
        return false;
    }
}
