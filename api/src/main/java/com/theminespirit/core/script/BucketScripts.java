package com.theminespirit.core.script;

import java.util.List;

public final class BucketScripts {
    public static final String FILL_BUCKET = "fill_bucket";
    public static final String POUR_BUCKET = "pour_bucket";

    private BucketScripts() {}

    public static List<ScriptSpec> all() {
        return List.of(
                new ScriptSpec(
                        FILL_BUCKET,
                        "набрать воду или лаву в пустое ведро у источника",
                        List.of(
                                ParamSpec.enumeration("fluid", true, FluidKind.ids()),
                                ParamSpec.integer(
                                        "radius",
                                        false,
                                        1,
                                        FillBucketParams.RADIUS_MAX,
                                        FillBucketParams.RADIUS_DEFAULT)),
                        List.of("в инвентаре есть пустое ведро", "в радиусе есть источник нужной жидкости"),
                        List.of("полное ведро в инвентаре", "источник не тронут: дух только зачерпнул")),
                new ScriptSpec(
                        POUR_BUCKET,
                        "вылить воду или лаву из ведра в заданную клетку",
                        List.of(
                                ParamSpec.enumeration("fluid", true, FluidKind.ids()),
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("в инвентаре есть полное ведро", "клетка свободна и входит в план"),
                        List.of("в клетке стоит источник жидкости", "пустое ведро вернулось духу")));
    }
}
