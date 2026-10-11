package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TreeGrid implements TreeView {
    private static final char AIR = '.';
    private static final char LOG = 'L';
    private static final char LEAVES = '@';
    private static final char SOIL = '#';

    private final Map<BlockCoord, Character> cells = new HashMap<>();
    private final Set<BlockCoord> logCoords = new HashSet<>();
    private int cellReads;
    private int soilReads;

    static TreeGrid ofLayers(String... layers) {
        TreeGrid grid = new TreeGrid();
        for (int y = 0; y < layers.length; y++) {
            String[] rows = layers[y].split("\n", -1);
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                for (int x = 0; x < row.length(); x++) {
                    grid.put(new BlockCoord(x, y, z), row.charAt(x));
                }
            }
        }
        return grid;
    }

    TreeGrid log(int x, int y, int z) {
        put(new BlockCoord(x, y, z), LOG);
        return this;
    }

    TreeGrid leaves(int x, int y, int z) {
        put(new BlockCoord(x, y, z), LEAVES);
        return this;
    }

    TreeGrid soil(int x, int y, int z) {
        put(new BlockCoord(x, y, z), SOIL);
        return this;
    }

    Set<BlockCoord> logs() {
        return Set.copyOf(logCoords);
    }

    int reads() {
        return cellReads + soilReads;
    }

    @Override
    public boolean isLog(BlockCoord coord) {
        cellReads++;
        return cell(coord) == LOG;
    }

    @Override
    public boolean isNaturalLeaves(BlockCoord coord) {
        cellReads++;
        return cell(coord) == LEAVES;
    }

    @Override
    public boolean isSoil(BlockCoord coord) {
        soilReads++;
        return cell(coord) == SOIL;
    }

    private char cell(BlockCoord coord) {
        Character symbol = cells.get(coord);
        return symbol == null ? AIR : symbol;
    }

    private void put(BlockCoord coord, char symbol) {
        cells.put(coord, symbol);
        if (symbol == LOG) {
            logCoords.add(coord);
        } else {
            logCoords.remove(coord);
        }
    }
}