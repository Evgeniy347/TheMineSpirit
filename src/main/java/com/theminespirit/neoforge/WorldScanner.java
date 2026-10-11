package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ContainerRef;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorldScanner {
    private WorldScanner() {
    }

    public static WorldObservation observe(Level level, String botId, BlockCoord center, ScanRadius radius,
            Container inventory) {
        int r = radius.value();
        List<ItemCount> blocks = new ArrayList<>();
        List<ContainerRef> containers = new ArrayList<>();
        Map<String, Integer> blockCounts = new LinkedHashMap<>();

        net.minecraft.core.BlockPos min = new net.minecraft.core.BlockPos(center.x() - r, center.y() - r, center.z() - r);
        net.minecraft.core.BlockPos max = new net.minecraft.core.BlockPos(center.x() + r, center.y() + r, center.z() + r);
        net.minecraft.core.BlockPos.betweenClosed(min, max).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                blockCounts.merge(key, 1, Integer::sum);
            }
            if (level.getBlockEntity(pos) instanceof Container) {
                containers.add(new ContainerRef(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(), toCore(pos)));
            }
        });
        blockCounts.forEach((item, count) -> blocks.add(new ItemCount(item, count)));

        Vec3 centerPos = new Vec3(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5);
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        List<EntityRef> entities = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds, e -> !e.isRemoved())) {
            String type = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
            entities.add(new EntityRef(type, toCore(entity.blockPosition()),
                    entity.position().distanceTo(centerPos)));
        }

        return new WorldObservation(botId, level.getGameTime(), center, inventoryEntries(inventory), blocks,
                entities, containers, r);
    }

    private static List<ItemCount> inventoryEntries(Container inventory) {
        List<ItemCount> entries = new ArrayList<>();
        if (inventory == null) {
            return entries;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                entries.add(new ItemCount(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        return entries;
    }

    private static BlockCoord toCore(net.minecraft.core.BlockPos pos) {
        return new com.theminespirit.core.observation.BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}