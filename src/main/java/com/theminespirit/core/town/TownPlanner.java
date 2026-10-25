package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TownPlanner {
    public static final String FENCE = "fence";
    public static final String TORCHES = "torches";
    public static final Set<String> LINE_ELEMENTS = Set.of(FENCE, TORCHES);

    private TownPlanner() {}

    public static TownPlan plan(TerrainGrid grid, BlockCoord anchor, TownRules rules, List<List<String>> stages) {
        Objects.requireNonNull(grid, "Рельеф не может быть null: план кладётся по нему");
        Objects.requireNonNull(anchor, "Якорь не может быть null: от него растёт участок");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: они — данные town.json");
        Objects.requireNonNull(stages, "Состав этапов не может быть null: этапы берут из плана свой набор");
        ColumnMask region = PlotContour.grow(grid, anchor.x(), anchor.z(), rules.ground());
        TownStreets streets = TownStreets.lay(grid, region, anchor.x(), anchor.z(), rules.streets());
        BuildingPlacer placer = new BuildingPlacer(grid, region, streets, anchor.x(), anchor.z(), rules);
        List<String> unplaced = new ArrayList<>();
        for (boolean center : new boolean[] {true, false}) {
            for (int stage = 1; stage <= stages.size(); stage++) {
                for (String id : stages.get(stage - 1)) {
                    if (LINE_ELEMENTS.contains(id)) {
                        continue;
                    }
                    TownEntry entry = rules.entry(id)
                            .orElseThrow(() -> new IllegalArgumentException("Этап ссылается на постройку '" + id
                                    + "', которой нет в каталоге town.json: этап не возьмёт её место из плана"));
                    if ((entry.quarter() == TownEntry.Quarter.CENTER) == center
                            && placer.place(entry, stage).isEmpty()) {
                        unplaced.add(id);
                    }
                }
            }
        }
        List<Placement> placed = placer.placed();
        ColumnMask reserved = placed.stream()
                .filter(place -> rules.entry(place.id()).orElseThrow().quarter() == TownEntry.Quarter.CENTER)
                .map(Placement::mask)
                .reduce(ColumnMask::union)
                .orElse(ColumnMask.rect(anchor.x(), anchor.z(), anchor.x(), anchor.z()));
        TownStreets open = new TownStreets(
                streets.main().minus(reserved),
                streets.side().minus(reserved),
                streets.ring().minus(reserved));
        FenceLine fence = FenceGates.around(region, open);
        return new TownPlan(anchor, region, open, placed, fence, unplaced);
    }
}
