package com.theminespirit.core.route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SnapshotWorld implements RouteWorld {
    private static final int HAND_FACTOR = 30;
    private static final int WRONG_TOOL_FACTOR = 100;
    private static final Set<String> OPEN = Set.of(
            "air",
            "cave_air",
            "short_grass",
            "tall_grass",
            "fern",
            "dandelion",
            "poppy",
            "oak_sapling",
            "torch",
            "wall_torch");
    private static final Map<String, Double> SOFT = Map.of(
            "dirt", 0.5,
            "grass_block", 0.6,
            "gravel", 0.6,
            "sand", 0.5,
            "oak_leaves", 0.2,
            "oak_log", 2.0);
    private static final Set<String> SETTLEMENT = Set.of(
            "jungle_planks",
            "jungle_stairs",
            "jungle_fence",
            "jungle_fence_gate",
            "crafting_table",
            "chest",
            "furnace");

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] cells;
    private int reads;

    private SnapshotWorld(int[] origin, int[] size, int[] cells) {
        this.originX = origin[0];
        this.originY = origin[1];
        this.originZ = origin[2];
        this.sizeX = size[0];
        this.sizeY = size[1];
        this.sizeZ = size[2];
        this.cells = cells;
    }

    static SnapshotWorld load(String resource) {
        String text;
        try (InputStream stream = SnapshotWorld.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет ресурса снимка " + resource + ": проверьте src/test/resources");
            }
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Снимок " + resource + " не прочитан: " + error.getMessage(), error);
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.lines().toList()) {
            if (!line.isBlank() && !line.startsWith("#")) {
                lines.add(line.trim());
            }
        }
        String[] head = lines.get(0).split(" ", -1);
        int[] origin = {Integer.parseInt(head[1]), Integer.parseInt(head[2]), Integer.parseInt(head[3])};
        int[] size = {Integer.parseInt(head[5]), Integer.parseInt(head[6]), Integer.parseInt(head[7])};
        String[] names = lines.get(1).split(" ", -1);
        int[] palette = new int[names.length - 1];
        for (int index = 1; index < names.length; index++) {
            palette[index - 1] = classify(names[index]);
        }
        int[] cells = new int[size[0] * size[1] * size[2]];
        int at = 0;
        for (String line : lines.subList(2, lines.size())) {
            for (String run : line.split(" ", -1)) {
                int star = run.indexOf('*');
                int value = palette[Integer.parseInt(star < 0 ? run : run.substring(0, star))];
                int count = star < 0 ? 1 : Integer.parseInt(run.substring(star + 1));
                for (int repeat = 0; repeat < count; repeat++) {
                    cells[at++] = value;
                }
            }
        }
        if (at != cells.length) {
            throw new IllegalStateException("Снимок " + resource + ": клеток " + at + " вместо " + cells.length);
        }
        return new SnapshotWorld(origin, size, cells);
    }

    static int classify(String name) {
        String block = name.contains("[") ? name.substring(0, name.indexOf('[')) : name;
        if (OPEN.contains(block)) {
            return RouteCell.AIR;
        }
        if ("water".equals(block)) {
            return RouteCell.WATER;
        }
        if ("lava".equals(block)) {
            return RouteCell.LAVA;
        }
        if ("vine".equals(block) || "ladder".equals(block)) {
            return RouteCell.CLIMB;
        }
        if ("bedrock".equals(block) || SETTLEMENT.contains(block)) {
            return RouteCell.BLOCKED;
        }
        if (block.endsWith("_fence") || block.endsWith("_wall") || block.endsWith("_fence_gate")) {
            return RouteCell.tall(2 * HAND_FACTOR);
        }
        Double soft = SOFT.get(block);
        if (soft != null) {
            return RouteCell.solid((int) Math.ceil(soft * HAND_FACTOR));
        }
        return RouteCell.solid((int) Math.ceil(1.5 * WRONG_TOOL_FACTOR));
    }

    @Override
    public int cell(int x, int y, int z) {
        reads++;
        int dx = x - originX;
        int dy = y - originY;
        int dz = z - originZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) {
            return RouteCell.BLOCKED;
        }
        return cells[(dy * sizeZ + dz) * sizeX + dx];
    }

    int reads() {
        return reads;
    }
}
