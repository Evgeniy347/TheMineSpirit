package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ChopStands {
    private static final int STAND_HEIGHT = 2;

    public interface View {
        boolean standable(BlockCoord feet);
    }

    private ChopStands() {}

    public static List<BlockCoord> around(Tree tree, View view) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, где стоянка у ствола");
        Objects.requireNonNull(view, "Взгляд на мир не может быть null: по нему проверяется стоянка");
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        int baseY = tree.base().y();
        List<BlockCoord> stands = new ArrayList<>();
        for (BlockCoord log : tree.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = 0; dy < STAND_HEIGHT; dy++) {
                        BlockCoord feet = new BlockCoord(log.x() + dx, baseY + dy, log.z() + dz);
                        BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
                        if (logs.contains(feet) || logs.contains(below) || !view.standable(feet)) {
                            continue;
                        }
                        stands.add(feet);
                    }
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(stands));
    }
}
