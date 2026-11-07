package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

final class LiveNavWorld implements NavWorld {
    private enum Kind {
        AIR,
        SOFT,
        HARD,
        FALLING,
        WATER,
        LAVA,
        BEDROCK
    }

    private final Map<BlockCoord, Kind> cells = new HashMap<>();

    private LiveNavWorld() {}

    static LiveNavWorld live() throws IOException {
        try (InputStream stream = LiveNavWorld.class.getResourceAsStream("/live/pit-warehouse-0611.live")) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры ямы: /live/pit-warehouse-0611.live");
            }
            LiveNavWorld world = new LiveNavWorld();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                    continue;
                }
                StringTokenizer parts = new StringTokenizer(line);
                if (parts.countTokens() != 4) {
                    throw new IllegalArgumentException("Строка живой фикстуры ямы не из четырёх полей: " + line);
                }
                world.cells.put(
                        new BlockCoord(
                                Integer.parseInt(parts.nextToken()),
                                Integer.parseInt(parts.nextToken()),
                                Integer.parseInt(parts.nextToken())),
                        Kind.valueOf(parts.nextToken()));
            }
            return world;
        }
    }

    private Kind kind(int x, int y, int z) {
        return cells.getOrDefault(new BlockCoord(x, y, z), Kind.HARD);
    }

    @Override
    public boolean available(int x, int z) {
        return true;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.AIR || kind == Kind.WATER;
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        return kind == Kind.SOFT || kind == Kind.HARD || kind == Kind.FALLING || kind == Kind.BEDROCK;
    }

    @Override
    public boolean water(int x, int y, int z) {
        return kind(x, y, z) == Kind.WATER;
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return false;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        Kind kind = kind(x, y, z);
        if (kind == Kind.AIR || kind == Kind.WATER) {
            return 0;
        }
        return kind == Kind.BEDROCK ? NavActionCosts.COST_INF : 10;
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
