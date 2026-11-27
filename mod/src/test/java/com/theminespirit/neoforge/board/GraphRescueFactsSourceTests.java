package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphRescueFactsSourceTests {
    @Test
    void stuck_PhysicalTrapWithoutBoardMark_StillReachesRescueGraph() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphFactInputs.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains(
                        "service.board().stuck().isStuck(spirit.getUUID(),now)||PitRescue.trapped(spirit).isPresent()"),
                "Физическая ловушка попадает в факты даже после снятия board-метки: иначе rescue не "
                        + "появляется в графе и дух навсегда остаётся на платформе. При смене "
                        + "stuck_PhysicalTrapWithoutBoardMark_StillReachesRescueGraph проверь GraphFactInputs.stuck "
                        + "и RescueGroupPost.");
    }
}
