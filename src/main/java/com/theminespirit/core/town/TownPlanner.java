package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public static TownPlan plant(
            TerrainGrid grid,
            TownPlan plan,
            TownRules rules,
            List<List<String>> stages,
            List<String> demanded,
            int stage) {
        Objects.requireNonNull(grid, "Рельеф не может быть null: новый участок кладётся по нему");
        Objects.requireNonNull(plan, "План не может быть null: к нему добавляется место");
        Objects.requireNonNull(demanded, "Требуемые места не могут быть null: пустые задают пустым списком");
        BuildingPlacer placer = new BuildingPlacer(
                grid,
                plan.region(),
                plan.streets(),
                plan.anchor().x(),
                plan.anchor().z(),
                rules,
                plan.placements());
        List<String> unplaced = new ArrayList<>(plan.unplaced());
        Set<String> failed = new HashSet<>(unplaced);
        Set<String> have =
                new HashSet<>(plan.placements().stream().map(Placement::id).toList());
        for (String id : stageEntries(stages, stage)) {
            if (have.add(id)
                    && !failed.contains(id)
                    && place(placer, rules, id, stage).isEmpty()) {
                unplaced.add(id);
                failed.add(id);
            }
        }
        boolean bound = false;
        for (String id : demanded) {
            if (failed.contains(id) || !have.add(id)) {
                continue;
            }
            if (!bound && have.remove(StageSites.ORCHARD_EXTRA)) {
                bound = true;
                continue;
            }
            TownEntry template = rules.template(id)
                    .orElseThrow(() -> new IllegalArgumentException("Нет шаблона участка для '" + id
                            + "': этап берёт его из каталога town.json, иначе место не разметить"));
            if (placer.placeAs(template, id, stage).isEmpty()) {
                unplaced.add(id);
                failed.add(id);
            }
        }
        List<Placement> placed = bindSlots(placer.placed(), demanded);
        return new TownPlan(plan.anchor(), plan.region(), plan.streets(), placed, plan.fence(), unplaced);
    }

    private static List<String> stageEntries(List<List<String>> stages, int stage) {
        if (stage < 1 || stage > stages.size()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : stages.get(stage - 1)) {
            if (!LINE_ELEMENTS.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static Optional<Placement> place(BuildingPlacer placer, TownRules rules, String id, int stage) {
        TownEntry entry = rules.entry(id)
                .orElseThrow(() -> new IllegalArgumentException("Этап ссылается на постройку '" + id
                        + "', которой нет в каталоге town.json: этап не возьмёт её место из плана"));
        return placer.place(entry, stage);
    }

    private static List<Placement> bindSlots(List<Placement> placed, List<String> demanded) {
        List<Placement> out = new ArrayList<>(placed);
        for (String id : demanded) {
            if (out.stream().anyMatch(place -> place.id().equals(id))) {
                continue;
            }
            for (int index = 0; index < out.size(); index++) {
                Placement place = out.get(index);
                if (StageSites.ORCHARD_EXTRA.equals(place.id())) {
                    out.set(
                            index,
                            new Placement(
                                    id,
                                    place.name(),
                                    place.stage(),
                                    place.x(),
                                    place.z(),
                                    place.width(),
                                    place.depth(),
                                    place.facing()));
                    break;
                }
            }
        }
        return List.copyOf(out);
    }
}
