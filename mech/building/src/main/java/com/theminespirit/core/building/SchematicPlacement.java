package com.theminespirit.core.building;

import com.theminespirit.core.building.Schematic.Cell;
import com.theminespirit.core.building.Schematic.Entry;
import com.theminespirit.core.building.Schematic.PlacedEntry;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class SchematicPlacement {
    private SchematicPlacement() {}

    static List<BlockCoord> airCells(Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, где очищать");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него очистится не та клетка");
        Set<BlockCoord> cells = new LinkedHashSet<>();
        boolean grounded = SchematicGround.groundLayer(schematic);
        Set<Long> groundColumns = grounded ? groundColumns(schematic, rotation) : Set.of();
        for (Cell cell : schematic.air()) {
            BuildRotation.Cell rotated = rotation.rotate(cell.dx(), cell.dz(), schematic.sizeX(), schematic.sizeZ());
            int dy = groundColumns.contains(key(rotated.x(), rotated.z())) ? cell.dy() - 1 : cell.dy();
            cells.add(new BlockCoord(origin.x() + rotated.x(), origin.y() + dy, origin.z() + rotated.z()));
        }
        if (grounded) {
            Set<BlockCoord> occupied = new HashSet<>();
            for (PlacedEntry entry : entries(schematic, origin, rotation)) {
                occupied.add(entry.pos());
            }
            for (Entry entry : schematic.blocks()) {
                if (entry.dy() != 0 || !SchematicGround.inGround(entry.block())) {
                    continue;
                }
                BuildRotation.Cell rotated =
                        rotation.rotate(entry.dx(), entry.dz(), schematic.sizeX(), schematic.sizeZ());
                BlockCoord cell = new BlockCoord(origin.x() + rotated.x(), origin.y(), origin.z() + rotated.z());
                if (!occupied.contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        return List.copyOf(cells);
    }

    static List<PlacedEntry> entries(Schematic schematic, BlockCoord origin) {
        return entries(schematic, origin, BuildRotation.NONE);
    }

    static List<PlacedEntry> entries(Schematic schematic, BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, куда ставить");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него блок встанет не туда");
        List<PlacedEntry> placed = new ArrayList<>(schematic.blocks().size());
        boolean grounded = SchematicGround.groundLayer(schematic);
        Set<Long> groundColumns = grounded ? groundColumns(schematic, rotation) : Set.of();
        for (Entry entry : schematic.blocks()) {
            BuildRotation.Cell cell = rotation.rotate(entry.dx(), entry.dz(), schematic.sizeX(), schematic.sizeZ());
            int dy = groundColumns.contains(key(cell.x(), cell.z())) ? entry.dy() - 1 : entry.dy();
            placed.add(new PlacedEntry(
                    new BlockCoord(origin.x() + cell.x(), origin.y() + dy, origin.z() + cell.z()),
                    entry.block(),
                    entry.state(),
                    entry.text(),
                    entry.optional()));
        }
        placed.sort(Comparator.comparingInt((PlacedEntry e) -> e.pos().y())
                .thenComparingInt(e -> placementRank(e.block()))
                .thenComparingInt(e -> e.pos().x())
                .thenComparingInt(e -> e.pos().z()));
        return List.copyOf(placed);
    }

    static Set<Long> groundColumns(Schematic schematic, BuildRotation rotation) {
        Set<Long> columns = new HashSet<>();
        for (Entry entry : schematic.blocks()) {
            if (entry.dy() != 0 || !SchematicGround.inGround(entry.block())) {
                continue;
            }
            BuildRotation.Cell cell = rotation.rotate(entry.dx(), entry.dz(), schematic.sizeX(), schematic.sizeZ());
            columns.add(key(cell.x(), cell.z()));
        }
        return columns;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    static int placementRank(String block) {
        String path = block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
        if (path.endsWith("_sign")) {
            return 2;
        }
        return "chest".equals(path) || "trapped_chest".equals(path) || path.endsWith("_chest") ? 1 : 0;
    }
}
