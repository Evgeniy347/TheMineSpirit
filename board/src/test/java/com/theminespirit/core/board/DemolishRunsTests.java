package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemolishRunsTests {
    @Test
    void runs_HangingColumn_OneSessionForWholeRun() {
        List<BlockCoord> pillar = List.of(
                new BlockCoord(-176, 75, -509),
                new BlockCoord(-176, 73, -509),
                new BlockCoord(-176, 74, -509),
                new BlockCoord(-176, 76, -509));

        assertEquals(
                List.of(new DemolishRuns.Run(-176, -509, 73, 76)),
                DemolishRuns.runs(pillar),
                "висячая колонка сносится одной сессией clear_area: дух ставит столб один раз, режет сверху вниз и"
                        + " разбирает столб один раз (владелец 12.11: раньше на каждый блок свой подъём и спуск)."
                        + " Сломается: N подъёмов на колонку, расход земли, падения. Проверить при смене:"
                        + " StagePlans.demolish и ClearScaffoldWork (CLIMB/DESCEND)");
    }

    @Test
    void runs_GapInColumn_SplitsRuns() {
        List<BlockCoord> cells = List.of(new BlockCoord(0, 70, 0), new BlockCoord(0, 71, 0), new BlockCoord(0, 74, 0));

        assertEquals(
                List.of(new DemolishRuns.Run(0, 0, 70, 71), new DemolishRuns.Run(0, 0, 74, 74)),
                DemolishRuns.runs(cells),
                "разрыв в колонке делит отрезки: в промежутке может стоять блок плана или чужое, а clear_area сносит"
                        + " весь отрезок с demolish=true. Сломается: снос блоков плана между висячими. Проверить при"
                        + " смене: условие y != previous + 1");
    }

    @Test
    void runs_SeveralColumns_LowestFirst() {
        List<BlockCoord> cells = List.of(new BlockCoord(5, 80, 5), new BlockCoord(1, 66, 1));

        assertEquals(
                List.of(new DemolishRuns.Run(1, 1, 66, 66), new DemolishRuns.Run(5, 5, 80, 80)),
                DemolishRuns.runs(cells),
                "отрезки идут снизу вверх по нижней клетке, как прежний порядок сноса: сначала низкие, доступные с"
                        + " земли, потом требующие столба. Сломается: порядок станет зависеть от хэша. Проверить: сортировка");
    }
}
