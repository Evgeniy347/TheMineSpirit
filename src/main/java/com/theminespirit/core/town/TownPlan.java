package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TownPlan(
        BlockCoord anchor,
        ColumnMask region,
        TownStreets streets,
        List<Placement> placements,
        FenceLine fence,
        List<String> unplaced) {
    public TownPlan {
        Objects.requireNonNull(anchor, "Якорь плана не может быть null: от него размечен участок");
        Objects.requireNonNull(region, "Контур плана не может быть null");
        Objects.requireNonNull(streets, "Улицы плана не могут быть null");
        Objects.requireNonNull(fence, "Ограда плана не может быть null");
        placements = List.copyOf(Objects.requireNonNull(placements, "Места построек не могут быть null"));
        unplaced = List.copyOf(Objects.requireNonNull(unplaced, "Список не поместившихся не может быть null"));
    }

    public int area() {
        return region.count();
    }

    public List<Placement> upToStage(int stage) {
        return placements.stream().filter(place -> place.stage() <= stage).toList();
    }

    public List<Placement> ofStage(int stage) {
        return placements.stream().filter(place -> place.stage() == stage).toList();
    }

    public Optional<Placement> placement(String id) {
        Objects.requireNonNull(id, "id постройки не может быть null");
        return placements.stream().filter(place -> place.id().equals(id)).findFirst();
    }

    public boolean onPlot(int x, int z) {
        return region.contains(x, z);
    }

    public TownPlan atLevel(int level) {
        return new TownPlan(
                new BlockCoord(anchor.x(), level, anchor.z()), region, streets, placements, fence, unplaced);
    }
}
