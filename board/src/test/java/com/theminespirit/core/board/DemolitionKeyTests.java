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
}
