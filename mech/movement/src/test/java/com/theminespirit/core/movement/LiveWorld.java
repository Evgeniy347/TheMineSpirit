package com.theminespirit.core.movement;

import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

final class LiveWorld implements RouteWorld, NavWorld {
    private final Map<BlockCoord, String> cells = new HashMap<>();

    static LiveWorld load(String resource) throws IOException {
        LiveWorld world = new LiveWorld();
        try (InputStream stream = LiveWorld.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                        continue;
                    }
                    StringTokenizer parts = new StringTokenizer(line);
                    if (parts.countTokens() != 4) {
                        throw new IllegalArgumentException("Строка живой фикстуры не из четырёх полей: " + line);
                    }
                    world.cells.put(
                            new BlockCoord(
                                    Integer.parseInt(parts.nextToken()),
                                    Integer.parseInt(parts.nextToken()),
                                    Integer.parseInt(parts.nextToken())),
                            parts.nextToken());
                }
            }
        }
        return world;
    }

    private String kind(int x, int y, int z) {
        return cells.getOrDefault(new BlockCoord(x, y, z), "HARD");
    }

    @Override
    public int cell(int x, int y, int z) {
        return switch (kind(x, y, z)) {
            case "AIR" -> RouteCell.AIR;
            case "WATER" -> RouteCell.WATER;
            case "LAVA" -> RouteCell.LAVA;
            case "SOFT", "FALLING" -> RouteCell.solid(8);
            case "HARD" -> RouteCell.solid(20);
            default -> RouteCell.BLOCKED;
        };
    }

    @Override
    public boolean available(int x, int z) {
        return true;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        String value = kind(x, y, z);
        return "AIR".equals(value) || "WATER".equals(value);
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        String value = kind(x, y, z);
        return "SOFT".equals(value) || "HARD".equals(value) || "FALLING".equals(value) || "BEDROCK".equals(value);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return "WATER".equals(kind(x, y, z));
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return false;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        return "BEDROCK".equals(kind(x, y, z)) ? Double.POSITIVE_INFINITY : 10;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 320;
    }
}
