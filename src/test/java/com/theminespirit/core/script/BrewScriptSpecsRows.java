package com.theminespirit.core.script;

import java.util.List;
import java.util.Map;

final class BrewScriptSpecsRows {
    private BrewScriptSpecsRows() {}

    static List<ScriptCatalogTests.ScriptExpectation> rows() {
        return List.of(
                new ScriptCatalogTests.ScriptExpectation(
                        "brew",
                        List.of("item", "count"),
                        Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 32})),
                new ScriptCatalogTests.ScriptExpectation(
                        "farm_nether_wart", List.of(), Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 16
                        })));
    }
}
