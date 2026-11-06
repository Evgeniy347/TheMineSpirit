package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StageSites {
    public static final String HUT = "hut";
    public static final String YARD = "yard";
    public static final String MINE = "mine";
    public static final String ORCHARD = "orchard";
    public static final String ORCHARD_EXTRA = "orchard_extra";
    public static final String MINE_DESCENT_SCHEME = "mine_descent_v1";
    public static final String MINE_DESCENT_ROTATION = "clockwise_90";
    public static final String ORCHARD_SCHEME = "orchard_1";

    private StageSites() {}

    public static Optional<StageSite> find(TownPlan plan, int stage, int levelY, TownRules rules) {
        return find(plan, stage, levelY, rules, ORCHARD_SCHEME);
    }

    public static Optional<StageSite> find(
            TownPlan plan, int stage, int levelY, TownRules rules, String orchardScheme) {
        Objects.requireNonNull(plan, "План не может быть null: этап берёт места из него");
        return complete(plan) ? Optional.of(of(plan, stage, levelY, rules, orchardScheme)) : Optional.empty();
    }

    public static boolean complete(TownPlan plan) {
        return List.of(HUT, YARD, MINE, ORCHARD).stream()
                .allMatch(id -> plan.placement(id).isPresent());
    }

    public static Optional<ColumnMask> levelArea(TownPlan plan, int stage, TownRules rules) {
        Objects.requireNonNull(plan, "План не может быть null: площадь этапа берётся из него");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: поле ограды этапа — данные");
        return complete(plan) ? layout(plan, stage, rules).map(Layout::level) : Optional.empty();
    }

    public static StageSite of(TownPlan plan, int stage, int levelY, TownRules rules) {
        return of(plan, stage, levelY, rules, ORCHARD_SCHEME);
    }

    public static StageSite of(TownPlan plan, int stage, int levelY, TownRules rules, String orchardScheme) {
        Objects.requireNonNull(plan, "План не может быть null: этап берёт места из него");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: поле ограды этапа — данные");
        Objects.requireNonNull(orchardScheme, "Схема сада не может быть null: саду нужны места деревьев");
        Layout layout = layout(plan, stage, rules)
                .orElseThrow(() -> new IllegalArgumentException(
                        "У этапа " + stage + " нет ни одной площадки под выравнивание: план без построек этапа"));
        BlockCoord anchor =
                new BlockCoord(layout.anchor().x(), levelY, layout.anchor().z());
        List<Placement> own = plan.ofStage(stage);
        Placement mine = need(plan, MINE, stage);
        Placement orchard = need(plan, ORCHARD, stage);
        return new StageSite(
                stage,
                anchor,
                need(plan, HUT, stage),
                need(plan, YARD, stage),
                mine,
                minePlot(mine, levelY),
                orchard,
                orchardSaplings(orchard, levelY, orchardScheme),
                extraOrchards(own, levelY, orchardScheme),
                layout.level(),
                layout.streets(),
                layout.enclosure(),
                layout.fenceStrip(),
                layout.ring());
    }

    private record Layout(
            BlockCoord anchor,
            ColumnMask level,
            ColumnMask streets,
            ColumnMask enclosure,
            ColumnMask fenceStrip,
            FenceLine ring) {}

    private static Optional<Layout> layout(TownPlan plan, int stage, TownRules rules) {
        List<Placement> own = plan.ofStage(stage);
        Optional<ColumnMask> pads = own.stream()
                .filter(place ->
                        rules.template(place.id()).map(TownEntry::level).orElse(false))
                .map(Placement::mask)
                .reduce(ColumnMask::union);
        if (pads.isEmpty()) {
            return Optional.empty();
        }
        ColumnMask pad = pads.orElseThrow();
        BlockCoord anchor = new BlockCoord(plan.anchor().x(), 0, plan.anchor().z());
        ColumnMask streets = plan.streets()
                .main()
                .union(plan.streets().side())
                .union(plan.streets().ring());
        ColumnMask built = plan.upToStage(stage).stream()
                .map(Placement::mask)
                .reduce(ColumnMask::union)
                .orElseThrow();
        ColumnMask core = built.union(ColumnMask.rect(anchor.x(), anchor.z(), anchor.x(), anchor.z()));
        ColumnMask enclosure =
                connected(box(core, rules.spacing().stageMargin()).intersect(plan.region()), anchor);
        FenceLine ring = FenceGates.around(enclosure, plan.streets());
        ColumnMask fenceStrip =
                FenceStrip.of(ring, rules.spacing().fenceStrip()).intersect(plan.region());
        ColumnMask between = streets.intersect(box(pad, 0));
        ColumnMask level = pad.union(between).union(fenceStrip).intersect(plan.region());
        return Optional.of(new Layout(anchor, level, streets, enclosure, fenceStrip, ring));
    }

    private static List<OrchardPlot> extraOrchards(List<Placement> own, int levelY, String orchardScheme) {
        List<OrchardPlot> plots = new ArrayList<>();
        for (Placement place : own) {
            if (OrchardSites.isSite(place.id())) {
                String wood = OrchardSites.speciesOfBuilding(place.id()).orElseThrow();
                plots.add(new OrchardPlot(place, place.id(), wood, orchardSaplings(place, levelY, orchardScheme)));
            }
        }
        return List.copyOf(plots);
    }

    private static List<BlockCoord> orchardSaplings(Placement orchard, int levelY, String scheme) {
        Schematic schematic = PresetLibrary.current()
                .byId(scheme)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Нет схемы '" + scheme + "' для сада: проверьте presets.json и файл схемы"))
                .schematic();
        List<BlockCoord> cells = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.optional() && SchematicBlocks.path(entry.block()).endsWith("_sapling")) {
                cells.add(new BlockCoord(orchard.x() + entry.dx(), levelY + entry.dy(), orchard.z() + entry.dz()));
            }
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("В схеме '" + scheme + "' нет мягких клеток-саженцев: сад остался бы "
                    + "без деревьев, а рубка и защита — без мест");
        }
        return List.copyOf(cells);
    }

    private static MinePlot minePlot(Placement mine, int levelY) {
        String scheme = MINE_DESCENT_SCHEME;
        Preset preset = PresetLibrary.current()
                .byId(scheme)
                .orElseThrow(() -> new IllegalArgumentException("Нет схемы '" + scheme
                        + "' для шахты: проверьте presets.json и файл схемы — без неё спуск не размечен"));
        return MinePlot.of(
                mine, levelY, preset.schematic(), preset.entry(), BuildRotation.parse(MINE_DESCENT_ROTATION));
    }

    private static Placement need(TownPlan plan, String id, int stage) {
        return plan.placement(id)
                .orElseThrow(() -> new IllegalArgumentException("В плане нет места '" + id + "' для этапа " + stage
                        + ": не поместилось " + plan.unplaced() + " — этап не начать без своего набора"));
    }

    private static ColumnMask box(ColumnMask mask, int margin) {
        int x1 = Integer.MAX_VALUE;
        int z1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int z2 = Integer.MIN_VALUE;
        for (ColumnMask.Cell cell : mask.cells()) {
            x1 = Math.min(x1, cell.x());
            z1 = Math.min(z1, cell.z());
            x2 = Math.max(x2, cell.x());
            z2 = Math.max(z2, cell.z());
        }
        return ColumnMask.rect(x1 - margin, z1 - margin, x2 + margin, z2 + margin);
    }

    public static boolean insideEnclosure(StageSite site) {
        for (Placement place : List.of(site.hut(), site.yard(), site.mine(), site.orchard())) {
            for (ColumnMask.Cell cell : place.mask().cells()) {
                if (!site.inside(cell.x(), cell.z())) {
                    return false;
                }
            }
        }
        for (OrchardPlot plot : site.extraOrchards()) {
            for (ColumnMask.Cell cell : plot.place().mask().cells()) {
                if (!site.inside(cell.x(), cell.z())) {
                    return false;
                }
            }
        }
        return site.inside(site.anchor().x(), site.anchor().z());
    }

    private static ColumnMask connected(ColumnMask mask, BlockCoord anchor) {
        GridBits bits = new GridBits(mask.width(), mask.depth());
        BitSet set = new BitSet(bits.size());
        for (ColumnMask.Cell cell : mask.cells()) {
            set.set(bits.at(cell.x() - mask.minX(), cell.z() - mask.minZ()));
        }
        int dx = anchor.x() - mask.minX();
        int dz = anchor.z() - mask.minZ();
        if (!bits.inside(dx, dz) || !set.get(bits.at(dx, dz))) {
            throw new IllegalArgumentException(
                    "Якорь " + anchor + " вне огороженной области этапа: дом духов " + "обязан быть внутри ограды");
        }
        BitSet kept = bits.fillHoles(bits.component(set, bits.at(dx, dz)));
        return new ColumnMask(mask.minX(), mask.minZ(), mask.width(), mask.depth(), kept);
    }
}
