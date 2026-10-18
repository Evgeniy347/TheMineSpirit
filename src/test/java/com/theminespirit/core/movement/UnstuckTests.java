package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnstuckTests {
    private static final Unstuck RULES = new Unstuck(3, 3);
    private static final BlockCoord FEET = new BlockCoord(0, 0, 0);
    private static final BlockCoord TARGET = new BlockCoord(10, 0, 0);

    @Test
    void table_OwnerDecision_UpToThreeBlocksThreeTimes() {
        assertEquals(new Unstuck(3, 3), Unstuck.load(),
                "Телепорт при застревании согласован владельцем 18.10: на 1–3 блока к цели, не больше трёх раз "
                        + "за подход; числа — movement/unstuck.json");
    }

    @Test
    void step_OpenGround_JumpsThreeBlocksTowardTarget() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> true);

        assertEquals(Optional.of(new BlockCoord(3, 0, 0)), step,
                "На открытой земле перенос — на 3 блока прямо к цели: ближе к цели клетки нет");
    }

    @Test
    void step_WallBetween_DoesNotCrossIt() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> cell.x() < 2);

        assertTrue(step.isPresent() && step.get().x() < 2,
                "Не сквозь стены (решение владельца 18.10): клетка за стеной не выбирается, даже если она ближе; "
                        + "фактически " + step);
        assertEquals(new BlockCoord(1, 0, 0), step.get(),
                "Остаётся ближайшая к цели клетка до стены");
    }

    @Test
    void step_NothingCloser_StaysStuck() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.x() < 0, cell -> true);

        assertEquals(Optional.empty(), step,
                "Перенос только ближе к цели: назад дух не телепортируется, а честно застревает");
    }
}
