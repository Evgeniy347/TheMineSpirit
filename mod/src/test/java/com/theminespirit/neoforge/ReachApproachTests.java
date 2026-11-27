package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReachApproachTests {
    @Test
    void standReachable_NetworkProbe_UsesCandidateInsteadOfCurrentFeet() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/ReachApproach.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("PathNetworkService.route(level,feet(),stand).isPresent()")
                        && compact.contains("createPath(newBlockPos(stand.x(),stand.y(),stand.z()),1)")
                        && compact.contains("twoWayWalk(stand)"),
                "Предфильтр проверяет путь к клетке стоянки, а не к текущим ногам: иначе открытый путь "
                        + "к верстаку отклоняется до начала движения. При смене "
                        + "standReachable_NetworkProbe_UsesCandidateInsteadOfCurrentFeet проверь ReachApproach."
                        + "standReachable и живой craft у (-139,64,-460).");
    }
}
