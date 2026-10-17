package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class MapFarmView implements FarmView {
    private static final FarmTile DEFAULT = new FarmTile(Ground.OTHER, Above.BLOCKED, Optional.empty());

    private final Map<BlockCoord, FarmTile> tiles = new HashMap<>();
    private final Map<BlockCoord, Integer> heights = new HashMap<>();
    private final Map<BlockCoord, BlockCoord> fruits = new HashMap<>();
    private final Map<BlockCoord, BlockCoord> attachedCrops = new HashMap<>();
    private final Map<BlockCoord, BlockCoord> attachedSpots = new HashMap<>();
    private int reads;

    MapFarmView set(int x, int y, int z, FarmTile tile) {
        tiles.put(new BlockCoord(x, y, z), tile);
        return this;
    }

    MapFarmView fillBed(int y, int x1, int z1, int x2, int z2, FarmTile tile) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                tiles.put(new BlockCoord(x, y, z), tile);
            }
        }
        return this;
    }

    FarmTile peek(BlockCoord coord) {
        return tiles.getOrDefault(coord, DEFAULT);
    }

    int reads() {
        return reads;
    }

    MapFarmView height(int x, int y, int z, int value) {
        heights.put(new BlockCoord(x, y, z), value);
        return this;
    }

    @Override
    public FarmTile tileAt(BlockCoord coord) {
        reads++;
        return tiles.getOrDefault(coord, DEFAULT);
    }

    @Override
    public int columnHeight(BlockCoord ground) {
        reads++;
        return heights.getOrDefault(ground, 1);
    }

    MapFarmView fruit(BlockCoord ground, BlockCoord fruit) {
        fruits.put(ground, fruit);
        return this;
    }

    @Override
    public Optional<BlockCoord> fruitAt(BlockCoord ground, CropKind kind) {
        reads++;
        return Optional.ofNullable(fruits.get(ground));
    }

    @Override
    public boolean hasFruitSpace(BlockCoord ground) {
        reads++;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) {
                    continue;
                }
                FarmTile neighbor = peek(new BlockCoord(ground.x() + dx, ground.y(), ground.z() + dz));
                boolean soil = neighbor.ground() == Ground.DIRT || neighbor.ground() == Ground.GRASS
                        || neighbor.ground() == Ground.FARMLAND;
                if (soil && neighbor.above() == Above.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    MapFarmView attachedCrop(BlockCoord support, BlockCoord crop) {
        attachedCrops.put(support, crop);
        return this;
    }

    @Override
    public Optional<BlockCoord> attachedCrop(BlockCoord support, CropKind kind) {
        reads++;
        return Optional.ofNullable(attachedCrops.get(support));
    }

    MapFarmView attachedSpot(BlockCoord support, BlockCoord spot) {
        attachedSpots.put(support, spot);
        return this;
    }

    @Override
    public Optional<BlockCoord> attachedSpot(BlockCoord support, CropKind kind) {
        reads++;
        return Optional.ofNullable(attachedSpots.get(support));
    }
}
