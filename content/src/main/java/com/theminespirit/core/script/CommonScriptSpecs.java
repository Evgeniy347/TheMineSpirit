package com.theminespirit.core.script;

import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;

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
