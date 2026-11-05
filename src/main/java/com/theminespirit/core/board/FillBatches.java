package com.theminespirit.core.board;

import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FillBatches {
    private FillBatches() {}

    public static List<List<PlotLeveling.Box>> of(List<PlotLeveling.Box> layers, int batch) {
        Objects.requireNonNull(layers, "Слои подсыпки не могут быть null: нет подсыпки — пустой список");
        if (batch < 1) {
            throw new IllegalArgumentException("Партия земли " + batch + ": хотя бы блок");
        }
        List<List<PlotLeveling.Box>> out = new ArrayList<>();
        List<PlotLeveling.Box> open = new ArrayList<>();
        int volume = 0;
        for (PlotLeveling.Box layer : layers) {
            if (!open.isEmpty() && volume + layer.volume() > batch) {
                out.add(List.copyOf(open));
                open.clear();
                volume = 0;
            }
            open.add(layer);
            volume += layer.volume();
        }
        if (!open.isEmpty()) {
            out.add(List.copyOf(open));
        }
        return List.copyOf(out);
    }

    public static int volume(List<PlotLeveling.Box> batch) {
        return batch.stream().mapToInt(PlotLeveling.Box::volume).sum();
    }
}
