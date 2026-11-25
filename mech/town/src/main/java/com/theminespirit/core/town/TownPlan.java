package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TownPlan(
        BlockCoord anchor,
        ColumnMask region,
        TownStreets streets,
        List<Placement> placements,
        FenceLine fence,
        List<String> unplaced,
        List<PathLine> paths) {
    public static final String PLAZA_NAME = "Площадь";

    public TownPlan {
        Objects.requireNonNull(anchor, "Якорь плана не может быть null: от него размечен участок");
        Objects.requireNonNull(region, "Контур плана не может быть null");
        Objects.requireNonNull(streets, "Улицы плана не могут быть null");
        Objects.requireNonNull(fence, "Ограда плана не может быть null");
        placements = List.copyOf(Objects.requireNonNull(placements, "Места построек не могут быть null"));
        unplaced = List.copyOf(Objects.requireNonNull(unplaced, "Список не поместившихся не может быть null"));
        paths = List.copyOf(Objects.requireNonNull(paths, "Тропинки плана не могут быть null"));
    }

    public TownPlan(
            BlockCoord anchor,
            ColumnMask region,
            TownStreets streets,
            List<Placement> placements,
            FenceLine fence,
            List<String> unplaced) {
        this(anchor, region, streets, placements, fence, unplaced, List.of());
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

    public Optional<Placement> plaza() {
        return placements.stream()
                .filter(place -> PLAZA_NAME.equals(place.name()))
                .min(Comparator.comparing(Placement::id));
    }

    public boolean onPlot(int x, int z) {
        return region.contains(x, z);
    }

    public TownPlan atLevel(int level) {
        return new TownPlan(
                new BlockCoord(anchor.x(), level, anchor.z()), region, streets, placements, fence, unplaced, paths);
    }
}
