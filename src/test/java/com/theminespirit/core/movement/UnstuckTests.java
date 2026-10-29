package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UnstuckTests {
    private static final Unstuck RULES = new Unstuck(3, 3);
    private static final BlockCoord FEET = new BlockCoord(0, 0, 0);
    private static final BlockCoord TARGET = new BlockCoord(10, 0, 0);

    @Test
    void table_OwnerDecision_UpToThreeBlocksThreeTimes() {
        assertEquals(
                new Unstuck(3, 3),
                Unstuck.load(),
                "Телепорт при застревании согласован владельцем 18.10: на 1–3 блока к цели, не больше трёх раз "
                        + "за подход; числа — movement/unstuck.json");
    }

    @Test
    void step_OpenGround_JumpsThreeBlocksTowardTarget() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> true);

        assertEquals(
                Optional.of(new BlockCoord(3, 0, 0)),
                step,
                "На открытой земле перенос — на 3 блока прямо к цели: ближе к цели клетки нет");
    }

    @Test
    void step_WallBetween_DoesNotCrossIt() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> cell.x() < 2);

        assertTrue(
                step.isPresent() && step.get().x() < 2,
                "Не сквозь стены (решение владельца 18.10): клетка за стеной не выбирается, даже если она ближе; "
                        + "фактически " + step);
        assertEquals(new BlockCoord(1, 0, 0), step.get(), "Остаётся ближайшая к цели клетка до стены");
    }

    @Test
    void step_NothingCloser_StaysStuck() {
        Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.x() < 0, cell -> true);

        assertEquals(
                Optional.empty(),
                step,
                "Перенос только ближе к цели: назад дух не телепортируется, а честно застревает");
    }

    @Test
    void step_SameTargetAgain_DoesNotRepeatTeleportCell() {
        TeleportTrail trail = new TeleportTrail();
        trail.aimAt(TARGET);
        Set<BlockCoord> seen = new HashSet<>();

        for (int attempt = 0; attempt < RULES.teleportsPerApproach(); attempt++) {
            Optional<BlockCoord> step = RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> true, trail::used);

            assertTrue(step.isPresent(), "На открытой земле перенос к цели ещё есть: попытка " + (attempt + 1));
            assertTrue(
                    seen.add(step.get()),
                    "Перенос не повторяет уже пройденную клетку: клетка " + step.get() + " уже была в " + seen
                            + ", иначе дух ходит по кругу (аномалия 29.10: (-156,72,-504) → (-157,73,-505))");
            trail.record(step.get(), attempt);
        }

        assertTrue(
                RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> true, trail::used)
                        .filter(cell -> !seen.contains(cell))
                        .isPresent(),
                "После трёх переносов остаётся новая клетка: повтор уже пройденной запрещён, иначе дух ходит по кругу");
    }

    @Test
    void step_EveryCloserCellUsed_StaysStuck() {
        assertTrue(
                RULES.step(FEET, TARGET, cell -> cell.y() == 0, cell -> true, cell -> true)
                        .isEmpty(),
                "Если все клетки ближе к цели уже пробовали, переносов не остаётся: честный провал, а не петля");
    }

    @Test
    void trail_NewApproachSameTarget_KeepsAttemptsAndCells() {
        TeleportTrail trail = new TeleportTrail();
        trail.aimAt(TARGET);
        trail.record(new BlockCoord(3, 0, 0), 10);

        trail.aimAt(TARGET);

        assertEquals(1, trail.attempts(), "Новый подход к той же цели не обнуляет попытки: иначе счётчик крутится");
        assertTrue(trail.used(new BlockCoord(3, 0, 0)), "Новый подход к той же цели помнит прошлую клетку переноса");
        assertEquals(Optional.of(new BlockCoord(3, 0, 0)), trail.last(), "Прошлая клетка переноса остаётся известной");
        assertEquals(10, trail.lastTick(), "Время прошлого переноса остаётся известным для лога");
    }

    @Test
    void trail_NewTarget_ForgetsPreviousAttempts() {
        TeleportTrail trail = new TeleportTrail();
        trail.aimAt(TARGET);
        trail.record(new BlockCoord(3, 0, 0), 10);

        trail.aimAt(new BlockCoord(20, 0, 0));

        assertEquals(0, trail.attempts(), "Другая цель — другая память переносов: прошлые попытки к ней не относятся");
        assertFalse(trail.used(new BlockCoord(3, 0, 0)), "Клетка переноса к прошлой цели не запрещена у новой цели");
    }
}
