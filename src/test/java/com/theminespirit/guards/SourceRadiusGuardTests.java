package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceRadiusGuardTests {
    private static final Path NEOFORGE = Path.of("src/main/java/com/theminespirit/neoforge");
    private static final String LIMITER = "ChopTuning.gatherDistance";

    @Test
    void treeSourcePaths_CommonLimiter_IsUsedByEveryPath() {
        Map<Path, String> sources = new LinkedHashMap<>();
        Map<Path, String> all = SourceFiles.javaUnder(NEOFORGE);
        for (String name : new String[] {"ChopSearch.java", "GatherTargets.java", "StripLogScript.java"}) {
            Path path = NEOFORGE.resolve(name);
            sources.put(path, all.get(path));
        }

        for (Map.Entry<Path, String> source : sources.entrySet()) {
            assertTrue(
                    source.getValue() != null && source.getValue().contains(LIMITER),
                    source.getKey().getFileName() + " снова выбирает дерево/источник без общего ограничителя " + LIMITER
                            + ": дальние деревья за 100+ блоков возвращаются (observe20 п.2)");
        }
    }
}
