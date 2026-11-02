package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GraphWorldFacts(
        List<GraphFacts.Stray> strays, GraphFacts.Terrain terrain, Map<String, GraphFacts.Building> buildings) {
    public GraphWorldFacts {
        strays = List.copyOf(
                Objects.requireNonNull(strays, "Лишние постройки не могут быть null: без них — пустой список"));
        Objects.requireNonNull(terrain, "Рельеф не может быть null: ровный задают Terrain.empty()");
        buildings =
                Map.copyOf(Objects.requireNonNull(buildings, "Постройки не могут быть null: без них — пустая карта"));
    }
}
