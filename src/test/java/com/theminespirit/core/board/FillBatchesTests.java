package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FillBatchesTests {

    @Test
    void of_SmallLayers_OneBatchForWholePit() {
        List<PlotLeveling.Box> layers = List.of(
                new PlotLeveling.Box(0, 60, 0, 2, 60, 2),
                new PlotLeveling.Box(0, 61, 0, 2, 61, 2),
                new PlotLeveling.Box(0, 62, 0, 2, 62, 2));
        assertEquals(
                List.of(layers),
                FillBatches.of(layers, 128),
                "Яма 27 блоков — одна партия: земля на всю яму набирается за раз, без ходок по 2–3 блока (INC-73)");
    }

    @Test
    void of_LayersOverBatch_SplitAtBatch() {
        List<PlotLeveling.Box> layers = List.of(
                new PlotLeveling.Box(0, 60, 0, 9, 60, 9),
                new PlotLeveling.Box(0, 61, 0, 9, 61, 9),
                new PlotLeveling.Box(0, 62, 0, 9, 62, 9));
        assertEquals(
                List.of(200, 100),
                FillBatches.of(layers, 256).stream().map(FillBatches::volume).toList(),
                "Слои по 100 при партии 256 — партии 200 и 100: в инвентарь не берётся больше партии");
    }
}
