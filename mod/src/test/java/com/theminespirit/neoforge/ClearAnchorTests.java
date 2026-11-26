package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClearAnchorTests {
    private static final int REACH = 3;

    @Test
    void best_MultipleStands_PicksWidestCoverage() {
        ClearAnchor.Stand wide = new ClearAnchor.Stand(0, 64, 0);
        ClearAnchor.Stand narrow = new ClearAnchor.Stand(20, 64, 0);
        List<BlockCoord> cells = List.of(
                new BlockCoord(1, 64, 0),
                new BlockCoord(2, 64, 1),
                new BlockCoord(-1, 66, 0),
                new BlockCoord(21, 64, 0));

        ClearAnchor.Stand chosen =
                ClearAnchor.best(cells, List.of(narrow, wide), new BlockCoord(20, 64, 0), REACH, Set.of());

        assertEquals(
                wide,
                chosen,
                "Точка выбирается по охвату клеток, а не по близости: иначе дух идёт к ближней клетке мимо кучи, "
                        + "и задание ползёт по одной клетке за подход. При смене best_MultipleStands_PicksWidestCoverage "
                        + "проверь вес охвата против расстояния.");
    }

    @Test
    void best_EqualCoverage_PrefersNearerStand() {
        ClearAnchor.Stand near = new ClearAnchor.Stand(2, 64, 0);
        ClearAnchor.Stand far = new ClearAnchor.Stand(10, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(9, 64, 0), new BlockCoord(3, 64, 0));

        ClearAnchor.Stand chosen =
                ClearAnchor.best(cells, List.of(far, near), new BlockCoord(0, 64, 0), REACH, Set.of());

        assertEquals(
                near,
                chosen,
                "При равном охвате выигрывает ближняя стоянка: иначе дух каждый раз уходит к дальней точке, "
                        + "а пеший подход съедает время задания. При смене best_EqualCoverage_PrefersNearerStand "
                        + "проверь тай-брейк по distanceSqr.");
    }

    @Test
    void best_TriedStandAndEmptyCoverage_AreSkipped() {
        ClearAnchor.Stand tried = new ClearAnchor.Stand(0, 64, 0);
        ClearAnchor.Stand empty = new ClearAnchor.Stand(50, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(1, 64, 0));

        ClearAnchor.Stand chosen = ClearAnchor.best(
                cells, List.of(tried, empty), new BlockCoord(0, 64, 0), REACH, Set.of(new BlockCoord(0, 64, 0)));

        assertNull(
                chosen,
                "Опробованная и пустая стоянки не выбираются повторно: иначе дух бесконечно возвращается к той же "
                        + "неудачной точке и задание не кончается. При смене best_TriedStandAndEmptyCoverage_AreSkipped "
                        + "проверь фильтры tried и score == 0.");
    }

    @Test
    void best_CellsOutsideVerticalBand_DoNotScore() {
        ClearAnchor.Stand stand = new ClearAnchor.Stand(0, 64, 0);
        List<BlockCoord> tooHigh = List.of(new BlockCoord(1, 64 + ClearAnchor.MAX_RISE + 1, 0));
        List<BlockCoord> tooLow = List.of(new BlockCoord(1, 64 - ClearAnchor.BELOW - 1, 0));
        List<BlockCoord> ok = List.of(new BlockCoord(1, 64 + ClearAnchor.MAX_RISE, 0));

        assertNull(
                ClearAnchor.best(tooHigh, List.of(stand), new BlockCoord(0, 64, 0), REACH, Set.of()),
                "Клетка выше предела подъёма не считается: иначе точка «накрывает» недостижимый столб и дух "
                        + "строит лестницу в пустоту. При смене best_CellsOutsideVerticalBand_DoNotScore проверь MAX_RISE.");
        assertNull(
                ClearAnchor.best(tooLow, List.of(stand), new BlockCoord(0, 64, 0), REACH, Set.of()),
                "Клетка ниже допуска не считается: иначе дух спускается за целью, до которой не достаёт стоя. "
                        + "При смене best_CellsOutsideVerticalBand_DoNotScore проверь BELOW.");
        assertEquals(
                stand,
                ClearAnchor.best(ok, List.of(stand), new BlockCoord(0, 64, 0), REACH, Set.of()),
                "Клетка на границе подъёма ещё считается: иначе верхний слой столба остаётся неубранным, "
                        + "и задание переоткрывается. При смене best_CellsOutsideVerticalBand_DoNotScore проверь "
                        + "границу MAX_RISE.");
    }

    @Test
    void best_StandThreeAboveTarget_CoversTopLayer() {
        ClearAnchor.Stand high = new ClearAnchor.Stand(0, 67, 0);
        List<BlockCoord> top = List.of(new BlockCoord(1, 64, 0));

        assertEquals(
                high,
                ClearAnchor.best(top, List.of(high), new BlockCoord(0, 64, 0), REACH, Set.of()),
                "Верхний слой холма можно снять со стоянки на три блока выше: иначе clear_area снова "
                        + "строит бесполезные столбы и не расчищает wheat_field. При смене "
                        + "best_StandThreeAboveTarget_CoversTopLayer проверь ClearAnchor.BELOW и живую поле.");
    }

    @Test
    void best_StandDeepBelowSpirit_IsNotChosen() {
        ClearAnchor.Stand pit = new ClearAnchor.Stand(0, 45, 0);
        ClearAnchor.Stand near = new ClearAnchor.Stand(3, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(0, 49, 0), new BlockCoord(0, 60, 0), new BlockCoord(4, 64, 0));

        ClearAnchor.Stand chosen =
                ClearAnchor.best(cells, List.of(pit, near), new BlockCoord(0, 64, 0), REACH, Set.of());

        assertEquals(
                near,
                chosen,
                "Спуск в глубокую яму ради охвата не выбирается: иначе дух бесконечно ищет путь к точке на 19 "
                        + "ниже (живой лог 22.11, -168,49,-493) и клетки не снимаются вовсе, поэтому берём стоянку "
                        + "уровня духа. При смене best_StandDeepBelowSpirit_IsNotChosen проверь порог BELOW");
    }

    @Test
    void reachFor_SpiritHorizontal_WidensToBreakingReach() {
        assertEquals(
                4,
                ClearAnchor.reachFor(7),
                "Якорь видит стоянки на всю руку ломки, а не на клетку уже: иначе клетка в четырёх по x "
                        + "считается недостижимой (живой лог 22.11, grass у -178,69,-517), иначе билет падает "
                        + "вхолостую. При смене reachFor_SpiritHorizontal_WidensToBreakingReach проверь верхнюю границу");
        assertEquals(
                2,
                ClearAnchor.reachFor(2),
                "Меньший горизонтальный радиус духа не расширяется: иначе якорь обещает охват, которого нет, "
                        + "и подход валится. При смене reachFor_SpiritHorizontal_WidensToBreakingReach проверь минимум");
    }

    @Test
    void best_CellsBeyondHorizontalReach_DoNotScore() {
        ClearAnchor.Stand stand = new ClearAnchor.Stand(0, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(REACH + 1, 64, 0));

        assertNull(
                ClearAnchor.best(cells, List.of(stand), new BlockCoord(0, 64, 0), REACH, Set.of()),
                "Клетка за горизонтальным радиусом не считается: иначе якорь обещает охват, которого рука не "
                        + "достанет, и дух топчется на месте. При смене best_CellsBeyondHorizontalReach_DoNotScore "
                        + "проверь REACH.");
    }
}
