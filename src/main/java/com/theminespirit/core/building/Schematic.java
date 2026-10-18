package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
    public record Entry(int dx, int dy, int dz, String block, String text) {
        public Entry {
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            if (block.isBlank()) {
                throw new IllegalArgumentException("Блок схемы пуст: по пустому id блок не поставить");
            }
            text = text == null ? "" : text;
        }

        public Entry(int dx, int dy, int dz, String block) {
            this(dx, dy, dz, block, "");
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
                    entry.block(), entry.text()));
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

    public record PlacedEntry(BlockCoord pos, String block, String text) {
        public PlacedEntry {
            Objects.requireNonNull(pos, "Позиция блока не может быть null: без неё блок не поставить");
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            text = text == null ? "" : text;
        }

        public PlacedEntry(BlockCoord pos, String block) {
            this(pos, block, "");
        }
    }
}
