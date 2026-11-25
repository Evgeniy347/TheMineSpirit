package com.theminespirit.core.town;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TownMapEdits {
    private TownMapEdits() {}

    public static Optional<TownMap> removePlot(TownMap map, String id) {
        Objects.requireNonNull(map, "Карта не может быть null: удалять не из чего");
        Objects.requireNonNull(id, "id участка не может быть null: по нему выбирается объект");
        if (map.plots().stream().noneMatch(plot -> plot.id().equals(id))) {
            return Optional.empty();
        }
        List<TownMap.MapPlot> left =
                map.plots().stream().filter(plot -> !plot.id().equals(id)).toList();
        return Optional.of(new TownMap(map.anchor(), left, map.streets(), map.wall()));
    }
}
