package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SchematicBody(int minX, int maxX, int minZ, int maxZ) {
    public record Normal(int dx, int dz) {}

    public static SchematicBody of(Schematic schematic, BlockCoord entry) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё тело не найти");
        Objects.requireNonNull(entry, "Вход не может быть null: тело меряется по слою порога");
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Schematic.Entry block : schematic.blocks()) {
            if (block.dy() != entry.y()) {
                continue;
            }
            minX = Math.min(minX, block.dx());
            maxX = Math.max(maxX, block.dx());
            minZ = Math.min(minZ, block.dz());
            maxZ = Math.max(maxZ, block.dz());
        }
        minX = Math.min(minX, entry.x());
        maxX = Math.max(maxX, entry.x());
        minZ = Math.min(minZ, entry.z());
        maxZ = Math.max(maxZ, entry.z());
        return new SchematicBody(minX, maxX, minZ, maxZ);
    }

    public boolean covers(Schematic schematic) {
        return minX == 0 && minZ == 0 && maxX == schematic.sizeX() - 1 && maxZ == schematic.sizeZ() - 1;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public BlockCoord local(BlockCoord cell) {
        return new BlockCoord(cell.x() - minX, cell.y(), cell.z() - minZ);
    }

    public Schematic crop(Schematic schematic) {
        List<Schematic.Entry> blocks = new ArrayList<>();
        for (Schematic.Entry block : schematic.blocks()) {
            if (contains(block.dx(), block.dz())) {
                blocks.add(new Schematic.Entry(
                        block.dx() - minX, block.dy(), block.dz() - minZ, block.block(), block.state(), block.text()));
            }
        }
        List<Schematic.Cell> air = new ArrayList<>();
        for (Schematic.Cell cell : schematic.air()) {
            if (contains(cell.dx(), cell.dz())) {
                air.add(new Schematic.Cell(cell.dx() - minX, cell.dy(), cell.dz() - minZ));
            }
        }
        return new Schematic(sizeX(), schematic.sizeY(), sizeZ(), blocks, air);
    }

    public Optional<Normal> outwardNormal(BlockCoord entry) {
        if (entry.z() == minZ) {
            return Optional.of(new Normal(0, -1));
        }
        if (entry.z() == maxZ) {
            return Optional.of(new Normal(0, 1));
        }
        if (entry.x() == minX) {
            return Optional.of(new Normal(-1, 0));
        }
        if (entry.x() == maxX) {
            return Optional.of(new Normal(1, 0));
        }
        return Optional.empty();
    }
}
