package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Вход без прыжка: от земли перед постройкой до порога каждый шаг поднимает не больше чем на полблока (ступени,
 * полублок) — столько игрок и дух проходят шагом. Пол выше земли требует ступеньки перед порогом в крыльце схемы.
 * Высоты считаются в полублоках: земля за габаритом схемы — 0, порог — высота клетки входа.
 */
public final class EntranceStep {
    private static final int HALVES = 2;
    private static final int MAX_RISE = 1;
    private static final int FENCE = 3;

    private EntranceStep() {}

    public static Optional<String> problem(Schematic schematic, SchematicBody body, BlockCoord entry) {
        Optional<SchematicBody.Normal> normal = body.outwardNormal(entry);
        if (normal.isEmpty()) {
            return Optional.empty();
        }
        Map<BlockCoord, Schematic.Entry> grid = new HashMap<>();
        for (Schematic.Entry block : schematic.blocks()) {
            grid.put(new BlockCoord(block.dx(), block.dy(), block.dz()), block);
        }
        Facing outward = Facing.ofVector(normal.get().dx(), normal.get().dz()).orElseThrow();
        int previous = entry.y() * HALVES;
        int x = entry.x();
        int z = entry.z();
        while (true) {
            x += outward.dx();
            z += outward.dz();
            boolean inside = x >= 0 && z >= 0 && x < schematic.sizeX() && z < schematic.sizeZ();
            int surface = inside ? surface(grid, x, entry.y(), z, outward) : 0;
            if (previous - surface > MAX_RISE) {
                return Optional.of("перед порогом входа [" + entry.x() + "," + entry.y() + "," + entry.z()
                        + "] подъём " + (previous - surface) / (double) HALVES + " блока с клетки [" + x + ",*," + z
                        + "]: в постройку приходится прыгать — поставьте ступени или полублок перед порогом");
            }
            if (!inside) {
                return Optional.empty();
            }
            previous = surface;
        }
    }

    private static int surface(Map<BlockCoord, Schematic.Entry> grid, int x, int threshold, int z, Facing outward) {
        for (int y = threshold - 1; y >= 0; y--) {
            Schematic.Entry block = grid.get(new BlockCoord(x, y, z));
            if (block != null) {
                return y * HALVES + height(block, outward);
            }
        }
        return 0;
    }

    private static int height(Schematic.Entry block, Facing outward) {
        String path = SchematicBlocks.path(block.block());
        boolean lower = !"top".equals(block.state().get("half"))
                && !"top".equals(block.state().get("type"));
        if (path.endsWith("_slab") && lower && !"double".equals(block.state().get("type"))) {
            return 1;
        }
        if (path.endsWith("_stairs") && lower) {
            Facing high =
                    Facing.of(block.state().getOrDefault("facing", "north")).orElse(Facing.NORTH);
            return high == outward ? HALVES : 1;
        }
        if (path.endsWith("_fence") || path.endsWith("_wall") || path.endsWith("_fence_gate")) {
            return FENCE;
        }
        return SchematicBlocks.isFullBlock(block.block()) ? HALVES : 0;
    }
}
