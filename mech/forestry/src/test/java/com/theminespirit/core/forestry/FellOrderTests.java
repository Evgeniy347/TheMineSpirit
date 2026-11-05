package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FellOrderTests {
    private static TreeGrid trunk() {
        return TreeGrid.ofLayers("###\n###\n###", "@@@\n@L@\n@@@", "@@@\n@L@\n@@@", "@@@\n@L@\n@@@", "@@@\n@L@\n@@@");
    }

    @Test
    void of_TreeFromBase_OrdersFromTheVeryTop() {
        TreeGrid grid = trunk();

        List<BlockCoord> order = FellOrder.of(new BlockCoord(1, 1, 1), grid);

        assertEquals(
                grid.logs(),
                Set.copyOf(order),
                "Валка берёт всё дерево целиком, а не только брёвна внутри ящика расчистки: " + order);
        assertEquals(
                List.of(4, 3, 2, 1),
                order.stream().map(BlockCoord::y).toList(),
                "Дерево валится сверху вниз: первым — самое высокое бревно, последним — основание: " + order);
        assertTrue(
                order.get(0).y() == 4, "Первое бревно в порядке обязано быть вершиной, а не стволом у земли: " + order);
    }

    @Test
    void of_NotATree_NoOrder() {
        assertEquals(
                List.of(),
                FellOrder.of(new BlockCoord(0, 1, 0), TreeGrid.ofLayers("....", ".L..", "....")),
                "Одинокое бревно без листвы — не дерево: расчистка не выдумывает валку там, где её нет");
        assertEquals(
                List.of(),
                FellOrder.of(new BlockCoord(0, 1, 0), TreeGrid.ofLayers("....", ".#..", "....")),
                "Не бревно — не начало валки: порядок не строится");
    }
}
