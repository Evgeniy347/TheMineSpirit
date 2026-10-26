package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class YardScheme {
    public static final String YARD = "yard_1";
    public static final String CHESTS = "yard_chest";
    public static final String GROUND_TORCH = "ground_torch";
    public static final List<String> TABLE = List.of("crafting_table");
    public static final List<String> CHEST = List.of("chest");
    public static final List<String> FURNACE = List.of("furnace");
    public static final List<String> TORCH = List.of("torch");
    private static final String LEFT = "left";

    private YardScheme() {}

    public static BlockCoord origin(StageSite site) {
        return new BlockCoord(site.yard().x(), site.anchor().y(), site.yard().z());
    }

    public static List<List<BlockCoord>> slots(StageSite site) {
        return slots(DataTables.INSTANCE.presets().current(), site);
    }

    public static List<List<BlockCoord>> slots(PresetLibrary presets, StageSite site) {
        Objects.requireNonNull(site, "Место этапа не может быть null: двор стоит на нём");
        List<Schematic.PlacedEntry> chests = scheme(presets, YARD).entries(origin(site)).stream()
                .filter(entry -> entry.block().endsWith("chest"))
                .toList();
        List<List<BlockCoord>> slots = new ArrayList<>();
        slots.add(pair(chests, false));
        List<Schematic.PlacedEntry> reserve = chests.stream()
                .filter(entry -> entry.optional() && LEFT.equals(entry.state().get("type")))
                .sorted(Comparator.comparingInt(
                                (Schematic.PlacedEntry entry) -> entry.pos().x())
                        .thenComparingInt(entry -> entry.pos().z()))
                .toList();
        for (Schematic.PlacedEntry left : reserve) {
            slots.add(List.of(
                    left.pos(),
                    new BlockCoord(
                            left.pos().x() + 1, left.pos().y(), left.pos().z())));
        }
        return List.copyOf(slots);
    }

    public static List<SchemeBuild> stock(StageSite site, int slot) {
        if (slot <= 1) {
            return List.of(
                    new SchemeBuild(YARD, origin(site), BuildRotation.NONE, TABLE),
                    new SchemeBuild(YARD, origin(site), BuildRotation.NONE, CHEST));
        }
        PresetLibrary presets = DataTables.INSTANCE.presets().current();
        List<List<BlockCoord>> slots = slots(presets, site);
        if (slot > slots.size()) {
            throw new IllegalArgumentException("Места " + slot + " под сундуки во дворе нет: резерв схемы " + YARD
                    + " — " + (slots.size() - 1) + " пар; дальше — склад следующего этапа");
        }
        BlockCoord left = slots.get(slot - 1).get(0);
        Schematic.Entry own = scheme(presets, CHESTS).blocks().stream()
                .filter(entry -> LEFT.equals(entry.state().get("type")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Схема " + CHESTS + " без левой половины сундука: пару не к чему привязать"));
        return List.of(new SchemeBuild(
                CHESTS,
                new BlockCoord(left.x() - own.dx(), left.y() - own.dy(), left.z() - own.dz()),
                BuildRotation.NONE));
    }

    public static SchemeBuild furnace(StageSite site) {
        return new SchemeBuild(YARD, origin(site), BuildRotation.NONE, FURNACE);
    }

    public static List<SchemeBuild> lighting(StageSite site) {
        Schematic.Entry torch = scheme(DataTables.INSTANCE.presets().current(), GROUND_TORCH)
                .blocks()
                .get(0);
        List<SchemeBuild> builds = new ArrayList<>();
        builds.add(new SchemeBuild(YARD, origin(site), BuildRotation.NONE, TORCH));
        BlockCoord house = site.houseOrigin();
        List<BlockCoord> spots = List.of(
                new BlockCoord(site.mine().x(), site.anchor().y(), site.mine().z()),
                new BlockCoord(
                        site.mine().x() + site.mine().width() - 1,
                        site.anchor().y(),
                        site.mine().z()),
                new BlockCoord(house.x() - 1, house.y(), house.z()),
                new BlockCoord(house.x() + StageSite.HOUSE_WIDTH, house.y(), house.z()));
        for (BlockCoord spot : spots) {
            builds.add(new SchemeBuild(
                    GROUND_TORCH,
                    new BlockCoord(spot.x() - torch.dx(), spot.y() - torch.dy(), spot.z() - torch.dz()),
                    BuildRotation.NONE));
        }
        return List.copyOf(builds);
    }

    private static List<BlockCoord> pair(List<Schematic.PlacedEntry> chests, boolean optional) {
        List<BlockCoord> pair = chests.stream()
                .filter(entry -> entry.optional() == optional)
                .sorted(Comparator.comparingInt(
                        (Schematic.PlacedEntry entry) -> entry.pos().x()))
                .map(Schematic.PlacedEntry::pos)
                .toList();
        if (pair.size() != 2) {
            throw new IllegalStateException("Во дворе " + YARD + " " + pair.size()
                    + " обязательных сундуков вместо пары: место первых сундуков не определить");
        }
        return pair;
    }

    private static Schematic scheme(PresetLibrary presets, String id) {
        return presets.byId(id)
                .orElseThrow(() -> new IllegalStateException("presets.json: нет схемы '" + id + "' двора"))
                .schematic();
    }
}
