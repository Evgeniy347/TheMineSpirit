package com.theminespirit.core.script;

import java.util.List;
import java.util.Map;

final class ClearScriptSpecRows {
    private static final long WORLD_MIN = -30_000_000L;
    private static final long WORLD_MAX = 30_000_000L;

    private ClearScriptSpecRows() {}

    static List<ScriptCatalogTests.ScriptExpectation> rows() {
        return List.of(
                new ScriptCatalogTests.ScriptExpectation(
                        "clear_area",
                        List.of("x1", "y1", "z1", "x2", "y2", "z2"),
                        Map.of(
                                "x1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y1",
                                new long[] {-64, 319},
                                "z1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "x2",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y2",
                                new long[] {-64, 319},
                                "z2",
                                new long[] {WORLD_MIN, WORLD_MAX})),
                new ScriptCatalogTests.ScriptExpectation(
                        "clear_high",
                        List.of("scaffold", "x", "y", "z", "height", "cells", "tower"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })));
    }
}
