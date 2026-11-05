package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record WorldReport(
        int strayBlocks,
        int strayGroups,
        int pits,
        int mounds,
        int hanging,
        int damaged,
        boolean unknown,
        List<Stray> strays,
        List<Damaged> buildings,
        int natural,
        Map<String, Integer> floating) {
    public record Stray(
            String type,
            int x,
            int y,
            int z,
            int blocks,
            @Nullable String builder) {
        public Stray {
            Objects.requireNonNull(type, "Тип лишнего не может быть null: наблюдателю нужно имя");
            if (blocks < 1) {
                throw new IllegalArgumentException(
                        "Кусок лишнего на " + blocks + " блоков: у находки хотя бы один блок");
            }
        }
    }

    public record Damaged(String id, int damage, boolean unknown) {
        public Damaged {
            Objects.requireNonNull(id, "Постройка без id: наблюдатель не поймёт, что чинить");
            if (damage < 0) {
                throw new IllegalArgumentException("Повреждений " + damage + ": отсутствие — ноль");
            }
        }
    }

    public WorldReport {
        if (strayBlocks < 0 || strayGroups < 0 || pits < 0 || mounds < 0 || hanging < 0 || damaged < 0 || natural < 0) {
            throw new IllegalArgumentException("Счётчики мира не бывают отрицательными: отсутствие — ноль");
        }
        strays = List.copyOf(Objects.requireNonNull(strays, "Куски лишнего не могут быть null"));
        buildings = List.copyOf(Objects.requireNonNull(buildings, "Повреждённые постройки не могут быть null"));
        floating = Map.copyOf(Objects.requireNonNull(floating, "Состав висящего не может быть null: пустой — карта"));
    }

    public WorldReport(
            int strayBlocks,
            int strayGroups,
            int pits,
            int mounds,
            int hanging,
            int damaged,
            boolean unknown,
            List<Stray> strays,
            List<Damaged> buildings,
            int natural) {
        this(strayBlocks, strayGroups, pits, mounds, hanging, damaged, unknown, strays, buildings, natural, Map.of());
    }

    public WorldReport(
            int strayBlocks,
            int strayGroups,
            int pits,
            int mounds,
            int hanging,
            int damaged,
            boolean unknown,
            List<Stray> strays,
            List<Damaged> buildings) {
        this(strayBlocks, strayGroups, pits, mounds, hanging, damaged, unknown, strays, buildings, 0, Map.of());
    }

    public String hangingText() {
        if (floating.isEmpty()) {
            return Integer.toString(hanging);
        }
        String types = floating.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return hanging + " (" + types + ")";
    }

    public static WorldReport of(GraphFacts facts) {
        Objects.requireNonNull(facts, "Факты графа не могут быть null: из них собирается сводка мира");
        int blocks = 0;
        List<Stray> strays = new ArrayList<>();
        for (GraphSiteFacts.Stray stray : facts.strays()) {
            int size =
                    stray.blocks().values().stream().mapToInt(Integer::intValue).sum();
            blocks += Math.max(1, size);
            strays.add(new Stray(
                    stray.type(),
                    stray.position().x(),
                    stray.position().y(),
                    stray.position().z(),
                    Math.max(1, size),
                    stray.builder()));
        }
        List<Damaged> buildings = new ArrayList<>();
        facts.buildings().values().stream()
                .filter(building -> !building.unknown() && building.damage() > 0)
                .sorted(Comparator.comparing(GraphFacts.Building::id))
                .forEach(building -> buildings.add(new Damaged(building.id(), building.damage(), building.unknown())));
        return new WorldReport(
                blocks,
                strays.size(),
                facts.terrain().pits(),
                facts.terrain().cut().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum(),
                facts.terrain().hanging(),
                buildings.size(),
                facts.terrain().unknown(),
                strays,
                buildings,
                facts.terrain().natural(),
                facts.terrain().floating());
    }
}
