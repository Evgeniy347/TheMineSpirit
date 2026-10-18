package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
    public record Entry(int dx, int dy, int dz, String block, Map<String, String> state) {
        public Entry(int dx, int dy, int dz, String block) {
            this(dx, dy, dz, block, Map.of());
        }

        public Entry {
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            if (block.isBlank()) {
                throw new IllegalArgumentException("Блок схемы пуст: по пустому id блок не поставить");
            }
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
        }
    }

    public Schematic {
        Objects.requireNonNull(blocks, "Список блоков схемы не может быть null: пустую схему задают пустым списком только через парсер с ошибкой");
        blocks = List.copyOf(blocks);
    }

    public List<PlacedEntry> entries(BlockCoord origin) {
        return entries(origin, BuildRotation.NONE);
    }

    public List<PlacedEntry> entries(BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, куда ставить");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него блок встанет не туда");
        List<PlacedEntry> placed = new ArrayList<>(blocks.size());
        for (Entry entry : blocks) {
            BuildRotation.Cell cell = rotation.rotate(entry.dx(), entry.dz(), sizeX, sizeZ);
            placed.add(new PlacedEntry(
                    new BlockCoord(origin.x() + cell.x(), origin.y() + entry.dy(), origin.z() + cell.z()),
                    entry.block(), entry.state()));
        }
        placed.sort(Comparator.comparingInt((PlacedEntry e) -> e.pos().y()).thenComparingInt(e -> e.pos().x())
                .thenComparingInt(e -> e.pos().z()));
        return List.copyOf(placed);
    }

    public record PlacedEntry(BlockCoord pos, String block, Map<String, String> state) {
        public PlacedEntry(BlockCoord pos, String block) {
            this(pos, block, Map.of());
        }

        public PlacedEntry {
            Objects.requireNonNull(pos, "Позиция блока схемы не может быть null: без неё неизвестно, куда ставить");
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
        }
    }
}
