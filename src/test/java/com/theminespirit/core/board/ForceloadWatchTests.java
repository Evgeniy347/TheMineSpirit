package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;

class ForceloadWatchTests {
    private static final UUID SPIRIT = UUID.fromString("6f1c2d3e-0000-4000-8000-000000000001");
    private static final long CHUNK_ZERO = 0L;

    private static LongPredicate forced(long... chunks) {
        Set<Long> marked = new HashSet<>();
        for (long chunk : chunks) {
            marked.add(chunk);
        }
        return marked::contains;
    }

    @Test
    void check_GoalOutside_NamesGoalAndChunk() {
        String text = new ForceloadWatch()
                .check(
                        SPIRIT,
                        "spirit-3",
                        new BlockCoord(5, 64, 5),
                        "first:build_mine",
                        new BlockCoord(20, 64, 5),
                        forced(CHUNK_ZERO))
                .orElseThrow();

        assertTrue(text.contains("цель задания first:build_mine"), "WARN обязан назвать задание: " + text);
        assertTrue(text.contains("(20, 64, 5)"), "WARN обязан назвать координаты цели: " + text);
        assertTrue(text.contains("чанк [1, 0]"), "WARN обязан назвать чанк цели: " + text);
        assertFalse(text.contains("дух spirit-3"), "Дух внутри forceload — о нём предупреждать не надо: " + text);
    }

    @Test
    void check_RepeatedOutsideState_DoesNotRepeatWarning() {
        ForceloadWatch watch = new ForceloadWatch();

        assertTrue(
                watch.check(SPIRIT, "spirit-3", new BlockCoord(5, 64, 5), null, null, forced())
                        .isPresent(),
                "Первый выход за forceload обязан дать предупреждение");
        assertEquals(
                Optional.empty(),
                watch.check(SPIRIT, "spirit-3", new BlockCoord(6, 64, 5), null, null, forced()),
                "Тот же выход за forceload не должен повторяться каждую проверку");
    }

    @Test
    void check_BackInsideThenOutside_WarnsAgain() {
        ForceloadWatch watch = new ForceloadWatch();
        BlockCoord position = new BlockCoord(6, 64, 5);
        assertTrue(
                watch.check(SPIRIT, "spirit-3", position, null, null, forced()).isPresent(),
                "Первый выход за forceload обязан дать предупреждение");
        assertEquals(
                Optional.empty(),
                watch.check(SPIRIT, "spirit-3", position, null, null, forced(CHUNK_ZERO)),
                "Возврат в forceload снимает пометку, но не пишет WARN");

        assertTrue(
                watch.check(SPIRIT, "spirit-3", position, null, null, forced()).isPresent(),
                "Повторный выход после возврата — снова WARN");
    }
}
