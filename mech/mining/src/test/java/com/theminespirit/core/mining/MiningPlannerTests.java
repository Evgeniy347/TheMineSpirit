package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningPlannerTests {
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);

    @Test
    void next_SingleSafeTarget_ReturnsItWithStand() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                outcome,
                "Безопасная обнажённая цель обязана вернуться со стоянкой: без неё дух не знает, куда встать"
                        + " Иначе тест next_SingleSafeTarget_ReturnsItWithStand перестанет ловить эту поломку. При смене next_SingleSafeTarget_ReturnsItWithStand проверь это утверждение.");
    }

    @Test
    void next_CloserTarget_IsPreferred() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID)
                .set(11, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.next(FROM, List.of(new BlockCoord(10, 0, 0), new BlockCoord(0, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                outcome,
                "Из двух безопасных целей выбирается ближняя по стоянке: дальняя — лишний путь и время"
                        + " Иначе тест next_CloserTarget_IsPreferred перестанет ловить эту поломку. При смене next_CloserTarget_IsPreferred проверь это утверждение.");
    }

    @Test
    void next_AllUnsafe_ReturnsExhaustedWithUnsafeCount() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.LAVA)
                .set(10, 0, 0, Cell.SOLID)
                .set(11, 0, 0, Cell.WATER);

        MiningPlanner.Outcome outcome =
                MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Exhausted(2, 0, 0),
                outcome,
                "Все цели с жидкостью обязаны считаться в unsafe: по числу видно, почему добыча не началась"
                        + " Иначе тест next_AllUnsafe_ReturnsExhaustedWithUnsafeCount перестанет ловить эту поломку. При смене next_AllUnsafe_ReturnsExhaustedWithUnsafeCount проверь это утверждение.");
    }

    @Test
    void next_AllWithoutStand_ReturnsExhaustedWithNoStandCount() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Exhausted(0, 1, 0),
                outcome,
                "Замурованная цель обязана считаться в noStand: без числа непонятно, что дух не нашёл подход"
                        + " Иначе тест next_AllWithoutStand_ReturnsExhaustedWithNoStandCount перестанет ловить эту поломку. При смене next_AllWithoutStand_ReturnsExhaustedWithNoStandCount проверь это утверждение.");
    }

    @Test
    void next_SkippedAndSafeTargets_ReturnsSafeAndCountsSkipped() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.LAVA)
                .set(5, 0, 0, Cell.SOLID)
                .set(5, 1, 0, Cell.SOLID)
                .set(5, -1, 0, Cell.SOLID)
                .set(6, 0, 0, Cell.SOLID)
                .set(4, 0, 0, Cell.SOLID)
                .set(5, 0, 1, Cell.SOLID)
                .set(5, 0, -1, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID)
                .set(11, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(
                FROM, List.of(new BlockCoord(0, 0, 0), new BlockCoord(5, 0, 0), new BlockCoord(10, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(10, 0, 0), new BlockCoord(11, 0, 0)),
                outcome,
                "Небезопасная и замурованная цели пропускаются, добывается безопасная: иначе задача встанет на первой цели"
                        + " При смене next_SkippedAndSafeTargets_ReturnsSafeAndCountsSkipped проверь это утверждение.");
    }

    @Test
    void next_AfterOpeningBuriedTarget_ReturnsIt() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID)
                .set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID)
                .set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID)
                .set(0, 0, -1, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID)
                .set(11, -1, 0, Cell.SOLID);
        List<BlockCoord> remaining = List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0));

        MiningPlanner.Outcome before = MiningPlanner.next(FROM, remaining, world);
        world.set(1, 0, 0, Cell.PASSABLE).set(1, -1, 0, Cell.SOLID);
        MiningPlanner.Outcome after = MiningPlanner.next(FROM, remaining, world);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(10, 0, 0), new BlockCoord(11, 0, 0)),
                before,
                "Пока ближняя цель замурована, выбирается дальняя обнажённая"
                        + " Иначе тест next_AfterOpeningBuriedTarget_ReturnsIt перестанет ловить эту поломку. При смене next_AfterOpeningBuriedTarget_ReturnsIt проверь это утверждение.");
        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                after,
                "После обнажения ближней цели планировщик возвращается к ней: иначе дух навсегда обходит открытую руду"
                        + " При смене next_AfterOpeningBuriedTarget_ReturnsIt проверь это утверждение.");
    }

    @Test
    void next_EmptyRemaining_ReturnsExhaustedZero() {
        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(), new CellWorld());

        assertEquals(
                new MiningPlanner.Exhausted(0, 0, 0),
                outcome,
                "Пустой список целей — Exhausted с нулями: вызывающий отличает конец списка от пропусков"
                        + " Иначе тест next_EmptyRemaining_ReturnsExhaustedZero перестанет ловить эту поломку. При смене next_EmptyRemaining_ReturnsExhaustedZero проверь это утверждение.");
    }

    @Test
    void next_FlatGroundTarget_IsReachableFromSurface() {
        CellWorld world = new CellWorld().fill(0, -2, -2, 2, 2, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(-1, 1, 0)),
                outcome,
                "Сбор на ровной земле не должен заканчиваться Exhausted(noStand>0): цель открыта сверху, "
                        + "стоянка — клетка поверхности рядом с целью"
                        + " Иначе тест next_FlatGroundTarget_IsReachableFromSurface перестанет ловить эту поломку. При смене next_FlatGroundTarget_IsReachableFromSurface проверь это утверждение.");
    }

    @Test
    void next_LineOf256Targets_ReadsStayWithinEightPerTarget() {
        CellWorld world = new CellWorld();
        List<BlockCoord> targets = new ArrayList<>(256);
        for (int x = 0; x < 256; x++) {
            world.set(x, 2, 0, Cell.SOLID);
            targets.add(new BlockCoord(x, 2, 0));
        }

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, targets, world);

        assertTrue(
                outcome instanceof MiningPlanner.Next,
                "Каждая цель линии даёт стоянку с пола: планирование обязано выбрать цель, а не выйти Exhausted"
                        + " Иначе тест next_LineOf256Targets_ReadsStayWithinEightPerTarget перестанет ловить эту поломку. При смене next_LineOf256Targets_ReadsStayWithinEightPerTarget проверь это утверждение.");
        assertTrue(
                world.reads() <= 446 * 8L,
                "Ранний выход обязан читать целиком не больше 8 целей: стоянка дальней цели не ближе "
                        + "(dist(from,target) − 3), и дальше перебирать незачем. Чтений: " + world.reads()
                        + ", предел: " + 446 * 8L
                        + " Иначе тест next_LineOf256Targets_ReadsStayWithinEightPerTarget перестанет ловить эту поломку. При смене next_LineOf256Targets_ReadsStayWithinEightPerTarget проверь это утверждение.");
    }

    @Test
    void next_SafeTarget_ReturnedTargetPassesCanBreak() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertTrue(
                outcome instanceof MiningPlanner.Next next
                        && MiningSafety.canBreak(next.target(), world) instanceof BreakSafety.Safe,
                "Возвращённая цель обязана быть безопасной для ломки: иначе дух бьёт блок с лавой за гранью"
                        + " При смене next_SafeTarget_ReturnedTargetPassesCanBreak проверь это утверждение.");
    }
}
