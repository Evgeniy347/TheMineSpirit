package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.forestry.api.ChopTuning;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeShapeSettlementTests {
    @Test
    void detect_SettlementLoneHighLog_ReturnsSingleLog() {
        TreeGrid grid = new TreeGrid();
        grid.log(0, 12, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(0, 12, 0), grid, true, ChopMode.SETTLEMENT),
                "В зоне поселения одиночное бревно на высоте обязано считаться деревом: иначе обломок в кроне "
                        + "остаётся стоять, а фоновая заготовка бесконечно его отвергает и не добирает брёвна. "
                        + "При смене режима SETTLEMENT проверь строку брёвен ниже в тесте");

        assertEquals(
                List.of(new BlockCoord(0, 12, 0)),
                tree.logs(),
                "Одиночное верхнее бревно — вся группа: никаких требований к стволу, почве и листве в режиме "
                        + "очистки зоны, иначе обломок не рубят и заготовка висит без прогресса. "
                        + "При смене детектора проверь строку координаты выше в тесте");
        assertEquals(
                new BlockCoord(0, 12, 0),
                tree.base(),
                "Основание группы — её единственное бревно: стоянку и рубку считают от него, иначе дух ищет "
                        + "землю под чужим стволом и не подходит к обломку. При смене base проверь строку выше "
                        + "в тесте");
    }

    @Test
    void detect_WildLoneHighLog_StillRejected() {
        TreeGrid grid = new TreeGrid();
        grid.log(0, 12, 0);

        assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 12, 0), grid, true, ChopMode.WILD),
                "Вне режима очистки зоны одиночное бревно остаётся не деревом: дикая рубка не должна ходить "
                        + "по обломкам, иначе она уходит в кроны и висит без цели. При смене WILD проверь строку "
                        + "режима ниже в тесте");
    }

    @Test
    void detect_SettlementTallTrunk_StopsAtClearanceRadius() {
        TreeGrid grid = new TreeGrid();
        for (int y = 0; y <= 34; y++) {
            grid.log(0, y, 0);
        }

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(0, 0, 0), grid, true, ChopMode.SETTLEMENT),
                "Ствол выше старого предела MAX_LOGS обязан распознаваться в зоне: мягкий детектор ограничен "
                        + "только радиусом очистки, иначе высокое дерево снова отвергается и заготовка встаёт. "
                        + "При смене CLEARANCE_RADIUS проверь размер группы ниже в тесте");

        assertEquals(
                ChopTuning.CLEARANCE_RADIUS + 1,
                tree.logs().size(),
                "В группу входят брёвна в пределах CLEARANCE_RADIUS по каждой оси: дальше обход не идёт, иначе "
                        + "мягкий детектор склеит соседние постройки и рубка заденет план. При смене радиуса "
                        + "проверь строку выше в тесте");
    }
}
