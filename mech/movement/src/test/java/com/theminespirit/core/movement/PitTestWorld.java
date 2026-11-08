package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

final class PitTestWorld implements PitExitPlanner.World {
    static final BlockCoord FEET = new BlockCoord(0, 64, 0);
    static final BlockCoord LIVE_FEET = new BlockCoord(0, 0, 0);

    private final Map<BlockCoord, PitExitPlanner.Kind> cells = new HashMap<>();

    private PitTestWorld() {}

    static PitTestWorld flat() {
        PitTestWorld world = new PitTestWorld();
        world.surface(FEET.y());
        return world;
    }

    static PitTestWorld pit(int depth, int half, PitExitPlanner.Kind wall) {
        PitTestWorld world = new PitTestWorld();
        int surface = FEET.y() + depth;
        world.surface(surface);
        world.shaft(half, FEET.y(), surface - 1);
        world.walls(half + 1, FEET.y(), surface - 1, wall);
        return world;
    }

    static PitTestWorld indoorPit() {
        PitTestWorld world = pit(5, 0, PitExitPlanner.Kind.SOFT);
        int floor = FEET.y() + 5;
        world.surface(floor);
        world.roof(floor + 3);
        return world;
    }

    static PitTestWorld live() throws IOException {
        List<String> lines;
        try (InputStream stream = PitTestWorld.class.getResourceAsStream("/live/pit-warehouse-0611.live")) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры ямы: /live/pit-warehouse-0611.live");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                lines = reader.lines().toList();
            }
        }
        PitTestWorld world = new PitTestWorld();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                continue;
            }
            StringTokenizer parts = new StringTokenizer(line);
            if (parts.countTokens() != 4) {
                throw new IllegalArgumentException("Строка живой фикстуры ямы не из четырёх полей: " + line);
            }
            world.set(
                    new BlockCoord(
                            Integer.parseInt(parts.nextToken()),
                            Integer.parseInt(parts.nextToken()),
                            Integer.parseInt(parts.nextToken())),
                    PitExitPlanner.Kind.valueOf(parts.nextToken()));
        }
        return world;
    }

    static Set<BlockCoord> without(Set<BlockCoord> cells, BlockCoord skip) {
        Set<BlockCoord> copy = new HashSet<>(cells);
        copy.remove(skip);
        return copy;
    }

    static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    void set(BlockCoord cell, PitExitPlanner.Kind kind) {
        cells.put(cell, kind);
    }

    private void surface(int y) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int above = y; above <= y + 6; above++) {
                    set(new BlockCoord(x, above, z), PitExitPlanner.Kind.AIR);
                }
            }
        }
    }

    private void shaft(int half, int bottom, int top) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = bottom; y <= top; y++) {
                    set(new BlockCoord(x, y, z), PitExitPlanner.Kind.AIR);
                }
            }
        }
    }

    private void walls(int half, int bottom, int top, PitExitPlanner.Kind wall) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = bottom; y <= top; y++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (at(cell) == PitExitPlanner.Kind.HARD) {
                        set(cell, wall);
                    }
                }
            }
        }
    }

    private void roof(int y) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int above = y; above <= y + 3; above++) {
                    set(new BlockCoord(x, above, z), PitExitPlanner.Kind.HARD);
                }
            }
        }
    }

    @Override
    public PitExitPlanner.Kind at(BlockCoord cell) {
        return cells.getOrDefault(cell, PitExitPlanner.Kind.HARD);
    }
}
