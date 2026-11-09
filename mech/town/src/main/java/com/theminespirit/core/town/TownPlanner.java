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
    // lib: центр и дом с шахтой — первыми (их места привязаны к якорю), затем крупные постройки этапов 2–4.
    private static final Set<String> EARLY = Set.of("hut", "yard", "mine");

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
        // lib: крупные постройки получают места первыми: иначе мелкие этапа 1–2 занимают лучшие куски,
        // и дома этапов 3–4 не влезают в оставшиеся полосы.
        List<Planned> planned = new ArrayList<>();
        for (int stage = 1; stage <= stages.size(); stage++) {
            for (String id : stages.get(stage - 1)) {
                if (LINE_ELEMENTS.contains(id)) {
                    continue;
                }
                TownEntry entry = rules.entry(id)
                        .orElseThrow(() -> new IllegalArgumentException("Этап ссылается на постройку '" + id
                                + "', которой нет в каталоге town.json: этап не возьмёт её место из плана"));
                planned.add(new Planned(stage, entry));
            }
        }
        planned.sort((first, second) -> {
            int group = Integer.compare(order(first), order(second));
            if (group != 0) {
                return group;
            }
            if (order(first) == 1) {
                return 0;
            }
            int area = Integer.compare(
                    second.entry().width() * second.entry().depth(),
                    first.entry().width() * first.entry().depth());
            return area != 0
                    ? area
                    : first.entry().id().compareTo(second.entry().id());
        });
        for (Planned place : planned) {
            if (placer.place(place.entry(), place.stage()).isEmpty()) {
                unplaced.add(place.entry().id());
            }
        }
        List<Placement> placed = placer.placed();
        TownStreets open = openStreets(streets, placed, rules, anchor);
        FenceLine fence = FenceGates.around(region, open);
        return new TownPlan(anchor, region, open, placed, fence, unplaced);
    }

    public static boolean laidWith(TownPlan plan, TownRules rules) {
        Objects.requireNonNull(plan, "План не может быть null: сверяется разметка");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: обмер задаёт размер сетки");
        int side = 2 * rules.ground().probeRadius() + 1;
        return plan.region().width() == side && plan.region().depth() == side;
    }

    public static boolean keepStored(TownPlan plan) {
        Objects.requireNonNull(plan, "План не может быть null: старый обмер сохраняет свои места");
        return StageSites.complete(plan);
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

    public static boolean staleSizes(TownPlan plan, TownRules rules) {
        Objects.requireNonNull(plan, "План не может быть null: сверяются размеры участков");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: размеры берутся из каталога");
        for (Placement place : plan.placements()) {
            Optional<TownEntry> entry = rules.template(place.id());
            if (entry.isPresent()
                    && (place.width() != entry.orElseThrow().width()
                            || place.depth() != entry.orElseThrow().depth())) {
                return true;
            }
        }
        return false;
    }

    public static TownPlan readapt(TerrainGrid grid, TownPlan plan, TownRules rules, Set<String> built) {
        Objects.requireNonNull(grid, "Рельеф не может быть null: иначе участок не перенести");
        Objects.requireNonNull(plan, "План не может быть null: переносятся его участки");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: в них новые размеры");
        Objects.requireNonNull(built, "Готовые постройки не могут быть null: их не двигают");
        if (!staleSizes(plan, rules)) {
            return plan;
        }
        List<Placement> keep = new ArrayList<>();
        List<Placement> resize = new ArrayList<>();
        for (Placement place : plan.placements()) {
            Optional<TownEntry> entry = rules.template(place.id());
            boolean same = entry.isEmpty()
                    || (place.width() == entry.orElseThrow().width()
                            && place.depth() == entry.orElseThrow().depth());
            if (built.contains(place.id()) || same) {
                keep.add(place);
            } else {
                resize.add(place);
            }
        }
        BuildingPlacer placer = new BuildingPlacer(
                grid,
                plan.region(),
                plan.streets(),
                plan.anchor().x(),
                plan.anchor().z(),
                rules,
                keep);
        List<String> unplaced = new ArrayList<>(plan.unplaced());
        List<Placement> failed = new ArrayList<>();
        for (Placement place : resize) {
            TownEntry entry = rules.template(place.id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Нет шаблона участка для '" + place.id() + "': новый размер неоткуда взять"));
            Optional<Placement> moved =
                    placer.placeAt(entry, place.id(), place.stage(), place.x(), place.z(), place.facing());
            if (moved.isEmpty()) {
                moved = placer.placeAs(entry, place.id(), place.stage());
            }
            if (moved.isEmpty()) {
                unplaced.add(place.id());
                failed.add(place);
            }
        }
        List<Placement> placements = new ArrayList<>(placer.placed());
        placements.addAll(failed);
        TownStreets open = openStreets(plan.streets(), placements, rules, plan.anchor());
        return new TownPlan(plan.anchor(), plan.region(), open, placements, plan.fence(), unplaced);
    }

    private static TownStreets openStreets(
            TownStreets streets, List<Placement> placements, TownRules rules, BlockCoord anchor) {
        ColumnMask reserved = placements.stream()
                .filter(place -> rules.entry(place.id()).orElseThrow().quarter() == TownEntry.Quarter.CENTER)
                .map(Placement::mask)
                .reduce(ColumnMask::union)
                .orElse(ColumnMask.rect(anchor.x(), anchor.z(), anchor.x(), anchor.z()));
        return new TownStreets(
                streets.main().minus(reserved),
                streets.side().minus(reserved),
                streets.ring().minus(reserved));
    }

    private record Planned(int stage, TownEntry entry) {}

    private static int order(Planned place) {
        if (place.entry().quarter() == TownEntry.Quarter.CENTER) {
            return 0;
        }
        return EARLY.contains(place.entry().id()) ? 1 : 2;
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
