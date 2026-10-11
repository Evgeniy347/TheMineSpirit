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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorldScanner {
    private WorldScanner() {
    }

    public record Scan(WorldObservation observation, int scannedPositions) {
    }

    public static Scan observe(Level level, String botId, BlockCoord center, ScanRadius radius,
            Container inventory) {
        int r = radius.value();
        int minX = center.x() - r;
        int maxX = center.x() + r;
        int minY = Math.max(center.y() - r, level.getMinBuildHeight());
        int maxY = Math.min(center.y() + r, level.getMaxBuildHeight() - 1);
        int minZ = center.z() - r;
        int maxZ = center.z() + r;

        List<ItemCount> blocks = new ArrayList<>();
        List<ContainerRef> containers = new ArrayList<>();
        Map<String, Integer> blockCounts = new LinkedHashMap<>();
        int scannedPositions = 0;

        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                if (!(level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    continue;
                }
                int scanMinX = Math.max(chunkX * 16, minX);
                int scanMaxX = Math.min(chunkX * 16 + 15, maxX);
                int scanMinZ = Math.max(chunkZ * 16, minZ);
                int scanMaxZ = Math.min(chunkZ * 16 + 15, maxZ);
                for (int x = scanMinX; x <= scanMaxX; x++) {
                    for (int z = scanMinZ; z <= scanMaxZ; z++) {
                        // Поблочный обход идёт напрямую по секциям: LevelChunkSection читает state по локальным
                        // координатам без выделения BlockPos на каждую позицию скана (до 275 тыс. позиций).
                        LevelChunkSection[] sections = chunk.getSections();
                        for (int y = minY; y <= maxY; y++) {
                            scannedPositions++;
                            LevelChunkSection section = sections[chunk.getSectionIndex(y)];
                            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                            if (!state.isAir()) {
                                String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                                blockCounts.merge(key, 1, Integer::sum);
                            }
                        }
                    }
                }
                scanContainers(chunk, minX, maxX, minY, maxY, minZ, maxZ, containers);
            }
        }
        blockCounts.forEach((item, count) -> blocks.add(new ItemCount(item, count)));

        Vec3 centerPos = new Vec3(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5);
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        List<EntityRef> entities = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds, e -> !e.isRemoved())) {
            String type = EntityType.getKey(entity.getType()).toString();
            entities.add(new EntityRef(type, toCore(entity.blockPosition()),
                    entity.position().distanceTo(centerPos)));
        }

        WorldObservation observation = new WorldObservation(botId, level.getGameTime(), center,
                inventoryEntries(inventory), blocks, entities, containers, r);
        return new Scan(observation, scannedPositions);
    }

    private static void scanContainers(LevelChunk chunk, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            List<ContainerRef> containers) {
        // Контейнеры берутся из уже загруженных блок-сущностей чанка, а не из второй проверки
        // level.getBlockState(pos): спектр этого же чанка отсчитывается без повторного запроса.
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                    || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }
            if (entry.getValue() instanceof Container container) {
                containers.add(new ContainerRef(
                        BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlockState().getBlock()).toString(),
                        toCore(pos)));
            }
        }
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

    private static BlockCoord toCore(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}