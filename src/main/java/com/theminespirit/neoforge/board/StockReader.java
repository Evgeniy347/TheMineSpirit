package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Снимок запасов поселения для гейта доски: инвентари духов плюс контейнеры в радиусе
 * чтения. Бюджетирован: за одну пересборку читает не больше {@code containerBudget}
 * контейнеров — иначе снимок разросся бы на мире с десятками сундуков. Идентификаторы
 * предметов приводятся к короткому виду ({@code minecraft:dirt} → {@code dirt}):
 * гейт сравнивает с ключами {@code CraterNeed.estimate} и {@code AnimalLifecycle}.
 */
public final class StockReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONTAINER_RADIUS = 8;
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private final int containerBudget;

    public StockReader(int containerBudget) {
        if (containerBudget <= 0) {
            throw new IllegalArgumentException("containerBudget равен " + containerBudget
                    + " и не положителен: снимок без бюджета читает склад целиком за пересборку");
        }
        this.containerBudget = containerBudget;
    }

    public StockView collect(ServerLevel level, List<SpiritEntity> spirits) {
        Map<String, Integer> available = new HashMap<>();
        for (SpiritEntity spirit : spirits) {
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                add(available, spirit.getInventory().getItem(slot));
            }
        }
        int reads = 0;
        Set<BlockPos> opened = new HashSet<>();
        for (BlockPos pos : containersNear(level, spirits)) {
            if (reads >= containerBudget) {
                break;
            }
            if (!opened.add(pos)) {
                continue;
            }
            ContainerAccess.Open open = ContainerAccess.open(level, pos);
            if (open instanceof ContainerAccess.Opened container) {
                StackMapper mapper = new StackMapper();
                for (Stack stack : mapper.map(container.container())) {
                    if (stack != null) {
                        available.merge(stack.item(), stack.count(), Integer::sum);
                    }
                }
                reads++;
            }
        }
        if (reads >= containerBudget) {
            LOGGER.debug("Снимок запасов обрезан бюджетом {} контейнеров: остальные сундуки "
                    + "дочитаются на следующей пересборке", containerBudget);
        }
        return new StockView(available);
    }

    private List<BlockPos> containersNear(ServerLevel level, List<SpiritEntity> spirits) {
        Set<BlockPos> found = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            AABB area = new AABB(spirit.blockPosition()).inflate(CONTAINER_RADIUS);
            int minChunkX = (int) Math.floor(area.minX) >> 4;
            int maxChunkX = (int) Math.floor(area.maxX) >> 4;
            int minChunkZ = (int) Math.floor(area.minZ) >> 4;
            int maxChunkZ = (int) Math.floor(area.maxZ) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk
                            .getBlockEntities().entrySet()) {
                        BlockPos pos = entry.getKey();
                        if (area.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                                && ContainerAccess.allowedBlocks().contains(entry.getValue().getBlockState()
                                        .getBlock())) {
                            found.add(pos.immutable());
                        }
                    }
                }
            }
        }
        List<BlockPos> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparingLong(pos -> spirits.stream()
                .mapToLong(spirit -> (long) pos.distSqr(spirit.blockPosition())).min().orElse(Long.MAX_VALUE)));
        return ordered;
    }

    private static void add(Map<String, Integer> available, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        available.merge(shortId(StackMapper.itemId(stack)), stack.getCount(), Integer::sum);
    }

    private static String shortId(String itemId) {
        return itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
    }
}
