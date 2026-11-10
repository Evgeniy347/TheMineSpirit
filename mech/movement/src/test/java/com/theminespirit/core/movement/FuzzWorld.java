package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.Optional;

final class FuzzWorld implements RouteWorld {
    static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    static final int SIZE = 10;
    static final int Y_MIN = -7;
    static final int Y_MAX = 9;
    static final int Y_COUNT = Y_MAX - Y_MIN + 1;

    enum Cell {
        AIR("."),
        WATER("~"),
        LAVA("!"),
        DIRT("d"),
        STONE("s"),
        LADDER("h"),
        CHEST("C"),
        DOOR("D"),
        TRAPDOOR("T"),
        BED("B");

        private final String mark;

        Cell(String mark) {
            this.mark = mark;
        }

        boolean blocksSky() {
            return this != AIR && this != LADDER;
        }

        boolean protectedBlock() {
            return this == CHEST || this == DOOR || this == TRAPDOOR || this == BED;
        }

        int route() {
            return switch (this) {
                case AIR -> RouteCell.AIR;
                case WATER -> RouteCell.WATER;
                case LAVA -> RouteCell.LAVA;
                case LADDER -> RouteCell.CLIMB;
                case DIRT -> RouteCell.solid(8);
                case STONE -> RouteCell.solid(20);
                case CHEST, DOOR, TRAPDOOR, BED -> RouteCell.BLOCKED;
            };
        }

        PitExitPlanner.Kind pit() {
            return switch (this) {
                case AIR, LADDER -> PitExitPlanner.Kind.AIR;
                case WATER -> PitExitPlanner.Kind.WATER;
                case LAVA -> PitExitPlanner.Kind.LAVA;
                case DIRT -> PitExitPlanner.Kind.SOFT;
                case STONE -> PitExitPlanner.Kind.HARD;
                case CHEST, DOOR, TRAPDOOR, BED -> PitExitPlanner.Kind.BEDROCK;
            };
        }
    }

    private final Cell[] cells = new Cell[SIZE * SIZE * Y_COUNT];
    private final long seed;
    private final String type;
    private BlockCoord start;
    private boolean impossible;

    private FuzzWorld(long seed, String type, BlockCoord start, boolean impossible) {
        this.seed = seed;
        this.type = type;
        this.start = start;
        this.impossible = impossible;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = Y_MIN; y <= Y_MAX; y++) {
                    set(x, y, z, y <= 0 ? Cell.DIRT : Cell.AIR);
                }
            }
        }
    }

    static FuzzWorld flat() {
        return new FuzzWorld(0, "flat", new BlockCoord(5, 1, 5), false);
    }

    static FuzzWorld generate(long seed) {
        int type = Math.floorMod(seed, 13);
        int variant = Math.floorMod(seed / 13, 8);
        int depth = 1 + Math.floorMod(seed / 13, 6);
        FuzzWorld world = new FuzzWorld(seed, FuzzWorldShapes.name(type), new BlockCoord(5, 1, 5), false);
        switch (type) {
            case 0 -> {
                FuzzWorldShapes.pit(world, 5, 5, depth);
                world.start = new BlockCoord(5, 1 - depth, 5);
            }
            case 1 -> {
                int chestDepth = Math.max(3, depth);
                FuzzWorldShapes.pit(world, 5, 5, chestDepth);
                world.set(5, 1, 5, Cell.CHEST);
                world.start = new BlockCoord(5, 1 - chestDepth, 5);
            }
            case 2 -> {
                FuzzWorldShapes.room(world, 3 + variant % 3, true, variant % 3 == 0);
                world.start = new BlockCoord(5, 1, 5);
            }
            case 3 -> {
                FuzzWorldShapes.room(world, 2 + variant % 3, false, variant % 2 == 0);
                world.start = new BlockCoord(5, 1, 5);
            }
            case 4 -> {
                FuzzWorldShapes.lintelRoom(world, variant);
                world.start = new BlockCoord(5, 1, 5);
            }
            case 5 -> {
                FuzzWorldShapes.slit(world, variant);
                world.start = new BlockCoord(5, 1, 2);
            }
            case 6 -> {
                FuzzWorldShapes.caveMouth(world, variant);
                world.start = new BlockCoord(4, -1, 5);
            }
            case 7 -> {
                int height = 1 + variant % 4;
                FuzzWorldShapes.platform(world, 4, 4, 5, 5, height);
                world.start = new BlockCoord(4, height + 1, 4);
            }
            case 8 -> {
                int steps = 3 + variant % 4;
                FuzzWorldShapes.stairs(world, steps);
                world.start = new BlockCoord(3, 2 - steps, 5);
            }
            case 9 -> {
                int waterDepth = 3 + variant % 3;
                FuzzWorldShapes.waterPit(world, 5, 5, waterDepth);
                world.start = new BlockCoord(5, 1 - waterDepth, 5);
            }
            case 10 -> {
                FuzzWorldShapes.ledge(world, variant);
                world.start = new BlockCoord(5, 3, 5);
            }
            case 11 -> {
                int height = 2 + variant % 4;
                FuzzWorldShapes.platform(world, 5, 5, 5, 5, height);
                world.start = new BlockCoord(5, height + 1, 5);
            }
            default -> {
                if (variant == 7) {
                    FuzzWorldShapes.cage(world);
                    world.start = new BlockCoord(5, 2, 5);
                    world.impossible = true;
                } else {
                    int pit = 4 + variant % 3;
                    FuzzWorldShapes.pit(world, 5, 5, pit);
                    world.set(5, 1, 5, Cell.CHEST);
                    world.set(5, 1 - pit, 5, Cell.WATER);
                    world.set(4, 3, 4, Cell.STONE);
                    world.set(4, 4, 4, Cell.STONE);
                    world.start = new BlockCoord(5, 1 - pit, 5);
                }
            }
        }
        return world;
    }

    BlockCoord start() {
        return start;
    }

    boolean physicallyImpossible() {
        return impossible;
    }

    boolean expectPitEscape() {
        return switch (type) {
            case "pit",
                    "pit-under-chest",
                    "cell-with-roof",
                    "cell-without-roof",
                    "lintel",
                    "slit-height-one",
                    "cave-mouth",
                    "stairs",
                    "water",
                    "mixed" -> true;
            default -> false;
        };
    }

    boolean expectPitWalkOut() {
        return expectPitEscape() && !"water".equals(type);
    }

    int stepBlocks() {
        return 8;
    }

    Cell cellAt(BlockCoord cell) {
        return cellAt(cell.x(), cell.y(), cell.z());
    }

    Cell cellAt(int x, int y, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < Y_MIN) {
            return Cell.DIRT;
        }
        if (y > Y_MAX) {
            return Cell.AIR;
        }
        return cells[index(x, y, z)];
    }

    void set(int x, int y, int z, Cell value) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < Y_MIN || y > Y_MAX) {
            return;
        }
        cells[index(x, y, z)] = value;
    }

    private static int index(int x, int y, int z) {
        return (x * SIZE + z) * Y_COUNT + (y - Y_MIN);
    }

    @Override
    public int cell(int x, int y, int z) {
        return cellAt(x, y, z).route();
    }

    boolean protectedAt(BlockCoord cell) {
        return cellAt(cell).protectedBlock();
    }

    boolean sky(BlockCoord cell) {
        if (cell.y() < 1 || cell.y() > Y_MAX || !openAbove(cell.x(), cell.y(), cell.z())) {
            return false;
        }
        for (int[] side : SIDES) {
            if (height(cell.x() + side[0], cell.z() + side[1]) <= cell.y()) {
                return true;
            }
        }
        return false;
    }

    private boolean openAbove(int x, int y, int z) {
        for (int level = y; level <= Y_MAX; level++) {
            if (cellAt(x, level, z).blocksSky()) {
                return false;
            }
        }
        return true;
    }

    private int height(int x, int z) {
        for (int y = Y_MAX; y >= Y_MIN; y--) {
            if (cellAt(x, y, z).blocksSky()) {
                return y + 1;
            }
        }
        return Y_MIN;
    }

    Optional<BlockCoord> surfaceTarget() {
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                BlockCoord cell = new BlockCoord(x, 1, z);
                if (sky(cell) && BodyMotion.standable(this, cell)) {
                    return Optional.of(cell);
                }
            }
        }
        return Optional.empty();
    }

    String render() {
        StringBuilder map = new StringBuilder();
        map.append("мир seed=")
                .append(seed)
                .append(" тип=")
                .append(type)
                .append(" старт=")
                .append(start);
        for (int y = Y_MAX; y >= Y_MIN; y--) {
            map.append("\nслой y=").append(y).append('\n');
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    map.append(cellAt(x, y, z).mark);
                }
                map.append('\n');
            }
        }
        return map.toString();
    }
}
