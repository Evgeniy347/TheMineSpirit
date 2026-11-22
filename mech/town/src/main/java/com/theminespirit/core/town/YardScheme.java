package com.theminespirit.core.town;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;

public final class YardScheme {
    public static final String YARD = "yard_1";
    public static final String GROUND_TORCH = "ground_torch";
    public static final String LAMP_2 = "lamp_2";
    public static final String LAMP_3 = "lamp_3";
    public static final List<String> TABLE = List.of("crafting_table");
    public static final List<String> FURNACE = List.of("furnace");
    public static final List<String> TORCH = List.of("torch");

    private YardScheme() {}

    public static BlockCoord origin(StageSite site) {
        return new BlockCoord(site.yard().x(), site.anchor().y(), site.yard().z());
    }

    public static SchemeBuild table(StageSite site) {
        return new SchemeBuild(YARD, origin(site), BuildRotation.NONE, TABLE);
    }

    public static SchemeBuild furnace(StageSite site) {
        return new SchemeBuild(YARD, origin(site), BuildRotation.NONE, FURNACE);
    }

    public static String lightScheme(int stage) {
        return stage >= 3 ? LAMP_3 : stage == 2 ? LAMP_2 : GROUND_TORCH;
    }

    public static List<SchemeBuild> lighting(StageSite site) {
        return lighting(site, site.stage());
    }

    public static List<SchemeBuild> lighting(StageSite site, int stage) {
        String scheme = lightScheme(stage);
        Schematic.Entry base = scheme(PresetLibrary.current(), scheme).blocks().get(0);
        List<SchemeBuild> builds = new ArrayList<>();
        if (stage < 2) {
            builds.add(new SchemeBuild(YARD, origin(site), BuildRotation.NONE, TORCH));
        }
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
                    scheme,
                    new BlockCoord(spot.x() - base.dx(), spot.y() - base.dy(), spot.z() - base.dz()),
                    BuildRotation.NONE));
        }
        return List.copyOf(builds);
    }

    private static Schematic scheme(PresetLibrary presets, String id) {
        return presets.byId(id)
                .orElseThrow(() -> new IllegalStateException("presets.json: нет схемы '" + id + "' двора"))
                .schematic();
    }
}
