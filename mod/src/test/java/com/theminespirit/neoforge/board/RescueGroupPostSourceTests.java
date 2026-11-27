package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RescueGroupPostSourceTests {
    @Test
    void rescue_PhysicalTrapWithoutMark_UsesPositionAsEpisode() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/RescueGroupPost.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("orElseGet(()->newStuckSpirits.Stuck(at,post.now,false))")
                        && !compact.contains("if(mark.isEmpty()){pending=true;continue;}"),
                "Физическая ловушка без board-метки получает временную метку и Rescue-билет: иначе "
                        + "участник бесконечно откладывается и дух остаётся на платформе. При смене "
                        + "rescue_PhysicalTrapWithoutMark_UsesPositionAsEpisode проверь RescueGroupPost.rescue "
                        + "и живой rescue:spirit-6.");
    }
}
