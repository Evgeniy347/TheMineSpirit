package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NavigationReachProvisionalTests {
    @Test
    void verdict_PathPendingTooLong_AllowsProvisionalAssignment() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/NavigationReach.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("PROVISIONAL_TICKS=200")
                        && compact.contains("cache.pendingTicks(spirit,from,target,now)>=PROVISIONAL_TICKS")
                        && compact.contains("newDispatchReach.Verdict(true,\"путьпроверяется:временнаявыдача\")"),
                "Медленный поиск не держит свободного духа бесконечно: иначе при 16 pending-запросах "
                        + "все задания остаются «проверяется». При смене "
                        + "verdict_PathPendingTooLong_AllowsProvisionalAssignment проверь NavigationReach.verdict "
                        + "и RescueReach.Cache.pendingTicks.");
    }
}
