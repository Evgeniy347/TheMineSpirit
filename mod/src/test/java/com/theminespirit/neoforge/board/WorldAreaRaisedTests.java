package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldAreaRaisedTests {
    @Test
    void leveling_TallPillar_WholeColumnIsCut() {
        List<BlockCoord> column = new ArrayList<>();
        for (int y = 64; y <= 97; y++) {
            column.add(new BlockCoord(0, y, 0));
        }
        int cut = LevelRepair.leveling(List.of(), column).cut().stream()
                .mapToInt(PlotLeveling.Box::volume)
                .sum();

        assertEquals(
                column.size(),
                cut,
                "срезка участка берёт столб целиком, сколько бы он ни был высок: высокие блоки на участке убирают"
                        + " духи (владелец 12.11: предел «пол + 10» не согласован и снят). Сломается: верхушки столбов"
                        + " висят над участком навсегда. Проверить при смене: WorldAreaScan.raised, LevelRepair.leveling");
    }
}
