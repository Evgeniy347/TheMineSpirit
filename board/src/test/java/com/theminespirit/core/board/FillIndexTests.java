package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FillIndexTests {
    private static PlotLeveling fill(PlotLeveling.Box... boxes) {
        return new PlotLeveling(List.of(), List.of(boxes), PlotLeveling.DEFAULT_BLOCK);
    }

    private static PlotLeveling.Box box(int x, int y, int z) {
        return new PlotLeveling.Box(x, y, z, x, y, z);
    }

    @Test
    void covering_CellOfOpenFill_NamesItsTicket() {
        FillIndex index = FillIndex.of(Map.of("world:backfill:a", fill(box(4, 64, 7))));

        assertEquals(
                Optional.of("world:backfill:a"),
                index.covering(List.of(new BlockCoord(4, 64, 7))),
                "Клетка ямы внутри открытой засыпки обязана находить её задание, а не ставить второе");
    }

    @Test
    void covering_PitTouchingSeveralFills_ReturnsFirstKnownTicket() {
        Map<String, PlotLeveling> open = new LinkedHashMap<>();
        open.put("world:backfill:first", fill(box(1, 60, 1)));
        open.put("world:backfill:second", fill(box(2, 60, 1)));
        FillIndex index = FillIndex.of(open);

        assertEquals(
                Optional.of("world:backfill:first"),
                index.covering(List.of(new BlockCoord(2, 60, 1), new BlockCoord(1, 60, 1))),
                "Яма, задетая двумя заданиями, продолжает ближайшее найденное, а не плодит третье");
    }

    @Test
    void covering_FreeCellOrNoFills_IsEmpty() {
        FillIndex index = FillIndex.of(Map.of("world:backfill:a", fill(box(4, 64, 7))));

        assertTrue(index.covering(List.of(new BlockCoord(4, 65, 7))).isEmpty(), "Клетка над засыпкой свободна");
        assertTrue(
                FillIndex.of(Map.of())
                        .covering(List.of(new BlockCoord(1, 2, 3)))
                        .isEmpty(),
                "Нет засыпок — нет попадания");
        assertTrue(FillIndex.of(Map.of()).covering(List.of()).isEmpty(), "Пустая яма не находит задания");
    }

    @Test
    void covering_WideBox_IndexesEveryCell() {
        PlotLeveling leveling = fill(new PlotLeveling.Box(10, 60, 20, 11, 61, 20));
        FillIndex index = FillIndex.of(Map.of("world:backfill:wide", leveling));

        assertEquals(
                Optional.of("world:backfill:wide"),
                index.covering(List.of(new BlockCoord(11, 61, 20))),
                "Индекс обязан покрывать весь объём коробки, а не только её угол");
    }
}
