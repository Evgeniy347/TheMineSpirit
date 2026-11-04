package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class NavigationChunkRequests {
    public static final int WINDOW_TICKS = 20;

    public record Chunk(int x, int z) {
        public long key() {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }
    }

    private final int perTrip;
    private final int perSecond;
    private long window = Long.MIN_VALUE;
    private int used;

    public NavigationChunkRequests(int perTrip, int perSecond) {
        if (perTrip < 1) {
            throw new IllegalArgumentException(
                    "Бюджет чанков на поход " + perTrip + " не положителен: без него путь через лес не собрать");
        }
        if (perSecond < 1) {
            throw new IllegalArgumentException(
                    "Бюджет чанков в секунду " + perSecond + " не положителен: иначе загрузка встанет навсегда");
        }
        this.perTrip = perTrip;
        this.perSecond = perSecond;
    }

    public int perTrip() {
        return perTrip;
    }

    public int perSecond() {
        return perSecond;
    }

    public List<Chunk> next(long tick, List<Chunk> missing, int held) {
        Objects.requireNonNull(missing, "Список незагруженных чанков не может быть null: пусто — грузить нечего");
        if (tick < 0) {
            throw new IllegalArgumentException("Тик " + tick + " отрицателен: время сервера не идёт назад");
        }
        long bucket = tick / WINDOW_TICKS;
        if (bucket != window) {
            window = bucket;
            used = 0;
        }
        int leftTrip = Math.max(0, perTrip - held);
        int leftSecond = Math.max(0, perSecond - used);
        int take = Math.min(leftTrip, leftSecond);
        List<Chunk> chosen = new ArrayList<>(Math.min(take, missing.size()));
        Set<Long> seen = new LinkedHashSet<>();
        for (Chunk chunk : missing) {
            if (chosen.size() >= take) {
                break;
            }
            if (seen.add(chunk.key())) {
                chosen.add(chunk);
            }
        }
        used += chosen.size();
        return List.copyOf(chosen);
    }

    public static List<Chunk> corridor(BlockCoord from, BlockCoord target, int radius) {
        Objects.requireNonNull(from, "Старт маршрута не может быть null: от него строится коридор");
        Objects.requireNonNull(target, "Цель маршрута не может быть null: до неё строится коридор");
        Set<Long> seen = new LinkedHashSet<>();
        List<Chunk> chunks = new ArrayList<>();
        int x0 = from.x() >> 4;
        int z0 = from.z() >> 4;
        int x1 = target.x() >> 4;
        int z1 = target.z() >> 4;
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int error = dx - dz;
        int x = x0;
        int z = z0;
        while (true) {
            add(chunks, seen, x, z);
            if (x == x1 && z == z1) {
                break;
            }
            int twice = 2 * error;
            if (twice > -dz) {
                error -= dz;
                x += sx;
            }
            if (twice < dx) {
                error += dx;
                z += sz;
            }
        }
        int pad = Math.max(0, radius);
        for (int cx = x1 - pad; cx <= x1 + pad; cx++) {
            for (int cz = z1 - pad; cz <= z1 + pad; cz++) {
                add(chunks, seen, cx, cz);
            }
        }
        return List.copyOf(chunks);
    }

    private static void add(List<Chunk> chunks, Set<Long> seen, int x, int z) {
        Chunk chunk = new Chunk(x, z);
        if (seen.add(chunk.key())) {
            chunks.add(chunk);
        }
    }
}
