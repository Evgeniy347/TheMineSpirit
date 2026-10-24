package com.theminespirit.core.scene;

import com.theminespirit.core.board.SettlementLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Генератор неплоской сцены этапа 1 по сиду — тот же сид даёт ту же сцену на любой машине. Вокруг центра участка
 * поселения: холмы до {@link #RELIEF} над базой, низина до {@link #RELIEF} под базой у края участка с прудом на дне,
 * пещера под участком, деревья одной породы (джунгли или дуб) на участке и за ним, остатки старых построек
 * поселения на участке (ограда, стенка, подсыпка) — их блоки помечены {@code settlement}.
 */
public final class SceneGenerator {
    public static final int RADIUS = 30;
    public static final int RELIEF = 4;
    public static final int BOTTOM = -7;
    public static final int SPAWN_FREE = 3;
    public static final List<String> WOODS = List.of("jungle", "oak");
    private static final int PLOT = SettlementLayout.HALF;
    private static final int TREE_REACH = RADIUS - 4;
    private static final int MIN_TREES_ON_PLOT = 4;
    private static final int MIN_TREES_OUTSIDE = 8;
    private static final int CAVE_TOP = -4;
    private static final int CAVE_BOTTOM = BOTTOM + 1;
    private static final int POND_RADIUS = 2;

    private SceneGenerator() {}

    public static ScenePlan generate(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        String wood = WOODS.get(random.nextInt(WOODS.size()));
        int[] relief = relief(random);
        List<Integer> heights = Arrays.stream(relief).boxed().toList();
        ScenePlan terrain = new ScenePlan(seed, wood, RADIUS, BOTTOM, heights, List.of());
        List<SceneBlock> blocks = new ArrayList<>();
        cave(random, terrain, blocks);
        int[] low = lowland(relief);
        pond(terrain, low, blocks);
        Set<Long> taken = new HashSet<>();
        trees(random, terrain, wood, taken, blocks);
        ruins(random, terrain, wood, taken, blocks);
        return new ScenePlan(seed, wood, RADIUS, BOTTOM, heights, blocks);
    }

    private static int[] relief(SplittableRandom random) {
        int side = 2 * RADIUS + 1;
        double[] raw = new double[side * side];
        int edge = random.nextInt(4);
        int along = random.nextInt(-PLOT + 4, PLOT - 3);
        int across = PLOT + random.nextInt(4);
        int lx = switch (edge) {
            case 0 -> along;
            case 1 -> across;
            case 2 -> along;
            default -> -across;
        };
        int lz = switch (edge) {
            case 0 -> -across;
            case 1 -> along;
            case 2 -> across;
            default -> along;
        };
        double lowRadius = 7 + random.nextInt(3);
        int hills = 3 + random.nextInt(2);
        for (int hill = 0; hill < hills; hill++) {
            int reach = hill == 0 ? PLOT - 3 : RADIUS - 6;
            int cx = random.nextInt(-reach, reach + 1);
            int cz = random.nextInt(-reach, reach + 1);
            double radius = 6 + random.nextInt(5);
            while (hill == 0 && Math.hypot(cx - lx, cz - lz) < lowRadius + 2) {
                cx = random.nextInt(-reach, reach + 1);
                cz = random.nextInt(-reach, reach + 1);
            }
            double amplitude = hill == 0 ? RELIEF + 1 : 2 + random.nextInt(3);
            bump(raw, cx, cz, radius, amplitude);
        }
        int[] heights = new int[side * side];
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int index = (x + RADIUS) * side + z + RADIUS;
                double distance = Math.hypot(x - lx, z - lz);
                double bowl = distance >= lowRadius ? 0 : -(RELIEF + 1) * (1 - distance / lowRadius);
                double value = bowl < 0 ? Math.min(raw[index] * 0.25, 0) + bowl : raw[index];
                heights[index] = (int) Math.max(-RELIEF, Math.min(RELIEF, Math.round(value)));
            }
        }
        return heights;
    }

    private static void bump(double[] raw, int cx, int cz, double radius, double amplitude) {
        int side = 2 * RADIUS + 1;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double distance = Math.hypot(x - cx, z - cz);
                if (distance < radius) {
                    raw[(x + RADIUS) * side + z + RADIUS] +=
                            amplitude * 0.5 * (1 + Math.cos(Math.PI * distance / radius));
                }
            }
        }
    }

    private static int[] lowland(int[] heights) {
        int side = 2 * RADIUS + 1;
        int best = 0;
        for (int index = 1; index < heights.length; index++) {
            if (heights[index] < heights[best]) {
                best = index;
            }
        }
        return new int[] {best / side - RADIUS, best % side - RADIUS};
    }

    private static void cave(SplittableRandom random, ScenePlan terrain, List<SceneBlock> blocks) {
        int cx = random.nextInt(-PLOT + 4, PLOT - 3);
        int cz = random.nextInt(-PLOT + 4, PLOT - 3);
        int halfX = 2 + random.nextInt(3);
        int halfZ = 1 + random.nextInt(3);
        for (int x = cx - halfX; x <= cx + halfX; x++) {
            for (int z = cz - halfZ; z <= cz + halfZ; z++) {
                int roof = Math.min(CAVE_TOP, terrain.height(x, z) - 2);
                for (int y = CAVE_BOTTOM; y <= roof; y++) {
                    blocks.add(SceneBlock.natural(x, y, z, "air"));
                }
            }
        }
    }

    private static void pond(ScenePlan terrain, int[] low, List<SceneBlock> blocks) {
        int level = terrain.height(low[0], low[1]) + 1;
        for (int x = low[0] - POND_RADIUS; x <= low[0] + POND_RADIUS; x++) {
            for (int z = low[1] - POND_RADIUS; z <= low[1] + POND_RADIUS; z++) {
                if (Math.abs(x) > RADIUS || Math.abs(z) > RADIUS || terrain.height(x, z) >= level) {
                    continue;
                }
                blocks.add(SceneBlock.natural(x, terrain.height(x, z), z, "sand"));
                blocks.add(SceneBlock.natural(x, level, z, "water"));
            }
        }
        blocks.add(SceneBlock.natural(low[0], level - 1, low[1], "water"));
    }

    private static void trees(
            SplittableRandom random, ScenePlan terrain, String wood, Set<Long> taken, List<SceneBlock> blocks) {
        int onPlot = 0;
        int outside = 0;
        int attempts = 0;
        int wanted = MIN_TREES_ON_PLOT + MIN_TREES_OUTSIDE + random.nextInt(6);
        while (onPlot + outside < wanted || onPlot < MIN_TREES_ON_PLOT || outside < MIN_TREES_OUTSIDE) {
            if (++attempts > 2000) {
                throw new IllegalStateException("Сид " + terrain.seed() + ": деревья сцены не разместились");
            }
            boolean plot = onPlot < MIN_TREES_ON_PLOT;
            int reach = plot ? PLOT - 1 : TREE_REACH;
            int x = random.nextInt(-reach, reach + 1);
            int z = random.nextInt(-reach, reach + 1);
            boolean inside = Math.abs(x) <= PLOT && Math.abs(z) <= PLOT;
            if (!free(terrain, taken, x, z, 2) || (plot && !inside) || dry(terrain, blocks, x, z)) {
                continue;
            }
            tree(terrain, wood, x, z, 5 + random.nextInt(3), blocks);
            claim(taken, x, z, 2);
            if (inside) {
                onPlot++;
            } else {
                outside++;
            }
        }
    }

    private static boolean dry(ScenePlan terrain, List<SceneBlock> blocks, int x, int z) {
        int top = terrain.height(x, z) + 1;
        return blocks.stream().anyMatch(b -> b.x() == x && b.z() == z && b.y() == top && "water".equals(b.block()));
    }

    private static void tree(ScenePlan terrain, String wood, int x, int z, int trunk, List<SceneBlock> blocks) {
        int ground = terrain.height(x, z);
        for (int y = 1; y <= trunk; y++) {
            blocks.add(SceneBlock.natural(x, ground + y, z, wood + "_log"));
        }
        String leaves = wood + "_leaves";
        int top = ground + trunk;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    blocks.add(SceneBlock.natural(x + dx, top - 1, z + dz, leaves));
                    if (dx == 0 || dz == 0) {
                        blocks.add(SceneBlock.natural(x + dx, top, z + dz, leaves));
                    }
                }
            }
        }
        blocks.add(SceneBlock.natural(x, top + 1, z, leaves));
    }

    private static void ruins(
            SplittableRandom random, ScenePlan terrain, String wood, Set<Long> taken, List<SceneBlock> blocks) {
        int placed = 0;
        int attempts = 0;
        while (placed < 3) {
            if (++attempts > 2000) {
                throw new IllegalStateException("Сид " + terrain.seed() + ": старые постройки не разместились");
            }
            boolean alongX = random.nextBoolean();
            int length = placed == 0 ? 5 + random.nextInt(5) : 2 + random.nextInt(2);
            int x0 = random.nextInt(-PLOT + 1, PLOT - length);
            int z0 = random.nextInt(-PLOT + 1, PLOT - length);
            List<int[]> cells = new ArrayList<>();
            for (int step = 0; step < length; step++) {
                cells.add(alongX ? new int[] {x0 + step, z0} : new int[] {x0, z0 + step});
            }
            if (!cells.stream().allMatch(cell -> free(terrain, taken, cell[0], cell[1], 1))) {
                continue;
            }
            for (int[] cell : cells) {
                ruin(terrain, wood, placed, cell, blocks);
                claim(taken, cell[0], cell[1], 1);
            }
            placed++;
        }
    }

    private static void ruin(ScenePlan terrain, String wood, int kind, int[] cell, List<SceneBlock> blocks) {
        int x = cell[0];
        int z = cell[1];
        int ground = terrain.height(x, z);
        switch (kind) {
            case 0 -> blocks.add(SceneBlock.settlement(x, ground + 1, z, wood + "_fence"));
            case 1 -> {
                blocks.add(SceneBlock.settlement(x, ground + 1, z, "cobblestone"));
                blocks.add(SceneBlock.settlement(x, ground + 2, z, wood + "_planks"));
            }
            default -> blocks.add(SceneBlock.settlement(x, ground + 1, z, "dirt"));
        }
    }

    private static boolean free(ScenePlan terrain, Set<Long> taken, int x, int z, int gap) {
        if (Math.abs(x) <= SPAWN_FREE && Math.abs(z) <= SPAWN_FREE) {
            return false;
        }
        if (Math.abs(x) > RADIUS - 1 || Math.abs(z) > RADIUS - 1) {
            return false;
        }
        for (int dx = -gap; dx <= gap; dx++) {
            for (int dz = -gap; dz <= gap; dz++) {
                if (taken.contains(key(x + dx, z + dz))) {
                    return false;
                }
            }
        }
        return terrain.height(x, z) > -RELIEF;
    }

    private static void claim(Set<Long> taken, int x, int z, int gap) {
        for (int dx = -gap; dx <= gap; dx++) {
            for (int dz = -gap; dz <= gap; dz++) {
                taken.add(key(x + dx, z + dz));
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
