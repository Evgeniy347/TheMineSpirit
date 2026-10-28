package com.theminespirit.core.script;

import java.util.List;

final class ItemScriptSpecs {
    private static final String ITEM = "item";
    private static final String COUNT = "count";

    private ItemScriptSpecs() {}

    static ScriptSpec acquireAt() {
        return new ScriptSpec(
                "acquire_at",
                "иметь предмет в инвентаре; не хватает — добыть у заданного места (земля подсыпки за оградой)",
                List.of(
                        ParamSpec.text(ITEM, true),
                        ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                        ParamSpec.integer(
                                "x", true, -CatalogLimits.WORLD_HORIZONTAL_LIMIT, CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                        ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                        ParamSpec.integer(
                                "z",
                                true,
                                -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                List.of(),
                List.of("предмет есть в инвентаре"));
    }
}
