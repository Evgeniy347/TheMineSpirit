package com.theminespirit.core.script;

final class CommonScriptSpecs {
    private CommonScriptSpecs() {}

    static ParamSpec perceptionRadius() {
        return ParamSpec.integer(
                "radius",
                false,
                CatalogLimits.PERCEPTION_RADIUS_MIN,
                CatalogLimits.PERCEPTION_RADIUS_MAX,
                CatalogLimits.PERCEPTION_RADIUS_DEFAULT);
    }
}
