package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MineLevelsTests {
    private static final BlockCoord ENTRANCE = new BlockCoord(-171, 64, -489);
    private static final BlockCoord ANCHOR = new BlockCoord(-170, 70, -490);
    private static final BlockCoord HOME = new BlockCoord(-168, 70, -492);

    @Test
    void home_EntranceOfStage_WinsOverPlotAndSpiritHome() {
        assertEquals(
                Optional.of(ENTRANCE),
                MineLevels.home(Optional.of(ENTRANCE), Optional.of(ANCHOR), List.of(HOME)),
                "Спуск уровня шахты считается от входа шахты этапа: якорь участка и дом духа — запасные "
                        + "точки, иначе спуск встаёт не там, где шахта плана");
    }

    @Test
    void home_NoEntrance_FallsBackToPlotAnchor() {
        assertEquals(
                Optional.of(ANCHOR),
                MineLevels.home(Optional.empty(), Optional.of(ANCHOR), List.of(HOME)),
                "Без входа шахты место спуска считают от якоря размеченного участка, а не от случайного духа");
    }

    @Test
    void home_NoPlot_FallsBackToSpiritHome() {
        assertEquals(
                Optional.of(HOME),
                MineLevels.home(Optional.empty(), Optional.empty(), List.of(HOME)),
                "Без участка домом остаётся дом духа: уровень шахты ставится у поселения, где духи живут");
    }

    @Test
    void home_NoPlotAndNoHome_IsEmptyInsteadOfInventedPoint() {
        assertTrue(
                MineLevels.home(Optional.empty(), Optional.empty(), List.of()).isEmpty(),
                "Нет ни участка, ни дома — домашней точки нет: выдуманная (0,64,0) увела бы спуск шахты в "
                        + "случайное место мира. Как надо: не ставить задание уровня, пока дома нет");
    }
}
