package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NavigationReachTests {
    @Test
    void verdict_PathStillPending_DoesNotAllowAssignment() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/NavigationReach.java"));
        String compact = source.replaceAll("\\s+", "");

        assertFalse(
                compact.contains("newDispatchReach.Verdict(true"),
                "Неизвестный путь не выдаётся за достижимый: иначе storage и repair снова уводят духов "
                        + "в заведомо недостижимую расчистку. При смене verdict_PathStillPending_DoesNotAllowAssignment "
                        + "проверь NavigationReach.verdict и живой storage_3.");
        assertTrue(
                compact.contains("returnOptional.empty();"),
                "Пока поиск не завершён, кандидат остаётся неподтверждённым: иначе таймаут снова становится "
                        + "разрешением на невыполнимое задание. При смене verdict_PathStillPending_DoesNotAllowAssignment "
                        + "проверь DispatchPick.reachable.");
    }
}
