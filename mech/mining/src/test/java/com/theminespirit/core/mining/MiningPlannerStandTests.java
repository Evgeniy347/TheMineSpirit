package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningPlannerStandTests {
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);

    @Test
    void next_ExcludedStand_PicksNextOpenStand() {
        CellWorld world = new CellWorld().set(1, -1, 0, Cell.SOLID).set(-1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.next(FROM, List.of(new BlockCoord(0, 0, 0)), world, Set.of(new BlockCoord(-1, 0, 0)));

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                outcome,
                "Исключённая стоянка обязана пропускаться: дух встаёт на следующую открытую, "
                        + "а не идёт в глухое место");
    }

    @Test
    void next_AllStandsExcluded_ReturnsExhaustedWithUnreachableCount() {
        CellWorld world = new CellWorld().set(1, -1, 0, Cell.SOLID).set(-1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                world,
                Set.of(new BlockCoord(-1, 0, 0), new BlockCoord(1, 0, 0)));

        assertEquals(
                new MiningPlanner.Exhausted(0, 0, 1),
                outcome,
                "Цель, чьи стоянки нашлись, но все исключены, обязана считаться недостижимой, а не без места: "
                        + "иначе в итоге нельзя отличить глухую цель от замурованной");
    }

    @Test
    void next_StandNineAboveSpirit_ReturnsExhaustedUnreachable() {
        CellWorld world = new CellWorld()
                .set(0, 9, 0, Cell.SOLID)
                .set(1, 9, 0, Cell.PASSABLE)
                .set(1, 8, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, List.of(new BlockCoord(0, 9, 0)), world);

        assertEquals(
                new MiningPlanner.Exhausted(0, 0, 1),
                outcome,
                "Стоянка на 9 блоков выше духа недостижима шагом: иначе планировщик выдаёт цель, RouteDetour "
                        + "возвращает FOUND, а WALK застревает на 60 тиков и петля повторяется каждую минуту "
                        + "(аномалия 3). Как надо: высокую стоянку отбрасывать до выдачи и считать цель недостижимой");
    }

    @Test
    void next_StandAtRiseLimit_IsAccepted() {
        CellWorld world = new CellWorld()
                .set(0, MiningTuning.MAX_STAND_RISE, 0, Cell.SOLID)
                .set(1, MiningTuning.MAX_STAND_RISE, 0, Cell.PASSABLE)
                .set(1, MiningTuning.MAX_STAND_RISE - 1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.next(FROM, List.of(new BlockCoord(0, MiningTuning.MAX_STAND_RISE, 0)), world);

        assertEquals(
                new MiningPlanner.Next(
                        new BlockCoord(0, MiningTuning.MAX_STAND_RISE, 0),
                        new BlockCoord(1, MiningTuning.MAX_STAND_RISE, 0)),
                outcome,
                "Стоянка ровно на пределе подъёма остаётся достижимой: порог не должен отбрасывать "
                        + "цель, к которой дух поднимается по ступеням на допустимую высоту");
    }

    @Test
    void next_ExcludedAndWalledTargets_CountsSeparately() {
        CellWorld world = new CellWorld()
                .set(1, -1, 0, Cell.SOLID)
                .set(-1, -1, 0, Cell.SOLID)
                .set(10, 1, 0, Cell.SOLID)
                .set(10, -1, 0, Cell.SOLID)
                .set(11, 0, 0, Cell.SOLID)
                .set(9, 0, 0, Cell.SOLID)
                .set(10, 0, 1, Cell.SOLID)
                .set(10, 0, -1, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(
                FROM,
                List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0)),
                world,
                Set.of(new BlockCoord(-1, 0, 0), new BlockCoord(1, 0, 0)));

        assertEquals(
                new MiningPlanner.Exhausted(0, 1, 1),
                outcome,
                "Замурованная цель идёт в счётчик без места, цель с исключёнными стоянками — в недостижимые: "
                        + "смешивание счётчиков врёт в причине отказа");
    }

    @Test
    void next_NearestStandUnreachable_ReturnsFartherReachableTarget() {
        CellWorld world = new CellWorld()
                .set(1, -1, 0, Cell.SOLID)
                .set(10, 0, 0, Cell.SOLID)
                .set(11, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome = MiningPlanner.next(
                FROM,
                List.of(new BlockCoord(0, 0, 0), new BlockCoord(10, 0, 0)),
                world,
                Set.of(),
                stand -> !stand.equals(new BlockCoord(1, 0, 0)));

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(10, 0, 0), new BlockCoord(11, 0, 0)),
                outcome,
                "Цель без пути к стоянке не выбирается до старта: иначе дух идёт к ближнему блоку, не может "
                        + "подойти и весь поход кончается «недостижимо: 22» вместо добычи дальнего дерева");
    }
}
