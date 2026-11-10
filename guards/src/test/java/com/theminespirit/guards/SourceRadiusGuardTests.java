package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceRadiusGuardTests {
    private static final Path NEOFORGE = SourceFiles.path("neoforge");

    @Test
    void treeSourcePaths_CommonLimiter_IsUsedByEveryPath() {
        Map<Path, String> all = SourceFiles.javaUnder(NEOFORGE);
        Map<Path, String> trees = Map.of(
                NEOFORGE.resolve("ChopSearch.java"), "ChopTuning.maxDistance",
                NEOFORGE.resolve("StripLogScript.java"), "ChopTuning.maxDistance",
                NEOFORGE.resolve("GatherTargets.java"), "ChopTuning.gatherDistance");

        trees.forEach((path, limiter) -> {
            String source = all.get(path);
            assertTrue(
                    source != null && source.contains(limiter),
                    path.getFileName() + " снова выбирает дерево/источник без общего ограничителя " + limiter
                            + ": рубка обязана идти в радиусе доски (100), а сбор у поселения — не дальше 32; "
                            + "подмена одного предела другим даёт «ближайшее дерево 35 при пределе 32» (11.11)");
        });
    }
}
