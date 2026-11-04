package com.theminespirit.core.script;

import java.util.ArrayList;
import java.util.List;

public final class PortalScripts {
    public static final String CAST_OBSIDIAN = "cast_obsidian";
    public static final String LIGHT_PORTAL = "light_portal";

    private PortalScripts() {}

    public static List<ScriptSpec> all() {
        List<ScriptSpec> specs = new ArrayList<>(List.of(
                new ScriptSpec(
                        CAST_OBSIDIAN,
                        "получить обсидиан: залить водой источник лавы и сломать алмазной киркой",
                        List.of(
                                ParamSpec.integer("count", false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1),
                                ParamSpec.integer("radius", false, 1, 32, CastObsidianParams.RADIUS_DEFAULT)),
                        List.of("в инвентаре есть ведро воды", "есть алмазная или незеритовая кирка"),
                        List.of("обсидиан добыт и лежит в инвентаре")),
                new ScriptSpec(
                        LIGHT_PORTAL,
                        "зажечь незажжённую рамку портала огнивом",
                        List.of(ParamSpec.integer("radius", false, 1, 32, LightPortalParams.RADIUS_DEFAULT)),
                        List.of("рядом есть рамка 4×5 из обсидиана с воздухом внутри"),
                        List.of("внутри рамки зажжён портал Нижнего мира"))));
        specs.addAll(BucketScripts.all());
        return List.copyOf(specs);
    }
}
