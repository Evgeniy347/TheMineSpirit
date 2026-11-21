package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.StandSpotsTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandSpotsTestsExtraTests {

    @Test
    void of_StandFourBelowTarget_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.PASSABLE)
                .set(1, -1, 0, Cell.PASSABLE)
                .set(1, -2, 0, Cell.PASSABLE)
                .set(1, -3, 0, Cell.PASSABLE)
                .set(1, -4, 0, Cell.PASSABLE)
                .set(1, -5, 0, Cell.SOLID);

        List<BlockCoord> spots = StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0));

        assertEquals(
                List.of(),
                spots,
                "Стоянка на четыре ниже цели вне досягаемости (вниз 3): её нельзя предлагать, иначе дух "
                        + "выберет клетку, с которой не достанет блок. Стоянки: " + spots
                        + " При смене of_StandFourBelowTarget_IsRejected проверь это утверждение.");
    }
}
