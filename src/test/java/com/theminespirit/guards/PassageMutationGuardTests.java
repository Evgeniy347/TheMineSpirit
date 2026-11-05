package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PassageMutationGuardTests {
    private static final Set<String> MUTATIONS =
            Set.of("setBlock", "destroyBlock", "placeBlock", "removeBlock", "BlockBreaker", "BlockPlacer");
    private static final List<Path> DECISION_LAYERS = List.of(
            SourceFiles.path("core/board"),
            SourceFiles.path("neoforge/board"),
            SourceFiles.path("core/navigation"),
            SourceFiles.path("core/route"),
            SourceFiles.path("core/movement"));

    @Test
    void decisionLayers_WorldMutationApis_AreAbsent() {
        List<String> offenders = new ArrayList<>();
        for (Path root : DECISION_LAYERS) {
            for (Map.Entry<Path, String> source : SourceFiles.javaUnder(root).entrySet()) {
                for (String mutation : MUTATIONS) {
                    if (source.getValue().contains(mutation)) {
                        offenders.add(source.getKey() + ": " + mutation);
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                offenders,
                "Проход сквозь мир решает граф (узлы dig_out, bridge, pillar, restore_trace), а код доски и "
                        + "навигации только читает мир и отдаёт факты. Цена нарушения: дух роет или строит мимо "
                        + "плана. Ломать и ставить блоки можно только в исполнителе действия. Нарушители");
    }

    @Test
    void guard_SetBlockCall_IsFound() {
        assertEquals(
                1,
                scan("class Sample { void run() { level.setBlock(pos, state, 3); } }"),
                "Сторож ослеп: вызов setBlock обязан находиться");
    }

    private static int scan(String text) {
        int found = 0;
        for (String mutation : MUTATIONS) {
            if (text.contains(mutation)) {
                found++;
            }
        }
        return found;
    }

    @Test
    void decisionLayers_RootDirectories_Exist() {
        for (Path root : DECISION_LAYERS) {
            assertTrue(Files.isDirectory(root), "Сторож не нашёл слой решений " + root + ": запуск из корня проекта");
        }
    }
}
