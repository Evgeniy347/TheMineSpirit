package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MineColumn(BlockCoord floor, int height) {
    public static final int PASSAGE_HEIGHT = 3;
    public static final int MAX_STEP = 1;

    public MineColumn {
        Objects.requireNonNull(floor, "Пол колонки не может быть null: без него неизвестно, где дух стоит");
        if (height < 1) {
            throw new IllegalArgumentException("Высота колонки " + height + " меньше 1: над полом обязан быть воздух");
        }
    }

    public static MineColumn passage(int x, int floorY, int z) {
        return new MineColumn(new BlockCoord(x, floorY, z), PASSAGE_HEIGHT);
    }

    public BlockCoord stand() {
        return new BlockCoord(floor.x(), floor.y() + 1, floor.z());
    }

    public List<BlockCoord> cells() {
        List<BlockCoord> cells = new ArrayList<>(height);
        for (int dy = 1; dy <= height; dy++) {
            cells.add(new BlockCoord(floor.x(), floor.y() + dy, floor.z()));
        }
        return cells;
    }

    public boolean contains(BlockCoord cell) {
        return cell.x() == floor.x() && cell.z() == floor.z() && cell.y() > floor.y() && cell.y() <= floor.y() + height;
    }

    public boolean walksTo(MineColumn other) {
        int dx = Math.abs(other.floor.x() - floor.x());
        int dz = Math.abs(other.floor.z() - floor.z());
        return dx + dz == 1 && Math.abs(other.floor.y() - floor.y()) <= MAX_STEP;
    }

    public MineColumn shifted(int dx, int dz) {
        return new MineColumn(new BlockCoord(floor.x() + dx, floor.y(), floor.z() + dz), PASSAGE_HEIGHT);
    }
}
