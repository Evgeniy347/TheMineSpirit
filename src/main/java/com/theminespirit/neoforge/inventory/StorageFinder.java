package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.StoragePoints;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SettlementChestStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class StorageFinder {
    public static final int SEARCH_RADIUS = 16;
    private static final int CANDIDATE_LIMIT = 8;
    private static final Logger LOGGER = LogUtils.getLogger();

    private StorageFinder() {}

    public static Optional<BlockCoord> depositPoint(ServerLevel level, BlockCoord origin, String item, int count) {
        List<StoragePoints.Spot> spots = spots(level, origin);
        StoragePoints.Choice choice =
                StoragePoints.deposit(spots, origin, new Stack(item, 0, Math.min(count, 64), 64), SEARCH_RADIUS);
        LOGGER.info("[storage] {}", choice.log());
        return Optional.ofNullable(choice.point());
    }

    public static Optional<BlockCoord> foodPoint(ServerLevel level, BlockCoord origin, String item, int count) {
        List<StoragePoints.Spot> spots = spots(level, origin);
        StoragePoints.Choice choice = StoragePoints.withdraw(spots, origin, item, Math.max(1, count), SEARCH_RADIUS);
        LOGGER.info("[storage] {}", choice.log());
        return Optional.ofNullable(choice.point());
    }

    private static List<StoragePoints.Spot> spots(ServerLevel level, BlockCoord origin) {
        List<BlockCoord> known = SettlementChestStore.get(level).chests().nearest(origin, CANDIDATE_LIMIT);
        List<StoragePoints.Spot> spots = new ArrayList<>();
        for (BlockCoord pos : known) {
            if (distanceSqr(origin, pos) > (long) SEARCH_RADIUS * SEARCH_RADIUS) {
                continue;
            }
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!level.isLoaded(at)) {
                LOGGER.info("[storage] контейнер {} не в загруженном чанке — осмотр пропущен", coord(pos));
                continue;
            }
            String type = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock())
                    .toString();
            ContainerAccess.Open open = ContainerAccess.open(level, at);
            if (open instanceof ContainerAccess.Opened opened) {
                spots.add(new StoragePoints.Spot(
                        pos, type, new ContainerSnapshot(pos, new StackMapper().map(opened.container()))));
            } else if (open instanceof ContainerAccess.Refused refused) {
                LOGGER.info("[storage] контейнер {} ({}) отклонён: {}", coord(pos), type, refused.reason());
            }
        }
        return spots;
    }

    private static long distanceSqr(BlockCoord from, BlockCoord to) {
        long dx = (long) from.x() - to.x();
        long dy = (long) from.y() - to.y();
        long dz = (long) from.z() - to.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String coord(BlockCoord pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }
}
