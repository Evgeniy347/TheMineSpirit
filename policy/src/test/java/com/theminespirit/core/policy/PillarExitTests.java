package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PillarExitTests {
    private static final BlockCoord FEET = new BlockCoord(-177, 81, -489);
    private static final BlockCoord GROUND = new BlockCoord(-180, 68, -515);

    @Test
    void ownColumnToBreak_TargetBelowOwnColumn_TopCellReturned() {
        Set<BlockCoord> own =
                Set.of(new BlockCoord(-177, 80, -489), new BlockCoord(-177, 79, -489), new BlockCoord(-177, 78, -489));

        assertEquals(
                Optional.of(new BlockCoord(-177, 80, -489)),
                ownColumnToBreak(FEET, GROUND, own::contains),
                "Цель ниже, ноги на своём столбе — разбор с клетки под ногами: дух спускается по своему "
                        + "столбу, а не ищет путь до земли (живой сервер 14.11: spirit-5 трёлся на y81 "
                        + "с target y68 и выработал бюджет подхода)");
    }

    @Test
    void ownColumnToBreak_LevelOrForeignOrAbove_Empty() {
        assertTrue(
                ownColumnToBreak(FEET, new BlockCoord(-180, 81, -491), pos -> true)
                        .isEmpty(),
                "Цель на уровне ног — столб не при чём: обычный путь, разбор опоры рядом запрещён");
        assertTrue(
                ownColumnToBreak(FEET, GROUND, pos -> false).isEmpty(),
                "Под ногами чужой блок (земля бугра) — правило не применимо: своё отличает пометка "
                        + "PlacedBlocksData, а не догадка");
        assertTrue(
                ownColumnToBreak(FEET, new BlockCoord(-177, 82, -489), pos -> true)
                        .isEmpty(),
                "Цель выше ног — спуск не нужен");
    }

    private static Optional<BlockCoord> ownColumnToBreak(
            BlockCoord feet, BlockCoord target, Predicate<BlockCoord> own) {
        if (target.y() >= feet.y()) {
            return Optional.empty();
        }
        BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
        return own.test(below) ? Optional.of(below) : Optional.empty();
    }
}
