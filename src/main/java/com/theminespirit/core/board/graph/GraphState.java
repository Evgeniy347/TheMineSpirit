package com.theminespirit.core.board.graph;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GraphState(
        Map<String, Integer> stock,
        int stage,
        Map<String, Map<String, Integer>> kits,
        Map<String, Built> buildings,
        Map<String, Map<String, Integer>> materials,
        int storageFreePercent,
        Set<String> events,
        Set<String> world) {
    public enum Built {
        BUILT,
        PLANNED,
        ABSENT
    }

    public GraphState {
        stock = Map.copyOf(Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой"));
        kits = Map.copyOf(Objects.requireNonNull(kits, "Наборы духов не могут быть null: без духов — пустая карта"));
        buildings = Map.copyOf(
                Objects.requireNonNull(buildings, "Постройки не могут быть null: неизвестные считаются ABSENT"));
        materials = Map.copyOf(Objects.requireNonNull(
                materials, "Материалы построек не могут быть null: постройка без схемы — пустая карта"));
        events = Set.copyOf(Objects.requireNonNull(events, "События не могут быть null: без событий — пустой набор"));
        world = Set.copyOf(
                Objects.requireNonNull(world, "Ресурсы мира не могут быть null: в округе ничего нет — пустой набор"));
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + " меньше 1: поселение начинается с этапа 1");
        }
    }

    public int spirits() {
        return kits.size();
    }

    public int count(String item) {
        return stock.getOrDefault(item, 0);
    }

    public Built built(String building) {
        return buildings.getOrDefault(building, Built.ABSENT);
    }

    public int tier(String spirit, String kind) {
        return kits.getOrDefault(spirit, Map.of()).getOrDefault(kind, -1);
    }

    public int bestTier(String kind) {
        return kits.keySet().stream()
                .mapToInt(spirit -> tier(spirit, kind))
                .max()
                .orElse(-1);
    }
}
