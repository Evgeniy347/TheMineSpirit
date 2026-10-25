package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.WarehouseCategories;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

final class ContainerCategories {
    private static final int HORIZONTAL_REACH = 2;
    private static final int VERTICAL_REACH = 5;

    private ContainerCategories() {}

    static String of(Level level, BlockPos container, List<Stack> slots, WarehouseCategories categories) {
        String sign = near(level, container, categories);
        if (!sign.isEmpty()) {
            return sign;
        }
        return categories.categoryOfSlots(slots);
    }

    static String near(Level level, BlockPos container, WarehouseCategories categories) {
        Objects.requireNonNull(level, "Уровень не может быть null: без него табличку не найти");
        Objects.requireNonNull(container, "Позиция контейнера не может быть null: от неё ищут табличку");
        Objects.requireNonNull(categories, "Категории не могут быть null: по ним читается надпись");
        String best = "";
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -HORIZONTAL_REACH; dx <= HORIZONTAL_REACH; dx++) {
            for (int dz = -HORIZONTAL_REACH; dz <= HORIZONTAL_REACH; dz++) {
                for (int dy = 0; dy <= VERTICAL_REACH; dy++) {
                    BlockPos pos = container.offset(dx, dy, dz);
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (!(entity instanceof SignBlockEntity sign)) {
                        continue;
                    }
                    String text = sign.getFrontText().getMessage(0, false).getString();
                    if (!categories.known(text)) {
                        continue;
                    }
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = text;
                    }
                }
            }
        }
        return best;
    }
}
