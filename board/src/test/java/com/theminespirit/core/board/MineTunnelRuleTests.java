package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.MinePlot;
import org.junit.jupiter.api.Test;

class MineTunnelRuleTests {
    private static final BlockCoord ENTRANCE = new BlockCoord(0, 68, 0);
    private static final BlockCoord BOTTOM = new BlockCoord(20, -57, 0);
    private static final MinePlot PLOT = new MinePlot(new BlockCoord(0, 67, 0), ENTRANCE, 0, -1, 20, 1);

    @Test
    void allowed_AtTunnelBottom_True() {
        assertTrue(
                MineTunnelRule.allowed(new BlockCoord(20, -57, 0), PLOT, ENTRANCE, BOTTOM),
                "Дух на дне шахты руду из стен хода берёт: это и есть место прохода к руде");
    }

    @Test
    void allowed_AlongTunnelFromBottom_True() {
        assertTrue(
                MineTunnelRule.allowed(new BlockCoord(60, -57, 0), PLOT, ENTRANCE, BOTTOM),
                "Ход продолжается от низа спуска: дух внутри выработки руду брать может");
    }

    @Test
    void allowed_AtWarehouseSurface_False() {
        assertFalse(
                MineTunnelRule.allowed(new BlockCoord(-180, 57, -519), PLOT, ENTRANCE, BOTTOM),
                "Яма у склада (-180,57,-519) — не шахта: проход к руде оттуда запрещён, иначе снова яма под складом");
    }

    @Test
    void allowed_OnSurfaceAboveEntrance_False() {
        assertFalse(
                MineTunnelRule.allowed(new BlockCoord(0, 69, 0), PLOT, ENTRANCE, BOTTOM),
                "На поверхности над входом руду в любую сторону не копают: шахта начинается только по спуску");
    }

    @Test
    void allowed_OutsideSettlement_True() {
        assertTrue(
                MineTunnelRule.allowed(new BlockCoord(500, 40, 500), null, null, null),
                "Вне поселения плана шахты нет: правило не запрещает проход к руде в дикой местности");
    }

    @Test
    void allowed_OffTunnelCorridor_False() {
        assertFalse(
                MineTunnelRule.allowed(new BlockCoord(20, -57, 8), PLOT, ENTRANCE, BOTTOM),
                "Сбоку от хода (другая z) — уже не шахта: проход в любую сторону запрещён");
    }
}
