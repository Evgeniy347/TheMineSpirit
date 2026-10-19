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
    private static final char PLANKS = 'P';

    private final Map<BlockCoord, Character> cells = new HashMap<>();
    private final Set<BlockCoord> logCoords = new HashSet<>();
    private int logReads;
    private int leavesReads;
    private int soilReads;
    private int artificialReads;

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

    TreeGrid planks(int x, int y, int z) {
        put(new BlockCoord(x, y, z), PLANKS);
        return this;
    }

    TreeGrid air(int x, int y, int z) {
        put(new BlockCoord(x, y, z), AIR);
        return this;
    }

    Set<BlockCoord> logs() {
        return Set.copyOf(logCoords);
    }

    int logReads() {
        return logReads;
    }

    int leavesReads() {
        return leavesReads;
    }

    int soilReads() {
        return soilReads;
    }

    int artificialReads() {
        return artificialReads;
    }

    @Override
    public boolean isLog(BlockCoord coord) {
        logReads++;
        return cell(coord) == LOG;
    }

    @Override
    public boolean isNaturalLeaves(BlockCoord coord) {
        leavesReads++;
        return cell(coord) == LEAVES;
    }

    @Override
    public boolean isSoil(BlockCoord coord) {
        soilReads++;
        return cell(coord) == SOIL;
    }

    @Override
    public boolean isArtificial(BlockCoord coord) {
        artificialReads++;
        return cell(coord) == PLANKS;
    }

    @Override
    public boolean isEmpty(BlockCoord coord) {
        return cell(coord) == AIR;
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
