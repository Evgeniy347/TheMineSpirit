package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemolitionKeyTests {
    @Test
    void idFor_PieceAnchor_StableWhenSetShrinks() {
        BlockCoord anchor = new BlockCoord(-170, 73, -484);

        String first = DemolitionKey.idFor(List.of(anchor, new BlockCoord(-169, 73, -484)));
        String second = DemolitionKey.idFor(List.of(anchor));

        assertEquals(
                first,
                second,
                "Один билет на кусок: пока начальная клетка та же, набор уборки не рождает новый demolish:<id>");
    }

    @Test
    void piece_NearCells_TakesOnlyOneClearArea() {
        BlockCoord anchor = new BlockCoord(-170, 73, -484);
        BlockCoord far = new BlockCoord(-100, 73, -400);

        List<BlockCoord> piece = DemolitionKey.piece(
                List.of(anchor, new BlockCoord(-168, 73, -485), far, new BlockCoord(-170, 80, -484)));

        assertEquals(
                List.of(anchor, new BlockCoord(-168, 73, -485)),
                piece,
                "Билет берёт кусок под одну область clear_area (радиус 4 и три слоя), а не весь участок");
        assertTrue(!piece.contains(far), "Далёкая клетка — другой кусок со своим билетом");
    }

    @Test
    void treePiece_NeighbourTrees_ShareOneStandWithoutCuttingTrunks() {
        BlockCoord stand = new BlockCoord(0, 64, 0);
        BlockCoord firstTree = new BlockCoord(1, 70, 1);
        BlockCoord secondTree = new BlockCoord(3, 71, -2);
        List<BlockCoord> cells = List.of(
                new BlockCoord(1, 65, 1),
                new BlockCoord(1, 66, 1),
                firstTree,
                new BlockCoord(3, 65, -2),
                new BlockCoord(3, 66, -2),
                secondTree);

        assertTrue(
                DemolitionKey.withinStand(stand, List.of(firstTree, secondTree)),
                "Два дерева в радиусе 4 от общего стенда — один пакет уборки");
        assertEquals(
                cells,
                DemolitionKey.treePiece(cells, stand),
                "Пакет нескольких деревьев у общего стенда не режет стволы по высоте: иначе от дерева остаётся "
                        + "только верхушка");
    }

    @Test
    void withinStand_TreeFarFromStand_IsAnotherTicket() {
        BlockCoord stand = new BlockCoord(0, 64, 0);
        assertTrue(
                !DemolitionKey.withinStand(stand, List.of(new BlockCoord(0, 65, 0), new BlockCoord(10, 65, 0))),
                "Дерево за радиусом стенда — отдельный билет: общий стенд до него не достаёт");
    }
}
