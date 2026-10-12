package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;

final class AsciiWorld implements SafetyView {
    private final Map<BlockCoord, Cell> cells = new HashMap<>();
    private final Map<Character, BlockCoord> markers = new HashMap<>();
    private final Cell outside;
    private int reads;

    private AsciiWorld(Cell outside) {
        this.outside = outside;
    }

    static AsciiWorld of(BlockCoord corner, Cell outside, String... layers) {
        AsciiWorld world = new AsciiWorld(outside);
        for (int layer = 0; layer < layers.length; layer++) {
            String[] rows = layers[layer].split("/");
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length(); column++) {
                    BlockCoord at = new BlockCoord(corner.x() + column, corner.y() + layer, corner.z() + row);
                    world.put(at, rows[row].charAt(column));
                }
            }
        }
        return world;
    }

    private void put(BlockCoord at, char symbol) {
        switch (symbol) {
            case '#' -> cells.put(at, Cell.SOLID);
            case 'W' -> cells.put(at, Cell.WATER);
            case 'L' -> cells.put(at, Cell.LAVA);
            case 'F' -> cells.put(at, Cell.FIRE);
            case 'H' -> cells.put(at, Cell.HAZARD);
            case 'S' -> cells.put(at, Cell.FALLING);
            case '.' -> cells.put(at, Cell.PASSABLE);
            default -> {
                cells.put(at, Cell.PASSABLE);
                markers.put(symbol, at);
            }
        }
    }

    BlockCoord marker(char symbol) {
        BlockCoord at = markers.get(symbol);
        if (at == null) {
            throw new IllegalStateException("В картинке нет метки '" + symbol + "': проверьте символы слоёв");
        }
        return at;
    }

    Cell peek(BlockCoord at) {
        return cells.getOrDefault(at, outside);
    }

    int reads() {
        return reads;
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        reads++;
        return peek(coord);
    }
}
