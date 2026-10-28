package com.theminespirit.core.script;

import java.util.List;

final class BrewScriptSpecs {
    private BrewScriptSpecs() {}

    static List<ScriptSpec> all() {
        return List.of(GatherScriptSpecs.stripLog(), brew(), farmNetherWart());
    }

    static ScriptSpec brew() {
        return new ScriptSpec(
                "brew",
                "сварить зелье в варочной стойке",
                List.of(
                        ParamSpec.text("item", true),
                        ParamSpec.integer("count", true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                        stationRadius()),
                List.of("есть стойка, топливо, бутылки и ингредиенты"),
                List.of("зелья сварены"));
    }

    static ScriptSpec farmNetherWart() {
        return new ScriptSpec(
                "farm_nether_wart",
                "посадить адский нарост на песок душ и дождаться урожая",
                List.of(
                        ParamSpec.integer(
                                "count",
                                false,
                                CatalogLimits.COUNT_MIN,
                                CatalogLimits.COUNT_MAX,
                                CatalogLimits.PLANT_COUNT_DEFAULT),
                        ParamSpec.integer(
                                "radius",
                                false,
                                CatalogLimits.FARM_RADIUS_MIN,
                                CatalogLimits.FARM_RADIUS_MAX,
                                CatalogLimits.FARM_RADIUS_DEFAULT)),
                List.of("есть песок душ и адский нарост"),
                List.of("урожай адского нароста собран"));
    }

    private static ParamSpec stationRadius() {
        return ParamSpec.integer(
                "radius",
                false,
                CatalogLimits.STATION_RADIUS_MIN,
                CatalogLimits.STATION_RADIUS_MAX,
                CatalogLimits.STATION_RADIUS_DEFAULT);
    }
}
