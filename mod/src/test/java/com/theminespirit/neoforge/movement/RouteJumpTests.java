package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RouteJumpTests {
    @Test
    void jump_TwoCellRoute_UsesDedicatedExecutor() throws IOException {
        String walk = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/movement/RouteWalk.java"));
        String compactWalk = walk.replaceAll("\\s+", "");
        String jump = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/movement/RouteJump.java"));
        String compactJump = jump.replaceAll("\\s+", "");
        int dedicated = compactWalk.indexOf("RouteJump.tryLong(spirit,cell)");
        int ascent = compactWalk.indexOf("WalkAscent.next(cell,pose(),body(cell))", dedicated);

        assertTrue(
                dedicated >= 0
                        && ascent > dedicated
                        && compactJump.contains("distance<=MIN_LONG_JUMP")
                        && compactJump.contains("distance>MAX_LONG_JUMP")
                        && compactJump.contains("spirit.getJumpControl().jump()"),
                "Двухклеточный JUMP исполняется отдельно до WalkAscent: иначе маршрут падает с «прыжком "
                        + "не взять» на ровном месте. При смене jump_TwoCellRoute_UsesDedicatedExecutor проверь "
                        + "RouteWalk.jump, RouteJump.tryLong и живой шаг (-169,65,-422)→(-169,65,-424).");
    }

    @Test
    void jump_BlockedGap_DoesNotForceMovement() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/movement/RouteJump.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("!clear(level,gap)")
                        && compact.contains("!clear(level,gap.above())")
                        && compact.contains("isFaceSturdy(level,floor,Direction.UP)"),
                "Прыжок выполняется только через свободный промежуток на твёрдую цель: иначе дух прыгнет "
                        + "в стену или яму. При смене jump_BlockedGap_DoesNotForceMovement проверь RouteJump.tryLong.");
    }
}
