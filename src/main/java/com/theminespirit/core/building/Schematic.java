package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks, List<Cell> air) {
    public record Cell(int dx, int dy, int dz) {}

    public record Entry(int dx, int dy, int dz, String block, Map<String, String> state, String text) {
        public Entry(int dx, int dy, int dz, String block) {
            this(dx, dy, dz, block, Map.of(), "");
        }

        public Entry(int dx, int dy, int dz, String block, String text) {
            this(dx, dy, dz, block, Map.of(), text);
        }

        public Entry(int dx, int dy, int dz, String block, Map<String, String> state) {
            this(dx, dy, dz, block, state, "");
        }

        public Entry {
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            if (block.isBlank()) {
                throw new IllegalArgumentException("Блок схемы пуст: по пустому id блок не поставить");
            }
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
            text = text == null ? "" : text;
        }
    }

    public Schematic {
        Objects.requireNonNull(
                blocks,
                "Список блоков схемы не может быть null: пустую схему задают пустым списком только через парсер с ошибкой");
        Objects.requireNonNull(
                air, "Список клеток воздуха не может быть null: схему без обязательного воздуха задают пустым списком");
        blocks = List.copyOf(blocks);
        air = List.copyOf(air);
    }

    public Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        this(sizeX, sizeY, sizeZ, blocks, List.of());
    }

    public List<BlockCoord> airCells(BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, где очищать");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него очистится не та клетка");
        List<BlockCoord> cells = new ArrayList<>(air.size());
        for (Cell cell : air) {
            BuildRotation.Cell rotated = rotation.rotate(cell.dx(), cell.dz(), sizeX, sizeZ);
            cells.add(new BlockCoord(origin.x() + rotated.x(), origin.y() + cell.dy(), origin.z() + rotated.z()));
        }
        return List.copyOf(cells);
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
                    entry.block(),
                    entry.state(),
                    entry.text()));
        }
        placed.sort(Comparator.comparingInt((PlacedEntry e) -> e.pos().y())
                .thenComparingInt(e -> placementRank(e.block()))
                .thenComparingInt(e -> e.pos().x())
                .thenComparingInt(e -> e.pos().z()));
        return List.copyOf(placed);
    }

    private static int placementRank(String block) {
        String path = block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
        if (path.endsWith("_sign")) {
            return 2;
        }
        return path.equals("chest") || path.equals("trapped_chest") || path.endsWith("_chest") ? 1 : 0;
    }

    public record PlacedEntry(BlockCoord pos, String block, Map<String, String> state, String text) {
        public PlacedEntry(BlockCoord pos, String block) {
            this(pos, block, Map.of(), "");
        }

        public PlacedEntry(BlockCoord pos, String block, String text) {
            this(pos, block, Map.of(), text);
        }

        public PlacedEntry(BlockCoord pos, String block, Map<String, String> state) {
            this(pos, block, state, "");
        }

        public PlacedEntry {
            Objects.requireNonNull(pos, "Позиция блока схемы не может быть null: без неё неизвестно, куда ставить");
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
            text = text == null ? "" : text;
        }
    }
}
