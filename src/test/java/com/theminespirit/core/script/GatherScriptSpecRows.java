package com.theminespirit.core.script;

import java.util.List;
import java.util.Map;

final class GatherScriptSpecRows {
    private GatherScriptSpecRows() {}

    static List<ScriptCatalogTests.ScriptExpectation> rows() {
        return List.of(new ScriptCatalogTests.ScriptExpectation(
                "strip_log",
                List.of("species"),
                Map.of("count", new long[] {1, 256}, "radius", new long[] {1, CatalogLimits.CHOP_RADIUS_MAX})));
    }
}
