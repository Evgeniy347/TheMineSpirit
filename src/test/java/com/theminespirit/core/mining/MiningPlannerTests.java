package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningPlannerTests {
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);

    @Test
    void next_SingleSafeTarget_ReturnsItWithStand() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)), outcome,
                "Безопасная обнажённая цель обязана вернуться со стоянкой: без неё дух не знает, куда встать");
    }

    @Test
    void next_CloserTarget_IsPreferred() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID).set(11, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM,
                List.of(new BlockCoord(10, 0, 0), new BlockCoord(0, 0, 0)), world);

        assertEquals(new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)), outcome,
                "Из двух безопасных целей выбирается ближняя по стоянке: дальняя — лишний путь и время");
    }

    @Test
    void next_AllUnsafe_ReturnsExhaustedWithUnsafeCount() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID).set(1, 0, 0, Cell.LAVA)
                .set(10, 0, 0, Cell.SOLID).set(11, 0, 0, Cell.WATER);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM,
                List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0)), world);

        assertEquals(new MiningPlanner.Exhausted(2, 0), outcome,
                "Все цели с жидкостью обязаны считаться в unsafe: по числу видно, почему добыча не началась");
    }

    @Test
    void next_AllWithoutStand_ReturnsExhaustedWithNoStandCount() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID).set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(new MiningPlanner.Exhausted(0, 1), outcome,
                "Замурованная цель обязана считаться в noStand: без числа непонятно, что дух не нашёл подход");
    }

    @Test
    void next_SkippedAndSafeTargets_ReturnsSafeAndCountsSkipped() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID).set(1, 0, 0, Cell.LAVA)
                .set(5, 0, 0, Cell.SOLID)
                .set(5, 1, 0, Cell.SOLID).set(5, -1, 0, Cell.SOLID)
                .set(6, 0, 0, Cell.SOLID).set(4, 0, 0, Cell.SOLID)
                .set(5, 0, 1, Cell.SOLID).set(5, 0, -1, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID).set(11, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM,
                List.of(new BlockCoord(0, 0, 0), new BlockCoord(5, 0, 0), new BlockCoord(10, 0, 0)), world);

        assertEquals(new MiningPlanner.Next(new BlockCoord(10, 0, 0), new BlockCoord(11, 0, 0)), outcome,
                "Небезопасная и замурованная цели пропускаются, добывается безопасная: иначе задача встанет на первой цели");
    }

    @Test
    void next_AfterOpeningBuriedTarget_ReturnsIt() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, 1, 0, Cell.SOLID).set(0, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID).set(-1, 0, 0, Cell.SOLID)
                .set(0, 0, 1, Cell.SOLID).set(0, 0, -1, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID).set(11, -1, 0, Cell.SOLID);
        List<BlockCoord> remaining = List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0));

        MiningPlanner.Outcome before = MiningPlanner.next(FROM, remaining, world);
        world.set(1, 0, 0, Cell.PASSABLE).set(1, -1, 0, Cell.SOLID);
        MiningPlanner.Outcome after = MiningPlanner.next(FROM, remaining, world);

        assertEquals(new MiningPlanner.Next(new BlockCoord(10, 0, 0), new BlockCoord(11, 0, 0)), before,
                "Пока ближняя цель замурована, выбирается дальняя обнажённая");
        assertEquals(new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)), after,
                "После обнажения ближней цели планировщик возвращается к ней: иначе дух навсегда обходит открытую руду");
    }

    @Test
    void next_EmptyRemaining_ReturnsExhaustedZero() {
        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(), new CellWorld());

        assertEquals(new MiningPlanner.Exhausted(0, 0), outcome,
                "Пустой список целей — Exhausted с нулями: вызывающий отличает конец списка от пропусков");
    }

    @Test
    void next_FlatGroundTarget_IsReachableFromSurface() {
        CellWorld world = new CellWorld().fill(0, -2, -2, 2, 2, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertEquals(new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(-1, 1, 0)), outcome,
                "Сбор на ровной земле не должен заканчиваться Exhausted(noStand>0): цель открыта сверху, "
                        + "стоянка — клетка поверхности рядом с целью");
    }

    @Test
    void next_SafeTarget_ReturnedTargetPassesCanBreak() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world);

        assertTrue(outcome instanceof MiningPlanner.Next next
                        && MiningSafety.canBreak(next.target(), world) instanceof BreakSafety.Safe,
                "Возвращённая цель обязана быть безопасной для ломки: иначе дух бьёт блок с лавой за гранью");
    }
}
