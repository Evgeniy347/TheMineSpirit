package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphPostSourceTests {
    @Test
    void building_StageCaseLimitReached_StillPostsWork() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphPost.java"));

        assertFalse(
                source.contains("StageOpen.full"),
                "Лимит открытых кейсов не блокирует новые задания графа: иначе шесть недостижимых "
                        + "билетов останавливают расчистку и сад. При смене "
                        + "building_StageCaseLimitReached_StillPostsWork проверь GraphPost.building.");
    }
}
